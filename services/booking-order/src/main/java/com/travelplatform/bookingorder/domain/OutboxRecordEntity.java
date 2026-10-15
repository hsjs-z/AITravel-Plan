package com.travelplatform.bookingorder.domain;

import com.travelplatform.common.outbox.AbstractOutboxRecord;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Outbox rows of booking-order, written in the same transaction as the order change. */
@Entity
@Table(name = "outbox_record")
public class OutboxRecordEntity extends AbstractOutboxRecord {
}
