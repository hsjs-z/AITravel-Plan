package com.travelplatform.itinerary.domain;

import com.travelplatform.common.outbox.OutboxRecordJpaRepository;

public interface OutboxRecordRepository extends OutboxRecordJpaRepository<OutboxRecordEntity> {
}
