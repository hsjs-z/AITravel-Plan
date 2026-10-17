package com.travelplatform.aiplanning.application;

import com.travelplatform.aiplanning.domain.PlanningTask;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic development planner. It derives candidates from the real
 * attraction tool for the requested destination; nothing is hardcoded.
 */
@Component
public class MockPlannerClient {

    private final AttractionSearchTool tool;

    public MockPlannerClient(AttractionSearchTool tool) {
        this.tool = tool;
    }

    public PlanningOutput.Plan generate(PlanningTask task, PlanningRunContext context) {
        PlanningRunContextHolder.set(context);
        try {
            List<AttractionRef> refs = tool.searchAttractions(task.getDestination());
            List<PlanningOutput.Day> days = new ArrayList<>();
            int cursor = 0;
            for (int day = 1; day <= task.getDays(); day++) {
                List<PlanningOutput.Attraction> attractions = new ArrayList<>();
                for (int slot = 0; slot < 2 && cursor < refs.size(); slot++, cursor++) {
                    AttractionRef ref = refs.get(cursor);
                    attractions.add(new PlanningOutput.Attraction(
                            ref.name(),
                            ref.suggestedHours(),
                            ref.summary(),
                            ref.ticketPrice(),
                            ref.resourceId(),
                            ref.specId()));
                }
                days.add(new PlanningOutput.Day(day, "第" + day + "天", attractions));
            }
            return new PlanningOutput.Plan(
                    task.getDestination(),
                    task.getDays(),
                    days,
                    List.of("mock 模式：基于目的地真实景区生成"));
        } finally {
            PlanningRunContextHolder.clear();
        }
    }
}
