# V2-07 Acceptance Evidence

**Date**: 2026-10-18

**Status**: PASS, awaiting user acceptance. T016/T017/T019/T020/T021/T022/T025
remain unchecked until review.

## 1. Scope

V2-07 migrated ai-planning to Spring AI `ChatClient` tool calling and removed the
RestTemplate DeepSeek client. Slot packing and pace remain V2-08.

Changed files:

- `services/ai-planning/pom.xml` — added `spring-ai-starter-model-deepseek`.
- `services/ai-planning/src/main/resources/application.yml` — Spring AI config and
  `planning.deepseek-timeout-ms` / `planning.generate-timeout-ms`.
- `services/ai-planning/src/main/java/com/travelplatform/aiplanning/application/PlanningOutput.java`
- `services/ai-planning/src/main/java/com/travelplatform/aiplanning/application/PlanningOutputParser.java`
- `services/ai-planning/src/main/java/com/travelplatform/aiplanning/application/AttractionRef.java`
- `services/ai-planning/src/main/java/com/travelplatform/aiplanning/application/AttractionSearchTool.java`
- `services/ai-planning/src/main/java/com/travelplatform/aiplanning/application/PlanningRunContext.java`
- `services/ai-planning/src/main/java/com/travelplatform/aiplanning/application/PlanningRunContextHolder.java`
- `services/ai-planning/src/main/java/com/travelplatform/aiplanning/application/PlannerClient.java`
- `services/ai-planning/src/main/java/com/travelplatform/aiplanning/application/DeepSeekPlannerClient.java`
- `services/ai-planning/src/main/java/com/travelplatform/aiplanning/application/MockPlannerClient.java`
- `services/ai-planning/src/main/java/com/travelplatform/aiplanning/application/PlannerClientProvider.java`
- `services/ai-planning/src/main/java/com/travelplatform/aiplanning/application/PlannerClientConfiguration.java`
- `services/ai-planning/src/main/java/com/travelplatform/aiplanning/application/PlanningService.java`
- `services/ai-planning/src/main/java/com/travelplatform/aiplanning/infrastructure/InventoryClient.java`
- removed `services/ai-planning/src/main/java/com/travelplatform/aiplanning/application/RecommendationTool.java`
- new tests under `services/ai-planning/src/test/java/com/travelplatform/aiplanning/application/`
- `specs/002-ai-travel-platform-v2/contracts/ai-planning.md`

No inventory, gateway, user-auth, itinerary, payment, booking-order, or
notification source was changed.

## 2. Build and tests

- `mvn -pl services/ai-planning -am test`: common 5 + ai-planning 17, 0 failures.
- Root `mvn test`: 10/10 modules SUCCESS.
- Test totals: common 5, gateway 4, user-auth 5, inventory 68, ai-planning 17,
  payment 7, booking-order 18, notification 13; itinerary has no tests.
- Fat jar contains `JarLauncher`, `PlannerClientProvider`, and
  `spring-ai-starter-model-deepseek-1.0.9.jar`.
- ai-planning starts and registers in Nacos; inventory is healthy on 8082.

## 3. Spring AI migration and tool

- `DeepSeekPlannerClient` no longer imports or uses `RestTemplate`.
- It uses `ChatClient.prompt().system(...).user(...).tools(attractionSearchTool).call()`
  and parses the final content into `PlanningOutput.Plan`.
- `AttractionSearchTool` is annotated with Spring AI `@Tool(name="searchAttractions")`
  and calls `InventoryClient.attractions(destination, null, 20, 0)` over Feign.
- `PlannerClientProvider` uses DeepSeek only when `PLANNER_PLANNER=deepseek` and
  `DEEPSEEK_API_KEY` is non-blank; otherwise it logs and uses mock.

## 4. Real DeepSeek evidence (single Xi'an task)

One real DeepSeek planning task was run, as agreed:

