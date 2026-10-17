package com.travelplatform.aiplanning.application;

import com.travelplatform.aiplanning.domain.PlanningTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Real DeepSeek planner backed by Spring AI {@link ChatClient} and real tool
 * calling. The API key comes from {@code DEEPSEEK_API_KEY}; no RestTemplate.
 */
@Component
public class DeepSeekPlannerClient {

    private static final String SYSTEM_PROMPT = """
            你是一个专业的中文旅行规划助手。规划前必须先调用 searchAttractions(destination)
            获取目的地真实景区数据；可以多次调用，结果以工具返回为准。
            只输出一个合法 JSON 对象，不要 Markdown，不要解释。
            结构必须为：
            {
              "destination": "目的地",
              "totalDays": 天数,
              "days": [
                {
                  "day": 1,
                  "theme": "当日主题",
                  "attractions": [
                    {
                      "name": "景区名称",
                      "suggestedHours": 4,
                      "description": "一句话简介",
                      "ticketPrice": 120,
                      "resourceId": "r-xxx",
                      "specId": "s-xxx"
                    }
                  ]
                }
              ],
              "tips": ["建议1", "建议2", "建议3"]
            }
            days 从 1 到 totalDays 有序。resourceId/specId 只能逐字符复制工具返回的值；
            免费景区 specId 为 null；未匹配资源两个字段都为 null；禁止编造 ID。
            如果工具没有合适景区，attractions 可为空数组。tips 为 3 到 8 条。
            """;

    private static final Logger log = LoggerFactory.getLogger(DeepSeekPlannerClient.class);

    private final ChatClient chatClient;
    private final AttractionSearchTool tool;
    private final PlanningOutputParser parser;
    private final ThreadPoolTaskExecutor callExecutor;
    private final Duration generateTimeout;

    public DeepSeekPlannerClient(ChatClient.Builder chatClientBuilder,
                                 AttractionSearchTool tool,
                                 PlanningOutputParser parser,
                                 @Qualifier("plannerCallExecutor") ThreadPoolTaskExecutor callExecutor,
                                 @Value("${planning.generate-timeout-ms:120000}") long generateTimeoutMs) {
        this.chatClient = chatClientBuilder.build();
        this.tool = tool;
        this.parser = parser;
        this.callExecutor = callExecutor;
        this.generateTimeout = Duration.ofMillis(Math.max(1000, generateTimeoutMs));
    }

    public PlanningOutput.Plan generate(PlanningTask task, PlanningRunContext context) {
        String userPrompt = buildPrompt(task);
        CompletableFuture<PlanningOutput.Plan> future = CompletableFuture.supplyAsync(() -> {
            PlanningRunContextHolder.set(context);
            try {
                String content = chatClient.prompt()
                        .system(SYSTEM_PROMPT)
                        .user(userPrompt)
                        .tools(tool)
                        .call()
                        .content();
                return parser.parse(content);
            } finally {
                PlanningRunContextHolder.clear();
            }
        }, callExecutor);

        try {
            return future.get(generateTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new IllegalStateException(
                    "planning task total timeout after " + generateTimeout.toMillis() + "ms");
        } catch (ExecutionException e) {
            throw translateFailure(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("planning interrupted");
        }
    }

    private String buildPrompt(PlanningTask task) {
        StringBuilder builder = new StringBuilder()
                .append("目的地=").append(task.getDestination())
                .append("，天数=").append(task.getDays())
                .append("，人数=").append(task.getPartySize());
        if (task.getBudget() != null) {
            builder.append("，预算=").append(task.getBudget()).append("元");
        }
        builder.append("，偏好=")
                .append(task.getPreferences() == null ? "无" : task.getPreferences());
        return builder.toString();
    }

    IllegalStateException translateFailure(Throwable cause) {
        if (cause instanceof IllegalStateException illegalState) {
            return illegalState;
        }
        if (containsSocketTimeout(cause)) {
            return new IllegalStateException("DeepSeek request timeout");
        }
        return new IllegalStateException(
                "DeepSeek request failed: " + (cause == null ? "unknown" : cause.getMessage()));
    }

    private boolean containsSocketTimeout(Throwable cause) {
        Throwable current = cause;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
