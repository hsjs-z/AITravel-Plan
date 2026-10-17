package com.travelplatform.aiplanning.application;

/**
 * Makes the current run's whitelist visible to the tool on the thread executing
 * the model call.
 */
public final class PlanningRunContextHolder {

    private static final ThreadLocal<PlanningRunContext> CURRENT = new ThreadLocal<>();

    private PlanningRunContextHolder() {
    }

    public static PlanningRunContext get() {
        return CURRENT.get();
    }

    public static void set(PlanningRunContext context) {
        CURRENT.set(context);
    }

    public static void clear() {
        CURRENT.remove();
    }
}
