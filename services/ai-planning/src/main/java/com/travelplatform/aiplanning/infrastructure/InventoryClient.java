package com.travelplatform.aiplanning.infrastructure;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

@FeignClient(name = "inventory")
public interface InventoryClient {

    @GetMapping("/inventory/candidates")
    CandidateApiResponse candidates(
            @RequestParam("destination") String destination,
            @RequestParam("date") String date);

    record CandidateApiResponse(int code, String message, List<Candidate> data) {
    }

    record Candidate(String resourceId, String resourceName, String type, List<Spec> specs) {
    }

    record Spec(String specId, String specName, Long price, Integer available) {
    }
}
