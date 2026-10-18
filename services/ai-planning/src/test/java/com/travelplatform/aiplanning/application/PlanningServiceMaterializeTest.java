package com.travelplatform.aiplanning.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplatform.aiplanning.api.PlanningController;
import com.travelplatform.aiplanning.domain.PlanningTask;
import com.travelplatform.aiplanning.domain.PlanningTaskRepository;
import com.travelplatform.aiplanning.domain.PlanningTaskStatus;
import com.travelplatform.aiplanning.infrastructure.ItineraryClient;
import com.travelplatform.common.outbox.OutboxRecorder;
import com.travelplatform.common.outbox.OutboxStore;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskExecutor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlanningServiceMaterializeTest {

    private final PlanningTaskRepository repository = mock(PlanningTaskRepository.class);
    private final PlannerClient planner = mock(PlannerClient.class);
    private final ItineraryClient itineraryClient = mock(ItineraryClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TaskExecutor executor = Runnable::run;
    private final Tracer tracer = mock(Tracer.class);
    private final Propagator propagator = mock(Propagator.class);

    private final PlanningEventPublisher events =
            new PlanningEventPublisher(new OutboxRecorder(
                    mock(OutboxStore.class), new ObjectMapper().findAndRegisterModules()));
    private final PlanningTaskWriter writer = new PlanningTaskWriter(repository, events);

    private final PlanningService service = new PlanningService(
            repository, planner, itineraryClient, new TimeSlotAllocator(),
            objectMapper, writer, executor, tracer, propagator);

    @Test
    void materializesValidPaidPairIntoItineraryItem() {
        primeTask();
        when(planner.generate(any(), any())).thenAnswer(invocation -> {
            PlanningRunContext context = invocation.getArgument(1);
            context.merge(List.of(paidRef("r-attraction-1", "s-ticket-1", 260L)));
            return new PlanningOutput.Plan(
                    "云南", 2,
                    List.of(new PlanningOutput.Day(1, "第一天", List.of(
                            new PlanningOutput.Attraction(
                                    "玉龙雪山", new BigDecimal("4"), "雪山景区", 260L,
                                    "r-attraction-1", "s-ticket-1")))),
                    List.of("提示"));
        });
        when(itineraryClient.create(any(), any()))
                .thenReturn(new ItineraryClient.ItineraryApiResponse(
                        0, "OK", new ItineraryClient.ItineraryData("it-1")));

        service.submit("u-1", new PlanningController.CreateTaskRequest("云南", 2, 2, 3000L, null));

        ArgumentCaptor<ItineraryClient.ItineraryCreateRequest> captor =
                ArgumentCaptor.forClass(ItineraryClient.ItineraryCreateRequest.class);
        verify(itineraryClient).create(eq("u-1"), captor.capture());
        assertTrue(captor.getValue().items().stream().anyMatch(item ->
                "r-attraction-1".equals(item.resourceId())
                        && "s-ticket-1".equals(item.specId())
                        && "ATTRACTION".equals(item.resourceType())));
    }

    @Test
    void fabricatedIdsAreNulledAndAttractionStaysAsUnmatched() {
        primeTask();
        when(planner.generate(any(), any())).thenAnswer(invocation -> {
            PlanningRunContext context = invocation.getArgument(1);
            context.merge(List.of(paidRef("r-attraction-1", "s-ticket-1", 260L)));
            return new PlanningOutput.Plan(
                    "云南", 1,
                    List.of(new PlanningOutput.Day(1, "第一天", List.of(
                            new PlanningOutput.Attraction(
                                    "编造景点", new BigDecimal("2"), "简介", 100L,
                                    "r-fabricated", "s-fabricated"),
                            new PlanningOutput.Attraction(
                                    "玉龙雪山", new BigDecimal("4"), "雪山景区", 260L,
                                    "r-attraction-1", "s-ticket-1")))),
                    List.of("提示"));
        });
        when(itineraryClient.create(any(), any()))
                .thenReturn(new ItineraryClient.ItineraryApiResponse(
                        0, "OK", new ItineraryClient.ItineraryData("it-1")));

        service.submit("u-1", new PlanningController.CreateTaskRequest("云南", 1, 2, 3000L, null));

        ArgumentCaptor<ItineraryClient.ItineraryCreateRequest> captor =
                ArgumentCaptor.forClass(ItineraryClient.ItineraryCreateRequest.class);
        verify(itineraryClient).create(eq("u-1"), captor.capture());

        List<ItineraryClient.Item> items = captor.getValue().items();
        ItineraryClient.Item fabricated = items.stream()
                .filter(item -> item.description().startsWith("编造景点"))
                .findFirst()
                .orElseThrow();
        assertNull(fabricated.resourceId());
        assertNull(fabricated.specId());
        assertTrue(items.stream().anyMatch(item ->
                "r-attraction-1".equals(item.resourceId())
                        && "s-ticket-1".equals(item.specId())));
    }

    @Test
    void freeResourceKeepsResourceIdAndNullSpecId() {
        primeTask();
        when(planner.generate(any(), any())).thenAnswer(invocation -> {
            PlanningRunContext context = invocation.getArgument(1);
            context.merge(List.of(freeRef("r-free-1", "丽江古城", 0L)));
            return new PlanningOutput.Plan(
                    "丽江", 1,
                    List.of(new PlanningOutput.Day(1, "第一天", List.of(
                            new PlanningOutput.Attraction(
                                    "丽江古城", new BigDecimal("3"), "古城景区", 0L,
                                    "r-free-1", null)))),
                    List.of("免费景点可加入行程"));
        });
        when(itineraryClient.create(any(), any()))
                .thenReturn(new ItineraryClient.ItineraryApiResponse(
                        0, "OK", new ItineraryClient.ItineraryData("it-1")));

        service.submit("u-1", new PlanningController.CreateTaskRequest("丽江", 1, 1, 0L, null));

        ArgumentCaptor<ItineraryClient.ItineraryCreateRequest> captor =
                ArgumentCaptor.forClass(ItineraryClient.ItineraryCreateRequest.class);
        verify(itineraryClient).create(eq("u-1"), captor.capture());
        assertTrue(captor.getValue().items().stream().anyMatch(item ->
                "r-free-1".equals(item.resourceId()) && item.specId() == null));
    }

    @Test
    void plannerFailureFailsTaskWithFailureReason() {
        AtomicReference<PlanningTask> saved = new AtomicReference<>();
        when(repository.save(any())).thenAnswer(invocation -> {
            PlanningTask task = invocation.getArgument(0);
            saved.set(task);
            return task;
        });
        when(repository.findById(anyString())).thenAnswer(invocation -> Optional.ofNullable(saved.get()));
        when(planner.generate(any(), any()))
                .thenThrow(new IllegalStateException("DeepSeek request timeout"));

        service.submit("u-1", new PlanningController.CreateTaskRequest("西安", 1, 1, 1000L, null));

        assertEquals(PlanningTaskStatus.FAILED, saved.get().getStatus());
        assertEquals("DeepSeek request timeout", saved.get().getFailureReason());
    }

    private void primeTask() {
        AtomicReference<PlanningTask> saved = new AtomicReference<>();
        when(repository.save(any())).thenAnswer(invocation -> {
            PlanningTask task = invocation.getArgument(0);
            saved.set(task);
            return task;
        });
        when(repository.findById(anyString())).thenAnswer(invocation -> Optional.ofNullable(saved.get()));
    }

    private AttractionRef paidRef(String resourceId, String specId, long price) {
        return new AttractionRef(resourceId, "付费景区", "云南省", "丽江", "5A", "称号",
                "简介", new BigDecimal("4"), price, false,
                new BigDecimal("27"), new BigDecimal("100"), "READY",
                specId, "成人票", price, 200);
    }

    private AttractionRef freeRef(String resourceId, String name, long price) {
        return new AttractionRef(resourceId, name, "云南省", "丽江", "5A", "称号",
                "简介", new BigDecimal("3"), price, true,
                new BigDecimal("26.87"), new BigDecimal("100.22"), "READY",
                null, null, 0L, null);
    }
}
