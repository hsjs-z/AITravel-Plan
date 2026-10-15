package com.travelplatform.inventory.api;

import java.time.LocalDate;
import java.util.List;

/**
 * Resource browsing DTOs (T055).
 *
 * <p>{@code tradable} separates tradable resources (hotel / ticket / train ticket)
 * from content resources (attraction / route / food / culture experience), and
 * {@code orderEntryAllowed} is the single flag the UI must honour before showing
 * any booking entry point: it is false for content resources, for off-shelf
 * resources and for resources without sellable inventory on the requested date
 * (FR-038, FR-039).
 */
public final class ResourceResponse {

    private ResourceResponse() {
    }

    /** Resource detail, see {@code GET /resources/{id}}. */
    public record Detail(
            String id,
            String type,
            String name,
            String destination,
            String status,
            boolean tradable,
            boolean soldOut,
            boolean orderEntryAllowed,
            LocalDate date,
            boolean availabilityKnown,
            Long priceFrom,
            Long priceTo,
            Integer availableTotal,
            List<SpecView> specs) {
    }

    /**
     * A spec of a resource. {@code available} and {@code bookable} are date-scoped:
     * without a {@code date} request parameter they are {@code null} / {@code false}
     * because the inventory unit is addressed by resource + date + spec.
     */
    public record SpecView(
            String specId,
            String specName,
            String specType,
            Integer occupancy,
            Long price,
            Integer available,
            boolean bookable) {
    }

    /** Resource card returned by search. */
    public record Summary(
            String id,
            String type,
            String name,
            String destination,
            String status,
            boolean tradable,
            boolean orderEntryAllowed,
            Long priceFrom,
            Long priceTo,
            Integer availableTotal) {
    }

    /**
     * Search page. {@code message} always carries an explicit hint instead of a
     * silent blank page: an empty-result hint when nothing matched (FR-040), or the
     * "date required for availability" hint when no date was supplied.
     */
    public record SearchResult(
            List<Summary> items,
            int total,
            boolean availabilityKnown,
            String message) {
    }
}
