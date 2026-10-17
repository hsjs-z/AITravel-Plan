package com.travelplatform.aiplanning.application;

import com.travelplatform.aiplanning.domain.PlanningTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Selects the real DeepSeek client only when explicitly requested and a key is
 * configured; otherwise the deterministic mock is used.
 */
@Component
public class PlannerClientProvider implements PlannerClient {

    private static final Logger log = LoggerFactory.getLogger(PlannerClientProvider.class);

    private final DeepSeekPlannerClient deepSeek;
    private final MockPlannerClient mock;
    private final String planner;
    private final String apiKey;

    public PlannerClientProvider(DeepSeekPlannerClient deepSeek,
                                 MockPlannerClient mock,
                                 @Value("${planning.planner:mock}") String planner,
                                 @Value("${DEEPSEEK_API_KEY:}") String apiKey) {
        this.deepSeek = deepSeek;
        this.mock = mock;
        this.planner = planner;
        this.apiKey = apiKey;
    }

    @Override
    public PlanningOutput.Plan generate(PlanningTask task, PlanningRunContext context) {
        if ("deepseek".equalsIgnoreCase(planner.trim()) && apiKey != null && !apiKey.isBlank()) {
            return deepSeek.generate(task, context);
        }
        if ("deepseek".equalsIgnoreCase(planner.trim())) {
            log.warn("DEEPSEEK_API_KEY is blank; falling back to mock planner");
        }
        return mock.generate(task, context);
    }
}
