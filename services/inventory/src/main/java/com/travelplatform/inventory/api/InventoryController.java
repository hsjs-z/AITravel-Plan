package com.travelplatform.inventory.api;

import com.travelplatform.common.api.ApiResponse;
import com.travelplatform.inventory.application.InventoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/inventory/query")
    public ApiResponse<QueryResponse> query(
            @RequestParam("resourceId") String resourceId,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam("specId") String specId) {
        Integer available = inventoryService.query(resourceId, date, specId);
        return ApiResponse.success(new QueryResponse(resourceId, date, specId, available));
    }

    @GetMapping("/inventory/candidates")
    public ApiResponse<List<Candidate>> candidates(
            @RequestParam("destination") String destination,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(inventoryService.candidates(destination, date));
    }

    @PostMapping("/inventory/locks")
    public ApiResponse<LockResponse> lock(@Valid @RequestBody LockRequest request) {
        return ApiResponse.success(inventoryService.lock(request));
    }

    @PostMapping("/inventory/locks/{lockId}/release")
    public ApiResponse<ReleaseResponse> release(@PathVariable("lockId") String lockId) {
        boolean released = inventoryService.release(lockId);
        return ApiResponse.success(new ReleaseResponse(lockId, released));
    }

    public record LockRequest(
            @NotBlank String resourceId,
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @NotBlank String specId,
            @NotNull @Min(1) Integer quantity,
            @NotBlank String purpose,
            Integer ttlSeconds,
            String holderType,
            String holderId) {
    }

    public record LockResponse(String lockId) {
    }

    public record ReleaseResponse(String lockId, boolean released) {
    }

    public record QueryResponse(String resourceId, LocalDate date, String specId, Integer available) {
    }

    public record Candidate(String resourceId, String resourceName, String type, List<Spec> specs) {
    }

    public record Spec(String specId, String specName, Long price, Integer available) {
    }
}
