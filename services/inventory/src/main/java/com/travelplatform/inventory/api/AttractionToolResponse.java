package com.travelplatform.inventory.api;

import java.math.BigDecimal;
import java.util.List;

/**
 * Read-only attraction data for the planner tool. This DTO deliberately does not
 * carry an order-entry flag: V2-06 only supplies data and does not open ticket
 * trading. Spec identifiers are for itinerary binding, not for booking.
 */
public final class AttractionToolResponse {

    private AttractionToolResponse() {
    }

    public record AttractionPage(
            int total,
            int offset,
            int limit,
            boolean hasMore,
            List<AttractionItem> items) {
    }

    public record AttractionItem(
            String resourceId,
            String name,
            String province,
            String city,
            String level,
            String honorTitle,
            String summary,
            BigDecimal suggestedHours,
            Long ticketPrice,
            boolean free,
            BigDecimal latitude,
            BigDecimal longitude,
            String coordinateStatus,
            String specId,
            String specName,
            Long price,
            Integer available,
            String imageUrl) {
    }
}
