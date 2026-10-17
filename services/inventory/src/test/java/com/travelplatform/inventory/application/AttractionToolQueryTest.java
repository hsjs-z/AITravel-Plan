package com.travelplatform.inventory.application;

import com.travelplatform.common.api.BusinessException;
import com.travelplatform.common.api.ErrorCode;
import com.travelplatform.inventory.api.AttractionToolResponse;
import com.travelplatform.inventory.domain.CoordinateStatus;
import com.travelplatform.inventory.domain.InventoryLockRepository;
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
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AttractionToolQueryTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 18);

    private final InventoryUnitRepository unitRepository = mock(InventoryUnitRepository.class);
    private final InventoryLockRepository lockRepository = mock(InventoryLockRepository.class);
    private final InventoryLockRedis redis = mock(InventoryLockRedis.class);
    private final ResourceRepository resourceRepository = mock(ResourceRepository.class);
    private final SpecRepository specRepository = mock(SpecRepository.class);
    private final InventoryUnitPersister persister = mock(InventoryUnitPersister.class);
    private final InventoryEventPublisher events = mock(InventoryEventPublisher.class);

    private final InventoryService service = new InventoryService(
            unitRepository, lockRepository, resourceRepository, specRepository,
            redis, persister, events);

    @Test
    void filtersOnShelfAttractionsAndAndsAllLocationParameters() {
        Resource lijiang = attraction("r-lijiang", "丽江古城", "云南省", "丽江", "5A", 0L,
                new BigDecimal("26.876468"), new BigDecimal("100.225830"), CoordinateStatus.READY);
        Resource yulong = attraction("r-yulong", "玉龙雪山", "云南省", "丽江", "5A", 100L,
                new BigDecimal("27.096143"), new BigDecimal("100.179253"), CoordinateStatus.READY);
        Resource dali = attraction("r-dali", "大理古城", "云南省", "大理", "4A", 0L,
                null, null, CoordinateStatus.PENDING);
        Resource xian = attraction("r-xian", "秦始皇兵马俑", "陕西省", "西安", "5A", 120L,
                new BigDecimal("34.384"), new BigDecimal("109.278"), CoordinateStatus.READY);
        when(resourceRepository.findByStatusAndType(ResourceStatus.ON_SHELF, ResourceType.ATTRACTION))
                .thenReturn(List.of(lijiang, yulong, dali, xian));

        Spec yulongSpec = spec("s-yulong", "r-yulong", "成人票", 100L);
        Spec xianSpec = spec("s-xian", "r-xian", "成人票", 120L);
        when(specRepository.findByResourceIdIn(any())).thenReturn(List.of(yulongSpec, xianSpec));
        when(unitRepository.findByResourceIdInAndDate(any(), eq(DATE))).thenReturn(List.of(
                unit("u-yulong", "r-yulong", "s-yulong", 200),
                unit("u-xian", "r-xian", "s-xian", 50)));
        when(redis.available("u-yulong")).thenReturn(200);
        when(redis.available("u-xian")).thenReturn(50);

        AttractionToolResponse.AttractionPage yunnanAll =
                service.attractions(null, "云南", null, DATE, 20, 0);
        assertEquals(3, yunnanAll.total());
        assertEquals(List.of("r-lijiang", "r-yulong", "r-dali"),
                yunnanAll.items().stream().map(AttractionToolResponse.AttractionItem::resourceId).toList());

        AttractionToolResponse.AttractionPage lijiangOnly =
                service.attractions(null, "云南", "丽江", DATE, 20, 0);
        assertEquals(2, lijiangOnly.total(), "province and city are ANDed");
        assertEquals(List.of("r-lijiang", "r-yulong"),
                lijiangOnly.items().stream().map(AttractionToolResponse.AttractionItem::resourceId).toList());

        AttractionToolResponse.AttractionPage impossible =
                service.attractions("云南", "云南", "西安", DATE, 20, 0);
        assertEquals(0, impossible.total(), "all supplied location filters must match");

        AttractionToolResponse.AttractionPage free = service.attractions(
                null, "云南", "大理", DATE, 20, 0);
        AttractionToolResponse.AttractionItem daliItem = free.items().get(0);
        assertTrue(daliItem.free());
        assertNull(daliItem.specId());
        assertEquals(0L, daliItem.price());
        assertNull(daliItem.available());
        assertEquals("PENDING", daliItem.coordinateStatus());
        assertNull(daliItem.latitude());
        assertNull(daliItem.longitude());

        AttractionToolResponse.AttractionItem yulongItem = lijiangOnly.items().get(1);
        assertEquals("s-yulong", yulongItem.specId());
        assertEquals(100L, yulongItem.price());
        assertEquals(200, yulongItem.available());

        verify(resourceRepository, atLeastOnce())
                .findByStatusAndType(ResourceStatus.ON_SHELF, ResourceType.ATTRACTION);
    }

    @Test
    void selectsLowestPricedTicketSpecAndUsesDateScopedAvailability() {
        Resource paid = attraction("r-paid", "付费景区", "云南省", "昆明", "5A", 100L,
                null, null, CoordinateStatus.PENDING);
        when(resourceRepository.findByStatusAndType(ResourceStatus.ON_SHELF, ResourceType.ATTRACTION))
                .thenReturn(List.of(paid));
        when(specRepository.findByResourceIdIn(any())).thenReturn(List.of(
                spec("s-high", "r-paid", "高价票", 150L),
                spec("s-low", "r-paid", "成人票", 100L),
                new Spec() {{
                    setId("s-room");
                    setResourceId("r-paid");
                    setName("房型");
                    setSpecType(SpecType.ROOM_TYPE);
                    setPrice(50L);
                }}));
        when(unitRepository.findByResourceIdInAndDate(any(), eq(DATE))).thenReturn(List.of(
                unit("u-low", "r-paid", "s-low", 37),
                unit("u-high", "r-paid", "s-high", 1)));
        when(redis.available("u-low")).thenReturn(37);

        AttractionToolResponse.AttractionPage withDate =
                service.attractions(null, "云南", null, DATE, 20, 0);
        AttractionToolResponse.AttractionItem item = withDate.items().get(0);
        assertEquals("s-low", item.specId());
        assertEquals(100L, item.price());
        assertEquals(37, item.available());

        AttractionToolResponse.AttractionPage withoutDate =
                service.attractions(null, "云南", null, null, 20, 0);
        assertNull(withoutDate.items().get(0).available(),
                "availability is date-scoped and unknown without a date");
    }

    @Test
    void paidAttractionWithoutTicketSpecIsDisplayOnly() {
        Resource paid = attraction("r-no-spec", "无票规格景区", "云南省", "昆明", "4A", 80L,
                null, null, CoordinateStatus.PENDING);
        when(resourceRepository.findByStatusAndType(ResourceStatus.ON_SHELF, ResourceType.ATTRACTION))
                .thenReturn(List.of(paid));
        when(specRepository.findByResourceIdIn(any())).thenReturn(List.of());

        AttractionToolResponse.AttractionItem item =
                service.attractions(null, "云南", null, DATE, 20, 0).items().get(0);

        assertFalse(item.free());
        assertNull(item.specId());
        assertNull(item.price());
        assertNull(item.available());
    }

    @Test
    void pagesAfterLevelAndPriceSorting() {
        Resource fivePaid = attraction("r-five-paid", "5A 付费", "云南省", "昆明", "5A", 80L,
                null, null, CoordinateStatus.READY);
        Resource fiveFree = attraction("r-five-free", "5A 免费", "云南省", "昆明", "5A", 0L,
                null, null, CoordinateStatus.READY);
        Resource fourFree = attraction("r-four-free", "4A 免费", "云南省", "昆明", "4A", 0L,
                null, null, CoordinateStatus.READY);
        when(resourceRepository.findByStatusAndType(ResourceStatus.ON_SHELF, ResourceType.ATTRACTION))
                .thenReturn(List.of(fivePaid, fourFree, fiveFree));
        when(specRepository.findByResourceIdIn(any())).thenReturn(List.of(
                spec("s-five", "r-five-paid", "成人票", 80L)));

        AttractionToolResponse.AttractionPage firstPage =
                service.attractions(null, "云南", null, null, 2, 0);

        assertEquals(3, firstPage.total());
        assertEquals(2, firstPage.items().size());
        assertTrue(firstPage.hasMore());
        assertEquals(List.of("r-five-free", "r-five-paid"),
                firstPage.items().stream().map(AttractionToolResponse.AttractionItem::resourceId).toList());

        AttractionToolResponse.AttractionPage secondPage =
                service.attractions(null, "云南", null, null, 2, 2);
        assertFalse(secondPage.hasMore());
        assertEquals(List.of("r-four-free"),
                secondPage.items().stream().map(AttractionToolResponse.AttractionItem::resourceId).toList());

        AttractionToolResponse.AttractionPage beyond =
                service.attractions(null, "云南", null, null, 2, 10);
        assertTrue(beyond.items().isEmpty());
        assertEquals(3, beyond.total());
    }

    @Test
    void rejectsMissingLocationFiltersAndInvalidPaging() {
        assertBadRequest(() -> service.attractions(null, null, null, DATE, 20, 0));
        assertBadRequest(() -> service.attractions(" ", "\t", null, DATE, 20, 0));
        assertBadRequest(() -> service.attractions(null, "云南", null, DATE, 0, 0));
        assertBadRequest(() -> service.attractions(null, "云南", null, DATE, 51, 0));
        assertBadRequest(() -> service.attractions(null, "云南", null, DATE, 20, -1));
    }

    @Test
    void readOnlyQueryDoesNotTouchLocksOrEvents() {
        Resource free = attraction("r-free", "免费景区", "云南省", "丽江", "5A", 0L,
                null, null, CoordinateStatus.READY);
        when(resourceRepository.findByStatusAndType(ResourceStatus.ON_SHELF, ResourceType.ATTRACTION))
                .thenReturn(List.of(free));
        when(specRepository.findByResourceIdIn(any())).thenReturn(List.of());

        service.attractions(null, "云南", null, null, 20, 0);

        verifyNoInteractions(lockRepository, persister, events, redis);
        verify(unitRepository, never()).findByResourceIdInAndDate(any(), any());
        verify(redis, never()).tryLock(any(), anyInt());
        verify(redis, never()).release(any(), any(), anyInt());
    }

    private void assertBadRequest(Runnable action) {
        BusinessException ex = assertThrows(BusinessException.class, action::run);
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    private Resource attraction(String id,
                                String name,
                                String province,
                                String city,
                                String level,
                                long ticketPrice,
                                BigDecimal latitude,
                                BigDecimal longitude,
                                CoordinateStatus coordinateStatus) {
        Resource resource = new Resource();
        resource.setId(id);
        resource.setType(ResourceType.ATTRACTION);
        resource.setName(name);
        resource.setDestination(province);
        resource.setProvince(province);
        resource.setCity(city);
        resource.setLevel(level);
        resource.setHonorTitle("测试称号");
        resource.setSummary("测试简介");
        resource.setSuggestedHours(new BigDecimal("2.0"));
        resource.setTicketPrice(ticketPrice);
        resource.setLatitude(latitude);
        resource.setLongitude(longitude);
        resource.setCoordinateStatus(coordinateStatus);
        resource.setStatus(ResourceStatus.ON_SHELF);
        resource.setSeedManaged(true);
        return resource;
    }

    private Spec spec(String id, String resourceId, String name, long price) {
        Spec spec = new Spec();
        spec.setId(id);
        spec.setResourceId(resourceId);
        spec.setName(name);
        spec.setSpecType(SpecType.TICKET_TYPE);
        spec.setPrice(price);
        return spec;
    }

    private InventoryUnit unit(String id, String resourceId, String specId, int available) {
        InventoryUnit unit = new InventoryUnit();
        unit.setId(id);
        unit.setResourceId(resourceId);
        unit.setSpecId(specId);
        unit.setDate(DATE);
        unit.setTotal(200);
        unit.setAvailable(available);
        unit.setLocked(0);
        return unit;
    }
}
