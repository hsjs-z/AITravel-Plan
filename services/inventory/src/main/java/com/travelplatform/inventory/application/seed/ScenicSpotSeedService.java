package com.travelplatform.inventory.application.seed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplatform.common.api.BusinessException;
import com.travelplatform.common.api.ErrorCode;
import com.travelplatform.inventory.domain.CoordinateStatus;
import com.travelplatform.inventory.domain.ResourceRepository;
import com.travelplatform.inventory.domain.ResourceType;
import com.travelplatform.inventory.domain.ScenicSeedRun;
import com.travelplatform.inventory.domain.ScenicSeedRunRepository;
import com.travelplatform.inventory.domain.ScenicSeedRunStatus;
import com.travelplatform.inventory.infrastructure.AmapGeocodingClient;
import com.travelplatform.inventory.infrastructure.DeepSeekScenicSpotClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ScenicSpotSeedService {

    private static final Logger log = LoggerFactory.getLogger(ScenicSpotSeedService.class);

    private final ScenicSeedRunRepository runRepository;
    private final DeepSeekScenicSpotClient llm;
    private final ScenicSpotValidator validator;
    private final AmapGeocodingClient amap;
    private final ScenicSpotStore store;
    private final ResourceRepository resourceRepository;
    private final ObjectMapper objectMapper;
    private final TaskExecutor executor;

    public ScenicSpotSeedService(ScenicSeedRunRepository runRepository,
                                 DeepSeekScenicSpotClient llm,
                                 ScenicSpotValidator validator,
                                 AmapGeocodingClient amap,
                                 ScenicSpotStore store,
                                 ResourceRepository resourceRepository,
                                 ObjectMapper objectMapper,
                                 @Qualifier("applicationTaskExecutor") TaskExecutor executor) {
        this.runRepository = runRepository;
        this.llm = llm;
        this.validator = validator;
        this.amap = amap;
        this.store = store;
        this.resourceRepository = resourceRepository;
        this.objectMapper = objectMapper;
        this.executor = executor;
    }

    public synchronized String start(List<String> requestedProvinces, String requestedBy) {
        List<String> provinces = ProvinceCatalog.resolve(requestedProvinces);
        if (!llm.isConfigured()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "DEEPSEEK_API_KEY is not configured");
        }
        if (!amap.isConfigured()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "AMAP_WEB_SERVICE_KEY is not configured");
        }
        if (runRepository.findFirstByStatusOrderByStartedAtDesc(ScenicSeedRunStatus.RUNNING) != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "a scenic seed run is already running");
        }

        ScenicSeedRun run = new ScenicSeedRun();
        run.setId(UUID.randomUUID().toString());
        run.setStatus(ScenicSeedRunStatus.RUNNING);
        run.setRequestedBy(requestedBy);
        run.setStartedAt(Instant.now());
        runRepository.save(run);
        executor.execute(() -> execute(run.getId(), provinces));
        return run.getId();
    }

    private void execute(String runId, List<String> provinces) {
        ScenicSeedRun run = runRepository.findById(runId).orElse(null);
        if (run == null) {
            return;
        }
        try {
            List<List<String>> batches = ProvinceCatalog.batches(provinces);
            Map<String, ProvinceStats> stats = new LinkedHashMap<>();
            provinces.forEach(province -> stats.put(province, new ProvinceStats(province)));
            List<SeedDtos.SeedBatchReport> batchReports = new ArrayList<>();

            for (int index = 0; index < batches.size(); index++) {
                List<String> batch = batches.get(index);
                AttemptResult attempt = generateWithRetry(batch);
                if (attempt.mappingFailed()) {
                    batchReports.add(new SeedDtos.SeedBatchReport(
                            index + 1, batch, attempt.attempts(), true,
                            0, 0, 0, 0, 0, 0, 0, attempt.errors()));
                    continue;
                }

                SeedDtos.SeedValidationResult validation =
                        validator.validate(batch, attempt.response());
                int generated = 0;
                int inserted = 0;
                int skippedExisting = 0;
                int skippedInvalid = validation.skippedInvalid();
                int failed = 0;
                int coordinateReady = 0;
                int coordinatePending = 0;
                List<String> errors = new ArrayList<>(attempt.errors());
                errors.addAll(validation.errors());

                for (String province : batch) {
                    ProvinceStats provinceStats = stats.get(province);
                    List<SeedDtos.ValidatedAttraction> valid =
                            validation.byProvince().getOrDefault(province, List.of());
                    generated += valid.size();
                    provinceStats.generated += valid.size();
                    int invalidForProvince =
                            validation.skippedInvalidByProvince().getOrDefault(province, 0);
                    provinceStats.skippedInvalid += invalidForProvince;

                    for (SeedDtos.ValidatedAttraction attraction : valid) {
                        if (store.exists(attraction.name(), attraction.city())) {
                            skippedExisting++;
                            provinceStats.skippedExisting++;
                            continue;
                        }

                        Optional<SeedDtos.SeedCoordinate> coordinate;
                        try {
                            coordinate = amap.geocode(attraction.name(), attraction.city());
                        } catch (Exception e) {
                            coordinate = Optional.empty();
                            errors.add(province + "/" + attraction.name()
                                    + ": geocoding failed: " + e.getMessage());
                        }

                        SeedDtos.SeedRecordOutcome outcome;
                        try {
                            outcome = store.upsert(attraction, coordinate.orElse(null));
                        } catch (Exception e) {
                            outcome = SeedDtos.SeedRecordOutcome.failed(e.getMessage());
                        }

                        if (outcome.status() == SeedDtos.SeedRecordOutcome.Status.INSERTED) {
                            inserted++;
                            provinceStats.inserted++;
                            if (coordinate.isPresent()) {
                                coordinateReady++;
                                provinceStats.coordinateReady++;
                            } else {
                                coordinatePending++;
                                provinceStats.coordinatePending++;
                            }
                        } else if (outcome.status() == SeedDtos.SeedRecordOutcome.Status.SKIPPED_EXISTING) {
                            skippedExisting++;
                            provinceStats.skippedExisting++;
                        } else {
                            failed++;
                            provinceStats.failed++;
                            errors.add(province + "/" + attraction.name() + ": " + outcome.error());
                        }
                    }
                }

                batchReports.add(new SeedDtos.SeedBatchReport(
                        index + 1, batch, attempt.attempts(), false,
                        generated, inserted, skippedExisting, skippedInvalid, failed,
                        coordinateReady, coordinatePending, errors));
            }

            int totalGenerated = stats.values().stream().mapToInt(item -> item.generated).sum();
            int totalInserted = stats.values().stream().mapToInt(item -> item.inserted).sum();
            int totalSkippedExisting = stats.values().stream().mapToInt(item -> item.skippedExisting).sum();
            int totalSkippedInvalid = stats.values().stream().mapToInt(item -> item.skippedInvalid).sum();
            int totalFailed = stats.values().stream().mapToInt(item -> item.failed).sum();
            int totalCoordinateReady = stats.values().stream().mapToInt(item -> item.coordinateReady).sum();
            int totalCoordinatePending = stats.values().stream().mapToInt(item -> item.coordinatePending).sum();
            long historicalPending = resourceRepository.countByTypeAndCoordinateStatus(
                    ResourceType.ATTRACTION, CoordinateStatus.PENDING);

            boolean allBatchesFailed = batchReports.stream().allMatch(SeedDtos.SeedBatchReport::mappingFailed);
            boolean anyBatchFailed = batchReports.stream().anyMatch(SeedDtos.SeedBatchReport::mappingFailed);
            ScenicSeedRunStatus finalStatus = allBatchesFailed
                    ? ScenicSeedRunStatus.FAILED
                    : (anyBatchFailed || totalFailed > 0 || totalSkippedInvalid > 0
                    ? ScenicSeedRunStatus.PARTIAL_FAILED
                    : ScenicSeedRunStatus.SUCCEEDED);

            SeedDtos.SeedSummary summary = new SeedDtos.SeedSummary(
                    provinces.size(), batches.size(), totalGenerated, totalInserted,
                    totalSkippedExisting, totalSkippedInvalid, totalFailed,
                    totalCoordinateReady, totalCoordinatePending, historicalPending);
            List<SeedDtos.ProvinceSeedReport> provinceReports = stats.values().stream()
                    .map(ProvinceStats::toReport)
                    .toList();
            SeedDtos.SeedRunReport report = new SeedDtos.SeedRunReport(
                    runId, finalStatus.name(), run.getRequestedBy(), run.getStartedAt(),
                    Instant.now(), null, summary, batchReports, provinceReports);

            run.setStatus(finalStatus);
            run.setFinishedAt(report.finishedAt());
            run.setReportJson(objectMapper.writeValueAsString(report));
            runRepository.save(run);
        } catch (Exception e) {
            log.error("scenic seed run {} failed", runId, e);
            run.setStatus(ScenicSeedRunStatus.FAILED);
            run.setFinishedAt(Instant.now());
            run.setFailureReason(truncate(e.getMessage(), 512));
            try {
                run.setReportJson(objectMapper.writeValueAsString(
                        Map.of("runId", runId, "status", "FAILED",
                                "failureReason", String.valueOf(e.getMessage()))));
            } catch (Exception serializationError) {
                log.warn("failed to serialize failed seed report: {}", serializationError.toString());
            }
            runRepository.save(run);
        }
    }

    private AttemptResult generateWithRetry(List<String> batch) {
        int attempt = 0;
        List<String> errors = new ArrayList<>();
        for (int i = 1; i <= 2; i++) {
            attempt = i;
            try {
                SeedDtos.SeedBatchResponse response = llm.generate(batch);
                if (response == null || response.provinces() == null || response.provinces().isEmpty()) {
                    throw new IllegalStateException("empty AI response");
                }
                return new AttemptResult(response, attempt, false, errors);
            } catch (Exception e) {
                errors.add("attempt " + i + ": " + e.getMessage());
            }
        }
        return new AttemptResult(null, attempt, true, errors);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private record AttemptResult(
            SeedDtos.SeedBatchResponse response,
            int attempts,
            boolean mappingFailed,
            List<String> errors) {
    }

    private static final class ProvinceStats {
        private final String province;
        private int generated;
        private int inserted;
        private int skippedExisting;
        private int skippedInvalid;
        private int failed;
        private int coordinateReady;
        private int coordinatePending;

        private ProvinceStats(String province) {
            this.province = province;
        }

        private SeedDtos.ProvinceSeedReport toReport() {
            return new SeedDtos.ProvinceSeedReport(
                    province, generated, inserted, skippedExisting,
                    skippedInvalid, failed, coordinateReady, coordinatePending);
        }
    }
}
