package com.travelplatform.aiplanning.infrastructure;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.time.LocalDate;
import java.util.List;

@FeignClient(name = "itinerary")
public interface ItineraryClient {

    @PostMapping("/itineraries")
    ItineraryApiResponse create(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody ItineraryCreateRequest request);

    record ItineraryCreateRequest(
            String destination,
            LocalDate startDate,
            LocalDate endDate,
            Integer partySize,
            Long budget,
            List<Item> items,
            List<String> warnings) {
    }

    record Item(
            LocalDate dayDate,
            String name,
            String description,
            java.math.BigDecimal suggestedHours,
            Long ticketPrice,
            java.math.BigDecimal latitude,
            java.math.BigDecimal longitude,
            String resourceType,
            String resourceId,
            String specId,
            String source,
            String timeSlot,
            Integer sortOrder) {
    }

    record ItineraryApiResponse(int code, String message, ItineraryData data) {
    }

    record ItineraryData(String id) {
    }
}
