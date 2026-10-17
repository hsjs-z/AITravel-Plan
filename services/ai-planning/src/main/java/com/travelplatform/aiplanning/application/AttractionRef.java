package com.travelplatform.aiplanning.application;

import java.math.BigDecimal;

/**
 * One attraction returned by {@code searchAttractions}. It mirrors the inventory
 * tool endpoint so the model can copy real {@code resourceId/specId} values.
 */
public record AttractionRef(
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
        Integer available) {
}
