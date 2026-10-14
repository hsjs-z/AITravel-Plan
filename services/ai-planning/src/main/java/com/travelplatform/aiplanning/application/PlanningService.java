package com.travelplatform.aiplanning.application;

import com.travelplatform.aiplanning.api.PlanningController;
import com.travelplatform.aiplanning.domain.PlanningTask;
import com.travelplatform.aiplanning.domain.PlanningTaskRepository;
import com.travelplatform.aiplanning.domain.PlanningTaskStateMachine;
import com.travelplatform.aiplanning.domain.PlanningTaskStatus;
import com.travelplatform.aiplanning.infrastructure.ItineraryClient;
import com.travelplatform.common.api.BusinessException;
import com.travelplatform.common.api.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.task.TaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class PlanningService {

    private static final Logger log = LoggerFactory.getLogger(PlanningService.class);

    private final PlanningTaskRepository repository;
    private final PlannerClient planner;
    private final ItineraryClient itineraryClient;
    private final ObjectMapper objectMapper;
    private final PlanningTaskStateMachine stateMachine = new PlanningTaskStateMachine();
    private final TaskExecutor executor;

    public PlanningService(PlanningTaskRepository repository,
                           PlannerClient planner,
                           ItineraryClient itineraryClient,
                           ObjectMapper objectMapper,
                           TaskExecutor executor) {
        this.repository = repository;
        this.planner = planner;
        this.itineraryClient = itineraryClient;
        this.objectMapper = objectMapper;
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
            materializeItinerary(task, result);
            stateMachine.succeed(task);
        } catch (Exception e) {
            stateMachine.fail(task, e.getMessage());
        }
        task.setUpdatedAt(Instant.now());
        repository.save(task);
    }

    private void materializeItinerary(PlanningTask task, String resultJson) {
        try {
            JsonNode root = objectMapper.readTree(resultJson);
            String destination = root.hasNonNull("destination")
                    ? root.get("destination").asText()
                    : task.getDestination();
            int days = root.path("totalDays").asInt(task.getDays());
            LocalDate startDate = LocalDate.now();

            List<ItineraryClient.Item> items = new ArrayList<>();
            int dayIndex = 0;
            for (JsonNode day : root.path("days")) {
                dayIndex++;
                List<String> parts = new ArrayList<>();
                day.path("arrangements").forEach(node -> parts.add(node.asText()));
                String description = parts.isEmpty() ? "自由活动" : String.join("；", parts);
                items.add(new ItineraryClient.Item(
                        startDate.plusDays(dayIndex - 1),
                        description,
                        null,
                        null,
                        null,
                        "AI"));
            }

            ItineraryClient.ItineraryCreateRequest request = new ItineraryClient.ItineraryCreateRequest(
                    destination,
                    startDate,
                    startDate.plusDays(Math.max(0, days - 1)),
                    task.getPartySize(),
                    task.getBudget(),
                    items);

            ItineraryClient.ItineraryApiResponse response =
                    itineraryClient.create(task.getUserId(), request);
            task.setItineraryId(response.data().id());
        } catch (Exception e) {
            log.warn("failed to materialize itinerary for task {}: {}", task.getId(), e.toString());
        }
    }
}
