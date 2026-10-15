package com.travelplatform.inventory.application;

import com.travelplatform.common.api.BusinessException;
import com.travelplatform.common.api.ErrorCode;
import com.travelplatform.inventory.api.ResourceResponse;
import com.travelplatform.inventory.domain.InventoryUnit;
import com.travelplatform.inventory.domain.InventoryUnitRepository;
import com.travelplatform.inventory.domain.Resource;
import com.travelplatform.inventory.domain.ResourceRepository;
import com.travelplatform.inventory.domain.ResourceStatus;
import com.travelplatform.inventory.domain.ResourceType;
import com.travelplatform.inventory.domain.Spec;
import com.travelplatform.inventory.domain.SpecRepository;
import com.travelplatform.inventory.infrastructure.InventoryLockRedis;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resource browsing and search (T053).
 *
 * <p>Availability is never answered from a cached "resource is bookable" flag: it is
 * derived from the inventory unit of {@code resource + date + spec}, because that is
 * the only object the booking path can actually lock (ADR-0002). Sold-out is a
 * per-date condition and must stay distinguishable from off-shelf, which is an
 * operational action that hides the resource from search results while keeping it
 * visible through a direct detail link with every booking entry point disabled.
 */
@Service
public class ResourceSearchService {

    static final String EMPTY_RESULT_MESSAGE = "没有找到匹配的资源，请调整目的地、日期或价格条件";
    static final String NO_DATE_MESSAGE = "未指定日期：可预订状态按资源+日期+规格判定，请选择日期后查询";

    private static final Set<String> SORTS = Set.of("priceasc", "pricedesc", "nameasc", "availabledesc");

    private final ResourceRepository resourceRepository;
    private final SpecRepository specRepository;
    private final InventoryUnitRepository unitRepository;
    private final InventoryLockRedis redis;

    public ResourceSearchService(ResourceRepository resourceRepository,
                                 SpecRepository specRepository,
                                 InventoryUnitRepository unitRepository,
                                 InventoryLockRedis redis) {
        this.resourceRepository = resourceRepository;
        this.specRepository = specRepository;
        this.unitRepository = unitRepository;
        this.redis = redis;
    }

    /** Search query; every filter is optional except that a price range must be coherent. */
    public record Criteria(
            String type,
            String destination,
            LocalDate date,
            Long priceMin,
            Long priceMax,
            String keyword,
            String sort) {
    }

    public ResourceResponse.SearchResult search(Criteria criteria) {
        ResourceType type = parseType(criteria.type());
        String sort = parseSort(criteria.sort());
        validatePriceRange(criteria.priceMin(), criteria.priceMax());

        List<Resource> candidates = loadCandidates(type, criteria.destination(), criteria.keyword());
        Map<String, List<Spec>> specsByResource = specsByResource(candidates);
        Map<String, List<InventoryUnit>> unitsByResource = unitsByResource(candidates, criteria.date());

        List<ResourceResponse.Summary> items = new ArrayList<>();
        for (Resource resource : candidates) {
            List<Spec> specs = specsByResource.getOrDefault(resource.getId(), List.of());
            Long priceFrom = minPrice(specs);
            Long priceTo = maxPrice(specs);
            if (!withinPriceRange(priceFrom, criteria.priceMin(), criteria.priceMax())) {
                continue;
            }
            Availability availability = availabilityOf(
                    resource, specs, unitsByResource.get(resource.getId()), criteria.date());
            items.add(new ResourceResponse.Summary(
                    resource.getId(),
                    resource.getType().name(),
                    resource.getName(),
                    resource.getDestination(),
                    resource.getStatus().name(),
                    resource.getType() == ResourceType.TRADABLE,
                    orderEntryAllowed(resource, criteria.date(), availability),
                    priceFrom,
                    priceTo,
                    availability.availableTotal()));
        }

        items.sort(comparator(sort));
        String message = items.isEmpty() ? EMPTY_RESULT_MESSAGE
                : criteria.date() == null ? NO_DATE_MESSAGE : null;
        return new ResourceResponse.SearchResult(items, items.size(), criteria.date() != null, message);
    }

