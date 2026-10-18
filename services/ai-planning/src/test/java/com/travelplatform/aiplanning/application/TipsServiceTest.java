package com.travelplatform.aiplanning.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class TipsServiceTest {

    @Test
    void mockTipsUseFinalItineraryItems() {
        TipsService service = new TipsService(
                mock(ChatClient.Builder.class),
                new ObjectMapper(),
                mock(ThreadPoolTaskExecutor.class),
                "mock",
                "",
                30000);

        List<String> tips = service.generate("西安", 2, List.of("大雁塔", "西安城墙"));

        assertTrue(tips.size() >= 3 && tips.size() <= 5);
        assertTrue(tips.get(0).contains("西安"));
        assertTrue(tips.stream().anyMatch(tip -> tip.contains("大雁塔")));
        assertTrue(tips.stream().allMatch(tip -> tip.length() <= 30));
    }
}
