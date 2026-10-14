package com.travelplatform.aiplanning.domain;

import com.travelplatform.common.api.BusinessException;
import com.travelplatform.common.api.ErrorCode;

/**
 * Planning task lifecycle: PENDING -> RUNNING -> SUCCEEDED | FAILED.
 * A FAILED task can be retried back to PENDING.
 */
public final class PlanningTaskStateMachine {

    public void start(PlanningTask task) {
        require(task.getStatus() == PlanningTaskStatus.PENDING, "task is not pending");
        task.setStatus(PlanningTaskStatus.RUNNING);
    }

    public void succeed(PlanningTask task) {
        require(task.getStatus() == PlanningTaskStatus.RUNNING, "task is not running");
        task.setStatus(PlanningTaskStatus.SUCCEEDED);
    }

    public void fail(PlanningTask task, String reason) {
        require(task.getStatus() == PlanningTaskStatus.RUNNING, "task is not running");
        task.setStatus(PlanningTaskStatus.FAILED);
        task.setFailureReason(reason);
    }

    public void retry(PlanningTask task) {
        require(task.getStatus() == PlanningTaskStatus.FAILED, "only failed tasks can be retried");
        task.setStatus(PlanningTaskStatus.PENDING);
        task.setFailureReason(null);
        task.setResult(null);
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new BusinessException(ErrorCode.CONFLICT, message);
        }
    }
}
