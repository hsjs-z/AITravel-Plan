# V2-06 Acceptance Evidence

**Date**: 2026-10-17

**Status**: PASS, awaiting user acceptance. T018 remains unchecked; T019/T020 stay in V2-07.

## 1. Scope

V2-06 adds the read-only attraction query used by the future planner tool.

Changed files:

- `services/inventory/src/main/java/com/travelplatform/inventory/api/InventoryController.java`
- `services/inventory/src/main/java/com/travelplatform/inventory/api/AttractionToolResponse.java`
- `services/inventory/src/main/java/com/travelplatform/inventory/application/InventoryService.java`
- `services/inventory/src/test/java/com/travelplatform/inventory/api/AttractionToolControllerTest.java`
- `services/inventory/src/test/java/com/travelplatform/inventory/application/AttractionToolQueryTest.java`
- `services/inventory/src/test/java/com/travelplatform/inventory/application/InventoryServiceTest.java`
- `specs/002-ai-travel-platform-v2/contracts/inventory.md`
- `specs/002-ai-travel-platform-v2/tasks.md` (T075 records the V2-11 candidates debt)

No `ai-planning` source or pom change was made.

## 2. Endpoint decision

The new endpoint is:

```text
GET /inventory/attractions
```

It is separate from `/resources/search` because `/resources/search` is a v1
user-facing browsing contract, while this endpoint returns internal `specId` and
date-scoped inventory data for the planner. The existing contract already named this
endpoint, so no public browsing contract was widened.

The endpoint requires at least one of `destination`, `province`, `city`. When more
than one is supplied they are ANDed. Parameters:

| Parameter | Result |
|---|---|
| `destination` | case-insensitive contains against destination/province/city |
| `province` | case-insensitive contains against province |
| `city` | case-insensitive contains against city |
| `date` | optional; date-scoped availability |
| `limit` | default 20, allowed 1..50 |
| `offset` | default 0, non-negative |

`limit` and `offset` are applied after sorting. The response includes
`total/offset/limit/hasMore/items`.

## 3. Sorting, paging and field semantics

Sorting:

1. `5A > 4A > 3A > 2A > 1A > null`
2. `ticketPrice` ascending (free first, null last)
3. `name` ascending
4. `resourceId` ascending

Free attraction:

- `free=true`, `ticketPrice=0`
- `specId=null`, `specName=null`, `price=0`, `available=null`
- still returned and eligible for itinerary display

Paid attraction:

- lowest-priced `TICKET_TYPE` spec; `specId` tie-break is deterministic
- `price` is the spec price; `available` is read for the requested date
- paid with no ticket spec returns `specId/price/available=null` and is display-only

`coordinateStatus=PENDING` returns null latitude/longitude. No `orderEntryAllowed`
flag was added to this internal endpoint; `/resources/search` remains the UI gate and
continues to return false for ATTRACTION.

## 4. Real HTTP evidence through gateway

An authenticated USER token was used against port 8080; inventory is on 8082.

### Lijiang city

`GET /inventory/attractions?city=丽江&date=2026-10-18` -> `200`, `total=2`.

| Name | Free | ticketPrice | specId | price | available | coordinate |
|---|---|---:|---|---:|---:|---|
| 丽江古城 | true | 0 | `null` | 0 | `null` | READY / 26.876468,100.225830 |
| 玉龙雪山 | false | 100 | `s-ticket-023d698c-7c7d-4b13-804e-081e9dce954c` | 100 | 200 | READY / 27.096143,100.179253 |

### Xi'an city

`GET /inventory/attractions?city=西安&date=2026-10-18` -> `200`, `total=4`.

Items: 大雁塔、西安城墙、华清宫、秦始皇兵马俑博物馆. All four are `5A`; the
response includes name/level/honorTitle/ticketPrice/coordinates/specId and the
5A rows precede any lower-level rows.

### Yunnan province and paging

`GET /inventory/attractions?province=云南省&date=2026-10-18` -> `200`, `total=6`.

Observed order:

