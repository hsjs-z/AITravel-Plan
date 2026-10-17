package com.travelplatform.inventory.application.seed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class SeedDtos {

    private SeedDtos() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SeedBatchResponse(List<ProvinceSeed> provinces) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProvinceSeed(String province, List<SeedAttraction> attractions) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SeedAttraction(
            String name,
            String city,
            String level,
            String honorTitle,
            String summary,
            BigDecimal suggestedHours,
            Long ticketPrice) {
    }

    public record ValidatedAttraction(
            String province,
            String name,
            String city,
            String level,
            String honorTitle,
            String summary,
            BigDecimal suggestedHours,
            Long ticketPrice) {
    }

    public record SeedValidationResult(
            Map<String, List<ValidatedAttraction>> byProvince,
            Map<String, Integer> skippedInvalidByProvince,
            int skippedInvalid,
            List<String> errors) {
    }

    public record SeedCoordinate(BigDecimal latitude, BigDecimal longitude) {
    }

    public record SeedRecordOutcome(Status status, String error) {

        public enum Status {
            INSERTED,
            SKIPPED_EXISTING,
            FAILED
        }

        public static SeedRecordOutcome inserted() {
            return new SeedRecordOutcome(Status.INSERTED, null);
        }

        public static SeedRecordOutcome skipped() {
            return new SeedRecordOutcome(Status.SKIPPED_EXISTING, null);
        }

        public static SeedRecordOutcome failed(String error) {
            return new SeedRecordOutcome(Status.FAILED, error);
        }
    }

    public record SeedSummary(
            int provinces,
            int batches,
            int generated,
            int inserted,
            int skippedExisting,
            int skippedInvalid,
            int failed,
            int coordinateReady,
            int coordinatePending,
            long historicalPendingCoordinates) {
    }

    public record ProvinceSeedReport(
            String province,
            int generated,
            int inserted,
            int skippedExisting,
            int skippedInvalid,
            int failed,
            int coordinateReady,
            int coordinatePending) {
    }

    public record SeedBatchReport(
            int index,
            List<String> provinces,
            int attempt,
            boolean mappingFailed,
            int generated,
            int inserted,
            int skippedExisting,
            int skippedInvalid,
            int failed,
            int coordinateReady,
            int coordinatePending,
            List<String> errors) {
    }

    public record SeedRunReport(
            String runId,
            String status,
            String requestedBy,
            Instant startedAt,
            Instant finishedAt,
            String failureReason,
            SeedSummary summary,
            List<SeedBatchReport> batches,
            List<ProvinceSeedReport> provinces) {
    }
}
