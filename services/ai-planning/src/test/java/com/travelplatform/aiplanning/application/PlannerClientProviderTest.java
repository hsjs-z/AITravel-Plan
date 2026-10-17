package com.travelplatform.aiplanning.application;

import com.travelplatform.aiplanning.domain.PlanningTask;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlannerClientProviderTest {

    private final DeepSeekPlannerClient deepSeek = mock(DeepSeekPlannerClient.class);
    private final MockPlannerClient mock = mock(MockPlannerClient.class);

    @Test
    void deepSeekWithKeyUsesDeepSeek() {
        PlannerClientProvider provider = new PlannerClientProvider(deepSeek, mock, "deepseek", "key");
        PlanningTask task = task();
        PlanningRunContext context = new PlanningRunContext();
        when(deepSeek.generate(task, context)).thenReturn(new PlanningOutput.Plan("西安", 1, java.util.List.of(), java.util.List.of()));

        provider.generate(task, context);

        verify(deepSeek).generate(task, context);
        verify(mock, never()).generate(task, context);
    }

    @Test
    void deepSeekWithoutKeyFallsBackToMock() {
        PlannerClientProvider provider = new PlannerClientProvider(deepSeek, mock, "deepseek", " ");
        PlanningTask task = task();
        PlanningRunContext context = new PlanningRunContext();
        when(mock.generate(task, context)).thenReturn(new PlanningOutput.Plan("西安", 1, java.util.List.of(), java.util.List.of()));

        provider.generate(task, context);

        verify(mock).generate(task, context);
        verify(deepSeek, never()).generate(task, context);
    }

    @Test
    void explicitMockModeUsesMock() {
        PlannerClientProvider provider = new PlannerClientProvider(deepSeek, mock, "mock", "key");
        PlanningTask task = task();
        PlanningRunContext context = new PlanningRunContext();
        when(mock.generate(task, context)).thenReturn(new PlanningOutput.Plan("西安", 1, java.util.List.of(), java.util.List.of()));

        provider.generate(task, context);

        verify(mock).generate(task, context);
        verify(deepSeek, never()).generate(task, context);
    }

    private PlanningTask task() {
        PlanningTask task = new PlanningTask();
        task.setDestination("西安");
        task.setDays(1);
        task.setPartySize(1);
        return task;
    }
}
