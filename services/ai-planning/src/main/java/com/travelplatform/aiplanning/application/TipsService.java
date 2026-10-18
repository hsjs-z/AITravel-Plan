package com.travelplatform.aiplanning.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Generates short itinerary tips from the final confirmed items. The mock is
 * deterministic and millisecond-level; DeepSeek makes at most one call.
 */
@Service
public class TipsService {

    private static final String SYSTEM_PROMPT =
            "你是中文旅行建议助手。只输出 JSON 字符串数组，3-5 条，每条不超过30字，不要 Markdown 或解释。";
    private static final Logger log = LoggerFactory.getLogger(TipsService.class);

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final ThreadPoolTaskExecutor callExecutor;
    private final String planner;
    private final String apiKey;
    private final long timeoutMs;

    public TipsService(ChatClient.Builder chatClientBuilder,
                       ObjectMapper objectMapper,
                       @Qualifier("plannerCallExecutor") ThreadPoolTaskExecutor callExecutor,
                       @Value("${planning.planner:mock}") String planner,
                       @Value("${DEEPSEEK_API_KEY:}") String apiKey,
                       @Value("${planning.deepseek-timeout-ms:30000}") long timeoutMs) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
        this.callExecutor = callExecutor;
        this.planner = planner;
        this.apiKey = apiKey;
        this.timeoutMs = Math.max(1000, timeoutMs);
    }

    public List<String> generate(String destination, int days, List<String> attractions) {
        String place = destination == null || destination.isBlank() ? "目的地" : destination;
        List<String> names = attractions == null ? List.of() : attractions.stream()
                .filter(name -> name != null && !name.isBlank())
                .toList();
        if ("deepseek".equalsIgnoreCase(planner.trim()) && apiKey != null && !apiKey.isBlank()) {
            return deepSeek(place, days, names);
        }
        return mock(place, days, names);
    }

    private List<String> deepSeek(String destination, int days, List<String> attractions) {
        String user = "目的地=" + destination + "；天数=" + days + "；景点="
                + (attractions.isEmpty() ? "无" : String.join("、", attractions));
        try {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(
                    () -> chatClient.prompt()
                            .system(SYSTEM_PROMPT)
                            .user(user)
                            .options(ChatOptions.builder()
                                    .temperature(0.4)
                                    .maxTokens(400)
                                    .build())
                            .call()
                            .content(),
                    callExecutor);
            String content = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            List<String> tips = objectMapper.readValue(stripFences(content), new TypeReference<>() {
            });
            return normalize(tips);
        } catch (Exception e) {
            log.warn("tips generation failed for {}: {}", destination, e.toString());
            return List.of();
        }
    }

    private List<String> mock(String destination, int days, List<String> attractions) {
        List<String> tips = new ArrayList<>();
        tips.add(destination + "行程建议按区域集中安排");
        tips.add(attractions.isEmpty()
                ? "先补充必去景点，再安排每日路线"
                : "优先游览" + attractions.get(0) + "，提前预约");
        tips.add("每天预留1小时机动时间，避免过赶");
        if (days >= 2) {
            tips.add("第二天节奏放缓，留出休息时间");
        }
        return normalize(tips);
    }

    private List<String> normalize(List<String> tips) {
        if (tips == null) {
            return List.of();
        }
        List<String> cleaned = tips.stream()
                .filter(tip -> tip != null && !tip.isBlank())
                .map(String::trim)
                .map(tip -> tip.length() <= 30 ? tip : tip.substring(0, 30))
                .distinct()
                .limit(5)
                .toList();
        return cleaned.size() >= 3 ? cleaned : List.of();
    }

    private String stripFences(String content) {
        String value = content == null ? "" : content.trim();
        if (value.startsWith("```")) {
            int firstLine = value.indexOf('\n');
            int lastFence = value.lastIndexOf("```");
            if (firstLine >= 0 && lastFence > firstLine) {
                return value.substring(firstLine + 1, lastFence).trim();
            }
        }
        return value;
    }
}
