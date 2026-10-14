package com.travelplatform.inventory.application;

import com.travelplatform.common.api.BusinessException;
import com.travelplatform.common.api.ErrorCode;
import com.travelplatform.inventory.api.InventoryController;
import com.travelplatform.inventory.domain.InventoryLock;
import com.travelplatform.inventory.domain.InventoryLockRepository;
import com.travelplatform.inventory.domain.InventoryUnit;
import com.travelplatform.inventory.domain.InventoryUnitRepository;
import com.travelplatform.inventory.domain.LockPurpose;
import com.travelplatform.inventory.domain.LockStatus;
import com.travelplatform.inventory.infrastructure.InventoryLockRedis;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class InventoryService {

    private final InventoryUnitRepository unitRepository;
    private final InventoryLockRepository lockRepository;
    private final InventoryLockRedis redis;

    public InventoryService(InventoryUnitRepository unitRepository,
                            InventoryLockRepository lockRepository,
                            InventoryLockRedis redis) {
        this.unitRepository = unitRepository;
        this.lockRepository = lockRepository;
        this.redis = redis;
    }

    public Integer query(String resourceId, LocalDate date, String specId) {
        InventoryUnit unit = requireUnit(resourceId, date, specId);
        redis.initialize(unit);
        Integer available = redis.available(unit.getId());
        return available != null ? available : unit.getAvailable();
    }

    @Transactional
    public InventoryController.LockResponse lock(InventoryController.LockRequest request) {
        InventoryUnit unit = requireUnit(request.resourceId(), request.date(), request.specId());
        redis.initialize(unit);

        long result = redis.tryLock(unit.getId(), request.quantity());
        if (result != 1) {
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
        lockRepository.save(lock);

        unit.setLocked(unit.getLocked() + request.quantity());
        unit.setAvailable(unit.getAvailable() - request.quantity());
        unitRepository.save(unit);
        return new InventoryController.LockResponse(lock.getId());
    }

    @Transactional
    public boolean release(String lockId) {
        InventoryLock lock = lockRepository.findById(lockId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "inventory lock not found"));
        if (lock.getStatus() == LockStatus.RELEASED) {
            return false;
        }

        long result = redis.release(lockId, lock.getInventoryUnitId(), lock.getQuantity());
        lock.setStatus(LockStatus.RELEASED);
        lockRepository.save(lock);

        if (result == 1) {
            unitRepository.findById(lock.getInventoryUnitId()).ifPresent(unit -> {
                unit.setLocked(Math.max(0, unit.getLocked() - lock.getQuantity()));
                unit.setAvailable(unit.getAvailable() + lock.getQuantity());
                unitRepository.save(unit);
            });
        }
        return result == 1;
    }

    private InventoryUnit requireUnit(String resourceId, LocalDate date, String specId) {
        return unitRepository.findByResourceIdAndDateAndSpecId(resourceId, date, specId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "inventory unit not found"));
    }
}
