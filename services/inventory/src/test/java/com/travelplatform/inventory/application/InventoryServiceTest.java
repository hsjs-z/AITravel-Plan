package com.travelplatform.inventory.application;

import com.travelplatform.common.api.BusinessException;
import com.travelplatform.inventory.api.InventoryController;
import com.travelplatform.inventory.domain.InventoryLock;
import com.travelplatform.inventory.domain.InventoryLockRepository;
import com.travelplatform.inventory.domain.InventoryUnit;
import com.travelplatform.inventory.domain.InventoryUnitRepository;
import com.travelplatform.inventory.domain.LockStatus;
import com.travelplatform.inventory.infrastructure.InventoryLockRedis;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
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

    private final InventoryService service = new InventoryService(unitRepository, lockRepository, redis);

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
        when(redis.tryLock("unit-1", 2)).thenReturn(0L);

        InventoryController.LockRequest request = new InventoryController.LockRequest(
                "r-1", LocalDate.of(2026, 11, 1), "s-1", 2, "BOOKING", 900, "ORDER", "o-1");

        assertThrows(BusinessException.class, () -> service.lock(request));
        verify(lockRepository, never()).save(any());
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
}
