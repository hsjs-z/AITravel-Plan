# V2-08 Acceptance Plan

**Status**: Approved on 2026-10-18. Implementation is complete; acceptance evidence
is awaiting review.

## 1. Scope and boundary

V2-08 adds pace, daily capacity, time-slot packing, and slot persistence.

In scope:

- `planning_task.pace` schema, entity, and request contract.
- `itinerary_item.time_slot` / `sort_order` schema and persistence.
- A pure `TimeSlotAllocator` in ai-planning.
- Wiring packing into `PlanningService.materializeItinerary`.

Out of scope:

- Itinerary add/delete APIs and per-slot enforcement in the UI stay in V2-09.
- Frontend form and result page stay in V2-10.
- V2-07's Spring AI tool and whitelist path is unchanged.

## 2. Rules source

Packing follows `spec.md` FR-V2-007 through FR-V2-011 and the capacity table in
`specs/002-ai-travel-platform-v2/contracts/ai-planning.md`:

| Pace | Daily max | Daily hours | Morning | Afternoon | Evening |
|---|---:|---:|---:|---:|---:|
| `RELAXED` | 2 | 4 | 3 | 1 | 0 |
| `BALANCED` | 4 | 8 | 4 | 4 | 0 |
| `VIBRANT` | 6 | 12 | 4 | 5 | 3 |

Every slot has a hard count limit of 3.

Note: `spec.md` FR-V2-017 is the result-page map requirement, not a packing rule.
Packing is governed by FR-V2-007–FR-V2-011. If the reviewer intended
`research.md` R-004, those same rules are covered here as well.

## 3. Packing ownership: ai-planning

Packing runs in ai-planning before `ItineraryClient.create`.

Reasons:

- pace, AI order, daily caps, and the whitelist are all planning-domain facts.
- itinerary is a persistence/display service; it should not recompute slots from AI order.
- keeping `TimeSlotAllocator` in ai-planning makes it a pure function with no DB,
  Redis, Feign, or Kafka dependency, which is the requested test strategy.

Itinerary only stores and returns `time_slot` and `sort_order`; it does not decide them.

## 4. Algorithm

`TimeSlotAllocator.allocate(pace, days)`:

1. For each AI day, truncate the ordered attraction list to the pace daily max.
2. Treat each attraction as an indivisible duration block.
   - Missing/null/<=0 `suggestedHours` falls back to `2.0`.
3. Keep a slot pointer starting at MORNING.
4. For each attraction, search from the current pointer forward for the first slot
   with remaining hours >= the attraction hours and item count < 3.
5. Place it there, decrement that slot's remaining hours, increment `sortOrder`,
   and move the pointer to that slot. Never move the pointer backward.
6. If no later slot fits, stop that day's packing. That attraction and all following
   ones for the day go to `warnings` and are not persisted.
7. `RELAXED` keeps EVENING capacity 0; the empty EVENING slot is still emitted so
   the page can render it.

The allocator returns `PackedDay(day, slots, warnings)`. It does not perform the
whitelist check; `PlanningService` keeps applying the existing V2-07 whitelist and
feeds the allocator only the already-validated raw output.

## 5. Edge cases

Must be covered by pure-function tests:

| Case | Expected |
|---|---|
| 4h first attraction, MORNING empty, BALANCED | goes to MORNING (4h fits) |
| 1h then 4h in BALANCED MORNING | 1h in MORNING, 4h overflows to AFTERNOON; no backfill |
| duration > remaining current slot, later slot fits | later slot; pointer never moves back |
| single attraction longer than every remaining slot | goes to `warnings`, not persisted |
| single attraction longer than the whole day | `warnings`, day has empty slots |
| RELAXED evening | capacity 0, empty EVENING emitted |
| more than daily max | truncation to RELAXED 2 / BALANCED 4 / VIBRANT 6 |
| more than 3 items in one slot | overflow to next slot |
| null/<=0 suggested hours | default 2.0 |

## 6. File plan

- `services/ai-planning/src/main/resources/db/migration/V3__planning_pace.sql`
  - add nullable/pace column and default `BALANCED`; make `budget` nullable.
- `services/ai-planning/src/main/java/com/travelplatform/aiplanning/domain/PlanningTask.java`
  - add `pace` field with default `BALANCED`.
- `services/ai-planning/src/main/java/com/travelplatform/aiplanning/api/PlanningController.java`
  - add optional `pace` to `CreateTaskRequest`; normalize default.
- `services/ai-planning/src/main/java/com/travelplatform/aiplanning/application/Pace.java`
  - enum `RELAXED/BALANCED/VIBRANT`.
- `services/ai-planning/src/main/java/com/travelplatform/aiplanning/application/TimeSlotAllocator.java`
  - pure packing implementation.
- `services/ai-planning/src/main/java/com/travelplatform/aiplanning/application/PackedOutput.java`
  - normalized result DTO (`PackedDay`, `PackedItem`, warnings).
- `services/ai-planning/src/main/java/com/travelplatform/aiplanning/application/PlanningService.java`
  - apply whitelist, then pack, then send slot fields through `ItineraryClient`.
- `services/ai-planning/src/main/java/com/travelplatform/aiplanning/infrastructure/ItineraryClient.java`
  - add `timeSlot` and `sortOrder` to `Item`.
- `services/itinerary/src/main/resources/db/migration/V3__itinerary_item_slots.sql`
  - add `time_slot` and `sort_order`.
- `services/itinerary/src/main/java/com/travelplatform/itinerary/domain/ItineraryItem.java`
  - add fields.
- `services/itinerary/src/main/java/com/travelplatform/itinerary/api/ItineraryController.java`
  - accept and return the new fields.
- `services/itinerary/src/main/java/com/travelplatform/itinerary/application/ItineraryService.java`
  - map and persist the new fields.
- Tests:
  - `services/ai-planning/src/test/java/com/travelplatform/aiplanning/application/TimeSlotAllocatorTest.java`
  - update `services/ai-planning/src/test/java/com/travelplatform/aiplanning/application/PlanningServiceMaterializeTest.java`
  - `services/itinerary/src/test/java/com/travelplatform/itinerary/application/ItinerarySlotsTest.java`

## 7. Acceptance evidence

| Check | Expected |
|---|---|
| `mvn -pl services/ai-planning -am test` | all ai-planning + common tests pass |
| `mvn -pl services/itinerary -am test` | all itinerary + common tests pass |
| root `mvn test` | full suite passes |
| pure-function tests | every edge case in section 5 |
| mock planning with `pace=BALANCED` | result contains packed slots and `sortOrder`; whitelist unchanged |
| mock planning with `pace=RELAXED` | empty EVENING slot emitted |
| DB | `itinerary_item.time_slot/sort_order` persisted |
| V2-07 real tool path | no change; existing tests still pass |

## 8. Approval points

1. Packing is computed in ai-planning; itinerary only persists slot fields.
2. Missing/null `suggestedHours` falls back to 2.0.
3. Unplaceable attractions go to warnings and are not persisted.
4. V2-08 adds itinerary slot persistence but not add/delete APIs (V2-09).
5. The FR-V2-017 reference is treated as the map requirement; packing rules come from
   FR-V2-007–FR-V2-011.
