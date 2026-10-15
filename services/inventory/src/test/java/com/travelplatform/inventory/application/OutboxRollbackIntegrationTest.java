package com.travelplatform.inventory.application;

import com.travelplatform.common.outbox.OutboxRecorder;
import com.travelplatform.common.outbox.OutboxTopics;
import com.travelplatform.inventory.domain.OutboxRecordRepository;
import com.travelplatform.inventory.domain.Resource;
import com.travelplatform.inventory.domain.ResourceRepository;
import com.travelplatform.inventory.domain.ResourceStatus;
import com.travelplatform.inventory.domain.ResourceType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the core outbox invariant (宪法 IV): when the business transaction rolls
 * back, the outbox row written inside it rolls back with it, so an event can never
 * be published for a business change that did not commit.
 *
 * <p>Hits the REAL MySQL (no mocks), like
 * {@link InventoryConcurrencyIntegrationTest}. The business write and the outbox
 * append share one {@link TransactionTemplate} transaction that is then forced to
 * roll back; both tables must be empty of the probe rows afterwards.
 */
@SpringBootTest
class OutboxRollbackIntegrationTest {

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ResourceRepository resources;

    @Autowired
    private OutboxRecordRepository outboxRecords;

    @Autowired
    private OutboxRecorder recorder;

    @Test
    void outboxRowRollsBackTogetherWithTheBusinessTransaction() {
        String aggregateId = "rollback-probe-" + UUID.randomUUID();
        Resource resource = new Resource();
        resource.setId(aggregateId);
        resource.setType(ResourceType.TRADABLE);
        resource.setName("回滚探针");
        resource.setDestination("PROBE");
        resource.setStatus(ResourceStatus.ON_SHELF);

        TransactionTemplate template = new TransactionTemplate(transactionManager);
        assertThrows(IllegalStateException.class, () -> template.executeWithoutResult(status -> {
            resources.save(resource);
            recorder.record(OutboxTopics.PLANNING_EVENTS, "ProbeEvent", "PROBE",
                    aggregateId, Map.of("probeId", aggregateId));
            throw new IllegalStateException("forced rollback");
        }));

        assertTrue(resources.findById(aggregateId).isEmpty(),
                "the business row must be rolled back");
        assertTrue(outboxRecords.findAll().stream()
                        .noneMatch(row -> aggregateId.equals(row.getAggregateId())),
                "the outbox row must be rolled back together with the business row");
    }
}
