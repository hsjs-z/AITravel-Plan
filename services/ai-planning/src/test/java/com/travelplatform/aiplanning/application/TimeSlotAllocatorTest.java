package com.travelplatform.aiplanning.application;

import com.travelplatform.aiplanning.domain.Pace;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeSlotAllocatorTest {

    private final TimeSlotAllocator allocator = new TimeSlotAllocator();

    @Test
    void fourHourFirstAttractionFitsBalancedMorning() {
        TimeSlotAllocator.AllocationResult result = allocate(Pace.BALANCED, attraction("兵马俑", "4"));
        TimeSlotAllocator.AllocatedDay day = result.days().get(0);

        assertEquals(List.of("兵马俑"), names(day, "MORNING"));
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void oneHourThenFourHoursOverflowsToAfternoonWithoutBackfill() {
        TimeSlotAllocator.AllocationResult result = allocate(Pace.BALANCED,
                attraction("小景点", "1"), attraction("大景点", "4"));
        TimeSlotAllocator.AllocatedDay day = result.days().get(0);

        assertEquals(List.of("小景点"), names(day, "MORNING"));
        assertEquals(List.of("大景点"), names(day, "AFTERNOON"));
        assertEquals(1, day.slots().get("AFTERNOON").get(0).sortOrder());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void relaxedEveningIsAlwaysPresentButEmpty() {
        TimeSlotAllocator.AllocationResult result = allocate(Pace.RELAXED,
                attraction("4h 景区", "4"));
        TimeSlotAllocator.AllocatedDay day = result.days().get(0);

        assertTrue(day.slots().containsKey("EVENING"));
        assertTrue(day.slots().get("EVENING").isEmpty());
        assertTrue(names(day, "MORNING").isEmpty());
        assertFalse(result.warnings().isEmpty());
    }

    @Test
    void singleAttractionLongerThanWholeDayGoesToWarnings() {
        TimeSlotAllocator.AllocationResult result = allocate(Pace.BALANCED,
                attraction("超长景区", "13"));

        assertTrue(names(result.days().get(0), "MORNING").isEmpty());
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).contains("超出当日节奏容量"));
    }

    @Test
    void missingHoursFallsBackToTwoHours() {
        TimeSlotAllocator.AllocationResult result = allocate(Pace.BALANCED,
                attractionWithHours("无时长", null));

        assertEquals(List.of("无时长"), names(result.days().get(0), "MORNING"));
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void dailyMaxTruncatesAndWarnsForExtras() {
        TimeSlotAllocator.AllocationResult result = allocate(Pace.RELAXED,
                attraction("A", "1"), attraction("B", "1"),
                attraction("C", "1"), attraction("D", "1"));

        assertEquals(2, result.days().get(0).slots().values().stream()
                .mapToInt(List::size).sum());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("每日 2 个上限")));
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("C")));
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("D")));
    }

    @Test
    void slotItemLimitOverflowsToNextSlot() {
        TimeSlotAllocator.AllocationResult result = allocate(Pace.VIBRANT,
                attraction("A", "1"), attraction("B", "1"), attraction("C", "1"),
                attraction("D", "1"));
        TimeSlotAllocator.AllocatedDay day = result.days().get(0);

        assertEquals(3, day.slots().get("MORNING").size());
        assertEquals(List.of("D"), names(day, "AFTERNOON"));
    }

    @Test
    void vibrantCanUseEveningAfterAfternoonCapacity() {
        TimeSlotAllocator.AllocationResult result = allocate(Pace.VIBRANT,
                attraction("A", "4"), attraction("B", "5"), attraction("C", "3"));
        TimeSlotAllocator.AllocatedDay day = result.days().get(0);

        assertEquals(List.of("A"), names(day, "MORNING"));
        assertEquals(List.of("B"), names(day, "AFTERNOON"));
        assertEquals(List.of("C"), names(day, "EVENING"));
    }

    private TimeSlotAllocator.AllocationResult allocate(Pace pace, PlanningOutput.Attraction... attractions) {
        PlanningOutput.Day day = new PlanningOutput.Day(1, "第一天", List.of(attractions));
        return allocator.allocate(pace, List.of(day));
    }

    private PlanningOutput.Attraction attraction(String name, String hours) {
        return attractionWithHours(name, new BigDecimal(hours));
    }

    private PlanningOutput.Attraction attractionWithHours(String name, BigDecimal hours) {
        return new PlanningOutput.Attraction(name, hours, "简介", 100L, null, null);
    }

    private List<String> names(TimeSlotAllocator.AllocatedDay day, String slot) {
        return day.slots().getOrDefault(slot, List.of()).stream()
                .map(item -> item.attraction().name())
                .toList();
    }

}
