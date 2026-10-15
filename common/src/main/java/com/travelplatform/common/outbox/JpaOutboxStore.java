package com.travelplatform.common.outbox;

import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

/**
 * Default {@link OutboxStore} over a JPA-mapped outbox table.
 *
 * <p>{@link #persist} deliberately runs without its own transaction so it joins the
 * caller's business transaction; the relay-side methods open their own transaction
 * because they are called from the scheduler thread.
 */
public class JpaOutboxStore<T extends AbstractOutboxRecord> implements OutboxStore {

    private static final int MAX_ERROR_LENGTH = 500;

    private final OutboxRecordJpaRepository<T> repository;
    private final Supplier<T> factory;

    public JpaOutboxStore(OutboxRecordJpaRepository<T> repository, Supplier<T> factory) {
        this.repository = repository;
        this.factory = factory;
    }

    @Override
    public void persist(OutboxMessage message) {
        T record = factory.get();
        record.setId(message.getId());
        record.setTopic(message.getTopic());
        record.setEventType(message.getEventType());
        record.setAggregateType(message.getAggregateType());
        record.setAggregateId(message.getAggregateId());
        record.setStatus(OutboxStatus.NEW);
        record.setPayload(message.getPayload());
        record.setTraceId(message.getTraceId());
        record.setAttempts(0);
        record.setCreatedAt(message.getCreatedAt());
        repository.save(record);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OutboxMessage> findPending(int limit) {
        return repository.findByStatusOrderByCreatedAtAsc(OutboxStatus.NEW, PageRequest.of(0, limit)).stream()
                .map(this::toMessage)
                .toList();
    }

    @Override
    @Transactional
    public void markSent(String id, Instant publishedAt) {
        repository.findById(id).ifPresent(record -> {
            record.setStatus(OutboxStatus.SENT);
            record.setPublishedAt(publishedAt);
            record.setLastError(null);
            repository.save(record);
        });
    }

    @Override
    @Transactional
    public void markAttemptFailure(String id, String error) {
        repository.findById(id).ifPresent(record -> {
            record.setAttempts(record.getAttempts() + 1);
            record.setLastError(truncate(error));
            repository.save(record);
        });
    }

    private OutboxMessage toMessage(T record) {
        OutboxMessage message = new OutboxMessage();
        message.setId(record.getId());
        message.setTopic(record.getTopic());
        message.setEventType(record.getEventType());
        message.setAggregateType(record.getAggregateType());
        message.setAggregateId(record.getAggregateId());
        message.setPayload(record.getPayload());
        message.setTraceId(record.getTraceId());
        message.setStatus(record.getStatus().name());
        message.setCreatedAt(record.getCreatedAt());
        message.setPublishedAt(record.getPublishedAt());
        return message;
    }

    private String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
    }
}
