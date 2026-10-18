package com.travelplatform.bookingorder.infrastructure;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

@FeignClient(name = "inventory")
public interface InventoryClient {

    @GetMapping("/resources/{resourceId}")
    ResourceApiResponse resource(@PathVariable("resourceId") String resourceId);

    @GetMapping("/inventory/query")
    AvailabilityApiResponse availability(
            @RequestParam("resourceId") String resourceId,
            @RequestParam("date") String date,
            @RequestParam("specId") String specId);

    @PostMapping("/inventory/locks")
    InventoryLockApiResponse lock(@RequestBody InventoryLockRequest request);

    @PostMapping("/inventory/locks/{lockId}/release")
    InventoryReleaseApiResponse release(@PathVariable("lockId") String lockId);

    record InventoryLockRequest(
            String resourceId,
            LocalDate date,
            String specId,
            Integer quantity,
            String purpose,
            Integer ttlSeconds,
            String holderType,
            String holderId) {
    }

    record InventoryLockApiResponse(int code, String message, InventoryLockData data) {
    }

    record InventoryLockData(String lockId) {
    }

    record InventoryReleaseApiResponse(int code, String message, InventoryReleaseData data) {
    }

    record InventoryReleaseData(String lockId, boolean released) {
    }

    record ResourceApiResponse(int code, String message, ResourceDetail data) {
    }

    record ResourceDetail(
            String id,
            String type,
            String status,
            List<SpecView> specs) {
    }

    record SpecView(String specId, String specType, Long price) {
    }

    record AvailabilityApiResponse(int code, String message, AvailabilityData data) {
    }

    record AvailabilityData(Integer available) {
    }
}
