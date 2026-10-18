package com.travelplatform.aiplanning.api;

import com.travelplatform.aiplanning.application.PlanningService;
import com.travelplatform.aiplanning.application.TipsService;
import com.travelplatform.aiplanning.domain.Pace;
import com.travelplatform.aiplanning.domain.PlanningTask;
import com.travelplatform.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class PlanningController {

    private final PlanningService planningService;
    private final TipsService tipsService;

    public PlanningController(PlanningService planningService, TipsService tipsService) {
        this.planningService = planningService;
        this.tipsService = tipsService;
    }

    @PostMapping("/planning/tasks")
    public ApiResponse<CreateTaskResponse> create(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody CreateTaskRequest request) {
        PlanningTask task = planningService.submit(userId, request);
        return ApiResponse.success(new CreateTaskResponse(task.getId()));
    }

    @GetMapping("/planning/tasks/{taskId}")
    public ApiResponse<TaskResponse> get(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable("taskId") String taskId) {
        PlanningTask task = planningService.get(userId, taskId);
        return ApiResponse.success(toResponse(task));
    }

    @PostMapping("/planning/tasks/{taskId}/retry")
    public ApiResponse<TaskResponse> retry(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable("taskId") String taskId) {
        PlanningTask task = planningService.retry(userId, taskId);
        return ApiResponse.success(toResponse(task));
    }

    @PostMapping("/planning/tips")
    public ApiResponse<List<String>> tips(@Valid @RequestBody TipsRequest request) {
        return ApiResponse.success(tipsService.generate(
                request.destination(), request.days(), request.attractions()));
    }

    private TaskResponse toResponse(PlanningTask task) {
        return new TaskResponse(
                task.getId(),
                task.getStatus().name(),
                task.getItineraryId(),
                task.getFailureReason(),
                task.getResult(),
                task.getPace() == null ? Pace.BALANCED.name() : task.getPace().name());
    }

    public record CreateTaskRequest(
            @NotBlank String destination,
            @NotNull @Min(1) @Max(30) Integer days,
            @NotNull @Min(1) @Max(20) Integer partySize,
            Long budget,
            Pace pace,
            String preferences) {

        public CreateTaskRequest(String destination, Integer days, Integer partySize,
                                 Long budget, String preferences) {
            this(destination, days, partySize, budget, null, preferences);
        }
    }

    public record CreateTaskResponse(String taskId) {
    }

    public record TipsRequest(
            @NotBlank String destination,
            @NotNull @Min(1) Integer days,
            List<String> attractions) {
    }

    public record TaskResponse(
            String id,
            String status,
            String itineraryId,
            String failureReason,
            String result,
            String pace) {
    }
}
