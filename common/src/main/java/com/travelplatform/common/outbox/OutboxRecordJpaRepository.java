package com.travelplatform.common.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository contract used by {@link JpaOutboxStore}. Each service extends this
 * with a concrete entity so Spring Data derives the query per schema.
 */
public interface OutboxRecordJpaRepository<T extends AbstractOutboxRecord> extends JpaRepository<T, String> {

    List<T> findByStatusOrderByCreatedAtAsc(OutboxStatus status, Pageable pageable);
}
