package com.travelplatform.itinerary.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplatform.common.api.BusinessException;
import com.travelplatform.common.api.ErrorCode;
import com.travelplatform.itinerary.api.ItineraryController;
import com.travelplatform.itinerary.domain.Itinerary;
import com.travelplatform.itinerary.domain.ItineraryItem;
import com.travelplatform.itinerary.domain.ItineraryItemRepository;
import com.travelplatform.itinerary.domain.ItineraryItemSource;
import com.travelplatform.itinerary.domain.ItineraryRepository;
import com.travelplatform.itinerary.domain.ItineraryStateMachine;
import com.travelplatform.itinerary.domain.ItineraryStatus;
import com.travelplatform.itinerary.domain.ItineraryTimeSlot;
import com.travelplatform.itinerary.infrastructure.InventoryClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class ItineraryService {

    private final ItineraryRepository repository;
    private final ItineraryItemRepository itemRepository;
    private final ItineraryEventPublisher events;
    private final ObjectMapper objectMapper;
    private final InventoryClient inventoryClient;
    private final ItineraryStateMachine stateMachine = new ItineraryStateMachine();

    public ItineraryService(ItineraryRepository repository,
                            ItineraryItemRepository itemRepository,
                            ItineraryEventPublisher events,
                            ObjectMapper objectMapper,
                            InventoryClient inventoryClient) {
        this.repository = repository;
        this.itemRepository = itemRepository;
        this.events = events;
        this.objectMapper = objectMapper;
        this.inventoryClient = inventoryClient;
    }

    @Transactional
    public Itinerary create(String userId, ItineraryController.CreateItineraryRequest request) {
        Itinerary itinerary = new Itinerary();
        itinerary.setId(UUID.randomUUID().toString());
        itinerary.setUserId(userId);
        itinerary.setDestination(request.destination());
        itinerary.setStartDate(request.startDate());
        itinerary.setEndDate(request.endDate());
        itinerary.setPartySize(request.partySize());
        itinerary.setBudget(request.budget());
        itinerary.setWarnings(serializeWarnings(request.warnings()));
        itinerary.setStatus(ItineraryStatus.DRAFT);
        itinerary.setHasOrder(false);
        itinerary.setCreatedAt(Instant.now());
        itinerary.setUpdatedAt(Instant.now());
        repository.save(itinerary);

        if (request.items() != null) {
            for (ItineraryController.ItemRequest itemRequest : request.items()) {
                itemRepository.save(toItem(itinerary.getId(), itemRequest));
            }
        }
        return itinerary;
    }

    public Itinerary get(String userId, String itineraryId) {
        return repository.findByIdAndUserId(itineraryId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "itinerary not found"));
    }

    public List<ItineraryItem> items(String itineraryId) {
        return itemRepository.findByItineraryIdOrderByDayDateAscTimeSlotAscSortOrderAsc(itineraryId);
    }

    public List<String> warnings(Itinerary itinerary) {
        if (itinerary.getWarnings() == null || itinerary.getWarnings().isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(itinerary.getWarnings(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            return List.of();
        }
    }

    @Transactional
    public Itinerary update(String userId, String itineraryId, ItineraryController.UpdateItineraryRequest request) {
        Itinerary itinerary = get(userId, itineraryId);
        stateMachine.edit(itinerary);
        itinerary.setDestination(request.destination());
        itinerary.setStartDate(request.startDate());
        itinerary.setEndDate(request.endDate());
        itinerary.setPartySize(request.partySize());
        itinerary.setBudget(request.budget());
        itinerary.setWarnings(serializeWarnings(request.warnings()));
        itinerary.setUpdatedAt(Instant.now());
        repository.save(itinerary);

        itemRepository.deleteByItineraryId(itineraryId);
        if (request.items() != null) {
            for (ItineraryController.ItemRequest itemRequest : request.items()) {
                itemRepository.save(toItem(itineraryId, itemRequest));
            }
        }
        // 行程变更通知只在「已存在订单」的行程被修改时触发（GLOSSARY 行程变更通知）。
        if (Boolean.TRUE.equals(itinerary.getHasOrder())) {
            events.itineraryChanged(itinerary);
        }
        return itinerary;
    }

    @Transactional
    public Itinerary confirm(String userId, String itineraryId) {
        Itinerary itinerary = get(userId, itineraryId);
        stateMachine.confirm(itinerary);
        itinerary.setUpdatedAt(Instant.now());
        Itinerary confirmed = repository.save(itinerary);
        events.itineraryConfirmed(confirmed);
        return confirmed;
    }

    @Transactional
    public void delete(String userId, String itineraryId) {
        Itinerary itinerary = get(userId, itineraryId);
        stateMachine.ensureDeletable(itinerary);
        itemRepository.deleteByItineraryId(itineraryId);
        repository.delete(itinerary);
    }

    @Transactional
    public Itinerary lock(String userId, String itineraryId) {
        Itinerary itinerary = get(userId, itineraryId);
        stateMachine.lock(itinerary);
        itinerary.setUpdatedAt(Instant.now());
        return repository.save(itinerary);
    }

    @Transactional
    public Itinerary release(String userId, String itineraryId) {
        Itinerary itinerary = get(userId, itineraryId);
        stateMachine.release(itinerary);
        itinerary.setUpdatedAt(Instant.now());
        return repository.save(itinerary);
    }

    @Transactional
    public Itinerary complete(String userId, String itineraryId) {
        Itinerary itinerary = get(userId, itineraryId);
        stateMachine.complete(itinerary);
        itinerary.setUpdatedAt(Instant.now());
        return repository.save(itinerary);
    }

    @Transactional
    public Itinerary addItem(String userId, String itineraryId, ItineraryController.AddItemRequest request) {
        Itinerary itinerary = get(userId, itineraryId);
        requireDraft(itinerary);
        if (request.timeSlot() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "timeSlot is required");
        }
        List<ItineraryItem> slotItems = itemRepository
                .findByItineraryIdAndTimeSlotOrderBySortOrderAsc(itineraryId, request.timeSlot());
        if (slotItems.size() >= 3) {
            throw new BusinessException(ErrorCode.CONFLICT, "time slot already has 3 items");
        }

        InventoryClient.ResourceDetail resource = requireAttraction(request.resourceId());
        long ticketPrice = resource.ticketPrice() == null ? 0L : resource.ticketPrice();
        String specId = request.specId();
        String requestedSpecId = request.specId();
        if (requestedSpecId != null && !requestedSpecId.isBlank()) {
            boolean validSpec = resource.specs() != null && resource.specs().stream()
                    .anyMatch(spec -> requestedSpecId.equals(spec.specId())
                            && "TICKET_TYPE".equals(spec.specType()));
            if (!validSpec) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "specId does not belong to a TICKET_TYPE spec of this resource");
            }
        }
        if (ticketPrice > 0 && (specId == null || specId.isBlank())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "a paid attraction requires a TICKET_TYPE specId");
        }
        if (ticketPrice == 0) {
            specId = null;
        }

        ItineraryItem item = new ItineraryItem();
        item.setId(UUID.randomUUID().toString());
        item.setItineraryId(itineraryId);
        item.setDayDate(request.dayDate());
        item.setName(blank(request.name()) ? resource.name() : request.name().trim());
        item.setDescription(blank(request.description())
                ? defaultDescription(resource)
                : request.description().trim());
        item.setSuggestedHours(request.suggestedHours() != null
                ? request.suggestedHours()
                : resource.suggestedHours());
        item.setTicketPrice(ticketPrice);
        item.setTimeSlot(request.timeSlot());
        item.setSortOrder(slotItems.stream()
                .map(ItineraryItem::getSortOrder)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0) + 1);
        item.setResourceType("ATTRACTION");
        item.setResourceId(resource.id());
        item.setSpecId(specId);
        item.setSource(ItineraryItemSource.MANUAL);
        itemRepository.save(item);
        itinerary.setUpdatedAt(Instant.now());
        return repository.save(itinerary);
    }

    @Transactional
    public Itinerary deleteItem(String userId, String itineraryId, String itemId) {
        Itinerary itinerary = get(userId, itineraryId);
        requireDraft(itinerary);
        ItineraryItem item = itemRepository.findById(itemId)
                .filter(candidate -> itineraryId.equals(candidate.getItineraryId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "itinerary item not found"));
        ItineraryTimeSlot slot = item.getTimeSlot();
        itemRepository.delete(item);
        if (slot != null) {
            List<ItineraryItem> remaining = itemRepository
                    .findByItineraryIdAndTimeSlotOrderBySortOrderAsc(itineraryId, slot);
            int order = 1;
            for (ItineraryItem remainingItem : remaining) {
                remainingItem.setSortOrder(order++);
            }
            itemRepository.saveAll(remaining);
        }
        itinerary.setUpdatedAt(Instant.now());
        return repository.save(itinerary);
    }

    private void requireDraft(Itinerary itinerary) {
        if (itinerary.getStatus() != ItineraryStatus.DRAFT) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "only draft itineraries can add or delete items");
        }
    }

    private InventoryClient.ResourceDetail requireAttraction(String resourceId) {
        InventoryClient.ResourceApiResponse response;
        try {
            response = inventoryClient.detail(resourceId);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "resource not found: " + resourceId);
        }
        if (response == null || response.data() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "resource not found: " + resourceId);
        }
        InventoryClient.ResourceDetail resource = response.data();
        if (!"ATTRACTION".equals(resource.type())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "resource is not an ATTRACTION: " + resourceId);
        }
        return resource;
    }

    private String defaultDescription(InventoryClient.ResourceDetail resource) {
        return blank(resource.summary()) ? "景点" : resource.summary();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private ItineraryItem toItem(String itineraryId, ItineraryController.ItemRequest request) {
        ItineraryItem item = new ItineraryItem();
        item.setId(UUID.randomUUID().toString());
        item.setItineraryId(itineraryId);
        item.setDayDate(request.dayDate());
        item.setName(request.name());
        item.setDescription(request.description());
        item.setSuggestedHours(request.suggestedHours());
        item.setTicketPrice(request.ticketPrice());
        item.setResourceType(request.resourceType());
        item.setResourceId(request.resourceId());
        item.setSpecId(request.specId());
        item.setSource(request.source() == null ? ItineraryItemSource.AI : request.source());
        item.setTimeSlot(request.timeSlot());
        item.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        return item;
    }

    private String serializeWarnings(List<String> warnings) {
        if (warnings == null || warnings.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(warnings);
        } catch (Exception e) {
            return null;
        }
    }
}
