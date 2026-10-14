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
    public String generate(PlanningTask task, String candidateContext) {
        return """
                {
                  "destination": "%s",
                  "totalDays": %d,
                  "partySize": %d,
                  "estimatedBudget": %d,
                  "days": [
                    {"day": 1, "arrangements": ["抵达", "入住酒店"]},
                    {"day": 2, "arrangements": ["游览玉龙雪山", "品尝美食"]}
                  ],
                  "hotels": [{"name": "丽江古城精品酒店", "resourceId": "r-hotel-1", "specId": "s-room-1", "dayIndex": 1}],
                  "attractions": [{"name": "玉龙雪山", "resourceId": "r-attraction-1", "specId": "s-ticket-1", "dayIndex": 2}],
                  "foods": [{"name": "过桥米线"}]
                }
                """.formatted(task.getDestination(), task.getDays(), task.getPartySize(), task.getBudget());
    }
}
