# V2-07 Acceptance Plan

**Status**: Approved on 2026-10-18. Implementation is complete; acceptance evidence
is awaiting review. T016/T017/T019/T020/T021/T022/T025 remain unchecked.

## 1. Scope and boundary

V2-07 is the Spring AI tool-calling migration for ai-planning.

In scope:

- Replace the RestTemplate DeepSeek call with Spring AI `ChatClient` + DeepSeek starter.
- Register the `searchAttractions(destination)` tool backed by `GET /inventory/attractions`.
- Enforce the tool-result whitelist on every itinerary binding.
- Replace the hardcoded-Yunnan mock with a destination-driven mock.
- Add auto-mock fallback when no `DEEPSEEK_API_KEY` is configured.
- Fail a planning task with a clear `failureReason` on timeout or tool failure.

Out of scope (deferred to V2-08):

- `pace`, daily caps, slot packing, warnings, and slot/sort-order persistence. V2-07
  materializes plain draft attraction items without time slots.

## 2. Planner interface change

Current `PlannerClient.generate(task, candidateContext)` returns a raw string and the
service builds a whitelist from the old `/inventory/candidates` Feign call.

V2-07 changes it to:

```java
PlanningOutput generate(PlanningTask task);
```

`PlanningOutput` is the raw model contract already documented in
`specs/002-ai-travel-platform-v2/contracts/ai-planning.md`:

```java
record PlanningOutput(String destination, int totalDays,
                      List<Day> days, List<String> tips) {}
record Day(int day, String theme, List<Attraction> attractions) {}
record Attraction(String name, BigDecimal suggestedHours, String description,
                  Long ticketPrice, String resourceId, String specId) {}
```

`RecommendationTool` and the old candidate-context prompt are removed from
ai-planning. The `/inventory/candidates` Feign method is also removed from
`InventoryClient`; the inventory endpoint itself stays unchanged for v1.

## 3. Spring AI migration

- Add `spring-ai-starter-model-deepseek` to
  `services/ai-planning/pom.xml` (parent BOM already pins Spring AI 1.0.9).
- Add Spring AI DeepSeek config to
  `services/ai-planning/src/main/resources/application.yml`:
  - `spring.ai.deepseek.api-key=${DEEPSEEK_API_KEY:...}` placeholder, so the context
    starts without a key;
  - `spring.ai.deepseek.base-url=https://api.deepseek.com`;
  - `spring.ai.deepseek.chat.options.model=deepseek-chat`, `temperature=0.2`.
- Delete `RestTemplateBuilder`/`RestTemplate` usage from `DeepSeekPlannerClient`.
- `DeepSeekPlannerClient` uses `ChatClient` and registers the tool, then deserializes
  the final assistant content into `PlanningOutput`.

`DEEPSEEK_API_KEY` is read only from `.env` through the existing environment
variable; no key is hardcoded or logged.

## 4. Tool contract

Tool name: `searchAttractions(destination)`.

Implementation file:
`services/ai-planning/src/main/java/com/travelplatform/aiplanning/application/AttractionSearchTool.java`.

Tool input:

| Field | Rule |
|---|---|
| `destination` | Required non-blank string; passed as `destination` to `/inventory/attractions` |

Tool output list item:

```java
record AttractionRef(
    String resourceId,
    String name,
    String province,
    String city,
    String level,
    String honorTitle,
    String summary,
    BigDecimal suggestedHours,
    Long ticketPrice,
    boolean free,
    BigDecimal latitude,
    BigDecimal longitude,
    String coordinateStatus,
    String specId,
    String specName,
    Long price,
    Integer available) {}
```

The tool calls:

```text
GET /inventory/attractions?destination={destination}&limit=20&offset=0
```

It maps `AttractionToolResponse.AttractionPage.items` to `AttractionRef` and records
the results in the current run's whitelist context before returning them to the model.

## 5. Whitelist iron rule

`PlanningRunContext` is a per-run whitelist holder, and
`PlanningRunContextHolder` exposes it on the thread executing the model call:

- `merge(List<AttractionRef>)` stores every resource returned by the tool for the
  current planning run.
