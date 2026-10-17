package com.travelplatform.aiplanning.application;

import com.travelplatform.aiplanning.domain.PlanningTask;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MockPlannerClientTest {

    private final AttractionSearchTool tool = mock(AttractionSearchTool.class);
    private final MockPlannerClient client = new MockPlannerClient(tool);

    @Test
    void mockBuildsOutputFromDestinationCandidatesWithoutHardcodedIds() {
        PlanningTask task = task("西安", 2);
        List<AttractionRef> refs = List.of(
                ref("r-xian-1", "大雁塔", false, "s-xian-1"),
                ref("r-xian-2", "西安城墙", false, "s-xian-2"),
                ref("r-xian-3", "华清宫", false, "s-xian-3"),
                ref("r-xian-4", "秦始皇兵马俑", false, "s-xian-4"));
        when(tool.searchAttractions("西安")).thenReturn(refs);

        PlanningOutput.Plan plan = client.generate(task, new PlanningRunContext());

        assertEquals("西安", plan.destination());
        assertEquals(2, plan.days().size());
        assertEquals(List.of("大雁塔", "西安城墙"),
                plan.days().get(0).attractions().stream()
                        .map(PlanningOutput.Attraction::name).toList());
        assertEquals(List.of("华清宫", "秦始皇兵马俑"),
                plan.days().get(1).attractions().stream()
                        .map(PlanningOutput.Attraction::name).toList());
        assertFalse(plan.days().stream()
                .flatMap(day -> day.attractions().stream())
                .anyMatch(attraction -> "r-hotel-1".equals(attraction.resourceId())
                        || "r-attraction-1".equals(attraction.resourceId())));
        verify(tool).searchAttractions("西安");
    }

    @Test
    void mockLeavesLaterDaysEmptyWhenCandidatesRunOut() {
        PlanningTask task = task("丽江", 3);
        when(tool.searchAttractions("丽江")).thenReturn(List.of(
                ref("r-lijiang-1", "丽江古城", true, null),
                ref("r-lijiang-2", "玉龙雪山", false, "s-lijiang-2")));

        PlanningOutput.Plan plan = client.generate(task, new PlanningRunContext());

        assertEquals(2, plan.days().get(0).attractions().size());
        assertEquals(0, plan.days().get(1).attractions().size());
        assertEquals(0, plan.days().get(2).attractions().size());
    }

    private PlanningTask task(String destination, int days) {
        PlanningTask task = new PlanningTask();
        task.setDestination(destination);
        task.setDays(days);
        task.setPartySize(2);
        return task;
    }

    private AttractionRef ref(String resourceId, String name, boolean free, String specId) {
        return new AttractionRef(resourceId, name, "陕西", "西安", "5A", "称号",
                "简介", new BigDecimal("3"), free ? 0L : 100L, free,
                new BigDecimal("34"), new BigDecimal("108"), "READY",
                specId, specId == null ? null : "成人票", free ? 0L : 100L, free ? null : 200);
    }
}
