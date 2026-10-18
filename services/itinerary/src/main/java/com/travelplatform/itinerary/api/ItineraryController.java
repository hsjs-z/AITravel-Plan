package com.travelplatform.itinerary.api;

import com.travelplatform.common.api.ApiResponse;
import com.travelplatform.itinerary.application.ItineraryService;
import com.travelplatform.itinerary.domain.Itinerary;
import com.travelplatform.itinerary.domain.ItineraryItem;
import com.travelplatform.itinerary.domain.ItineraryItemSource;
import com.travelplatform.itinerary.domain.ItineraryTimeSlot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
public class ItineraryController {

    private final ItineraryService itineraryService;

    public ItineraryController(ItineraryService itineraryService) {
        this.itineraryService = itineraryService;
    }

    @PostMapping("/itineraries")
    public ApiResponse<ItineraryResponse> create(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody CreateItineraryRequest request) {
        Itinerary itinerary = itineraryService.create(userId, request);
        return ApiResponse.success(toResponse(itinerary));
    }

    @GetMapping("/itineraries/{itineraryId}")
    public ApiResponse<ItineraryResponse> get(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable("itineraryId") String itineraryId) {
        Itinerary itinerary = itineraryService.get(userId, itineraryId);
        return ApiResponse.success(toResponse(itinerary));
    }

    @PutMapping("/itineraries/{itineraryId}")
    public ApiResponse<ItineraryResponse> update(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable("itineraryId") String itineraryId,
            @Valid @RequestBody UpdateItineraryRequest request) {
        Itinerary itinerary = itineraryService.update(userId, itineraryId, request);
        return ApiResponse.success(toResponse(itinerary));
    }

    @PostMapping("/itineraries/{itineraryId}/items")
    public ApiResponse<ItineraryResponse> addItem(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable("itineraryId") String itineraryId,
            @Valid @RequestBody AddItemRequest request) {
        return ApiResponse.success(toResponse(itineraryService.addItem(userId, itineraryId, request)));
    }

    @DeleteMapping("/itineraries/{itineraryId}/items/{itemId}")
    public ApiResponse<ItineraryResponse> deleteItem(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable("itineraryId") String itineraryId,
            @PathVariable("itemId") String itemId) {
        return ApiResponse.success(toResponse(itineraryService.deleteItem(userId, itineraryId, itemId)));
    }

    @PostMapping("/itineraries/{itineraryId}/confirm")
    public ApiResponse<ItineraryResponse> confirm(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable("itineraryId") String itineraryId) {
        return ApiResponse.success(toResponse(itineraryService.confirm(userId, itineraryId)));
    }

    @DeleteMapping("/itineraries/{itineraryId}")
    public ApiResponse<Void> delete(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable("itineraryId") String itineraryId) {
        itineraryService.delete(userId, itineraryId);
        return ApiResponse.success(null);
    }

    @PostMapping("/itineraries/{itineraryId}/lock")
    public ApiResponse<ItineraryResponse> lock(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable("itineraryId") String itineraryId) {
        return ApiResponse.success(toResponse(itineraryService.lock(userId, itineraryId)));
    }

    @PostMapping("/itineraries/{itineraryId}/release")
    public ApiResponse<ItineraryResponse> release(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable("itineraryId") String itineraryId) {
        return ApiResponse.success(toResponse(itineraryService.release(userId, itineraryId)));
    }

    @PostMapping("/itineraries/{itineraryId}/complete")
    public ApiResponse<ItineraryResponse> complete(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable("itineraryId") String itineraryId) {
        return ApiResponse.success(toResponse(itineraryService.complete(userId, itineraryId)));
    }

    private ItineraryResponse toResponse(Itinerary itinerary) {
        List<ItineraryItem> items = itineraryService.items(itinerary.getId());
        return new ItineraryResponse(
                itinerary.getId(),
                itinerary.getUserId(),
                itinerary.getDestination(),
                itinerary.getStartDate(),
                itinerary.getEndDate(),
                itinerary.getPartySize(),
                itinerary.getBudget(),
                itinerary.getStatus().name(),
                itinerary.getHasOrder(),
                items.stream().map(this::toItemResponse).toList(),
                itineraryService.warnings(itinerary));
    }

    private ItemResponse toItemResponse(ItineraryItem item) {
        return new ItemResponse(
                item.getId(),
                item.getDayDate(),
                item.getName(),
                item.getDescription(),
                item.getSuggestedHours(),
                item.getTicketPrice(),
                item.getResourceType(),
                item.getResourceId(),
                item.getSpecId(),
                item.getSource().name(),
                item.getTimeSlot() == null ? null : item.getTimeSlot().name(),
                item.getSortOrder());
    }

    public record CreateItineraryRequest(
            @NotBlank String destination,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotNull @Positive Integer partySize,
            @NotNull @Positive Long budget,
            List<ItemRequest> items,
            List<String> warnings) {
    }

    public record UpdateItineraryRequest(
            @NotBlank String destination,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotNull @Positive Integer partySize,
            @NotNull @Positive Long budget,
            List<ItemRequest> items,
            List<String> warnings) {
    }

    public record ItemRequest(
            @NotNull LocalDate dayDate,
            @NotBlank String description,
            String name,
            BigDecimal suggestedHours,
            Long ticketPrice,
            String resourceType,
            String resourceId,
            String specId,
            ItineraryItemSource source,
            ItineraryTimeSlot timeSlot,
            Integer sortOrder) {
    }

    public record AddItemRequest(
            @NotNull LocalDate dayDate,
            String name,
            String description,
            BigDecimal suggestedHours,
            @NotNull ItineraryTimeSlot timeSlot,
            @NotBlank String resourceId,
            String specId) {
    }

    public record ItineraryResponse(
            String id,
            String userId,
            String destination,
            LocalDate startDate,
            LocalDate endDate,
            Integer partySize,
            Long budget,
            String status,
            Boolean hasOrder,
            List<ItemResponse> items,
            List<String> warnings) {
    }

    public record ItemResponse(
            String id,
            LocalDate dayDate,
            String name,
            String description,
            BigDecimal suggestedHours,
            Long ticketPrice,
            String resourceType,
            String resourceId,
            String specId,
            String source,
            String timeSlot,
            Integer sortOrder) {
    }
}
