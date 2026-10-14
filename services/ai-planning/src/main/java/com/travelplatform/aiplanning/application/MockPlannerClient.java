package com.travelplatform.aiplanning.application;

import com.travelplatform.aiplanning.domain.PlanningTask;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deterministic development planner. Produces a sample itinerary JSON so the
 * async lifecycle is testable without a DeepSeek API key.
 */
@Component
@ConditionalOnProperty(name = "planning.planner", havingValue = "mock", matchIfMissing = true)
public class MockPlannerClient implements PlannerClient {

    @Override
    public String generate(PlanningTask task) {
        return """
                {
                  "destination": "%s",
                  "totalDays": %d,
                  "partySize": %d,
                  "estimatedBudget": %d,
                  "days": [
                    {"day": 1, "arrangements": ["抵达", "入住酒店"]},
                    {"day": 2, "arrangements": ["游览景点", "品尝美食"]}
                  ],
                  "hotels": [{"name": "示例酒店", "resourceId": null, "specId": null}],
                  "attractions": [{"name": "示例景点", "resourceId": null, "specId": null}],
                  "foods": [{"name": "示例美食", "resourceId": null}]
                }
                """.formatted(task.getDestination(), task.getDays(), task.getPartySize(), task.getBudget());
    }
}
