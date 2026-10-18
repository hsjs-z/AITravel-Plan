package com.travelplatform.aiplanning.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Normalized planning result returned to the API and persisted by itinerary.
 */
public final class PackedOutput {

    private PackedOutput() {
    }

    public record Plan(
            String destination,
            int totalDays,
            String pace,
            List<Day> days,
            List<String> warnings,
            List<String> tips) {
    }

    public record Day(int day, String theme, Map<String, List<Item>> slots) {
    }

    public record Item(
            String name,
            String description,
            BigDecimal suggestedHours,
            Long ticketPrice,
            String resourceId,
            String specId,
            boolean matched,
            boolean bookable,
            String priceLabel,
            BigDecimal latitude,
            BigDecimal longitude,
            String coordinateStatus,
            String timeSlot,
            int sortOrder) {
    }
}
