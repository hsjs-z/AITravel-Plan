package com.travelplatform.inventory.application;

import com.travelplatform.common.api.BusinessException;
import com.travelplatform.common.outbox.OutboxRecorder;
import com.travelplatform.common.outbox.OutboxStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplatform.inventory.api.InventoryController;
import com.travelplatform.inventory.domain.InventoryLock;
import com.travelplatform.inventory.domain.InventoryLockRepository;
import com.travelplatform.inventory.domain.InventoryUnit;
import com.travelplatform.inventory.domain.InventoryUnitRepository;
import com.travelplatform.inventory.domain.LockStatus;
import com.travelplatform.inventory.domain.Resource;
import com.travelplatform.inventory.domain.ResourceRepository;
import com.travelplatform.inventory.domain.ResourceStatus;
import com.travelplatform.inventory.domain.ResourceType;
import com.travelplatform.inventory.domain.Spec;
import com.travelplatform.inventory.domain.SpecRepository;
import com.travelplatform.inventory.domain.SpecType;
import com.travelplatform.inventory.infrastructure.InventoryLockRedis;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryServiceTest {

    private final InventoryUnitRepository unitRepository = mock(InventoryUnitRepository.class);
    private final InventoryLockRepository lockRepository = mock(InventoryLockRepository.class);
    private final InventoryLockRedis redis = mock(InventoryLockRedis.class);
    private final ResourceRepository resourceRepository = mock(ResourceRepository.class);
    private final SpecRepository specRepository = mock(SpecRepository.class);

    private final InventoryEventPublisher events =
            new InventoryEventPublisher(new OutboxRecorder(
                    mock(OutboxStore.class), new ObjectMapper().findAndRegisterModules()));
    private final InventoryUnitPersister persister =
            new InventoryUnitPersister(unitRepository, lockRepository, events);
    private final InventoryService service = new InventoryService(
            unitRepository, lockRepository, resourceRepository, specRepository, redis, persister, events);

    @Test
    void releaseIsIdempotentWhenLockAlreadyReleased() {
        InventoryLock released = new InventoryLock();
        released.setId("lock-1");
        released.setStatus(LockStatus.RELEASED);
        when(lockRepository.findById("lock-1")).thenReturn(Optional.of(released));

        boolean result = service.release("lock-1");

        assertFalse(result);
        verify(redis, never()).release(any(), any(), anyInt());
    }

    @Test
    void lockThrowsWhenStockIsInsufficient() {
        InventoryUnit unit = new InventoryUnit();
        unit.setId("unit-1");
        unit.setResourceId("r-1");
        unit.setDate(LocalDate.of(2026, 11, 1));
        unit.setSpecId("s-1");
        unit.setTotal(1);
        unit.setAvailable(1);
        unit.setLocked(0);
        when(unitRepository.findByResourceIdAndDateAndSpecId("r-1", LocalDate.of(2026, 11, 1), "s-1"))
                .thenReturn(Optional.of(unit));
        Resource resource = new Resource();
        resource.setId("r-1");
        resource.setType(ResourceType.TRADABLE);
        when(resourceRepository.findById("r-1")).thenReturn(Optional.of(resource));
        when(redis.tryLock("unit-1", 2)).thenReturn(0L);

        InventoryController.LockRequest request = new InventoryController.LockRequest(
                "r-1", LocalDate.of(2026, 11, 1), "s-1", 2, "BOOKING", 900, "ORDER", "o-1");

        assertThrows(BusinessException.class, () -> service.lock(request));
        verify(lockRepository, never()).save(any());
    }

    @Test
    void attractionCannotEnterTradingLockInV204() {
        Resource attraction = new Resource();
        attraction.setId("r-attraction-v2");
        attraction.setType(ResourceType.ATTRACTION);
        when(resourceRepository.findById("r-attraction-v2")).thenReturn(Optional.of(attraction));

        InventoryController.LockRequest request = new InventoryController.LockRequest(
                "r-attraction-v2", LocalDate.of(2026, 11, 1), "s-ticket-v2",
                2, "BOOKING", 900, "ORDER", "o-1");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.lock(request));

        assertEquals(com.travelplatform.common.api.ErrorCode.CONFLICT, ex.getErrorCode());
        verify(unitRepository, never()).findByResourceIdAndDateAndSpecId(any(), any(), any());
        verify(redis, never()).tryLock(any(), anyInt());
    }

    @Test
    void candidatesPreserveV1TradableExactDestinationBehaviorIncludingOffShelf() {
        LocalDate date = LocalDate.of(2026, 10, 18);
        Resource onShelf = resource("r-on", "在售酒店", ResourceStatus.ON_SHELF);
        Resource offShelf = resource("r-off", "下架酒店", ResourceStatus.OFF_SHELF);
        when(resourceRepository.findByDestinationAndType("云南", ResourceType.TRADABLE))
                .thenReturn(List.of(onShelf, offShelf));
        when(specRepository.findByResourceId("r-on"))
                .thenReturn(List.of(spec("s-on", "r-on", "标准间", 500L)));
        when(specRepository.findByResourceId("r-off"))
                .thenReturn(List.of(spec("s-off", "r-off", "标准间", 420L)));
        when(unitRepository.findByResourceIdAndDateAndSpecId("r-on", date, "s-on"))
                .thenReturn(Optional.of(unit("u-on", "r-on", "s-on", date, 8)));
        when(unitRepository.findByResourceIdAndDateAndSpecId("r-off", date, "s-off"))
                .thenReturn(Optional.of(unit("u-off", "r-off", "s-off", date, 0)));
        when(redis.available("u-on")).thenReturn(8);
        when(redis.available("u-off")).thenReturn(0);

        List<InventoryController.Candidate> candidates = service.candidates("云南", date);

        assertEquals(List.of("r-on", "r-off"),
                candidates.stream().map(InventoryController.Candidate::resourceId).toList());
        assertEquals(2, candidates.get(0).specs().size() + candidates.get(1).specs().size());
        assertEquals(8, candidates.get(0).specs().get(0).available());
        assertEquals(0, candidates.get(1).specs().get(0).available());
        verify(resourceRepository).findByDestinationAndType("云南", ResourceType.TRADABLE);
    }

    @Test
    void releaseReturnsStockExactlyOnce() {
        InventoryLock active = new InventoryLock();
        active.setId("lock-1");
        active.setInventoryUnitId("unit-1");
        active.setQuantity(2);
        active.setStatus(LockStatus.ACTIVE);
        when(lockRepository.findById("lock-1")).thenReturn(Optional.of(active));
        when(redis.release("lock-1", "unit-1", 2)).thenReturn(1L);

        InventoryUnit unit = new InventoryUnit();
        unit.setId("unit-1");
        unit.setAvailable(8);
        unit.setLocked(2);
        when(unitRepository.findById("unit-1")).thenReturn(Optional.of(unit));

        boolean result = service.release("lock-1");

        assertTrue(result);
        assertEquals(LockStatus.RELEASED, active.getStatus());
        assertEquals(10, unit.getAvailable());
        assertEquals(0, unit.getLocked());
        verify(redis).release(eq("lock-1"), eq("unit-1"), eq(2));
    }

    private Resource resource(String id, String name, ResourceStatus status) {
        Resource resource = new Resource();
        resource.setId(id);
        resource.setName(name);
        resource.setDestination("云南");
        resource.setType(ResourceType.TRADABLE);
        resource.setStatus(status);
        return resource;
    }

    private Spec spec(String id, String resourceId, String name, long price) {
        Spec spec = new Spec();
        spec.setId(id);
        spec.setResourceId(resourceId);
        spec.setName(name);
        spec.setSpecType(SpecType.ROOM_TYPE);
        spec.setPrice(price);
        return spec;
    }

    private InventoryUnit unit(String id, String resourceId, String specId,
                               LocalDate date, int available) {
        InventoryUnit unit = new InventoryUnit();
        unit.setId(id);
        unit.setResourceId(resourceId);
        unit.setSpecId(specId);
        unit.setDate(date);
        unit.setTotal(10);
        unit.setAvailable(available);
        unit.setLocked(0);
        return unit;
    }
}
