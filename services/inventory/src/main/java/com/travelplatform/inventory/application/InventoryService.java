package com.travelplatform.inventory.application;

import com.travelplatform.common.api.BusinessException;
import com.travelplatform.common.api.ErrorCode;
import com.travelplatform.inventory.api.AttractionToolResponse;
import com.travelplatform.inventory.api.InventoryController;
import com.travelplatform.inventory.domain.InventoryLock;
import com.travelplatform.inventory.domain.InventoryLockRepository;
import com.travelplatform.inventory.domain.InventoryUnit;
import com.travelplatform.inventory.domain.InventoryUnitRepository;
import com.travelplatform.inventory.domain.LockPurpose;
import com.travelplatform.inventory.domain.LockStatus;
import com.travelplatform.inventory.domain.Resource;
import com.travelplatform.inventory.domain.ResourceRepository;
import com.travelplatform.inventory.domain.ResourceStatus;
import com.travelplatform.inventory.domain.ResourceType;
import com.travelplatform.inventory.domain.Spec;
import com.travelplatform.inventory.domain.SpecRepository;
import com.travelplatform.inventory.domain.SpecType;
import com.travelplatform.inventory.infrastructure.InventoryLockRedis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class InventoryService {

    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final InventoryUnitRepository unitRepository;
    private final InventoryLockRepository lockRepository;
    private final ResourceRepository resourceRepository;
    private final SpecRepository specRepository;
    private final InventoryLockRedis redis;
    private final InventoryUnitPersister persister;
    private final InventoryEventPublisher events;

    public InventoryService(InventoryUnitRepository unitRepository,
                            InventoryLockRepository lockRepository,
                            ResourceRepository resourceRepository,
                            SpecRepository specRepository,
                            InventoryLockRedis redis,
                            InventoryUnitPersister persister,
                            InventoryEventPublisher events) {
        this.unitRepository = unitRepository;
        this.lockRepository = lockRepository;
        this.resourceRepository = resourceRepository;
        this.specRepository = specRepository;
        this.redis = redis;
        this.persister = persister;
        this.events = events;
    }

    public Integer query(String resourceId, LocalDate date, String specId) {
        InventoryUnit unit = requireUnit(resourceId, date, specId);
        redis.initialize(unit);
        Integer available = redis.available(unit.getId());
        return available != null ? available : unit.getAvailable();
    }

    public List<InventoryController.Candidate> candidates(String destination, LocalDate date) {
        List<Resource> resources = resourceRepository.findByDestinationAndType(destination, ResourceType.TRADABLE);
        List<InventoryController.Candidate> result = new ArrayList<>();
        for (Resource resource : resources) {
            List<InventoryController.Spec> specs = new ArrayList<>();
            for (Spec spec : specRepository.findByResourceId(resource.getId())) {
                Integer available = unitRepository
                        .findByResourceIdAndDateAndSpecId(resource.getId(), date, spec.getId())
                        .map(unit -> {
                            redis.initialize(unit);
                            Integer redisAvailable = redis.available(unit.getId());
                            return redisAvailable != null ? redisAvailable : unit.getAvailable();
                        })
                        .orElse(0);
                specs.add(new InventoryController.Spec(
                        spec.getId(), spec.getName(), spec.getPrice(), available));
            }
            if (!specs.isEmpty()) {
                result.add(new InventoryController.Candidate(
                        resource.getId(), resource.getName(), resource.getType().name(), specs));
            }
        }
        return result;
    }

    /**
     * Read-only attraction query for the planner tool. It has no lock or write
     * path; ATTRACTION trading remains guarded by {@link #lock(InventoryController.LockRequest)}.
     */
    public AttractionToolResponse.AttractionPage attractions(String destination,
                                                             String province,
                                                             String city,
                                                             LocalDate date,
                                                             int limit,
                                                             int offset) {
        validateAttractionQuery(destination, province, city, limit, offset);
        List<Resource> resources = resourceRepository
                .findByStatusAndType(ResourceStatus.ON_SHELF, ResourceType.ATTRACTION)
                .stream()
                .filter(resource -> matchesDestination(destination, resource))
                .filter(resource -> matchesField(province, resource.getProvince()))
                .filter(resource -> matchesField(city, resource.getCity()))
                .toList();

        Map<String, List<Spec>> specsByResource = attractionSpecs(resources);
        Map<String, InventoryUnit> unitsByResourceSpec = attractionUnits(resources, date);
        List<AttractionToolResponse.AttractionItem> allItems = resources.stream()
                .map(resource -> toAttractionItem(
                        resource,
                        specsByResource.getOrDefault(resource.getId(), List.of()),
                        unitsByResourceSpec,
                        date))
                .sorted(attractionComparator())
                .toList();

        int total = allItems.size();
        int from = Math.min(offset, total);
        int to = Math.min(from + limit, total);
        return new AttractionToolResponse.AttractionPage(
                total, offset, limit, to < total, List.copyOf(allItems.subList(from, to)));
    }

    public InventoryController.LockResponse lock(InventoryController.LockRequest request) {
        Resource resource = resourceRepository.findById(request.resourceId()).orElse(null);
        if (resource != null && resource.getType() == ResourceType.ATTRACTION) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "attraction trading is not enabled until V2-11");
        }
        InventoryUnit unit = requireUnit(request.resourceId(), request.date(), request.specId());
        redis.initialize(unit);

        long result = redis.tryLock(unit.getId(), request.quantity());
        if (result != 1) {
            recordSoldOutQuietly(request);
            throw new BusinessException(ErrorCode.CONFLICT, "insufficient stock for requested unit");
        }

        InventoryLock lock = new InventoryLock();
        lock.setId(UUID.randomUUID().toString());
        lock.setInventoryUnitId(unit.getId());
        lock.setQuantity(request.quantity());
        lock.setPurpose(LockPurpose.valueOf(request.purpose()));
        lock.setTtlSeconds(request.ttlSeconds() == null ? 900 : request.ttlSeconds());
        lock.setStatus(LockStatus.ACTIVE);
        lock.setHolderType(request.holderType());
        lock.setHolderId(request.holderId());
        lock.setCreatedAt(Instant.now());

        applyWithRetry(() -> persister.persistLock(lock, request.quantity()));
        return new InventoryController.LockResponse(lock.getId());
    }

    public boolean release(String lockId) {
        InventoryLock lock = lockRepository.findById(lockId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "inventory lock not found"));
        if (lock.getStatus() == LockStatus.RELEASED) {
            return false;
        }

        long result = redis.release(lockId, lock.getInventoryUnitId(), lock.getQuantity());
        if (result == 1) {
            applyWithRetry(() -> persister.persistRelease(lock, lock.getQuantity()));
            return true;
        }
        persister.markReleased(lockId);
        return false;
    }

    private InventoryUnit requireUnit(String resourceId, LocalDate date, String specId) {
        return unitRepository.findByResourceIdAndDateAndSpecId(resourceId, date, specId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "inventory unit not found"));
    }

    private void validateAttractionQuery(String destination,
                                         String province,
                                         String city,
                                         int limit,
                                         int offset) {
        if (isBlank(destination) && isBlank(province) && isBlank(city)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "at least one of destination, province, city is required");
        }
        if (limit < 1 || limit > 50) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "limit must be between 1 and 50");
        }
        if (offset < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "offset must not be negative");
        }
    }

    private Map<String, List<Spec>> attractionSpecs(List<Resource> resources) {
        if (resources.isEmpty()) {
            return Map.of();
        }
        List<String> ids = resources.stream().map(Resource::getId).toList();
        return specRepository.findByResourceIdIn(ids).stream()
                .filter(spec -> spec.getResourceId() != null)
                .collect(Collectors.groupingBy(Spec::getResourceId));
    }

    private Map<String, InventoryUnit> attractionUnits(List<Resource> resources, LocalDate date) {
        if (date == null || resources.isEmpty()) {
            return Map.of();
        }
        List<String> ids = resources.stream().map(Resource::getId).toList();
        return unitRepository.findByResourceIdInAndDate(ids, date).stream()
                .filter(unit -> unit.getResourceId() != null && unit.getSpecId() != null)
                .collect(Collectors.toMap(
                        unit -> unit.getResourceId() + "|" + unit.getSpecId(),
                        unit -> unit,
                        (first, second) -> first));
    }

    private AttractionToolResponse.AttractionItem toAttractionItem(
            Resource resource,
            List<Spec> specs,
            Map<String, InventoryUnit> unitsByResourceSpec,
            LocalDate date) {
        boolean free = resource.getTicketPrice() != null && resource.getTicketPrice() == 0L;
        Spec ticketSpec = free ? null : selectTicketSpec(specs);
        Long price;
        if (free) {
            price = 0L;
        } else if (ticketSpec == null) {
            price = null;
        } else {
            price = ticketSpec.getPrice();
        }
        Integer available = null;
        if (!free && ticketSpec != null && date != null) {
            InventoryUnit unit = unitsByResourceSpec.get(resource.getId() + "|" + ticketSpec.getId());
            available = unit == null ? 0 : available(unit);
        }
        return new AttractionToolResponse.AttractionItem(
                resource.getId(),
                resource.getName(),
                resource.getProvince(),
                resource.getCity(),
                resource.getLevel(),
                resource.getHonorTitle(),
                resource.getSummary(),
                resource.getSuggestedHours(),
                resource.getTicketPrice(),
                free,
                resource.getLatitude(),
                resource.getLongitude(),
                resource.getCoordinateStatus() == null ? null : resource.getCoordinateStatus().name(),
                ticketSpec == null ? null : ticketSpec.getId(),
                ticketSpec == null ? null : ticketSpec.getName(),
                price,
                available);
    }

    private Spec selectTicketSpec(List<Spec> specs) {
        return specs.stream()
                .filter(spec -> spec.getSpecType() == SpecType.TICKET_TYPE)
                .min(Comparator
                        .comparing(Spec::getPrice, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Spec::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
    }

    private Comparator<AttractionToolResponse.AttractionItem> attractionComparator() {
        return Comparator
                .comparingInt((AttractionToolResponse.AttractionItem item) -> levelRank(item.level()))
                .reversed()
                .thenComparing(AttractionToolResponse.AttractionItem::ticketPrice,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(AttractionToolResponse.AttractionItem::name,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(AttractionToolResponse.AttractionItem::resourceId,
                        Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private int levelRank(String level) {
        if (level == null) {
            return 0;
        }
        return switch (level.trim().toUpperCase(Locale.ROOT)) {
            case "5A" -> 5;
            case "4A" -> 4;
            case "3A" -> 3;
            case "2A" -> 2;
            case "1A" -> 1;
            default -> 0;
        };
    }

    private boolean matchesDestination(String destination, Resource resource) {
        if (isBlank(destination)) {
            return true;
        }
        return contains(resource.getDestination(), destination)
                || contains(resource.getProvince(), destination)
                || contains(resource.getCity(), destination);
    }

    private boolean matchesField(String filter, String value) {
        return isBlank(filter) || contains(value, filter);
    }

    private boolean contains(String value, String filter) {
        return value != null && value.toLowerCase(Locale.ROOT)
                .contains(filter.trim().toLowerCase(Locale.ROOT));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private int available(InventoryUnit unit) {
        redis.initialize(unit);
        Integer redisAvailable = redis.available(unit.getId());
        return redisAvailable != null ? redisAvailable : unit.getAvailable();
    }

    private void applyWithRetry(Runnable action) {
        for (int attempt = 0; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                action.run();
                return;
            } catch (ObjectOptimisticLockingFailureException e) {
                if (attempt == MAX_RETRY_ATTEMPTS - 1) {
                    throw e;
                }
            }
        }
    }

    /**
     * 库存不足是「拒绝预订」的业务结果，没有业务写入可与其同事务提交，
     * 因此该提示性事件不得掩盖拒绝本身：发布失败只记日志（FR-024 优先）。
     */
    private void recordSoldOutQuietly(InventoryController.LockRequest request) {
        try {
            events.inventorySoldOut(request.resourceId(), request.date(), request.specId(),
                    request.quantity(), request.holderType(), request.holderId());
        } catch (RuntimeException e) {
            log.warn("failed to record InventorySoldOut for {}/{}/{}: {}",
                    request.resourceId(), request.specId(), request.date(), e.toString());
        }
    }
}
