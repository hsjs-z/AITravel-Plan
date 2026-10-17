package com.travelplatform.aiplanning.application;

import com.travelplatform.aiplanning.infrastructure.InventoryClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Spring AI tool that queries the inventory attraction endpoint. Every returned
 * resource is merged into the current run's whitelist.
 */
@Component
public class AttractionSearchTool {

    private static final Logger log = LoggerFactory.getLogger(AttractionSearchTool.class);

    private final InventoryClient inventoryClient;

    public AttractionSearchTool(InventoryClient inventoryClient) {
        this.inventoryClient = inventoryClient;
    }

    @Tool(name = "searchAttractions",
          description = "查询中国某目的地、省份或城市的上架景区，返回名称、级别、称号、门票价、坐标、resourceId、specId 等真实库存数据。规划前必须调用本工具；resourceId/specId 只能从本工具结果中复制。")
    public List<AttractionRef> searchAttractions(String destination) {
        if (destination == null || destination.isBlank()) {
            log.info("searchAttractions(destination=<blank>) returned 0 results");
            return List.of();
        }
        InventoryClient.AttractionApiResponse response =
                inventoryClient.attractions(destination, null, 20, 0);
        List<InventoryClient.Attraction> items =
                response == null || response.data() == null || response.data().items() == null
                        ? List.of()
                        : response.data().items();
        List<AttractionRef> refs = items.stream().map(this::toRef).toList();

        PlanningRunContext context = PlanningRunContextHolder.get();
        if (context != null) {
            context.merge(refs);
            context.recordDestination(destination);
        }
        if (refs.isEmpty()) {
            log.info("searchAttractions(destination={}) returned 0 results", destination);
        } else {
            log.info("searchAttractions(destination={}) returned {} results", destination, refs.size());
        }
        return refs;
    }

    private AttractionRef toRef(InventoryClient.Attraction attraction) {
        return new AttractionRef(
                attraction.resourceId(),
                attraction.name(),
                attraction.province(),
                attraction.city(),
                attraction.level(),
                attraction.honorTitle(),
                attraction.summary(),
                attraction.suggestedHours(),
                attraction.ticketPrice(),
                attraction.free(),
                attraction.latitude(),
                attraction.longitude(),
                attraction.coordinateStatus(),
                attraction.specId(),
                attraction.specName(),
                attraction.price(),
                attraction.available());
    }
}
