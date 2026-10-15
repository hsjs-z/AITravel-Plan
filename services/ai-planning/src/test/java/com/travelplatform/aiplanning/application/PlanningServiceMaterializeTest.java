package com.travelplatform.aiplanning.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplatform.aiplanning.api.PlanningController;
import com.travelplatform.aiplanning.domain.PlanningTask;
import com.travelplatform.aiplanning.domain.PlanningTaskRepository;
import com.travelplatform.aiplanning.infrastructure.InventoryClient;
import com.travelplatform.aiplanning.infrastructure.ItineraryClient;
import com.travelplatform.common.outbox.OutboxRecorder;
import com.travelplatform.common.outbox.OutboxStore;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class PlanningServiceMaterializeTest {

    private final PlanningTaskRepository repository = mock(PlanningTaskRepository.class);
    private final PlannerClient planner = mock(PlannerClient.class);
    private final ItineraryClient itineraryClient = mock(ItineraryClient.class);
    private final RecommendationTool recommendationTool = mock(RecommendationTool.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TaskExecutor executor = Runnable::run;

    private final PlanningEventPublisher events =
            new PlanningEventPublisher(new OutboxRecorder(
                    mock(OutboxStore.class), new ObjectMapper().findAndRegisterModules()));
    private final PlanningTaskWriter writer = new PlanningTaskWriter(repository, events);

    private final PlanningService service =
            new PlanningService(
                    repository, planner, itineraryClient, recommendationTool, objectMapper, writer, executor);

    @Test
    void materializesResourceIdsIntoItineraryItems() {
        AtomicReference<PlanningTask> saved = new AtomicReference<>();
        when(repository.save(any())).thenAnswer(invocation -> {
            PlanningTask task = invocation.getArgument(0);
            saved.set(task);
            return task;
        });
        when(repository.findById(anyString())).thenAnswer(invocation -> Optional.of(saved.get()));

        when(planner.generate(any(), any())).thenReturn("""
                {
                  "destination": "云南",
                  "totalDays": 2,
                  "partySize": 2,
                  "estimatedBudget": 3000,
                  "days": [{"day": 1, "arrangements": ["抵达"]}, {"day": 2, "arrangements": ["游览"]}],
                  "hotels": [{"name": "丽江古城精品酒店", "resourceId": "r-hotel-1", "specId": "s-room-1", "dayIndex": 1}],
                  "attractions": [{"name": "玉龙雪山", "resourceId": "r-attraction-1", "specId": "s-ticket-1", "dayIndex": 2}]
                }
                """);

        when(recommendationTool.fetchCandidates(any(), any())).thenReturn(List.of(
                new InventoryClient.Candidate("r-hotel-1", "丽江古城精品酒店", "TRADABLE",
                        List.of(new InventoryClient.Spec("s-room-1", "标准大床房", 500L, 20))),
                new InventoryClient.Candidate("r-attraction-1", "玉龙雪山", "TRADABLE",
                        List.of(new InventoryClient.Spec("s-ticket-1", "成人票", 260L, 20)))));
        when(recommendationTool.buildCandidateContext(any())).thenReturn("ctx");
        when(itineraryClient.create(any(), any()))
                .thenReturn(new ItineraryClient.ItineraryApiResponse(0, "OK", new ItineraryClient.ItineraryData("it-1")));

        service.submit("u-1", new PlanningController.CreateTaskRequest("云南", 2, 2, 3000L, null));

        ArgumentCaptor<ItineraryClient.ItineraryCreateRequest> captor =
                ArgumentCaptor.forClass(ItineraryClient.ItineraryCreateRequest.class);
        verify(itineraryClient).create(eq("u-1"), captor.capture());

        List<ItineraryClient.Item> items = captor.getValue().items();
        assertTrue(items.stream().anyMatch(i ->
                        "r-hotel-1".equals(i.resourceId()) && "s-room-1".equals(i.specId())),
                "hotel resourceId/specId must be materialized");
        assertTrue(items.stream().anyMatch(i ->
                        "r-attraction-1".equals(i.resourceId()) && "s-ticket-1".equals(i.specId())),
                "attraction resourceId/specId must be materialized");
    }
}
