package com.travelplatform.aiplanning.application;

import com.travelplatform.aiplanning.api.PlanningController;
import com.travelplatform.aiplanning.domain.PlanningTask;
import com.travelplatform.aiplanning.domain.PlanningTaskRepository;
import com.travelplatform.aiplanning.domain.PlanningTaskStateMachine;
import com.travelplatform.aiplanning.domain.PlanningTaskStatus;
import com.travelplatform.common.api.BusinessException;
import com.travelplatform.common.api.ErrorCode;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class PlanningService {

    private final PlanningTaskRepository repository;
    private final PlannerClient planner;
    private final PlanningTaskStateMachine stateMachine = new PlanningTaskStateMachine();
    private final TaskExecutor executor;

    public PlanningService(PlanningTaskRepository repository, PlannerClient planner, TaskExecutor executor) {
        this.repository = repository;
        this.planner = planner;
        this.executor = executor;
    }

    public PlanningTask submit(String userId, PlanningController.CreateTaskRequest request) {
        PlanningTask task = new PlanningTask();
        task.setId(UUID.randomUUID().toString());
        task.setUserId(userId);
        task.setDestination(request.destination());
        task.setDays(request.days());
        task.setPartySize(request.partySize());
        task.setBudget(request.budget());
        task.setPreferences(request.preferences());
        task.setStatus(PlanningTaskStatus.PENDING);
        task.setCreatedAt(Instant.now());
        task.setUpdatedAt(Instant.now());
        repository.save(task);
        executor.execute(() -> run(task.getId()));
        return task;
    }

    public PlanningTask get(String userId, String taskId) {
        return repository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "planning task not found"));
    }

    public PlanningTask retry(String userId, String taskId) {
        PlanningTask task = get(userId, taskId);
        stateMachine.retry(task);
        task.setUpdatedAt(Instant.now());
        repository.save(task);
        executor.execute(() -> run(task.getId()));
        return task;
    }

    private void run(String taskId) {
        PlanningTask task = repository.findById(taskId).orElse(null);
        if (task == null) {
            return;
        }
        stateMachine.start(task);
        task.setUpdatedAt(Instant.now());
        repository.save(task);
        try {
            String result = planner.generate(task);
            task.setResult(result);
            stateMachine.succeed(task);
        } catch (Exception e) {
            stateMachine.fail(task, e.getMessage());
        }
        task.setUpdatedAt(Instant.now());
        repository.save(task);
    }
}