- `validSpecPair(resourceId, specId)` returns true only for a paid
  `(resourceId, specId)` pair that came from this run's tool result.
- `validFreeResource(resourceId)` returns true only for a free resource whose
  `resourceId` came from this run and whose `specId` is null.

Whitelist is cumulative: every `searchAttractions` call merges into the same run
context and never replaces earlier results. A multi-tool-call unit test covers
two separate calls producing a union whitelist.

`PlanningService` wraps the async run in this context. Materialization applies:

| AI output | Outcome |
|---|---|
| Paid `resourceId` + `specId` both present and valid | keep both |
| Free `resourceId` present, `specId` null, resource in results | keep `resourceId`, keep `specId` null |
| Any fabricated `resourceId`/`specId`, or a mixed free/paid pair | set both to null |

The attraction stays in the draft as an unmatched item; no inventory binding is
created. This reuses the existing whitelist idea but sources it from the tool result
instead of the old candidate list.

## 6. Mock mode

`MockPlannerClient` keeps the `PLANNER_PLANNER=mock` semantic but:

- depends on `AttractionSearchTool` (not hardcoded data);
- calls `searchAttractions(task.getDestination())`;
- builds `PlanningOutput` from the real returned attractions, in returned order;
- fills at most two attractions per day in V2-07 (no pace model yet), leaving later
  days empty when the destination has fewer attractions;
- uses each result's `summary` as `description` and its `suggestedHours` as-is;
- never references `r-hotel-1`, `r-attraction-1`, or any other hardcoded resource.

This proves a non-Yunnan destination, such as Xi'an, yields Xi'an resources only.

## 7. Auto-fallback and degradation

`PlannerClientProvider` selects the active planner at runtime:

```text
planning.planner=deepseek AND DEEPSEEK_API_KEY non-blank -> DeepSeekPlannerClient
otherwise -> MockPlannerClient (log a warning if deepseek was requested without a key)
```

The two clients become plain components; the old `@ConditionalOnProperty` beans are
replaced by this provider.

Degradation rules:

- DeepSeek call timeout is configurable as `planning.deepseek-timeout-ms` (default 30000).
- That timeout is the single DeepSeek HTTP request timeout, applied to the RestClient
  read timeout. A separate `planning.generate-timeout-ms` (default 120000) bounds the
  whole generate call including tool round-trips. The two failure reasons are distinct:
  `DeepSeek request timeout` versus `planning task total timeout after ...ms`.
- A timeout, DeepSeek error, or `searchAttractions` Feign failure throws; the async
  planning thread catches it and calls `writer.fail(taskId, reason)`, so the task ends
  `FAILED` with a non-empty `failureReason` instead of hanging.
- The failure reason is truncated to the `failureReason` column width.
- When `searchAttractions` returns zero results, the actual destination argument is
  logged so a fabricated/fallback scenario is diagnosable; the empty return behavior
  is unchanged.

## 8. File plan

- `services/ai-planning/pom.xml` — add DeepSeek starter.
- `services/ai-planning/src/main/resources/application.yml` — Spring AI config + timeout.
- `services/ai-planning/src/main/java/com/travelplatform/aiplanning/application/PlanningOutput.java` — new DTO.
- `services/ai-planning/src/main/java/com/travelplatform/aiplanning/application/PlannerClient.java` — new signature.
- `services/ai-planning/src/main/java/com/travelplatform/aiplanning/application/DeepSeekPlannerClient.java` — ChatClient + tool.
- `services/ai-planning/src/main/java/com/travelplatform/aiplanning/application/MockPlannerClient.java` — destination-driven mock.
- `services/ai-planning/src/main/java/com/travelplatform/aiplanning/application/AttractionSearchTool.java` — new.
- `services/ai-planning/src/main/java/com/travelplatform/aiplanning/application/PlanningRunContext.java` — new.
- `services/ai-planning/src/main/java/com/travelplatform/aiplanning/application/PlannerClientProvider.java` — new.
- `services/ai-planning/src/main/java/com/travelplatform/aiplanning/application/PlanningService.java` — run context, new materialization.
- `services/ai-planning/src/main/java/com/travelplatform/aiplanning/application/RecommendationTool.java` — deleted.
- `services/ai-planning/src/main/java/com/travelplatform/aiplanning/infrastructure/InventoryClient.java` — add `attractions`, remove `candidates`.
- Tests:
  - `services/ai-planning/src/test/java/com/travelplatform/aiplanning/application/AttractionWhitelistTest.java`
  - `services/ai-planning/src/test/java/com/travelplatform/aiplanning/application/MockPlannerClientTest.java`
  - `services/ai-planning/src/test/java/com/travelplatform/aiplanning/application/DeepSeekPlannerClientTest.java`
  - update `services/ai-planning/src/test/java/com/travelplatform/aiplanning/application/PlanningServiceMaterializeTest.java`
  - `services/ai-planning/src/test/java/com/travelplatform/aiplanning/application/PlannerClientProviderTest.java`
