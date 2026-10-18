package com.travelplatform.itinerary.infrastructure;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "ai-planning")
public interface AiPlanningClient {

    @PostMapping("/planning/tips")
    TipsApiResponse generateTips(@RequestBody TipsRequest request);

    record TipsRequest(String destination, int days, List<String> attractions) {
    }

    record TipsApiResponse(int code, String message, List<String> data) {
    }
}
