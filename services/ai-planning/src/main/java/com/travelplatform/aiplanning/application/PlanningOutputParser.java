package com.travelplatform.aiplanning.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Parses the model's final assistant content into the raw planning contract.
 * Markdown fences are tolerated even though the prompt forbids them.
 */
@Component
public class PlanningOutputParser {

    private final ObjectMapper objectMapper;

    public PlanningOutputParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public PlanningOutput.Plan parse(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("AI returned empty content");
        }
        try {
            return objectMapper.readValue(stripFences(content), PlanningOutput.Plan.class);
        } catch (Exception e) {
            throw new IllegalStateException("AI output is not valid JSON: " + e.getMessage());
        }
    }

    private String stripFences(String content) {
        String trimmed = content.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstNewline = trimmed.indexOf('\n');
        if (firstNewline < 0) {
            return trimmed;
        }
        String body = trimmed.substring(firstNewline + 1);
        int closing = body.lastIndexOf("```");
        if (closing >= 0) {
            body = body.substring(0, closing);
        }
        return body.trim();
    }
}
