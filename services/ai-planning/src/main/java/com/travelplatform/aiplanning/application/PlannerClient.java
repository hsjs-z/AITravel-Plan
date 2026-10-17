package com.travelplatform.aiplanning.application;

import com.travelplatform.aiplanning.domain.PlanningTask;

/**
 * Port for the LLM planner. The provider selects DeepSeek or the deterministic
 * mock based on configuration and key availability.
 */
public interface PlannerClient {

    PlanningOutput.Plan generate(PlanningTask task, PlanningRunContext context);
}
