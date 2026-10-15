package com.travelplatform.bookingorder.domain;

import com.travelplatform.common.outbox.OutboxRecordJpaRepository;

public interface OutboxRecordRepository extends OutboxRecordJpaRepository<OutboxRecordEntity> {
}
