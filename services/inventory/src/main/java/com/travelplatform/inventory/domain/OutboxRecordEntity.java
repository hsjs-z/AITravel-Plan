package com.travelplatform.inventory.domain;

import com.travelplatform.common.outbox.AbstractOutboxRecord;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Outbox rows of inventory, written in the same transaction as the lock change. */
@Entity
@Table(name = "outbox_record")
public class OutboxRecordEntity extends AbstractOutboxRecord {
}
