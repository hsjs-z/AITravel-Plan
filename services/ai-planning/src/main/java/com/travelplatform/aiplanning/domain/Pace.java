package com.travelplatform.aiplanning.domain;

/**
 * Daily packing capacity by travel pace.
 */
public enum Pace {
    RELAXED(2, 4, 3, 1, 0),
    BALANCED(4, 8, 4, 4, 0),
    VIBRANT(6, 12, 4, 5, 3);

    public static final int SLOT_ITEM_LIMIT = 3;

    private final int dailyMax;
    private final double dailyHours;
    private final double morningHours;
    private final double afternoonHours;
    private final double eveningHours;

    Pace(int dailyMax, double dailyHours, double morningHours, double afternoonHours, double eveningHours) {
        this.dailyMax = dailyMax;
        this.dailyHours = dailyHours;
        this.morningHours = morningHours;
        this.afternoonHours = afternoonHours;
        this.eveningHours = eveningHours;
    }

    public int dailyMax() {
        return dailyMax;
    }

    public double dailyHours() {
        return dailyHours;
    }

    public double hoursFor(String timeSlot) {
        return switch (timeSlot) {
            case "MORNING" -> morningHours;
            case "AFTERNOON" -> afternoonHours;
            case "EVENING" -> eveningHours;
            default -> 0;
        };
    }
}
