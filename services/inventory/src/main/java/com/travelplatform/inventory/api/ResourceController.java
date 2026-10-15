package com.travelplatform.inventory.api;

import com.travelplatform.common.api.ApiResponse;
import com.travelplatform.inventory.application.ResourceSearchService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Resource browsing endpoints (T054): search with destination/date/price filters and
 * resource detail. Contract: specs/001-ai-travel-platform-v1/contracts/inventory.md.
 */
@RestController
public class ResourceController {

    private final ResourceSearchService resourceSearchService;

    public ResourceController(ResourceSearchService resourceSearchService) {
        this.resourceSearchService = resourceSearchService;
    }

    @GetMapping("/resources/search")
    public ApiResponse<ResourceResponse.SearchResult> search(
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "destination", required = false) String destination,
            @RequestParam(name = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "priceMin", required = false) Long priceMin,
            @RequestParam(name = "priceMax", required = false) Long priceMax,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "category", required = false) String category) {
        return ApiResponse.success(resourceSearchService.search(
                new ResourceSearchService.Criteria(type, destination, date, priceMin, priceMax, keyword, sort, category)));
    }

    @GetMapping("/resources/{resourceId}")
    public ApiResponse<ResourceResponse.Detail> detail(
            @PathVariable("resourceId") String resourceId,
            @RequestParam(name = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(resourceSearchService.detail(resourceId, date));
    }
}
