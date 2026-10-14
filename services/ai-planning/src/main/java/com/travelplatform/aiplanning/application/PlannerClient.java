package com.travelplatform.aiplanning.application;

import com.travelplatform.aiplanning.domain.PlanningTask;

/**
 * Port for the LLM planner. The real DeepSeek implementation plugs in here;
 * a deterministic mock is used in development.
 */
public interface PlannerClient {

    String generate(PlanningTask task);
}
