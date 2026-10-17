# V2-06 Acceptance Plan

**Status**: Approved on 2026-10-17. Implementation is complete; acceptance evidence
is awaiting review. T018 remains unchecked until that review passes.

## 1. Scope and boundary

V2-06 delivers the read-only inventory query that will feed the future
`searchAttractions(destination)` Spring AI tool.

- Only `inventory` changes in this round.
- `/resources/search` and `/inventory/candidates` keep their current contracts and behavior.
- `POST /inventory/locks` keeps the ATTRACTION guard (`409`); V2-06 does not open ticket trading.
- `ai-planning` is not changed. `InventoryClient` and the actual Spring AI tool remain
  in V2-07 (T019/T020), because they belong to the Spring AI migration.

## 2. Endpoint decision: new `/inventory/attractions`, not a merge

Add:

```text
GET /inventory/attractions
```

Do not extend `/resources/search` for the tool.

Reasons:

1. `/resources/search` is a v1 user-facing browsing contract with all resource types,
   date/price/sort/category filters, and the `orderEntryAllowed` presentation guard.
   Adding `specId` there would expose internal inventory identifiers to the browser and
   risk changing a previously accepted page contract.
2. The tool needs a compact, stable payload with `specId/specName/price/available` so the
   model can bind only real inventory. That is a different consumer and a different shape.
3. `specs/002-ai-travel-platform-v2/contracts/inventory.md` already names
   `/inventory/attractions` as the internal tool query; no new public browsing surface is needed.
4. The implementation can reuse repository/spec/inventory reads without touching the
   `ResourceSearchService` sorting, message, or `orderEntryAllowed` logic.

The endpoint is read-only and is intended for the planner/Feign path. It is reachable
through the existing authenticated gateway route for acceptance, but it is not a frontend
booking contract.

## 3. Query contract

### Request

| Parameter | Required | Default | Semantics |
|---|---|---|---|
| `destination` | one of the three location filters | - | Case-insensitive contains against `destination`, `province`, or `city`, matching existing search semantics |
| `province` | one of the three location filters | - | Case-insensitive contains against `province` only |
| `city` | one of the three location filters | - | Case-insensitive contains against `city` only |
| `date` | no | - | ISO date; when present, `available` is date-scoped |
| `limit` | no | `20` | Page size; must be `1..50` |
| `offset` | no | `0` | Non-negative page offset |

At least one of `destination`, `province`, `city` must be non-blank. A request with none
returns `400`; this prevents an unbounded dump of all 204 attractions.

Multiple location filters are ANDed. `destination=云南` matches `province=云南省`;
`city=丽江` matches `city=丽江市`; `province=云南省&city=丽江` applies both.

Only `status=ON_SHELF` and `type=ATTRACTION` are returned.

### Response

`ApiResponse<AttractionPage>`:

```json
{
  "total": 6,
  "offset": 0,
  "limit": 20,
  "hasMore": false,
  "items": [
    {
      "resourceId": "r-attraction-...",
      "name": "丽江古城",
      "province": "云南省",
      "city": "丽江",
      "level": "5A",
      "honorTitle": "世界文化遗产",
      "summary": "...",
      "suggestedHours": 4.0,
      "ticketPrice": 0,
      "free": true,
      "latitude": 26.876468,
      "longitude": 100.225830,
      "coordinateStatus": "READY",
      "specId": null,
      "specName": null,
      "price": 0,
      "available": null
    }
  ]
}
```

Field rules:

- `ticketPrice` is the resource's reference price from the seed data.
- `free=true` when `ticketPrice=0`.
- Free attraction: `specId=null`, `specName=null`, `price=0`, `available=null`.
  It is still returned and can be added to an itinerary later; it never enters payment.
- Paid attraction with a `TICKET_TYPE` spec: `specId/specName/price` come from the
  lowest-priced ticket spec; ties are broken by `specId` for determinism.
- Paid attraction with no ticket spec: `specId=null`, `specName=null`, `price=null`,
  `available=null`; it remains display-only and cannot be bound to inventory.
- `available` is read from the inventory unit for `(resourceId, date, specId)` when
  `date` is present. If there is no unit, it is `0`. Without `date`, it is `null`.
- `coordinateStatus=PENDING` is returned with `latitude=null` and `longitude=null`.
- No `orderEntryAllowed` flag is added to this internal endpoint. The accepted
  `/resources/search` remains the sole place that exposes that UI gate, and it stays
  false for ATTRACTION until V2-11.

## 4. Sorting and paging

Stable sort order:

1. A-level descending: `5A > 4A > 3A > 2A > 1A > null`.
2. `ticketPrice` ascending; null last.
3. `name` ascending.
4. `resourceId` ascending as the final deterministic tie-breaker.

Paging is applied after sorting. `total` is the number of matching on-shelf attractions
before paging. `hasMore = offset + items.size() < total`.

`limit` outside `1..50` or a negative `offset` returns `400` with a clear message.

## 5. Read-only and guard proof

The new query path must not:

- call `inventoryService.lock`,
- call `InventoryLockRedis.tryLock` or `release`,
- write `inventory_lock`, `inventory_unit`, or outbox rows,
- emit `InventoryLocked/InventoryReleased/InventorySoldOut`.

