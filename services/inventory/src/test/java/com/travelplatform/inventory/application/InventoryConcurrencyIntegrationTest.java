package com.travelplatform.inventory.application;

import com.travelplatform.common.api.BusinessException;
import com.travelplatform.inventory.api.InventoryController;
import com.travelplatform.inventory.domain.InventoryUnit;
import com.travelplatform.inventory.domain.InventoryUnitRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Concurrency integration tests. These hit the REAL Redis and MySQL (no mocks),
 * which must be running via deploy/docker-compose.yml before execution.
 */
@SpringBootTest
class InventoryConcurrencyIntegrationTest {

    @Autowired
    private InventoryService service;

    @Autowired
    private InventoryUnitRepository unitRepository;

    @Test
    void twoHundredConcurrentLocksNeverOversell() throws Exception {
        InventoryUnit unit = newUnit(5);

        int threads = 200;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                try {
                    service.lock(new InventoryController.LockRequest(
                            unit.getResourceId(), unit.getDate(), unit.getSpecId(),
                            1, "BOOKING", 900, "TEST", "test-holder-" + idx));
                    success.incrementAndGet();
                } catch (BusinessException expected) {
                    // sold out
                }
            });
        }

        ready.await();
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "concurrent lock run did not finish in time");

        assertEquals(5, success.get(), "exactly 5 of 200 concurrent requests must succeed");

        InventoryUnit reloaded = unitRepository.findById(unit.getId()).orElseThrow();
        assertEquals(reloaded.getTotal(), reloaded.getAvailable() + reloaded.getLocked(),
                "total must equal available + locked");
        assertEquals(0, reloaded.getAvailable());
        assertEquals(5, reloaded.getLocked());
    }

    @Test
    void concurrentReleaseReplenishesExactlyOnce() throws Exception {
        InventoryUnit unit = newUnit(5);
        String lockId = service.lock(new InventoryController.LockRequest(
                unit.getResourceId(), unit.getDate(), unit.getSpecId(),
                2, "BOOKING", 900, "TEST", "test-holder")).lockId();

        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger released = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (service.release(lockId)) {
                    released.incrementAndGet();
                }
            });
        }

        ready.await();
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "concurrent release run did not finish in time");

        assertEquals(1, released.get(), "stock must be replenished exactly once");

        InventoryUnit reloaded = unitRepository.findById(unit.getId()).orElseThrow();
        assertEquals(5, reloaded.getAvailable());
        assertEquals(0, reloaded.getLocked());
    }

    private InventoryUnit newUnit(int total) {
        InventoryUnit unit = new InventoryUnit();
        unit.setId(UUID.randomUUID().toString());
        unit.setResourceId("test-r-" + UUID.randomUUID());
        unit.setDate(LocalDate.of(2026, 11, 1));
        unit.setSpecId("test-s-" + UUID.randomUUID());
        unit.setTotal(total);
        unit.setAvailable(total);
        unit.setLocked(0);
        return unitRepository.save(unit);
    }
}
