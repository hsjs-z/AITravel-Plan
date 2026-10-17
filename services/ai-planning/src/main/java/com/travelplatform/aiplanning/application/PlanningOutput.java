package com.travelplatform.aiplanning.application;

import java.math.BigDecimal;
import java.util.List;

/**
 * Raw model output contract for v2 planning. Time slots and pace are not part of
 * this structure; they are assigned by code in V2-08.
 */
public final class PlanningOutput {

    private PlanningOutput() {
    }

    public record Plan(
            String destination,
            int totalDays,
            List<Day> days,
            List<String> tips) {
    }

    public record Day(int day, String theme, List<Attraction> attractions) {
    }

    public record Attraction(
            String name,
            BigDecimal suggestedHours,
            String description,
            Long ticketPrice,
            String resourceId,
            String specId) {
    }
}
