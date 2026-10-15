package com.travelplatform.inventory.performance;

import com.travelplatform.inventory.api.ResourceResponse;
import com.travelplatform.inventory.application.ResourceSearchService;
import com.travelplatform.inventory.domain.InventoryUnit;
import com.travelplatform.inventory.domain.InventoryUnitRepository;
import com.travelplatform.inventory.domain.Resource;
import com.travelplatform.inventory.domain.ResourceRepository;
import com.travelplatform.inventory.domain.ResourceStatus;
import com.travelplatform.inventory.domain.ResourceType;
import com.travelplatform.inventory.domain.Spec;
import com.travelplatform.inventory.domain.SpecRepository;
import com.travelplatform.inventory.domain.SpecType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T074: search path performance/load verification — 95% of searches must answer
 * within 1s and 1000 concurrent requests must stay below a 1% error rate.
 *
 * <p>Hits the REAL MySQL and Redis (no mocks), so the local middleware stack from
 * deploy/docker-compose.yml must be running, exactly like
 * {@link com.travelplatform.inventory.application.InventoryConcurrencyIntegrationTest}.
 * The thresholds below are the acceptance criteria of the task, not tunables.
 */
@SpringBootTest
class SearchLoadTest {

    private static final Logger log = LoggerFactory.getLogger(SearchLoadTest.class);

    private static final int TOTAL_REQUESTS = 1000;
    private static final int CONCURRENCY = 200;
    private static final double P95_MILLIS_LIMIT = 1000.0;
    private static final double ERROR_RATE_LIMIT = 0.01;

    private static final LocalDate SEARCH_DATE = LocalDate.of(2026, 12, 1);
    private static final String DESTINATION = "LOAD-TEST-DESTINATION";
    private static final String RESOURCE_ID = "load-test-resource";
    private static final List<String> SPEC_IDS = List.of("load-test-spec-1", "load-test-spec-2", "load-test-spec-3");

    @Autowired
    private ResourceSearchService searchService;

    @Autowired
    private ResourceRepository resourceRepository;

    @Autowired
    private SpecRepository specRepository;

    @Autowired
    private InventoryUnitRepository unitRepository;

    /**
     * Seeds a dedicated catalog slice so the test stays self-sufficient even when the
     * demo seed data has not been loaded. Ids are deterministic, so repeated runs
     * overwrite the same rows instead of growing the catalog.
     */
    @BeforeEach
    void seedCatalog() {
        Resource resource = new Resource();
        resource.setId(RESOURCE_ID);
        resource.setType(ResourceType.TRADABLE);
        resource.setName("压测酒店");
        resource.setDestination(DESTINATION);
        resource.setStatus(ResourceStatus.ON_SHELF);
        resourceRepository.save(resource);

        for (int i = 0; i < SPEC_IDS.size(); i++) {
            Spec spec = new Spec();
            spec.setId(SPEC_IDS.get(i));
            spec.setResourceId(RESOURCE_ID);
            spec.setName("压测规格-" + (i + 1));
            spec.setSpecType(SpecType.ROOM_TYPE);
            spec.setPrice(300L + i * 100L);
            specRepository.save(spec);

            InventoryUnit unit = unitRepository
                    .findByResourceIdAndDateAndSpecId(RESOURCE_ID, SEARCH_DATE, SPEC_IDS.get(i))
                    .orElseGet(InventoryUnit::new);
            unit.setId("load-test-unit-" + (i + 1));
            unit.setResourceId(RESOURCE_ID);
            unit.setSpecId(SPEC_IDS.get(i));
            unit.setDate(SEARCH_DATE);
            unit.setTotal(100);
            unit.setAvailable(100);
            unit.setLocked(0);
            unitRepository.save(unit);
        }
    }

    @Test
    void searchStaysWithinP95AndErrorRateUnderLoad() throws Exception {
        ResourceSearchService.Criteria criteria = new ResourceSearchService.Criteria(
                "TRADABLE", DESTINATION, SEARCH_DATE, null, null, null, "price_asc");

        ResourceResponse.SearchResult warmUp = searchService.search(criteria);
        assertEquals(1, warmUp.total(), "the seeded load-test resource must be searchable");
        assertTrue(warmUp.items().get(0).orderEntryAllowed());

        long[] latenciesNanos = new long[TOTAL_REQUESTS];
        AtomicInteger errors = new AtomicInteger();
        AtomicInteger mismatches = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);
        CountDownLatch ready = new CountDownLatch(CONCURRENCY);
        CountDownLatch start = new CountDownLatch(1);

        for (int i = 0; i < TOTAL_REQUESTS; i++) {
            final int index = i;
            pool.submit(() -> {
                if (index < CONCURRENCY) {
                    ready.countDown();
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                long begin = System.nanoTime();
                try {
                    ResourceResponse.SearchResult result = searchService.search(criteria);
                    if (result.total() != 1) {
                        mismatches.incrementAndGet();
                    }
                } catch (RuntimeException e) {
                    errors.incrementAndGet();
                } finally {
                    latenciesNanos[index] = System.nanoTime() - begin;
                }
            });
        }

        assertTrue(ready.await(60, TimeUnit.SECONDS), "load threads did not reach the start line");
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(180, TimeUnit.SECONDS), "load run did not finish in time");

        double errorRate = (double) errors.get() / TOTAL_REQUESTS;
        assertEquals(0, mismatches.get(), "every search under load must still return the seeded resource");
        assertTrue(errorRate < ERROR_RATE_LIMIT,
                "error rate " + errorRate + " must stay below " + ERROR_RATE_LIMIT
                        + " (1000 concurrent search requests)");

        double p95Millis = percentileMillis(latenciesNanos, 0.95);
        double p99Millis = percentileMillis(latenciesNanos, 0.99);
        assertTrue(p95Millis < P95_MILLIS_LIMIT,
                "95% of searches must answer within " + P95_MILLIS_LIMIT + "ms but p95 was "
                        + p95Millis + "ms");

        log.info("T074 search load result: totalRequests={}, concurrency={}, "
                        + "p95={}ms, p99={}ms, errorRate={}, mismatches={}",
                TOTAL_REQUESTS, CONCURRENCY, p95Millis, p99Millis, errorRate, mismatches.get());
        System.out.println("[T074-SEARCH-LOAD] totalRequests=" + TOTAL_REQUESTS
                + " concurrency=" + CONCURRENCY
                + " p95Ms=" + p95Millis
                + " p99Ms=" + p99Millis
                + " errorRate=" + errorRate
                + " mismatches=" + mismatches.get());
    }

    private double percentileMillis(long[] latenciesNanos, double percentile) {
        long[] sorted = Arrays.copyOf(latenciesNanos, latenciesNanos.length);
        Arrays.sort(sorted);
        List<Long> values = new ArrayList<>(sorted.length);
        for (long value : sorted) {
            values.add(value);
        }
        int index = (int) Math.ceil(percentile * values.size()) - 1;
        return values.get(Math.max(index, 0)) / 1_000_000.0;
    }
}
