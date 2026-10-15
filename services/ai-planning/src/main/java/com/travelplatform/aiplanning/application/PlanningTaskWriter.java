package com.travelplatform.aiplanning.application;

import com.travelplatform.aiplanning.domain.PlanningTask;
import com.travelplatform.aiplanning.domain.PlanningTaskRepository;
import com.travelplatform.aiplanning.domain.PlanningTaskStateMachine;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Owns planning task state transitions together with their events, so the status
 * change and the outbox row commit in one transaction (宪法 IV). The async
 * planning run itself stays outside any transaction: the model call and the
 * itinerary materialization are remote calls and must not hold a database lock.
 */
@Component
public class PlanningTaskWriter {

    private final PlanningTaskRepository repository;
    private final PlanningEventPublisher events;
    private final PlanningTaskStateMachine stateMachine = new PlanningTaskStateMachine();

    public PlanningTaskWriter(PlanningTaskRepository repository, PlanningEventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    @Transactional
    public PlanningTask start(String taskId) {
        PlanningTask task = require(taskId);
        stateMachine.start(task);
        task.setUpdatedAt(Instant.now());
        return repository.save(task);
    }

    @Transactional
    public PlanningTask succeed(String taskId, String result, String itineraryId) {
        PlanningTask task = require(taskId);
        task.setResult(result);
        if (itineraryId != null) {
            task.setItineraryId(itineraryId);
        }
        stateMachine.succeed(task);
        task.setUpdatedAt(Instant.now());
        repository.save(task);
        events.planningSucceeded(task);
        return task;
    }

    @Transactional
    public PlanningTask fail(String taskId, String reason) {
        PlanningTask task = require(taskId);
        stateMachine.fail(task, reason);
        task.setUpdatedAt(Instant.now());
        repository.save(task);
        events.planningFailed(task);
        return task;
    }

    /** Retry is a state change without a contract event; it stays transactional for consistency. */
    @Transactional
    public PlanningTask retry(PlanningTask task) {
        stateMachine.retry(task);
        task.setUpdatedAt(Instant.now());
        return repository.save(task);
    }

    private PlanningTask require(String taskId) {
        return repository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("planning task not found: " + taskId));
    }
}
