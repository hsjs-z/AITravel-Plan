package com.travelplatform.aiplanning.application;

import com.travelplatform.aiplanning.infrastructure.InventoryClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Fetches the bookable candidate resources (resource + spec + price + stock)
 * from inventory and formats them as prompt context, so the planner outputs
 * concrete resourceId/specId instead of bare names (ADR-0002 / T019).
 */
@Component
public class RecommendationTool {

    private static final Logger log = LoggerFactory.getLogger(RecommendationTool.class);

    private final InventoryClient inventoryClient;

    public RecommendationTool(InventoryClient inventoryClient) {
        this.inventoryClient = inventoryClient;
    }

    public List<InventoryClient.Candidate> fetchCandidates(String destination, LocalDate date) {
        try {
            InventoryClient.CandidateApiResponse response = inventoryClient.candidates(destination, date);
            return (response == null || response.data() == null) ? List.of() : response.data();
        } catch (Exception e) {
            log.warn("failed to load inventory candidates: {}", e.toString());
            return List.of();
        }
    }

    public String buildCandidateContext(List<InventoryClient.Candidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("候选可订资源清单（hotels/attractions 必须从中选择，resourceId/specId 必须逐字符复制、禁止编造）：\n");
            for (InventoryClient.Candidate candidate : candidates) {
                sb.append("- ").append(candidate.resourceName())
                        .append(" resourceId=").append(candidate.resourceId()).append("\n");
                for (InventoryClient.Spec spec : candidate.specs()) {
                    sb.append("    - ").append(spec.specName())
                            .append(" specId=").append(spec.specId())
                            .append(" 单价=").append(spec.price())
                            .append(" 可订=").append(spec.available()).append("\n");
                }
        }
        return sb.toString();
    }
}
