package com.travelplatform.itinerary.application;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ItineraryService {

    private final ItineraryRepository repository;
    private final ItineraryItemRepository itemRepository;
    private final ItineraryEventPublisher events;
    private final ItineraryStateMachine stateMachine = new ItineraryStateMachine();

    public ItineraryService(ItineraryRepository repository,
                            ItineraryItemRepository itemRepository,
                            ItineraryEventPublisher events) {
        this.repository = repository;
        this.itemRepository = itemRepository;
        this.events = events;
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
        return itemRepository.findByItineraryId(itineraryId);
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

    private ItineraryItem toItem(String itineraryId, ItineraryController.ItemRequest request) {
        ItineraryItem item = new ItineraryItem();
        item.setId(UUID.randomUUID().toString());
        item.setItineraryId(itineraryId);
        item.setDayDate(request.dayDate());
        item.setDescription(request.description());
        item.setResourceType(request.resourceType());
        item.setResourceId(request.resourceId());
        item.setSpecId(request.specId());
        item.setSource(request.source() == null ? ItineraryItemSource.AI : request.source());
        return item;
    }
}
