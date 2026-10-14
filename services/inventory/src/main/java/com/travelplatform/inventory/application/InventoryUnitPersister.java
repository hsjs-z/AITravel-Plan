package com.travelplatform.inventory.application;

import com.travelplatform.common.api.BusinessException;
import com.travelplatform.common.api.ErrorCode;
import com.travelplatform.inventory.domain.InventoryLock;
import com.travelplatform.inventory.domain.InventoryLockRepository;
import com.travelplatform.inventory.domain.InventoryUnit;
import com.travelplatform.inventory.domain.InventoryUnitRepository;
import com.travelplatform.inventory.domain.LockStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional persistence for the hot-path inventory counters. Kept in a
 * separate bean so callers can retry on optimistic-lock conflicts with fresh
 * values (the {@code @Version} field on InventoryUnit makes lost updates fail
 * loudly instead of silently overwriting each other).
 */
@Component
public class InventoryUnitPersister {

    private final InventoryUnitRepository unitRepository;
    private final InventoryLockRepository lockRepository;

    public InventoryUnitPersister(InventoryUnitRepository unitRepository,
                                  InventoryLockRepository lockRepository) {
        this.unitRepository = unitRepository;
        this.lockRepository = lockRepository;
    }

    @Transactional
    public void persistLock(InventoryLock lock, int quantity) {
        InventoryUnit unit = requireUnit(lock.getInventoryUnitId());
        unit.setLocked(unit.getLocked() + quantity);
        unit.setAvailable(unit.getAvailable() - quantity);
        unitRepository.save(unit);
        lockRepository.save(lock);
    }

    @Transactional
    public void persistRelease(InventoryLock lock, int quantity) {
        InventoryUnit unit = requireUnit(lock.getInventoryUnitId());
        unit.setLocked(Math.max(0, unit.getLocked() - quantity));
        unit.setAvailable(unit.getAvailable() + quantity);
        unitRepository.save(unit);
        lock.setStatus(LockStatus.RELEASED);
        lockRepository.save(lock);
    }

    @Transactional
    public void markReleased(String lockId) {
        lockRepository.findById(lockId).ifPresent(lock -> {
            lock.setStatus(LockStatus.RELEASED);
            lockRepository.save(lock);
        });
    }

    private InventoryUnit requireUnit(String unitId) {
        return unitRepository.findById(unitId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "inventory unit not found"));
    }
}
