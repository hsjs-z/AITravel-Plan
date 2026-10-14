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
import com.travelplatform.inventory.domain.Resource;
import com.travelplatform.inventory.domain.ResourceRepository;
import com.travelplatform.inventory.domain.ResourceType;
import com.travelplatform.inventory.domain.Spec;
import com.travelplatform.inventory.domain.SpecRepository;
import com.travelplatform.inventory.infrastructure.InventoryLockRedis;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class InventoryService {

    private static final int MAX_RETRY_ATTEMPTS = 5;

    private final InventoryUnitRepository unitRepository;
    private final InventoryLockRepository lockRepository;
    private final ResourceRepository resourceRepository;
    private final SpecRepository specRepository;
    private final InventoryLockRedis redis;
    private final InventoryUnitPersister persister;

    public InventoryService(InventoryUnitRepository unitRepository,
                            InventoryLockRepository lockRepository,
                            ResourceRepository resourceRepository,
                            SpecRepository specRepository,
                            InventoryLockRedis redis,
                            InventoryUnitPersister persister) {
        this.unitRepository = unitRepository;
        this.lockRepository = lockRepository;
        this.resourceRepository = resourceRepository;
        this.specRepository = specRepository;
        this.redis = redis;
        this.persister = persister;
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
}
