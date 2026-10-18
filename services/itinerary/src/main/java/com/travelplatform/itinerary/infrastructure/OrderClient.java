package com.travelplatform.itinerary.infrastructure;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "booking-order")
public interface OrderClient {

    @GetMapping("/orders/summaries")
    OrderSummaryApiResponse summaries(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam("itineraryIds") String itineraryIds);

    record OrderSummaryApiResponse(int code, String message, List<OrderSummaryData> data) {
    }

    record OrderSummaryData(
            String itineraryId,
            long activeOrderCount,
            long completedOrderCount) {
    }
}
