package com.travelplatform.aiplanning.application;

import com.travelplatform.aiplanning.api.PlanningController;
import com.travelplatform.aiplanning.domain.PlanningTask;
import com.travelplatform.aiplanning.domain.PlanningTaskRepository;
import com.travelplatform.aiplanning.domain.PlanningTaskStatus;
import com.travelplatform.aiplanning.infrastructure.ItineraryClient;
import com.travelplatform.common.api.BusinessException;
import com.travelplatform.common.api.ErrorCode;
import com.travelplatform.common.observability.TraceContext;
import com.travelplatform.common.observability.TraceContinuation;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.task.TaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
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
    private final PlanningTaskWriter writer;
    private final TaskExecutor executor;
    private final Tracer tracer;
    private final Propagator propagator;

    public PlanningService(PlanningTaskRepository repository,
                           PlannerClient planner,
                           ItineraryClient itineraryClient,
                           ObjectMapper objectMapper,
                           PlanningTaskWriter writer,
                           @Qualifier("applicationTaskExecutor") TaskExecutor executor,
                           Tracer tracer,
                           Propagator propagator) {
        this.repository = repository;
        this.planner = planner;
        this.itineraryClient = itineraryClient;
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
                PlanningRunContext whitelist = new PlanningRunContext();
                PlanningOutput.Plan plan = planner.generate(task, whitelist);
                String result = objectMapper.writeValueAsString(plan);
                String itineraryId = materializeItinerary(task, plan, whitelist);
                writer.succeed(taskId, result, itineraryId);
            } catch (Exception e) {
                writer.fail(taskId, truncate(e.getMessage(), 512));
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
    private String materializeItinerary(PlanningTask task,
                                        PlanningOutput.Plan plan,
                                        PlanningRunContext whitelist) {
        try {
            LocalDate startDate = LocalDate.now();
            List<ItineraryClient.Item> items = new ArrayList<>();
            for (PlanningOutput.Day day : plan.days() == null ? List.<PlanningOutput.Day>of() : plan.days()) {
                for (PlanningOutput.Attraction attraction :
                        day.attractions() == null ? List.<PlanningOutput.Attraction>of() : day.attractions()) {
                    Binding binding = bindingOf(attraction, whitelist);
                    items.add(new ItineraryClient.Item(
                            startDate.plusDays(Math.max(0, day.day() - 1)),
                            descriptionOf(attraction),
                            "ATTRACTION",
                            binding.resourceId(),
                            binding.specId(),
                            "AI"));
                }
            }

            ItineraryClient.ItineraryCreateRequest request = new ItineraryClient.ItineraryCreateRequest(
                    plan.destination() == null || plan.destination().isBlank()
                            ? task.getDestination()
                            : plan.destination(),
                    startDate,
                    startDate.plusDays(Math.max(0, plan.totalDays() - 1)),
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

    private Binding bindingOf(PlanningOutput.Attraction attraction, PlanningRunContext whitelist) {
        String resourceId = attraction.resourceId();
        String specId = attraction.specId();
        if (resourceId != null && specId != null && whitelist.validSpecPair(resourceId, specId)) {
            return new Binding(resourceId, specId);
        }
        if (resourceId != null && (specId == null || specId.isBlank())
                && whitelist.validFreeResource(resourceId)) {
            return new Binding(resourceId, null);
        }
        return new Binding(null, null);
    }

    private String descriptionOf(PlanningOutput.Attraction attraction) {
        String name = attraction.name() == null || attraction.name().isBlank()
                ? "未命名景点"
                : attraction.name();
        if (attraction.description() == null || attraction.description().isBlank()) {
            return name;
        }
        return name + "：" + attraction.description();
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private record Binding(String resourceId, String specId) {
    }
}
