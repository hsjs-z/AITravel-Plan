package com.travelplatform.aiplanning.domain;

import com.travelplatform.common.outbox.AbstractOutboxRecord;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Outbox rows of ai-planning, written in the same transaction as the task change. */
@Entity
@Table(name = "outbox_record")
public class OutboxRecordEntity extends AbstractOutboxRecord {
}
