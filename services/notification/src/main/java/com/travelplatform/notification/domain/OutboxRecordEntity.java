package com.travelplatform.notification.domain;

import com.travelplatform.common.outbox.AbstractOutboxRecord;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Outbox rows of notification, written with the NotificationSent event. */
@Entity
@Table(name = "outbox_record")
public class OutboxRecordEntity extends AbstractOutboxRecord {
}