`丽江古城 > 崇圣寺三塔 > 西双版纳热带植物园 > 玉龙雪山 > 石林风景区 > 大理古城`

`limit=2&offset=0` -> 2 items, `total=6`, `hasMore=true`.

`limit=2&offset=5` -> 1 item, `total=6`, `hasMore=false`.

### AND semantics

`destination=云南&province=云南省&city=丽江` -> `total=2`.

`province=云南省&city=西安` -> `total=0`.

`destination=云南&province=云南省&city=西安` -> `total=0`.

This proves the three location filters are conjunctive, not alternatives.

### PENDING coordinate

`GET /inventory/attractions?city=呼和浩特` -> `200`, `total=1`.

| Name | coordinateStatus | latitude | longitude |
|---|---|---|---|
| 大召寺 | PENDING | `null` | `null` |

### Validation

| Request | Result |
|---|---|
| `GET /inventory/attractions` | `400` `{"code":40000,"message":"at least one of destination, province, city is required"}` |
| `GET /inventory/attractions?province=云南省&limit=51` | `400` `{"code":40000,"message":"limit must be between 1 and 50"}` |

## 5. Lock guard and read-only proof

The request used the real seeded resource `玉龙雪山` and its ticket spec.

```json
{
  "resourceId": "r-attraction-1565d961-8d22-40e4-9c39-efa3c7cb033d",
  "date": "2026-10-18",
  "specId": "s-ticket-023d698c-7c7d-4b13-804e-081e9dce954c",
  "quantity": 1,
  "purpose": "BOOKING"
}
```

`POST /inventory/locks` -> `409`:

```json
{"code":40900,"message":"attraction trading is not enabled until V2-11","data":null}
```

`inventory_lock` rows: 153 before, 153 after. No Redis stock lock or outbox row
was created by the new query.

Unit tests also verify that the attraction query never touches
`InventoryLockRepository`, `InventoryUnitPersister`, `InventoryEventPublisher`,
`InventoryLockRedis.tryLock`, or `InventoryLockRedis.release`.

## 6. Existing endpoint regression

Baseline captured before V2-06 code and rechecked after the new jar:

| Request | Before | After |
|---|---|---|
| `/resources/search?destination=云南&date=2026-10-18` | total 18: ATTRACTION 6, CONTENT 9, TRADABLE 3 | identical |
| `/resources/search?type=TRADABLE&destination=云南&date=2026-10-18` | total 3; all orderEntryAllowed=true | identical |
| `/resources/search?type=ATTRACTION&destination=云南&date=2026-10-18` | total 6; all orderEntryAllowed=false | identical |
| `/inventory/candidates?destination=云南&date=2026-10-18` | 4: `r-attraction-1`, `r-hotel-1`, `r-off-shelf-1`, `r-train-1`; specs 2/2/1/2 | identical |

The off-shelf candidate remains in `/inventory/candidates`. This is intentional
V2-06 preservation of v1 behavior and is recorded as T075 in the V2-11 ticket-trading
scope.

## 7. Tests and operations

- `mvn -pl services/inventory -am test`: 68 inventory tests, 0 failures; common 5 tests.
- New tests cover AND filters, free/paid behavior, lowest ticket spec, date-scoped
  availability, PENDING coordinates, sorting, paging, missing/invalid parameters,
  controller 200/400, and no lock/event interactions.
- One initial full-suite run had a transient load-test p95 of 1029ms (threshold 1000ms);
  an isolated rerun passed at 810ms and the subsequent full suite passed at 792.9ms.
  The threshold and load-test code were not changed.
- Fat jar contains `JarLauncher` and `AttractionToolResponse` classes.
- Inventory is healthy and registered in Nacos at `192.168.150.1:8082`.
- Temporary acceptance users were deleted; no test data was added.

## 8. Boundary

T018 remains unchecked until this report is accepted. T019/T020 (Feign client and
Spring AI `searchAttractions` tool) remain V2-07 work. T075 remains an open V2-11
task for the candidates OFF_SHELF filter.
