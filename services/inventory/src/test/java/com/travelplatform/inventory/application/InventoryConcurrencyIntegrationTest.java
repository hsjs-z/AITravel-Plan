package com.travelplatform.inventory.application;

import com.travelplatform.common.api.BusinessException;
import com.travelplatform.inventory.api.InventoryController;
import com.travelplatform.inventory.domain.InventoryUnit;
import com.travelplatform.inventory.domain.InventoryUnitRepository;
import com.travelplatform.inventory.domain.Resource;
import com.travelplatform.inventory.domain.ResourceRepository;
import com.travelplatform.inventory.domain.ResourceStatus;
import com.travelplatform.inventory.domain.ResourceType;
import com.travelplatform.inventory.domain.Spec;
import com.travelplatform.inventory.domain.SpecRepository;
import com.travelplatform.inventory.domain.SpecType;
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

    @Autowired
    private ResourceRepository resourceRepository;

    @Autowired
    private SpecRepository specRepository;

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
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String resourceId = "test-r-" + suffix;
        Resource resource = new Resource();
        resource.setId(resourceId);
        resource.setType(ResourceType.ATTRACTION);
        resource.setName("test-attraction-" + suffix);
        resource.setDestination("测试");
        resource.setStatus(ResourceStatus.ON_SHELF);
        resource.setTicketPrice(100L);
        resourceRepository.save(resource);

        String specId = "test-s-" + suffix;
        Spec spec = new Spec();
        spec.setId(specId);
        spec.setResourceId(resourceId);
        spec.setSpecType(SpecType.TICKET_TYPE);
        spec.setName("测试门票");
        spec.setPrice(100L);
        specRepository.save(spec);

        InventoryUnit unit = new InventoryUnit();
        unit.setId(UUID.randomUUID().toString());
        unit.setResourceId(resourceId);
        unit.setDate(LocalDate.of(2026, 11, 1));
        unit.setSpecId(specId);
        unit.setTotal(total);
        unit.setAvailable(total);
        unit.setLocked(0);
        return unitRepository.save(unit);
    }
}
