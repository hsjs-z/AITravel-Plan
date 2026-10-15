package com.travelplatform.aiplanning.application;

import com.travelplatform.aiplanning.domain.PlanningTask;
import com.travelplatform.common.outbox.EventTypes;
import com.travelplatform.common.outbox.OutboxRecorder;
import com.travelplatform.common.outbox.OutboxTopics;
import org.springframework.stereotype.Component;

/**
 * Publishes planning lifecycle events through the transactional outbox (T023).
 * The user id travels in the payload because notification is per-user (FR-033).
 */
@Component
public class PlanningEventPublisher {

    private static final String AGGREGATE_TYPE = "PLANNING_TASK";

    private final OutboxRecorder recorder;

    public PlanningEventPublisher(OutboxRecorder recorder) {
        this.recorder = recorder;
    }

    public void planningSucceeded(PlanningTask task) {
        recorder.record(OutboxTopics.PLANNING_EVENTS, EventTypes.PLANNING_SUCCEEDED, AGGREGATE_TYPE, task.getId(),
                new PlanningSucceeded(task.getId(), task.getUserId(), task.getItineraryId(),
                        task.getDestination(), task.getStatus().name()));
    }

    public void planningFailed(PlanningTask task) {
        recorder.record(OutboxTopics.PLANNING_EVENTS, EventTypes.PLANNING_FAILED, AGGREGATE_TYPE, task.getId(),
                new PlanningFailed(task.getId(), task.getUserId(), task.getFailureReason()));
    }

    public record PlanningSucceeded(
            String taskId, String userId, String itineraryId, String destination, String status) {
    }

    public record PlanningFailed(String taskId, String userId, String reason) {
    }
}
