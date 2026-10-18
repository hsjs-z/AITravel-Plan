# V2-08 Acceptance Evidence

**Date**: 2026-10-18

**Status**: PASS, accepted. T013/T014/T023/T024/T026/T027 completed.

## 1. Scope

V2-08 adds pace, daily capacity, pure time-slot packing, slot persistence, and
warnings returned by the itinerary API. The V2-07 tool and whitelist path is unchanged.

Changed areas:

- `services/ai-planning/.../domain/Pace.java`
- `services/ai-planning/.../application/TimeSlotAllocator.java`
- `services/ai-planning/.../application/PackedOutput.java`
- `services/ai-planning/.../application/PlanningService.java`
- `services/ai-planning/.../domain/PlanningTask.java`
- `services/ai-planning/.../api/PlanningController.java`
- `services/ai-planning/src/main/resources/db/migration/V3__planning_pace.sql`
- `services/itinerary/src/main/resources/db/migration/V3__itinerary_item_slots.sql`
- `services/itinerary/src/main/resources/db/migration/V4__itinerary_warnings.sql`
- itinerary entity/service/controller and `ItineraryClient`

No tool, whitelist, inventory, transaction, or observability behavior was changed.

## 2. Packing ownership and algorithm

- Packing is computed in ai-planning before `ItineraryClient.create`.
- `TimeSlotAllocator` is a pure function with no DB, Redis, Feign, Kafka, or logging
  dependency.
- Itinerary only stores/returns `time_slot`, `sort_order`, and `warnings`.

Rules implemented:

1. Truncate each day to the pace daily max.
2. Attractions are indivisible duration blocks.
3. Search from the current slot pointer forward for enough hours and fewer than 3 items.
4. Overflow forward is allowed; the pointer never moves back.
5. If no later slot fits, stop the day from that attraction onward and emit warnings.
6. RELAXED evening capacity is 0 but EVENING is still emitted as an empty slot.
7. Missing/null/<=0 suggested hours fall back to 2.0.

Pace capacities:

| Pace | Daily max | Morning | Afternoon | Evening |
|---|---:|---:|---:|---:|
| RELAXED | 2 | 3h | 1h | 0h |
| BALANCED | 4 | 4h | 4h | 0h |
| VIBRANT | 6 | 4h | 5h | 3h |

Per-slot item limit is always 3.

## 3. Warnings contract

Warnings are a string array on the planning result and on the itinerary API:

```json
["第1天：玉龙雪山（6.0h）超出当日节奏容量，未安排"]
```

- Daily-max truncation emits a warning per dropped attraction.
- Capacity/slot overflow emits a warning for the unplaced attraction and every
  following attraction that day.
- `POST/PUT /itineraries` accepts `warnings`; `GET /itineraries/{id}` returns them
  together with items.
- Warnings are persisted in `itinerary.warnings`; they are not log-only.

## 4. Edge-case tests

`TimeSlotAllocatorTest` covers all of these:

- 4h first attraction fits BALANCED morning.
- 1h then 4h: 1h stays in morning, 4h overflows to afternoon, no backfill.
- RELAXED EVENING exists but is empty; an unplaceable 4h item produces warnings.
- A single attraction longer than the whole day goes to warnings.
- Missing hours falls back to 2.0.
- Daily max truncation warns for each extra attraction.
- A fourth item in a slot overflows to the next slot.
- VIBRANT uses evening after morning/afternoon capacities.

`ItinerarySlotsTest` proves `time_slot`, `sort_order`, and warnings are persisted.

## 5. Real mock evidence

Xi'an, `pace=BALANCED`, 2 days:

| Day | Morning | Afternoon | Evening |
|---|---|---|---|
| 1 | 大雁塔 (MORNING:1) | 西安城墙 (AFTERNOON:1) | empty |
| 2 | 华清宫 (MORNING:1) | 秦始皇兵马俑博物馆 (AFTERNOON:1) | empty |

Result: `SUCCEEDED`, 0 warnings, itinerary `DRAFT` with 4 items and persisted
`timeSlot/sortOrder`.

Lijiang, `pace=RELAXED`, 2 days:

- Result: `SUCCEEDED`, 2 warnings (both seeded 6h attractions exceed the 4h daily
  capacity; no slot can hold 6h).
- Itinerary: `DRAFT`, API returned `warnings_count=2`, no items.
- This proves the empty-slot/all-unplaceable boundary and warnings transport.

## 6. Database and tests

- Flyway versions: ai-planning `3`, itinerary `4`.
- `planning_task.pace` column present; `itinerary_item` slot columns present;
  `itinerary.warnings` column present.
- DB row check: `pace=RELAXED`, warnings JSON length 59 for the RELAXED run.
- `mvn -pl services/ai-planning,services/itinerary -am test`: ai-planning 25 +
  itinerary 1 + common 5, 0 failures.
- Root `mvn test`: exit 0, all modules `BUILD SUCCESS`.

## 7. Boundary

T013/T014/T023/T024/T026/T027 are left unchecked for user review. V2-09 (item
add/delete APIs) and V2-10 (frontend) are not implemented here.
