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
import com.travelplatform.inventory.domain.SpecType;
import com.travelplatform.inventory.infrastructure.InventoryLockRedis;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResourceSearchServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 16);

    private final ResourceRepository resourceRepository = mock(ResourceRepository.class);
    private final SpecRepository specRepository = mock(SpecRepository.class);
    private final InventoryUnitRepository unitRepository = mock(InventoryUnitRepository.class);
    private final InventoryLockRedis redis = mock(InventoryLockRedis.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ResourceSearchService service =
            new ResourceSearchService(resourceRepository, specRepository, unitRepository, redis, objectMapper);

    @Test
    void searchFiltersByDestinationAndTypeAndSortsByPriceAscending() {
        Resource hotel = resource("r-hotel-1", ResourceType.TRADABLE, "丽江古城精品酒店", "云南", ResourceStatus.ON_SHELF);
        Resource train = resource("r-train-1", ResourceType.TRADABLE, "昆明大理动车票", "云南", ResourceStatus.ON_SHELF);
        Resource beijingHotel = resource("r-hotel-2", ResourceType.TRADABLE, "王府井商务酒店", "北京", ResourceStatus.ON_SHELF);
        when(resourceRepository.findByStatusAndType(ResourceStatus.ON_SHELF, ResourceType.TRADABLE))
                .thenReturn(List.of(hotel, train, beijingHotel));
        when(specRepository.findByResourceIdIn(any())).thenReturn(List.of(
                spec("s-room-1", "r-hotel-1", "标准大床房", 500, SpecType.ROOM_TYPE),
                spec("s-schedule-1", "r-train-1", "二等座", 145, SpecType.SCHEDULE),
                spec("s-room-3", "r-hotel-2", "标准双床房", 680, SpecType.ROOM_TYPE)));
        when(unitRepository.findByResourceIdInAndDate(any(), eq(DATE))).thenReturn(List.of(
                unit("u-hotel-1", "r-hotel-1", "s-room-1", 20),
                unit("u-train-1", "r-train-1", "s-schedule-1", 12)));

        ResourceResponse.SearchResult result = service.search(
                new ResourceSearchService.Criteria("TRADABLE", "云南", DATE, null, null, null, null, null));

        assertEquals(2, result.total(), "only on-shelf tradable resources of the requested destination match");
        assertEquals(List.of("r-train-1", "r-hotel-1"),
                result.items().stream().map(ResourceResponse.Summary::id).toList(),
                "default sort is price ascending");
        assertEquals(145L, result.items().get(0).priceFrom());
        assertEquals(500L, result.items().get(1).priceFrom());
        assertTrue(result.availabilityKnown());
        assertNull(result.message());
    }

    @Test
    void searchKeepsSoldOutAndOffShelfResourcesOutOfTheOrderEntry() {
        Resource soldOut = resource("r-hotel-1", ResourceType.TRADABLE, "丽江古城精品酒店", "云南", ResourceStatus.ON_SHELF);
        when(resourceRepository.findByStatus(ResourceStatus.ON_SHELF)).thenReturn(List.of(soldOut));
        when(specRepository.findByResourceIdIn(any())).thenReturn(List.of(
                spec("s-room-1", "r-hotel-1", "标准大床房", 500, SpecType.ROOM_TYPE)));
        when(unitRepository.findByResourceIdInAndDate(any(), eq(DATE))).thenReturn(List.of(
                unit("u-hotel-1", "r-hotel-1", "s-room-1", 0)));

        ResourceResponse.SearchResult result = service.search(
                new ResourceSearchService.Criteria(null, "云南", DATE, null, null, null, null, null));

        assertEquals(1, result.total());
        assertEquals(0, result.items().get(0).availableTotal());
        assertFalse(result.items().get(0).orderEntryAllowed(), "a sold-out resource must not offer an order entry");
    }

    @Test
    void searchHidesOffShelfResourcesFromBrowsing() {
        when(resourceRepository.findByStatus(ResourceStatus.ON_SHELF)).thenReturn(List.of());

        ResourceResponse.SearchResult result = service.search(
                new ResourceSearchService.Criteria(null, "云南", DATE, null, null, null, null, null));

        assertEquals(0, result.total());
        assertEquals(ResourceSearchService.EMPTY_RESULT_MESSAGE, result.message(),
                "an empty result must carry an explicit hint, never a silent blank page");
    }

    @Test
    void searchWithoutDateDoesNotClaimAvailability() {
        Resource hotel = resource("r-hotel-1", ResourceType.TRADABLE, "丽江古城精品酒店", "云南", ResourceStatus.ON_SHELF);
        when(resourceRepository.findByStatus(ResourceStatus.ON_SHELF)).thenReturn(List.of(hotel));
        when(specRepository.findByResourceIdIn(any())).thenReturn(List.of(
                spec("s-room-1", "r-hotel-1", "标准大床房", 500, SpecType.ROOM_TYPE)));

        ResourceResponse.SearchResult result = service.search(
                new ResourceSearchService.Criteria(null, null, null, null, null, null, null, null));

        assertFalse(result.availabilityKnown());
        assertNull(result.items().get(0).availableTotal(), "availability is date-scoped and unknown without a date");
        assertFalse(result.items().get(0).orderEntryAllowed());
        assertEquals(ResourceSearchService.NO_DATE_MESSAGE, result.message());
    }

    @Test
    void searchNeverOffersOrderEntryForContentResources() {
        Resource content = resource("r-food-1", ResourceType.CONTENT, "过桥米线", "云南", ResourceStatus.ON_SHELF);
        when(resourceRepository.findByStatusAndType(ResourceStatus.ON_SHELF, ResourceType.CONTENT))
                .thenReturn(List.of(content));
        when(specRepository.findByResourceIdIn(any())).thenReturn(List.of());

        ResourceResponse.SearchResult result = service.search(
                new ResourceSearchService.Criteria("CONTENT", "云南", DATE, null, null, null, null, null));

        assertEquals(1, result.total());
        assertFalse(result.items().get(0).tradable());
        assertFalse(result.items().get(0).orderEntryAllowed(), "content resources must never expose an order entry");
        assertNull(result.items().get(0).availableTotal(), "content resources have no sellable inventory");
    }

    @Test
    void searchAppliesPriceRangeAndSortsDescending() {
        Resource hotel = resource("r-hotel-1", ResourceType.TRADABLE, "丽江古城精品酒店", "云南", ResourceStatus.ON_SHELF);
        Resource train = resource("r-train-1", ResourceType.TRADABLE, "昆明大理动车票", "云南", ResourceStatus.ON_SHELF);
        when(resourceRepository.findByStatus(ResourceStatus.ON_SHELF)).thenReturn(List.of(hotel, train));
        when(specRepository.findByResourceIdIn(any())).thenReturn(List.of(
                spec("s-room-1", "r-hotel-1", "标准大床房", 500, SpecType.ROOM_TYPE),
                spec("s-schedule-1", "r-train-1", "二等座", 145, SpecType.SCHEDULE)));
        when(unitRepository.findByResourceIdInAndDate(any(), eq(DATE))).thenReturn(List.of(
                unit("u-hotel-1", "r-hotel-1", "s-room-1", 3),
                unit("u-train-1", "r-train-1", "s-schedule-1", 9)));

        ResourceResponse.SearchResult result = service.search(
                new ResourceSearchService.Criteria(null, null, DATE, 200L, 900L, null, "price_desc", null));

        assertEquals(1, result.total(), "the 145 CNY train ticket is below the price floor");
        assertEquals("r-hotel-1", result.items().get(0).id());
    }

    @Test
    void searchMatchesKeywordAgainstResourceName() {
        Resource hotel = resource("r-hotel-1", ResourceType.TRADABLE, "丽江古城精品酒店", "云南", ResourceStatus.ON_SHELF);
        Resource train = resource("r-train-1", ResourceType.TRADABLE, "昆明大理动车票", "云南", ResourceStatus.ON_SHELF);
        when(resourceRepository.findByStatus(ResourceStatus.ON_SHELF)).thenReturn(List.of(hotel, train));
        when(specRepository.findByResourceIdIn(any())).thenReturn(List.of(
                spec("s-room-1", "r-hotel-1", "标准大床房", 500, SpecType.ROOM_TYPE),
                spec("s-schedule-1", "r-train-1", "二等座", 145, SpecType.SCHEDULE)));
        when(unitRepository.findByResourceIdInAndDate(any(), eq(DATE))).thenReturn(List.of(
                unit("u-hotel-1", "r-hotel-1", "s-room-1", 3),
                unit("u-train-1", "r-train-1", "s-schedule-1", 9)));

        ResourceResponse.SearchResult result = service.search(
                new ResourceSearchService.Criteria(null, null, DATE, null, null, "动车", null, null));

        assertEquals(1, result.total());
        assertEquals("r-train-1", result.items().get(0).id());
    }

    @Test
    void searchRejectsInvalidFiltersInsteadOfReturningBlankResults() {
        BusinessException invalidType = assertThrows(BusinessException.class, () -> service.search(
                new ResourceSearchService.Criteria("HOTEL", null, DATE, null, null, null, null, null)));
        assertEquals(ErrorCode.BAD_REQUEST, invalidType.getErrorCode());

        BusinessException invalidSort = assertThrows(BusinessException.class, () -> service.search(
                new ResourceSearchService.Criteria(null, null, DATE, null, null, null, "cheapest", null)));
        assertEquals(ErrorCode.BAD_REQUEST, invalidSort.getErrorCode());

        BusinessException invalidRange = assertThrows(BusinessException.class, () -> service.search(
                new ResourceSearchService.Criteria(null, null, DATE, 900L, 100L, null, null, null)));
        assertEquals(ErrorCode.BAD_REQUEST, invalidRange.getErrorCode());
    }

    @Test
    void searchFiltersContentByCategory() {
        Resource food = content("r-food-1", "过桥米线", "云南", "FOOD", "蒙自传统米线");
        Resource culture = content("r-culture-1", "丽江古城", "云南", "CULTURE", "纳西族古城");
        when(resourceRepository.findByStatusAndType(ResourceStatus.ON_SHELF, ResourceType.CONTENT))
                .thenReturn(List.of(food, culture));
        when(specRepository.findByResourceIdIn(any())).thenReturn(List.of());

        ResourceResponse.SearchResult result = service.search(
                new ResourceSearchService.Criteria("CONTENT", "云南", null, null, null, null, null, "FOOD"));

        assertEquals(1, result.total(), "only the requested content category matches");
        assertEquals("r-food-1", result.items().get(0).id());
        assertEquals("FOOD", result.items().get(0).contentCategory());
        assertFalse(result.items().get(0).tradable());
        assertFalse(result.items().get(0).orderEntryAllowed(), "content resources never expose an order entry");
    }

    @Test
    void searchMatchesKeywordAgainstContentDescription() {
        Resource food = content("r-food-2", "汽锅鸡", "云南", "FOOD", "建水紫陶汽锅蒸制");
        when(resourceRepository.findByStatusAndType(ResourceStatus.ON_SHELF, ResourceType.CONTENT))
                .thenReturn(List.of(food));
        when(specRepository.findByResourceIdIn(any())).thenReturn(List.of());

        ResourceResponse.SearchResult result = service.search(
                new ResourceSearchService.Criteria("CONTENT", "云南", null, null, null, "紫陶", null, null));

        assertEquals(1, result.total(), "the keyword matches the description, not only the name");
        assertEquals("r-food-2", result.items().get(0).id());
    }

    @Test
    void searchRejectsCategoryOnTradableResources() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.search(
                new ResourceSearchService.Criteria("TRADABLE", "云南", DATE, null, null, null, null, "FOOD")));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    void searchRejectsUnknownContentCategory() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.search(
                new ResourceSearchService.Criteria("CONTENT", "云南", null, null, null, null, null, "SPA")));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    void searchReturnsAttractionMetadataWithoutTicketInventory() {
        Resource attraction = new Resource();
        attraction.setId("r-attraction-v2");
        attraction.setType(ResourceType.ATTRACTION);
        attraction.setName("玉龙雪山");
        attraction.setDestination("云南");
        attraction.setProvince("云南");
        attraction.setCity("丽江");
        attraction.setLevel("5A");
        attraction.setHonorTitle("纳西圣山");
        attraction.setSummary("雪山与蓝月谷组成的丽江标志性景区");
        attraction.setSuggestedHours(new BigDecimal("4.0"));
        attraction.setTicketPrice(100L);
        attraction.setLatitude(new BigDecimal("27.1000000"));
        attraction.setLongitude(new BigDecimal("100.1800000"));
        attraction.setCoordinateStatus(com.travelplatform.inventory.domain.CoordinateStatus.READY);
        attraction.setStatus(ResourceStatus.ON_SHELF);
        when(resourceRepository.findByStatusAndType(ResourceStatus.ON_SHELF, ResourceType.ATTRACTION))
                .thenReturn(List.of(attraction));
        when(specRepository.findByResourceIdIn(any())).thenReturn(List.of());

        ResourceResponse.SearchResult result = service.search(
                new ResourceSearchService.Criteria("ATTRACTION", "丽江", DATE, null, null, null, null, null));

        assertEquals(1, result.total());
        ResourceResponse.Summary item = result.items().get(0);
        assertEquals("ATTRACTION", item.type());
        assertTrue(item.tradable(), "ATTRACTION has a ticket model");
        assertFalse(item.orderEntryAllowed(), "an attraction without ticket inventory is not orderable");
        assertEquals("云南", item.province());
        assertEquals("丽江", item.city());
        assertEquals("5A", item.level());
        assertEquals("纳西圣山", item.honorTitle());
        assertEquals(0, new BigDecimal("4.0").compareTo(item.suggestedHours()));
        assertEquals(100L, item.ticketPrice());
        assertEquals("READY", item.coordinateStatus());
        assertEquals("ATTRACTION", item.contentCategory());
    }

    @Test
    void paidAttractionWithTicketInventoryAllowsOrderEntry() {
        Resource attraction = new Resource();
        attraction.setId("r-attraction-paid");
        attraction.setType(ResourceType.ATTRACTION);
        attraction.setName("玉龙雪山");
        attraction.setDestination("云南");
        attraction.setProvince("云南");
        attraction.setCity("丽江");
        attraction.setTicketPrice(100L);
        attraction.setStatus(ResourceStatus.ON_SHELF);
        when(resourceRepository.findByStatusAndType(ResourceStatus.ON_SHELF, ResourceType.ATTRACTION))
                .thenReturn(List.of(attraction));
        when(specRepository.findByResourceIdIn(any())).thenReturn(List.of(
                spec("s-ticket-paid", "r-attraction-paid", "门票", 100, SpecType.TICKET_TYPE)));
        when(unitRepository.findByResourceIdInAndDate(any(), eq(DATE))).thenReturn(List.of(
                unit("u-ticket-paid", "r-attraction-paid", "s-ticket-paid", 12)));
        when(redis.available("u-ticket-paid")).thenReturn(12);

        ResourceResponse.SearchResult result = service.search(
                new ResourceSearchService.Criteria("ATTRACTION", "丽江", DATE, null, null, null, null, null));

        assertTrue(result.items().get(0).orderEntryAllowed(),
                "a paid attraction with sellable TICKET_TYPE inventory must be orderable");
    }

    @Test
    void freeAttractionWithoutTicketSpecNeverAllowsOrderEntry() {
        Resource attraction = new Resource();
        attraction.setId("r-attraction-free");
        attraction.setType(ResourceType.ATTRACTION);
        attraction.setName("丽江古城");
        attraction.setDestination("云南");
        attraction.setProvince("云南");
        attraction.setCity("丽江");
        attraction.setTicketPrice(0L);
        attraction.setStatus(ResourceStatus.ON_SHELF);
        when(resourceRepository.findByStatusAndType(ResourceStatus.ON_SHELF, ResourceType.ATTRACTION))
                .thenReturn(List.of(attraction));
        when(specRepository.findByResourceIdIn(any())).thenReturn(List.of());
        when(unitRepository.findByResourceIdInAndDate(any(), eq(DATE))).thenReturn(List.of());

        ResourceResponse.SearchResult result = service.search(
                new ResourceSearchService.Criteria("ATTRACTION", "丽江", DATE, null, null, null, null, null));

        assertFalse(result.items().get(0).orderEntryAllowed(),
                "free attractions stay outside the payment path");
    }

    @Test
    void searchMatchesLegacyTradableResourceByBackfilledCity() {
        Resource legacy = resource(
                "r-attraction-1", ResourceType.TRADABLE, "玉龙雪山", "云南", ResourceStatus.ON_SHELF);
        legacy.setProvince("云南");
        legacy.setCity("丽江");
        when(resourceRepository.findByStatus(ResourceStatus.ON_SHELF)).thenReturn(List.of(legacy));
        when(specRepository.findByResourceIdIn(any())).thenReturn(List.of(
                spec("s-ticket-1", "r-attraction-1", "成人票", 260, SpecType.TICKET_TYPE)));
        when(unitRepository.findByResourceIdInAndDate(any(), eq(DATE))).thenReturn(List.of(
                unit("u-attraction-1", "r-attraction-1", "s-ticket-1", 20)));
        when(redis.available("u-attraction-1")).thenReturn(20);

        ResourceResponse.SearchResult result = service.search(
                new ResourceSearchService.Criteria(null, "丽江", DATE, null, null, null, null, null));

        assertEquals(1, result.total());
        assertEquals("r-attraction-1", result.items().get(0).id());
        assertEquals("TRADABLE", result.items().get(0).type());
        assertTrue(result.items().get(0).orderEntryAllowed(), "legacy TRADABLE behavior must remain");
    }

    @Test
    void detailExposesContentCategoryAndDescription() {
        Resource food = content("r-food-1", "过桥米线", "云南", "FOOD", "蒙自传统米线");
        when(resourceRepository.findById("r-food-1")).thenReturn(Optional.of(food));
        when(specRepository.findByResourceId("r-food-1")).thenReturn(List.of());

        ResourceResponse.Detail detail = service.detail("r-food-1", null);

        assertEquals("FOOD", detail.contentCategory());
        assertEquals("蒙自传统米线", detail.description());
        assertFalse(detail.tradable());
        assertFalse(detail.orderEntryAllowed());
    }

    @Test
    void detailShowsOffShelfResourceWithoutOrderEntry() {
        Resource offShelf = resource("r-hotel-9", ResourceType.TRADABLE, "停业装修酒店", "云南", ResourceStatus.OFF_SHELF);
        when(resourceRepository.findById("r-hotel-9")).thenReturn(Optional.of(offShelf));
        when(specRepository.findByResourceId("r-hotel-9")).thenReturn(List.of(
                spec("s-room-9", "r-hotel-9", "标准大床房", 420, SpecType.ROOM_TYPE)));
        when(unitRepository.findByResourceIdAndDate("r-hotel-9", DATE)).thenReturn(List.of());

        ResourceResponse.Detail detail = service.detail("r-hotel-9", DATE);

        assertEquals("OFF_SHELF", detail.status());
        assertTrue(detail.tradable());
        assertFalse(detail.soldOut(), "off-shelf is an operational state, not a sold-out one");
        assertFalse(detail.orderEntryAllowed(), "an off-shelf resource must not offer an order entry");
    }

    @Test
    void detailReportsRedisAvailabilityPerSpec() {
        Resource hotel = resource("r-hotel-1", ResourceType.TRADABLE, "丽江古城精品酒店", "云南", ResourceStatus.ON_SHELF);
        when(resourceRepository.findById("r-hotel-1")).thenReturn(Optional.of(hotel));
        when(specRepository.findByResourceId("r-hotel-1")).thenReturn(List.of(
                spec("s-room-1", "r-hotel-1", "标准大床房", 500, SpecType.ROOM_TYPE),
                spec("s-room-2", "r-hotel-1", "家庭套房", 900, SpecType.ROOM_TYPE)));
        when(unitRepository.findByResourceIdAndDate("r-hotel-1", DATE)).thenReturn(List.of(
                unit("u-room-1", "r-hotel-1", "s-room-1", 20),
                unit("u-room-2", "r-hotel-1", "s-room-2", 20)));
        when(redis.available("u-room-1")).thenReturn(4);
        when(redis.available("u-room-2")).thenReturn(0);

        ResourceResponse.Detail detail = service.detail("r-hotel-1", DATE);

        assertEquals(500L, detail.priceFrom());
        assertEquals(900L, detail.priceTo());
        assertEquals(4, detail.availableTotal(), "the live Redis counter wins over the persisted row");
        assertTrue(detail.specs().get(0).bookable());
        assertFalse(detail.specs().get(1).bookable());
        assertTrue(detail.orderEntryAllowed());
    }

    @Test
    void detailWithoutDateLeavesAvailabilityUnknown() {
        Resource content = resource("r-food-1", ResourceType.CONTENT, "过桥米线", "云南", ResourceStatus.ON_SHELF);
        when(resourceRepository.findById("r-food-1")).thenReturn(Optional.of(content));
        when(specRepository.findByResourceId("r-food-1")).thenReturn(List.of());

        ResourceResponse.Detail detail = service.detail("r-food-1", null);

        assertFalse(detail.availabilityKnown());
        assertNull(detail.availableTotal());
        assertFalse(detail.tradable());
        assertFalse(detail.orderEntryAllowed());
        assertTrue(detail.specs().isEmpty(), "a content resource without specs reports no spec views");
    }

    @Test
    void detailRejectsUnknownResource() {
        when(resourceRepository.findById(anyString())).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.detail("r-missing", null));

        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    private Resource resource(String id, ResourceType type, String name, String destination, ResourceStatus status) {
        Resource resource = new Resource();
        resource.setId(id);
        resource.setType(type);
        resource.setName(name);
        resource.setDestination(destination);
        resource.setStatus(status);
        return resource;
    }

    private Resource content(String id, String name, String destination, String category, String description) {
        Resource resource = new Resource();
        resource.setId(id);
        resource.setType(ResourceType.CONTENT);
        resource.setName(name);
        resource.setDestination(destination);
        resource.setStatus(ResourceStatus.ON_SHELF);
        resource.setAttributes("{\"category\":\"" + category + "\",\"description\":\"" + description + "\"}");
        return resource;
    }

    private Spec spec(String id, String resourceId, String name, long price, SpecType specType) {
        Spec spec = new Spec();
        spec.setId(id);
        spec.setResourceId(resourceId);
        spec.setName(name);
        spec.setPrice(price);
        spec.setSpecType(specType);
        return spec;
    }

    private InventoryUnit unit(String id, String resourceId, String specId, int available) {
        InventoryUnit unit = new InventoryUnit();
        unit.setId(id);
        unit.setResourceId(resourceId);
        unit.setSpecId(specId);
        unit.setDate(DATE);
        unit.setTotal(Math.max(available, 20));
        unit.setAvailable(available);
        unit.setLocked(0);
        return unit;
    }
}
