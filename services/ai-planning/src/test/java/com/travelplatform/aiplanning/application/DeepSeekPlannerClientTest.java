package com.travelplatform.aiplanning.application;

import com.travelplatform.aiplanning.domain.PlanningTask;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeepSeekPlannerClientTest {

    @Test
    void totalGenerateTimeoutProducesDistinctReason() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callResponse = mock(ChatClient.CallResponseSpec.class);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.tools(any())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponse);
        when(callResponse.content()).thenAnswer(invocation -> {
            Thread.sleep(1000);
            return "{}";
        });

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.initialize();

        DeepSeekPlannerClient client = new DeepSeekPlannerClient(
                builder, mock(AttractionSearchTool.class), new PlanningOutputParser(new com.fasterxml.jackson.databind.ObjectMapper()),
                executor, 100L);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> client.generate(task(), new PlanningRunContext()));

        assertEquals("planning task total timeout after 1000ms", ex.getMessage());
    }

    @Test
    void requestTimeoutProducesDistinctReason() {
        DeepSeekPlannerClient client = new DeepSeekPlannerClient(
                mock(ChatClient.Builder.class), mock(AttractionSearchTool.class),
                new PlanningOutputParser(new com.fasterxml.jackson.databind.ObjectMapper()),
                executor(), 120000L);

        IllegalStateException ex = client.translateFailure(new SocketTimeoutException("read timed out"));

        assertEquals("DeepSeek request timeout", ex.getMessage());
    }

    private PlanningTask task() {
        PlanningTask task = new PlanningTask();
        task.setDestination("西安");
        task.setDays(2);
        task.setPartySize(2);
        return task;
    }

    private ThreadPoolTaskExecutor executor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.initialize();
        return executor;
    }
}
