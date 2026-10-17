# V2-04 Acceptance Evidence: ATTRACTION Resource Model

**Date**: 2026-10-17

**Status**: PASS, awaiting user acceptance. V2-04 tasks remain unchecked.

## 1. Migration

- Flyway applied `V3__resource_attraction_v2.sql` successfully.
- `resource.type` is:
  `enum('TRADABLE','CONTENT','ATTRACTION')`.
- Added columns:
  `province/city/level/honor_title/summary/suggested_hours/ticket_price/`
  `latitude/longitude/coordinate_status/image_url/seed_managed/seeded_at`.
- Unique key: `uk_resource_name_city_type (name, city, type)`.
- Index: `idx_resource_province_city_status (province, city, status)`.

Migration order was: add columns -> backfill -> unique/index last.
The V3 script was manually executed a second time after migration and completed
without errors or duplicate changes.

## 2. Conflict pre-check

Before migration, `city` did not exist yet. The equivalent pre-check used
`(name, destination)` and returned no duplicate rows.

After migration:

```sql
SELECT name, city, COUNT(*) FROM resource
GROUP BY name, city HAVING COUNT(*) > 1;
```

Result: empty.

```sql
SELECT name, city, type, COUNT(*) FROM resource
GROUP BY name, city, type HAVING COUNT(*) > 1;
```

Result: empty.

## 3. Legacy backfill

| id | type | province | city |
|---|---|---|---|
| `r-attraction-1` | `TRADABLE` | 云南 | 丽江 |
| `load-test-resource` | `TRADABLE` | NULL | NULL |

`r-attraction-1` was not converted to `ATTRACTION`; its v1 ticket/inventory
behavior remains intact. `load-test-resource` is explicitly excluded from the
destination backfill and remains a performance-test fixture.

## 4. Real HTTP search

Inventory service was started with the new jar.

- `GET /resources/search?destination=云南`: 12 items.
- `GET /resources/search?destination=丽江`: 4 items.
- `GET /resources/search?type=CONTENT&destination=丽江`: 2 content items only.
- `GET /resources/search?type=TRADABLE&destination=丽江`: 2 TRADABLE items only.
- `GET /resources/r-attraction-1?date=2026-10-20`: `TRADABLE`,
  `orderEntryAllowed=true`, two ticket specs.

## 5. ATTRACTION staged behavior

A temporary ATTRACTION row was inserted for smoke verification, then deleted.

Search response:

- `type=ATTRACTION`
- `tradable=true`
- `orderEntryAllowed=false`
- `contentCategory=ATTRACTION`
- new address/metadata fields were returned

Inventory lock:

```json
{"code":40900,"message":"attraction trading is not enabled until V2-11","data":null}
```

The temporary row was deleted; row count returned to 23.

## 6. TRADABLE regression

An existing TRADABLE ticket lock was created and released through HTTP:

- lock returned a lockId;
- release returned `released=true`;
- no Redis/MySQL lock behavior changed for v1 resources.

## 7. Tests

- `mvn -pl services/inventory -am test`: 30 tests, 0 failures.
- Inventory test coverage includes:
  - concurrency and no-oversell;
  - idempotent release;
  - outbox rollback;
  - legacy resource search/detail;
  - ATTRACTION metadata and staged `orderEntryAllowed=false`;
  - ATTRACTION lock rejection;
  - migration/unique-key/backfill checks.
- `SearchLoadTest`: p95 530.4ms, p99 556.7ms, error rate 0.0.
