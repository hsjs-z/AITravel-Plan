package com.travelplatform.aiplanning.application;

import com.travelplatform.aiplanning.api.PlanningController;
import com.travelplatform.aiplanning.domain.PlanningTask;
import com.travelplatform.aiplanning.domain.PlanningTaskRepository;
import com.travelplatform.aiplanning.domain.PlanningTaskStatus;
import com.travelplatform.aiplanning.infrastructure.ItineraryClient;
import com.travelplatform.aiplanning.infrastructure.InventoryClient;
import com.travelplatform.common.api.BusinessException;
import com.travelplatform.common.api.ErrorCode;
import com.travelplatform.common.observability.TraceContext;
import com.travelplatform.common.observability.TraceContinuation;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.task.TaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class PlanningService {

    private static final Logger log = LoggerFactory.getLogger(PlanningService.class);

    private final PlanningTaskRepository repository;
    private final PlannerClient planner;
    private final ItineraryClient itineraryClient;
    private final RecommendationTool recommendationTool;
    private final ObjectMapper objectMapper;
    private final PlanningTaskWriter writer;
    private final TaskExecutor executor;
    private final Tracer tracer;
    private final Propagator propagator;

    public PlanningService(PlanningTaskRepository repository,
                           PlannerClient planner,
                           ItineraryClient itineraryClient,
                           RecommendationTool recommendationTool,
                           ObjectMapper objectMapper,
                           PlanningTaskWriter writer,
                           @Qualifier("applicationTaskExecutor") TaskExecutor executor,
                           Tracer tracer,
                           Propagator propagator) {
        this.repository = repository;
        this.planner = planner;
        this.itineraryClient = itineraryClient;
        this.recommendationTool = recommendationTool;
        this.objectMapper = objectMapper;
        this.writer = writer;
        this.executor = executor;
        this.tracer = tracer;
        this.propagator = propagator;
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
        String traceId = TraceContext.current();
        executor.execute(() -> run(task.getId(), traceId));
        return task;
    }

    public PlanningTask get(String userId, String taskId) {
        return repository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "planning task not found"));
    }

    public PlanningTask retry(String userId, String taskId) {
        PlanningTask task = get(userId, taskId);
        writer.retry(task);
        String traceId = TraceContext.current();
        executor.execute(() -> run(task.getId(), traceId));
        return task;
    }

    private void run(String taskId, String traceId) {
        Span span = spanFor(traceId);
        if (span != null) {
            TraceContext.put(span.context().traceId());
        }
        try (Tracer.SpanInScope ignored = span != null ? tracer.withSpan(span) : null) {
            PlanningTask task = repository.findById(taskId).orElse(null);
            if (task == null) {
                return;
            }
            writer.start(taskId);
            try {
                List<InventoryClient.Candidate> candidates =
                        recommendationTool.fetchCandidates(task.getDestination(), LocalDate.now());
                String candidateContext = recommendationTool.buildCandidateContext(candidates);
                String result = planner.generate(task, candidateContext);
                String itineraryId = materializeItinerary(task, result, candidates);
                writer.succeed(taskId, result, itineraryId);
            } catch (Exception e) {
                writer.fail(taskId, e.getMessage());
            }
        } finally {
            if (span != null) {
                span.end();
            }
            TraceContext.clear();
        }
    }

    private Span spanFor(String traceId) {
        String traceparent = TraceContinuation.traceparentFor(traceId);
        return traceparent == null
                ? null
                : TraceContinuation.startFromRemote(propagator, traceparent, "planning.run");
    }

    /**
     * Creates the draft itinerary for a successful planning run.
     *
     * @return the created itinerary id, or {@code null} when materialization failed
     *         (the planning task itself still succeeds, exactly as before).
     */
    private String materializeItinerary(PlanningTask task, String resultJson,
                                       List<InventoryClient.Candidate> candidates) {
        try {
            Set<String> validPairs = new HashSet<>();
            for (InventoryClient.Candidate candidate : candidates) {
                for (InventoryClient.Spec spec : candidate.specs()) {
                    validPairs.add(candidate.resourceId() + "|" + spec.specId());
                }
            }

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

            for (JsonNode hotel : root.path("hotels")) {
                String resourceId = hotel.path("resourceId").asText(null);
                String specId = hotel.path("specId").asText(null);
                boolean valid = resourceId != null && specId != null
                        && validPairs.contains(resourceId + "|" + specId);
                int hotelDayIndex = hotel.path("dayIndex").asInt(1);
                items.add(new ItineraryClient.Item(
                        startDate.plusDays(Math.max(0, hotelDayIndex - 1)),
                        hotel.path("name").asText("酒店"),
                        valid ? "HOTEL" : null,
                        valid ? resourceId : null,
                        valid ? specId : null,
                        "AI"));
            }

            for (JsonNode attraction : root.path("attractions")) {
                String resourceId = attraction.path("resourceId").asText(null);
                String specId = attraction.path("specId").asText(null);
                boolean valid = resourceId != null && specId != null
                        && validPairs.contains(resourceId + "|" + specId);
                int attractionDayIndex = attraction.path("dayIndex").asInt(1);
                items.add(new ItineraryClient.Item(
                        startDate.plusDays(Math.max(0, attractionDayIndex - 1)),
                        attraction.path("name").asText("景点"),
                        valid ? "ATTRACTION" : null,
                        valid ? resourceId : null,
                        valid ? specId : null,
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
            return response.data().id();
        } catch (Exception e) {
            log.warn("failed to materialize itinerary for task {}: {}", task.getId(), e.toString());
            return null;
        }
    }
}
