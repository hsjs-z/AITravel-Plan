package com.travelplatform.itinerary.domain;

import com.travelplatform.common.outbox.AbstractOutboxRecord;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Outbox rows of itinerary, written in the same transaction as the itinerary change. */
@Entity
@Table(name = "outbox_record")
public class OutboxRecordEntity extends AbstractOutboxRecord {
}
