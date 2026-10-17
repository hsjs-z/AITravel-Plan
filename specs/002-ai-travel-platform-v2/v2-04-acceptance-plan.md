# V2-04 Acceptance Plan: ATTRACTION Resource Model

**Date**: 2026-10-17

**Status**: Approved with four revisions; implementation starts after this plan.

## 1. Type and compatibility

- `resource.type` becomes `enum('TRADABLE','CONTENT','ATTRACTION')`.
- Existing rows keep their current type. In particular, `r-attraction-1`
  remains `TRADABLE`; V2-04 must not rewrite it to `ATTRACTION`.
- V2-04 only stores and displays `ATTRACTION`; it does not open trading.
- `ATTRACTION` responses use:
  - `tradable=true` — a ticket model may exist;
  - `orderEntryAllowed=false` — the only field the front end may use to show
    a booking entry.
- V2-11 opens ticket trading and replaces the temporary lock guard.

## 2. Unique key

Final key: `uk_resource_name_city_type (name, city, type)`.

Rationale:

- Existing `(玉龙雪山, 丽江, TRADABLE)` must not block the v2 seed from
  inserting `(玉龙雪山, 丽江, ATTRACTION)`.
- `(name, city, type)` preserves idempotency for repeated seeds while keeping
  legacy and v2 representations separate.
- MySQL unique indexes allow multiple NULL `city` values, so legacy rows without
  city data remain valid.

V2-05 skip rule is therefore:

> skip only when the same `(name, city, type)` already exists.

## 3. New columns and nullability

Migration file:

`services/inventory/src/main/resources/db/migration/V3__resource_attraction_v2.sql`

- `province varchar(64) NULL`
- `city varchar(64) NULL`
- `level varchar(8) NULL`
- `honor_title varchar(10) NULL`
- `summary varchar(512) NULL`
- `suggested_hours decimal(4,1) NULL`
- `ticket_price bigint NOT NULL DEFAULT 0`
- `latitude decimal(10,7) NULL`
- `longitude decimal(10,7) NULL`
- `coordinate_status enum('READY','PENDING') NOT NULL DEFAULT 'PENDING'`
- `image_url varchar(512) NULL`
- `seed_managed bit(1) NOT NULL DEFAULT 0`
- `seeded_at datetime(6) NULL`

Existing rows receive NULL/default values and keep current behavior.
`ticket_price=0` means free; no separate free flag.

## 4. Migration order and rerun safety

MySQL DDL is not transactional. The migration order is:

1. Add nullable/defaulted columns and extend the enum.
2. Backfill `province/city` for known v1 rows.
3. Add indexes, including the unique key, last.

Before and after migration, run:

```sql
SELECT name, city, COUNT(*)
FROM resource
GROUP BY name, city
HAVING COUNT(*) > 1;
```

Expected: empty result. The unique key is deliberately added last so a duplicate
check failure is explicit and the migration can be corrected safely.

## 5. Legacy Yunnan backfill

Mapping:

| IDs | province | city |
|---|---|---|
| `r-hotel-1`, `r-attraction-1`, `r-culture-1`, `r-off-shelf-1` | 云南 | 丽江 |
| `r-train-1`, `r-food-3` | 云南 | 昆明 |
| `r-content-1`, `r-content-4` | 云南 | 大理 |
| `r-content-2` | 云南 | 迪庆 |
| `r-food-1` | 云南 | 蒙自 |
| `r-food-2` | 云南 | 建水 |
| `r-culture-2` | 云南 | 丽江 |
| `r-content-3` | 云南 | NULL |
| `r-hotel-2`, `r-attraction-2`, `r-food-4`, `r-food-5`, `r-culture-3`, `r-culture-4`, `r-content-5`, `r-content-6`, `r-content-7` | 北京 | 北京 |

`load-test-resource` is explicitly excluded and keeps `province/city=NULL`.
The migration comment must state that it is a performance-test fixture with the
destination `LOAD-TEST-DESTINATION`, not a tourist resource.

Acceptance:

- `r-attraction-1` remains `TRADABLE` and becomes `(云南, 丽江)`.
- `GET /resources/search?destination=云南` hits Yunnan resources.
- `GET /resources/search?destination=丽江` hits Lijiang resources.
- `type=CONTENT` and `type=TRADABLE` results remain unchanged.
- `load-test-resource` remains `province=NULL`, `city=NULL`.

## 6. Trading and inventory impact

- V2-04 adds an explicit temporary guard in `InventoryService.lock`:
  `ATTRACTION` is rejected before Redis Lua execution.
- Existing `TRADABLE` hotel/ticket/train locking remains unchanged.
- `InventoryService.candidates` remains `TRADABLE`-only.
- V2-05 may seed `ATTRACTION` ticket specs and inventory, but the temporary guard
  blocks trading.
- V2-06 adds read-only attraction search/tool support.
- V2-11 removes the temporary guard and replaces it with ticket-only validation:
  ATTRACTION + TICKET_TYPE + inventory may lock; free attractions stay
  `orderEntryAllowed=false`.

## 7. Regression

- Full inventory test suite.
- Existing `TRADABLE/CONTENT` search and detail behavior.
- New tests:
  - attraction enum/model and nullable fields;
  - attraction search and `tradable=true/orderEntryAllowed=false`;
  - legacy province/city backfill and Lijiang city match;
  - `ATTRACTION` lock rejection while `TRADABLE` lock still works.
- If `InventoryService.lock` changes, run a v1 TRADABLE booking smoke to confirm
  `BOOKED/PAID` remains intact.
