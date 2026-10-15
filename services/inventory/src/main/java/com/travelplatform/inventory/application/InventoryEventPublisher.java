package com.travelplatform.inventory.application;

import com.travelplatform.common.outbox.EventTypes;
import com.travelplatform.common.outbox.OutboxRecorder;
import com.travelplatform.common.outbox.OutboxTopics;
import com.travelplatform.inventory.domain.InventoryLock;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Publishes inventory events through the transactional outbox (T045).
 *
 * <p>Lock/release events are written inside the same transaction as the inventory
 * counters they describe, so a rolled-back optimistic-lock retry cannot leak an
 * event for a lock that never happened.
 */
@Component
public class InventoryEventPublisher {

    private static final String AGGREGATE_TYPE = "INVENTORY_LOCK";
    private static final String UNIT_AGGREGATE_TYPE = "INVENTORY_UNIT";

    private final OutboxRecorder recorder;

    public InventoryEventPublisher(OutboxRecorder recorder) {
        this.recorder = recorder;
    }

    public void inventoryLocked(InventoryLock lock) {
        recorder.record(OutboxTopics.INVENTORY_EVENTS, EventTypes.INVENTORY_LOCKED, AGGREGATE_TYPE,
                lock.getId(), new InventoryLocked(lock.getId(), lock.getInventoryUnitId(),
                        lock.getQuantity(), lock.getPurpose().name(), lock.getHolderType(), lock.getHolderId()));
    }

    public void inventoryReleased(InventoryLock lock) {
        recorder.record(OutboxTopics.INVENTORY_EVENTS, EventTypes.INVENTORY_RELEASED, AGGREGATE_TYPE,
                lock.getId(), new InventoryReleased(lock.getId(), lock.getInventoryUnitId(),
                        lock.getQuantity(), lock.getHolderType(), lock.getHolderId()));
    }

    /**
     * A rejected lock attempt. There is no business write to commit together with this
     * event, so it is recorded on its own; it is informational (容量观察 / 运营提示).
     */
    public void inventorySoldOut(String resourceId, LocalDate date, String specId,
                                 int requestedQuantity, String holderType, String holderId) {
        String aggregateId = resourceId + "|" + specId + "|" + date;
        recorder.record(OutboxTopics.INVENTORY_EVENTS, EventTypes.INVENTORY_SOLD_OUT, UNIT_AGGREGATE_TYPE,
                aggregateId, new InventorySoldOut(resourceId, specId, date, requestedQuantity,
                        holderType, holderId));
    }

    public record InventoryLocked(String lockId, String inventoryUnitId, int quantity,
                                  String purpose, String holderType, String holderId) {
    }

    public record InventoryReleased(String lockId, String inventoryUnitId, int quantity,
                                    String holderType, String holderId) {
    }

    public record InventorySoldOut(String resourceId, String specId, LocalDate date, int requestedQuantity,
                                   String holderType, String holderId) {
    }
}
