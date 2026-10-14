package com.travelplatform.bookingorder.infrastructure;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "itinerary")
public interface ItineraryClient {

    @PostMapping("/itineraries/{itineraryId}/lock")
    void lock(@PathVariable("itineraryId") String itineraryId, @RequestHeader("X-User-Id") String userId);

    @PostMapping("/itineraries/{itineraryId}/release")
    void release(@PathVariable("itineraryId") String itineraryId, @RequestHeader("X-User-Id") String userId);
}