- Contracts:
  - `specs/002-ai-travel-platform-v2/contracts/ai-planning.md` — tool result fields and auto-fallback note.
- Evidence:
  - `specs/002-ai-travel-platform-v2/v2-07-acceptance.md`

## 9. Acceptance evidence matrix

Build and unit:

| Check | Expected |
|---|---|
| `mvn -pl services/ai-planning -am test` | all ai-planning + common tests pass |
| root `mvn test` (after stopping the affected jars) | full suite passes |
| fat jar | contains `JarLauncher` and DeepSeek starter classes |

Real DeepSeek (non-Yunnan):

1. Start ai-planning with `PLANNER_PLANNER=deepseek` and `DEEPSEEK_API_KEY` from `.env`.
2. `POST /planning/tasks` with `destination=西安`, `days=3`, `partySize=2`, preferences.
3. Poll until `SUCCEEDED`.
4. `planning_task.result` contains ordered `days[].attractions`; every paid item's
   `resourceId/specId` is one of the four seeded Xi'an attractions.
5. Draft itinerary items match those bindings; unmatched items have both IDs null.
6. Logs show `searchAttractions(destination=西安)`; Tempo shows one traceId spanning
   ai-planning and inventory.

Mock (destination-driven):

| Check | Expected |
|---|---|
| `PLANNER_PLANNER=mock`, destination=西安 | SUCCEEDED; draft references only Xi'an attractions, no hardcoded Yunnan IDs |
| `PLANNER_PLANNER=mock`, destination=丽江 | SUCCEEDED; draft references only Lijiang attractions |

Illegal injection:

| Check | Expected |
|---|---|
| Unit test injects fabricated `resourceId/specId` | both nulled; attraction still present as unmatched |
| Unit test injects a valid pair from the run's tool result | both kept |
| Unit test injects a free resource with a fabricated `specId` | both nulled |

Degradation:

| Check | Expected |
|---|---|
| Planner throws (timeout or tool failure) | task `FAILED` with non-empty `failureReason` |
| inventory stopped during a planning run | same FAILED behavior, no hang |

Regression:

- v1 mock main chain: register/login -> mock planning (Yunnan) -> draft -> confirm ->
  order -> pay -> notification unread, plus one unauthorized-404 sample.
- `/resources/search?destination=云南` and `/inventory/candidates?destination=云南`
  remain unchanged (baseline 18 and 4 with 2/2/1/2 specs).

## 10. Approval points

1. V2-07 stops at raw output + tool + whitelist + mock; slot packing/pace stays in V2-08.
2. `PlannerClient.generate(PlanningTask)` returns `PlanningOutput`; `RecommendationTool`
   and the old candidate-context are removed from ai-planning.
3. The tool result record carries full attraction metadata plus `specId`.
4. Auto-mock is used when `DEEPSEEK_API_KEY` is blank, even if `PLANNER_PLANNER=deepseek`.
5. Tool/DeepSeek failure always ends the task `FAILED` with a reason.
6. Mock fills at most two attractions per day in V2-07 and never hardcodes resources.
7. Illegal-injection proof is a deterministic unit test; the real Xi'an run proves the
   valid path, not model misbehavior.
