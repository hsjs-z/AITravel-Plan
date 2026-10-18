package com.travelplatform.itinerary.infrastructure;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;
import java.util.List;

@FeignClient(name = "inventory")
public interface InventoryClient {

    @GetMapping("/resources/{resourceId}")
    ResourceApiResponse detail(@PathVariable("resourceId") String resourceId);

    record ResourceApiResponse(int code, String message, ResourceDetail data) {
    }

    record ResourceDetail(
            String id,
            String type,
            String name,
            String status,
            Long ticketPrice,
            BigDecimal suggestedHours,
            String summary,
            List<SpecView> specs) {
    }

    record SpecView(
            String specId,
            String specName,
            String specType,
            Long price) {
    }
}
