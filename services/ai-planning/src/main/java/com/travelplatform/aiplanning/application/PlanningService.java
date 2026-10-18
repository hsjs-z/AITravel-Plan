package com.travelplatform.aiplanning.application;

import com.travelplatform.aiplanning.api.PlanningController;
import com.travelplatform.aiplanning.domain.Pace;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PlanningService {

    private static final Logger log = LoggerFactory.getLogger(PlanningService.class);

    private final PlanningTaskRepository repository;
    private final PlannerClient planner;
    private final ItineraryClient itineraryClient;
    private final TimeSlotAllocator allocator;
    private final ObjectMapper objectMapper;
    private final PlanningTaskWriter writer;
    private final TaskExecutor executor;
    private final Tracer tracer;
    private final Propagator propagator;

    public PlanningService(PlanningTaskRepository repository,
                           PlannerClient planner,
                           ItineraryClient itineraryClient,
                           TimeSlotAllocator allocator,
                           ObjectMapper objectMapper,
                           PlanningTaskWriter writer,
                           @Qualifier("applicationTaskExecutor") TaskExecutor executor,
                           Tracer tracer,
                           Propagator propagator) {
        this.repository = repository;
        this.planner = planner;
        this.itineraryClient = itineraryClient;
        this.allocator = allocator;
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
        task.setPace(request.pace() == null ? Pace.BALANCED : request.pace());
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
                Pace pace = task.getPace() == null ? Pace.BALANCED : task.getPace();
                PackedOutput.Plan packed = pack(plan, whitelist, pace);
                String result = objectMapper.writeValueAsString(packed);
                String itineraryId = materializeItinerary(task, packed);
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
    private PackedOutput.Plan pack(PlanningOutput.Plan plan,
                                   PlanningRunContext whitelist,
                                   Pace pace) {
        TimeSlotAllocator.AllocationResult allocation = allocator.allocate(pace, plan.days());
        List<PackedOutput.Day> days = new ArrayList<>();
        for (TimeSlotAllocator.AllocatedDay allocatedDay : allocation.days()) {
            Map<String, List<PackedOutput.Item>> slots = new LinkedHashMap<>();
            for (String slot : TimeSlotAllocator.SLOT_ORDER) {
                List<PackedOutput.Item> items = allocatedDay.slots()
                        .getOrDefault(slot, List.of())
                        .stream()
                        .map(allocated -> packedItem(allocated, whitelist))
                        .toList();
                slots.put(slot, items);
            }
            days.add(new PackedOutput.Day(allocatedDay.day(), allocatedDay.theme(), slots));
        }
        return new PackedOutput.Plan(
                plan.destination(),
                plan.totalDays(),
                pace.name(),
                days,
                allocation.warnings(),
                plan.tips());
    }

    private PackedOutput.Item packedItem(TimeSlotAllocator.AllocatedItem allocated,
                                         PlanningRunContext whitelist) {
        PlanningOutput.Attraction attraction = allocated.attraction();
        String rawResourceId = attraction.resourceId();
        String rawSpecId = attraction.specId();
        boolean paidValid = rawResourceId != null && rawSpecId != null
                && whitelist.validSpecPair(rawResourceId, rawSpecId);
        boolean freeValid = rawResourceId != null
                && (rawSpecId == null || rawSpecId.isBlank())
                && whitelist.validFreeResource(rawResourceId);
        boolean matched = paidValid || freeValid;
        String resourceId = matched ? rawResourceId : null;
        String specId = paidValid ? rawSpecId : null;
        AttractionRef ref = matched ? whitelist.find(resourceId) : null;
        String priceLabel = paidValid
                ? "¥" + (ref != null && ref.price() != null ? ref.price() : attraction.ticketPrice())
                : freeValid ? "免费/无需购票" : "以官方为准";
        return new PackedOutput.Item(
                attraction.name(),
                attraction.description(),
                attraction.suggestedHours(),
                attraction.ticketPrice(),
                resourceId,
                specId,
                matched,
                paidValid,
                priceLabel,
                ref == null ? null : ref.latitude(),
                ref == null ? null : ref.longitude(),
                ref == null ? null : ref.coordinateStatus(),
                allocated.timeSlot(),
                allocated.sortOrder());
    }

    private String materializeItinerary(PlanningTask task, PackedOutput.Plan plan) {
        try {
            LocalDate startDate = LocalDate.now();
            List<ItineraryClient.Item> items = new ArrayList<>();
            for (PackedOutput.Day day : plan.days() == null ? List.<PackedOutput.Day>of() : plan.days()) {
                for (String slot : TimeSlotAllocator.SLOT_ORDER) {
                    for (PackedOutput.Item item :
                            day.slots() == null ? List.<PackedOutput.Item>of()
                                    : day.slots().getOrDefault(slot, List.of())) {
                        items.add(new ItineraryClient.Item(
                                startDate.plusDays(Math.max(0, day.day() - 1)),
                                descriptionOf(item),
                                "ATTRACTION",
                                item.resourceId(),
                                item.specId(),
                                "AI",
                                item.timeSlot(),
                                item.sortOrder()));
                    }
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
                    items,
                    plan.warnings());

            ItineraryClient.ItineraryApiResponse response =
                    itineraryClient.create(task.getUserId(), request);
            return response.data().id();
        } catch (Exception e) {
            log.warn("failed to materialize itinerary for task {}: {}", task.getId(), e.toString());
            return null;
        }
    }

    private String descriptionOf(PackedOutput.Item item) {
        String name = item.name() == null || item.name().isBlank()
                ? "未命名景点"
                : item.name();
        if (item.description() == null || item.description().isBlank()) {
            return name;
        }
        return name + "：" + item.description();
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

}
