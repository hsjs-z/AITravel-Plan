package com.travelplatform.aiplanning.application;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttractionWhitelistTest {

    @Test
    void multipleToolCallsMergeIntoOneWhitelist() {
        PlanningRunContext context = new PlanningRunContext();
        context.merge(List.of(paidRef("r-1", "s-1")));
        context.merge(List.of(paidRef("r-2", "s-2")));

        assertTrue(context.validSpecPair("r-1", "s-1"));
        assertTrue(context.validSpecPair("r-2", "s-2"));
        assertFalse(context.validSpecPair("r-1", "s-2"));
    }

    @Test
    void freeResourceRequiresNullSpecIdAndKnownResource() {
        PlanningRunContext context = new PlanningRunContext();
        context.merge(List.of(freeRef("r-free")));

        assertTrue(context.validFreeResource("r-free"));
        assertFalse(context.validFreeResource("r-missing"));
        assertFalse(context.validSpecPair("r-free", "s-any"));
    }

    @Test
    void fabricatedIdsAreNotInWhitelist() {
        PlanningRunContext context = new PlanningRunContext();
        context.merge(List.of(paidRef("r-1", "s-1")));

        assertFalse(context.validSpecPair("r-fabricated", "s-fabricated"));
    }

    private AttractionRef paidRef(String resourceId, String specId) {
        return new AttractionRef(resourceId, "付费景区", "云南省", "丽江", "5A", "称号",
                "简介", new BigDecimal("4"), 100L, false,
                new BigDecimal("27"), new BigDecimal("100"), "READY",
                specId, "成人票", 100L, 200);
    }

    private AttractionRef freeRef(String resourceId) {
        return new AttractionRef(resourceId, "免费景区", "云南省", "丽江", "5A", "称号",
                "简介", new BigDecimal("3"), 0L, true,
                new BigDecimal("26.87"), new BigDecimal("100.22"), "READY",
                null, null, 0L, null);
    }
}
