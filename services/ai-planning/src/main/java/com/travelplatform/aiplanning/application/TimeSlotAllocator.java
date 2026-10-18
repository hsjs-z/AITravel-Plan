package com.travelplatform.aiplanning.application;

import com.travelplatform.aiplanning.domain.Pace;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pure time-slot packing. It has no DB, network, or event dependencies.
 */
@Component
public class TimeSlotAllocator {

    public static final List<String> SLOT_ORDER =
            List.of("MORNING", "AFTERNOON", "EVENING");

    private static final double DEFAULT_HOURS = 2.0;

    public AllocationResult allocate(Pace pace, List<PlanningOutput.Day> days) {
        Pace effectivePace = pace == null ? Pace.BALANCED : pace;
        List<AllocatedDay> allocatedDays = new ArrayList<>();
        List<String> allWarnings = new ArrayList<>();
        if (days == null) {
            return new AllocationResult(List.of(), List.of());
        }
        for (PlanningOutput.Day day : days) {
            if (day == null) {
                continue;
            }
            List<PlanningOutput.Attraction> raw =
                    day.attractions() == null ? List.of() : day.attractions();
            List<PlanningOutput.Attraction> truncated = raw.size() > effectivePace.dailyMax()
                    ? raw.subList(0, effectivePace.dailyMax())
                    : raw;
            List<String> dayWarnings = new ArrayList<>();
            for (int i = truncated.size(); i < raw.size(); i++) {
                addWarning(dayWarnings, allWarnings, truncationWarning(day.day(), raw.get(i), effectivePace));
            }

            Map<String, List<AllocatedItem>> slots = new LinkedHashMap<>();
            Map<String, Double> remaining = new LinkedHashMap<>();
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (String slot : SLOT_ORDER) {
                slots.put(slot, new ArrayList<>());
                remaining.put(slot, effectivePace.hoursFor(slot));
                counts.put(slot, 0);
            }

            int pointer = 0;
            for (int index = 0; index < truncated.size(); index++) {
                PlanningOutput.Attraction attraction = truncated.get(index);
                double hours = hoursOf(attraction);
                int target = findSlot(pointer, hours, remaining, counts);
                if (target < 0) {
                    for (int rest = index; rest < truncated.size(); rest++) {
                        addWarning(dayWarnings, allWarnings,
                                capacityWarning(day.day(), truncated.get(rest), hoursOf(truncated.get(rest))));
                    }
                    break;
                }
                String slot = SLOT_ORDER.get(target);
                int sortOrder = slots.get(slot).size() + 1;
                slots.get(slot).add(new AllocatedItem(attraction, slot, sortOrder));
                remaining.put(slot, remaining.get(slot) - hours);
                counts.put(slot, counts.get(slot) + 1);
                pointer = target;
            }

            allocatedDays.add(new AllocatedDay(
                    day.day(), day.theme(), immutableSlots(slots), List.copyOf(dayWarnings)));
        }
        return new AllocationResult(List.copyOf(allocatedDays), List.copyOf(allWarnings));
    }

    private int findSlot(int pointer,
                         double hours,
                         Map<String, Double> remaining,
                         Map<String, Integer> counts) {
        for (int index = pointer; index < SLOT_ORDER.size(); index++) {
            String slot = SLOT_ORDER.get(index);
            if (remaining.get(slot) + 0.0001 >= hours
                    && counts.get(slot) < Pace.SLOT_ITEM_LIMIT) {
                return index;
            }
        }
        return -1;
    }

    private Map<String, List<AllocatedItem>> immutableSlots(Map<String, List<AllocatedItem>> slots) {
        Map<String, List<AllocatedItem>> result = new LinkedHashMap<>();
        slots.forEach((slot, items) -> result.put(slot, List.copyOf(items)));
        return Collections.unmodifiableMap(result);
    }

    private double hoursOf(PlanningOutput.Attraction attraction) {
        BigDecimal hours = attraction.suggestedHours();
        if (hours == null || hours.compareTo(BigDecimal.ZERO) <= 0) {
            return DEFAULT_HOURS;
        }
        return hours.doubleValue();
    }

    private String truncationWarning(int day, PlanningOutput.Attraction attraction, Pace pace) {
        return String.format(Locale.ROOT, "第%d天：%s 超出 %s 每日 %d 个上限，未安排",
                day, nameOf(attraction), pace.name(), pace.dailyMax());
    }

    private String capacityWarning(int day, PlanningOutput.Attraction attraction, double hours) {
        return String.format(Locale.ROOT, "第%d天：%s（%.1fh）超出当日节奏容量，未安排",
                day, nameOf(attraction), hours);
    }

    private String nameOf(PlanningOutput.Attraction attraction) {
        return attraction.name() == null || attraction.name().isBlank()
                ? "未命名景点"
                : attraction.name();
    }

    private void addWarning(List<String> dayWarnings, List<String> allWarnings, String warning) {
        dayWarnings.add(warning);
        allWarnings.add(warning);
    }

    public record AllocationResult(List<AllocatedDay> days, List<String> warnings) {
    }

    public record AllocatedDay(
            int day,
            String theme,
            Map<String, List<AllocatedItem>> slots,
            List<String> warnings) {
    }

    public record AllocatedItem(
            PlanningOutput.Attraction attraction,
            String timeSlot,
            int sortOrder) {
    }
}
