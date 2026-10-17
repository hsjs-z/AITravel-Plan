package com.travelplatform.inventory.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.travelplatform.common.api.ApiResponse;
import com.travelplatform.common.api.BusinessException;
import com.travelplatform.common.api.ErrorCode;
import com.travelplatform.inventory.application.seed.ScenicSpotSeedService;
import com.travelplatform.inventory.domain.ScenicSeedRun;
import com.travelplatform.inventory.domain.ScenicSeedRunRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
public class AdminSeedController {

    private final ScenicSpotSeedService seedService;
    private final ScenicSeedRunRepository runRepository;
    private final ObjectMapper objectMapper;

    public AdminSeedController(ScenicSpotSeedService seedService,
                               ScenicSeedRunRepository runRepository,
                               ObjectMapper objectMapper) {
        this.seedService = seedService;
        this.runRepository = runRepository;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/admin/seed/scenic-spots")
    public ResponseEntity<ApiResponse<SeedStartResponse>> start(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestBody(required = false) SeedRequest request) {
        requireAdmin(role);
        List<String> provinces = request == null || request.provinces() == null
                ? List.of()
                : request.provinces();
        String runId = seedService.start(provinces, userId == null || userId.isBlank() ? "admin" : userId);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(new SeedStartResponse(runId, "RUNNING")));
    }

    @GetMapping("/admin/seed/scenic-spots/{runId}")
    public ApiResponse<SeedRunView> get(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable("runId") String runId) {
        requireAdmin(role);
        ScenicSeedRun run = runRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "seed run not found"));
        return ApiResponse.success(new SeedRunView(
                run.getId(),
                run.getStatus().name(),
                run.getRequestedBy(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getFailureReason(),
                parseReport(run.getReportJson())));
    }

    private JsonNode parseReport(String reportJson) {
        if (reportJson == null || reportJson.isBlank()) {
            return NullNode.getInstance();
        }
        try {
            return objectMapper.readTree(reportJson);
        } catch (Exception e) {
            return NullNode.getInstance();
        }
    }

    private void requireAdmin(String role) {
        if (!"ADMIN".equals(role)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "not found");
        }
    }

    public record SeedRequest(List<String> provinces) {
    }

    public record SeedStartResponse(String runId, String status) {
    }

    public record SeedRunView(
            String runId,
            String status,
            String requestedBy,
            Instant startedAt,
            Instant finishedAt,
            String failureReason,
            JsonNode report) {
    }
}