It may read the Redis live counter in the same way `/inventory/candidates` does.
The existing `InventoryService.lock` guard remains unchanged:

```text
resource.type == ATTRACTION -> BusinessException(CONFLICT)
```

Acceptance will call `POST /inventory/locks` with a real seeded attraction and prove
`409` plus unchanged `inventory_lock` count.

## 6. Regression contract for existing endpoints

Captured baseline on 2026-10-17 against the seeded database:

| Request | Current result to preserve |
|---|---|
| `/resources/search?destination=云南&date=2026-10-18` | total 18: ATTRACTION 6, CONTENT 9, TRADABLE 3 |
| `/resources/search?type=TRADABLE&destination=云南&date=2026-10-18` | total 3; order entry true for the three on-shelf tradable rows |
| `/resources/search?type=ATTRACTION&destination=云南&date=2026-10-18` | total 6; all `orderEntryAllowed=false` |
| `/inventory/candidates?destination=云南&date=2026-10-18` | 4 candidates: `r-attraction-1`, `r-hotel-1`, `r-off-shelf-1`, `r-train-1`; spec counts 2/2/1/2 |

The `/inventory/candidates` result intentionally includes the existing off-shelf
`r-off-shelf-1` row. V2-06 will not "fix" that behavior because it is outside this task
and changing it would alter ai-planning's current candidate semantics.

Regression proof:

- `ResourceSearchServiceTest` continues to pass unchanged.
- Add a candidates regression test that locks the current `TRADABLE` + exact destination +
  non-empty specs behavior, including the off-shelf row.
- Real HTTP before/after comparison of the four baseline requests, with identical IDs,
  counts, statuses, and order-entry flags.

## 7. Implementation file plan

- `services/inventory/src/main/java/com/travelplatform/inventory/api/InventoryController.java`
  - add `GET /inventory/attractions`, request validation, and response wiring.
- `services/inventory/src/main/java/com/travelplatform/inventory/application/InventoryService.java`
  - add the read-only `attractions(...)` query; reuse existing repositories and Redis reads.
- `services/inventory/src/main/java/com/travelplatform/inventory/api/AttractionToolResponse.java`
  - new response DTOs (`AttractionItem`, `AttractionPage`); keep `ResourceResponse` unchanged.
- `services/inventory/src/test/java/com/travelplatform/inventory/application/AttractionToolQueryTest.java`
  - filtering, free/paid, spec selection, coordinates, sorting, paging, no-lock interactions.
- `services/inventory/src/test/java/com/travelplatform/inventory/api/AttractionToolControllerTest.java`
  - `200` payload, missing-location `400`, invalid limit/offset `400`.
- `services/inventory/src/test/java/com/travelplatform/inventory/application/InventoryServiceTest.java`
  - add the candidates regression test and keep the existing ATTRACTION lock guard test.
- `specs/002-ai-travel-platform-v2/contracts/inventory.md`
  - finalize the endpoint parameters, response, sorting, paging, free/paid rules, and guard note.
- `specs/002-ai-travel-platform-v2/v2-06-acceptance.md`
  - capture the real HTTP and regression evidence after implementation.

## 8. Acceptance evidence matrix

Real HTTP through gateway with an authenticated USER token:

| Query | Expected |
|---|---|
| `GET /inventory/attractions?city=丽江&date=2026-10-18` | 2 rows; free `丽江古城` has `specId=null`, `price=0`; paid `玉龙雪山` has a non-null `specId`, `price=100`, `available=200` |
| `GET /inventory/attractions?city=西安&date=2026-10-18` | 4 rows; fields include name/level/honorTitle/ticketPrice/coordinates/specId; level 5A rows precede 4A |
| `GET /inventory/attractions?province=云南省&date=2026-10-18` | 6 rows; total/sorting/paging fields present |
| `GET /inventory/attractions?province=云南省&limit=2` | 2 items, `total=6`, `hasMore=true` |
| `GET /inventory/attractions?province=云南省&offset=5&limit=2` | 1 item, `hasMore=false` |
| `GET /inventory/attractions?city=呼和浩特` | at least one `coordinateStatus=PENDING` row with null coordinates |
| `GET /inventory/attractions` | `400` missing location filter |
| `POST /inventory/locks` with a seeded ATTRACTION | `409`; `inventory_lock` count unchanged |
| Four baseline `/resources/search` + `/inventory/candidates` queries | identical to the captured baseline |

Additional checks:

- `mvn -pl services/inventory -am test` all green.
- fat jar contains `JarLauncher`; inventory starts and registers in Nacos.
- no `ai-planning` source or pom diff in the V2-06 commit.

## 9. Approval points

Before coding, confirm these proposed decisions:

1. New `/inventory/attractions` endpoint rather than extending `/resources/search`.
2. Require at least one of `destination/province/city`; no unbounded all-resource query.
3. `limit` default 20/max 50 plus `offset` pagination.
4. `date` optional; without it `available=null`, with it availability is date-scoped.
5. Paid spec selection is the lowest-priced `TICKET_TYPE` (then `specId` tie-break).
6. Keep `/inventory/candidates` exactly as-is, including its current off-shelf inclusion.
7. V2-06 stays inventory-only; ai-planning Feign/tool work starts in V2-07.
