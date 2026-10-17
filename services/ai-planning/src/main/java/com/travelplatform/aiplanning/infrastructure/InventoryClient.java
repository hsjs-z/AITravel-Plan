package com.travelplatform.aiplanning.infrastructure;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.List;

@FeignClient(name = "inventory")
public interface InventoryClient {

    @GetMapping("/inventory/attractions")
    AttractionApiResponse attractions(
            @RequestParam("destination") String destination,
            @RequestParam(value = "date", required = false) String date,
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            @RequestParam(value = "offset", defaultValue = "0") int offset);

    record AttractionApiResponse(int code, String message, AttractionPage data) {
    }

    record AttractionPage(int total, int offset, int limit, boolean hasMore, List<Attraction> items) {
    }

    record Attraction(
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
}
