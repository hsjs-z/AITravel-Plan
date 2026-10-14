package com.travelplatform.bookingorder.infrastructure;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.time.LocalDate;

@FeignClient(name = "inventory")
public interface InventoryClient {

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
}
