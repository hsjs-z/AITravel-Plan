package com.travelplatform.aiplanning.application;

import com.travelplatform.aiplanning.domain.PlanningTask;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Real DeepSeek planner using the OpenAI-compatible chat completions endpoint.
 * Active only when {@code planning.planner=deepseek}; the API key comes from the
 * {@code DEEPSEEK_API_KEY} environment variable, never from code or config.
 */
@Component
@ConditionalOnProperty(name = "planning.planner", havingValue = "deepseek")
public class DeepSeekPlannerClient implements PlannerClient {

    private static final String ENDPOINT = "https://api.deepseek.com/chat/completions";

    private static final String SYSTEM_PROMPT = """
            你是一个专业的中文旅行规划助手。根据用户需求生成一份多日行程方案。
            只输出一个合法 JSON 对象，不要输出任何解释或 Markdown 代码块。
            JSON 结构必须为：
            {
              "destination": "目的地",
              "totalDays": 天数,
              "partySize": 人数,
              "estimatedBudget": 预算金额,
              "days": [{"day": 1, "arrangements": ["...", "..."]}],
              "hotels": [{"name": "酒店名"}],
              "attractions": [{"name": "景点名"}],
              "foods": [{"name": "美食名"}]
            }
            """;

    private final RestTemplate restTemplate;
    private final String apiKey;

    public DeepSeekPlannerClient(RestTemplateBuilder restTemplateBuilder,
                                 @Value("${DEEPSEEK_API_KEY:}") String apiKey) {
        this.restTemplate = restTemplateBuilder.build();
        this.apiKey = apiKey;
    }

    @Override
    public String generate(PlanningTask task) {
        String userPrompt = "目的地=%s，天数=%d，人数=%d，预算=%d元，偏好=%s"
                .formatted(
                        task.getDestination(),
                        task.getDays(),
                        task.getPartySize(),
                        task.getBudget(),
                        task.getPreferences() == null ? "无" : task.getPreferences());

        Map<String, Object> request = Map.of(
                "model", "deepseek-chat",
                "temperature", 0.7,
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", userPrompt)));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        ResponseEntity<Map> response = restTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                Map.class);

        Map<String, Object> body = response.getBody();
        List<Map<String, Object>> choices = (List<Map<String, Object>>) body.get("choices");
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        return (String) message.get("content");
    }
}