    public ResourceResponse.Detail detail(String resourceId, LocalDate date) {
        Resource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "resource not found: " + resourceId));
        List<Spec> specs = specRepository.findByResourceId(resourceId);
        Map<String, InventoryUnit> units = date == null ? Map.of() : unitsBySpec(resourceId, date);

        List<ResourceResponse.SpecView> specViews = specs.stream()
                .map(spec -> {
                    InventoryUnit unit = units.get(spec.getId());
                    Integer available = unit == null ? null : available(unit);
                    boolean bookable = available != null && available > 0;
                    return new ResourceResponse.SpecView(spec.getId(), spec.getName(),
                            spec.getSpecType() == null ? null : spec.getSpecType().name(),
                            spec.getOccupancy(), spec.getPrice(), available, bookable);
                })
                .toList();

        Availability availability = availabilityOf(resource, specs, List.copyOf(units.values()), date);
        return new ResourceResponse.Detail(
                resource.getId(),
                resource.getType().name(),
                resource.getName(),
                resource.getDestination(),
                resource.getStatus().name(),
                resource.getType() == ResourceType.TRADABLE,
                availability.soldOut(),
                orderEntryAllowed(resource, date, availability),
                date,
                date != null && resource.getType() == ResourceType.TRADABLE,
                minPrice(specs),
                maxPrice(specs),
                availability.availableTotal(),
                specViews);
    }

    private List<Resource> loadCandidates(ResourceType type, String destination, String keyword) {
        List<Resource> resources = type == null
                ? resourceRepository.findByStatus(ResourceStatus.ON_SHELF)
                : resourceRepository.findByStatusAndType(ResourceStatus.ON_SHELF, type);
        return resources.stream()
                .filter(resource -> matches(destination, resource.getDestination()))
                .filter(resource -> matches(keyword, resource.getName()))
                .toList();
    }

    private boolean matches(String filter, String value) {
        if (filter == null || filter.isBlank()) {
            return true;
        }
        return value != null && value.toLowerCase(Locale.ROOT).contains(filter.trim().toLowerCase(Locale.ROOT));
    }

    private Map<String, List<Spec>> specsByResource(List<Resource> resources) {
        if (resources.isEmpty()) {
            return Map.of();
        }
        List<String> ids = resources.stream().map(Resource::getId).toList();
        return specRepository.findByResourceIdIn(ids).stream()
                .filter(spec -> spec.getResourceId() != null)
                .collect(Collectors.groupingBy(Spec::getResourceId));
    }

    private Map<String, List<InventoryUnit>> unitsByResource(List<Resource> resources, LocalDate date) {
        if (date == null || resources.isEmpty()) {
            return Map.of();
        }
        List<String> ids = resources.stream().map(Resource::getId).toList();
        return unitRepository.findByResourceIdInAndDate(ids, date).stream()
                .filter(unit -> unit.getResourceId() != null)
                .collect(Collectors.groupingBy(InventoryUnit::getResourceId));
    }

    private Map<String, InventoryUnit> unitsBySpec(String resourceId, LocalDate date) {
        return unitRepository.findByResourceIdAndDate(resourceId, date).stream()
                .filter(unit -> unit.getSpecId() != null)
                .collect(Collectors.toMap(InventoryUnit::getSpecId, unit -> unit, (first, second) -> first));
    }

    /**
     * Availability of a resource on a date. Content resources have no inventory unit
     * at all, so availability is not applicable rather than zero; without a date
     * nothing can be asserted either, so the totals stay {@code null} and sold-out
     * stays false instead of guessing.
     */
    private Availability availabilityOf(Resource resource, List<Spec> specs,
                                        List<InventoryUnit> units, LocalDate date) {
        if (resource.getType() == ResourceType.CONTENT) {
            return Availability.NOT_APPLICABLE;
        }
        return availability(specs, units, date);
    }

    /**
     * Availability of a resource on a date, aggregated over its specs. Without a
     * date nothing can be asserted, so the totals stay {@code null} and sold-out
     * stays false rather than guessing.
     */
    private Availability availability(List<Spec> specs, List<InventoryUnit> units, LocalDate date) {
        if (date == null) {
            return new Availability(null, false, false);
        }
        List<InventoryUnit> resourceUnits = units == null ? List.of() : units;
        if (resourceUnits.isEmpty() || specs.isEmpty()) {
            return new Availability(0, false, false);
        }
        int total = resourceUnits.stream().mapToInt(this::available).sum();
        return new Availability(total, total == 0, true);
    }

    /**
     * Order entry requires: a tradable resource, still on shelf, and sellable
     * inventory for the requested date. Content resources never expose an entry
     * point (FR-039), off-shelf and sold-out resources must not expose one either
     * (FR-038).
     */
    private boolean orderEntryAllowed(Resource resource, LocalDate date, Availability availability) {
        return resource.getType() == ResourceType.TRADABLE
                && resource.getStatus() == ResourceStatus.ON_SHELF
                && date != null
                && availability.hasUnits()
                && !availability.soldOut();
    }

    private int available(InventoryUnit unit) {
        redis.initialize(unit);
        Integer redisAvailable = redis.available(unit.getId());
        return redisAvailable != null ? redisAvailable : unit.getAvailable();
    }

    private Comparator<ResourceResponse.Summary> comparator(String sort) {
        return switch (sort) {
            case "pricedesc" -> Comparator.comparing(ResourceResponse.Summary::priceFrom,
                    Comparator.nullsLast(Comparator.reverseOrder()));
            case "nameasc" -> Comparator.comparing(ResourceResponse.Summary::name,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "availabledesc" -> Comparator.comparing(ResourceResponse.Summary::availableTotal,
                    Comparator.nullsLast(Comparator.reverseOrder()));
            default -> Comparator.comparing(ResourceResponse.Summary::priceFrom,
                    Comparator.nullsLast(Comparator.naturalOrder()));
        };
    }

    private ResourceType parseType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        try {
            return ResourceType.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "invalid type: " + type + ", expected TRADABLE or CONTENT");
        }
    }

    private String parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return "priceasc";
        }
        String normalized = sort.trim().toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
        if (!SORTS.contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "invalid sort: " + sort + ", expected one of " + SORTS);
        }
        return normalized;
    }

    private void validatePriceRange(Long priceMin, Long priceMax) {
        if (priceMin != null && priceMin < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "priceMin must not be negative");
        }
        if (priceMax != null && priceMax < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "priceMax must not be negative");
        }
        if (priceMin != null && priceMax != null && priceMin > priceMax) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "priceMin must not exceed priceMax");
        }
    }

    private boolean withinPriceRange(Long priceFrom, Long priceMin, Long priceMax) {
        if (priceFrom == null) {
            return priceMin == null && priceMax == null;
        }
        return (priceMin == null || priceFrom >= priceMin) && (priceMax == null || priceFrom <= priceMax);
    }

    private Long minPrice(List<Spec> specs) {
        return specs.stream().map(Spec::getPrice).filter(Objects::nonNull)
                .min(Comparator.naturalOrder()).orElse(null);
    }

    private Long maxPrice(List<Spec> specs) {
        return specs.stream().map(Spec::getPrice).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
    }

    /** Date-scoped availability of one resource. */
    private record Availability(Integer availableTotal, boolean soldOut, boolean hasUnits) {

        /** Content resources never carry sellable inventory. */
        static final Availability NOT_APPLICABLE = new Availability(null, false, false);
    }
}