- Task: `f9d8f680-f901-40e5-9f93-ef8a035e06a7`
- Itinerary: `80681595-1ac6-4916-8b11-db7b7afc5159`
- Status: `SUCCEEDED`
- Destination: 西安

The result contains four ordered attractions across three days:

西安城墙、大雁塔、秦始皇兵马俑博物馆、华清宫。

Every paid attraction carries a real Xi'an `resourceId` and `specId`:

```text
r-attraction-9e9dfb83-9869-4134-8920-4290f532d25d / s-ticket-33a2c5a4-3cb3-48b7-8ca0-21881bf10d5d
r-attraction-217d5564-7f05-40bb-8335-afbb49eda0d1 / s-ticket-13de2b6d-08eb-481e-b6d5-91b6f3169e82
r-attraction-1102506e-a79e-4b78-8dea-dcce02aff16d / s-ticket-b8febcfa-9bc9-4553-aeae-7da0733120c8
r-attraction-29975df5-b9d7-4cb5-b02d-7d202846803b / s-ticket-9149e324-686d-44c6-a79e-db90bca5ea1f
```

Draft itinerary items match those bindings; no fabricated ID was materialized.

Tempo trace `ed330a8070b04711b222273dca0d0586` contains:

- `spring_ai chat_client` span with `spring.ai.chat.client.tool.names=["searchAttractions"]`
- `chat deepseek-chat` span with `gen_ai.response.finish_reasons=TOOL_CALLS`
- `tool_call search-attractions` span
- `HTTP GET` Feign client span to `/inventory/attractions`
- `http get /inventory/attractions` server span in the `inventory` service
- a second `chat deepseek-chat` span with `finish_reasons=STOP`

The ai-planning log contains:

```text
AttractionSearchTool - searchAttractions(destination=...) returned 4 results
```

## 5. Mock evidence (destination-driven)

Mock task `91a22ff8-1d58-4a76-8ee4-1f967cba8c95` for 西安 produced
`SUCCEEDED` and itinerary `75d80a16-bfbb-4d13-9f3d-d573f331c684`.

The result used only the four real Xi'an attractions from
`/inventory/attractions`; there were no `r-hotel-1`, `r-attraction-1`, or other
hardcoded resource IDs.

`MockPlannerClientTest` additionally verifies non-Yunnan destinations and
empty-later-day behavior.

## 6. Whitelist and illegal injection

- `PlanningRunContext.merge` is cumulative: two tool calls produce a union
  whitelist (`AttractionWhitelistTest.multipleToolCallsMergeIntoOneWhitelist`).
- `PlanningServiceMaterializeTest` proves:
  - a valid paid pair is materialized;
  - a fabricated pair is nulled while the valid pair is kept;
  - a free resource keeps `resourceId` and a null `specId`.
- `AttractionWhitelistTest` proves fabricated and mixed IDs are rejected.

## 7. Degradation

- `DeepSeekPlannerClientTest.requestTimeoutProducesDistinctReason` proves
  `DeepSeek request timeout`.
- `DeepSeekPlannerClientTest.totalGenerateTimeoutProducesDistinctReason` proves
  `planning task total timeout after ...ms`.
- `PlanningServiceMaterializeTest.plannerFailureFailsTaskWithFailureReason` proves
  the task ends `FAILED` with a non-empty reason.
- Real run: with ai-planning in mock mode and inventory stopped, task
  `f2e564ad-36f4-4d9c-8894-104e033bbd8f` ended `FAILED` with a non-empty
  `failureReason` and no hang. Inventory was then restarted and is healthy.

Timeout config is present in `application.yml` with comments distinguishing the
single-request timeout from the whole-generate timeout.

## 8. Cleanup

- Temporary acceptance users (`v207*@travel.local`) were deleted; 0 remain.
- Acceptance planning tasks and itineraries are retained as audit evidence.
- ai-planning is left running in mock mode (the default), inventory is healthy.
