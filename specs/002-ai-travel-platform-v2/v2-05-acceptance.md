# V2-05 Acceptance Evidence

**Date**: 2026-10-17

**Status**: PASS, awaiting user acceptance. T051–T062 remain unchecked.

## 1. Scope

This round implements only the manual scenic-spot seed pipeline in `inventory`.

- No `ai-planning` source or pom change: its existing RestTemplate chain remains untouched.
- No ATTRACTION trading is opened; `orderEntryAllowed` remains false.
- No scheduler was added; the only trigger is the administrator endpoint.

Implementation files:

- `services/inventory/src/main/resources/db/migration/V4__scenic_seed_run.sql`
- `services/inventory/src/main/java/com/travelplatform/inventory/application/seed/ProvinceCatalog.java`
- `services/inventory/src/main/java/com/travelplatform/inventory/application/seed/SeedDtos.java`
- `services/inventory/src/main/java/com/travelplatform/inventory/application/seed/ScenicSpotValidator.java`
- `services/inventory/src/main/java/com/travelplatform/inventory/application/seed/ScenicSpotStore.java`
- `services/inventory/src/main/java/com/travelplatform/inventory/application/seed/ScenicSpotSeedService.java`
- `services/inventory/src/main/java/com/travelplatform/inventory/application/seed/ScenicSeedRunRecovery.java`
- `services/inventory/src/main/java/com/travelplatform/inventory/infrastructure/DeepSeekScenicSpotClient.java`
- `services/inventory/src/main/java/com/travelplatform/inventory/infrastructure/AmapGeocodingClient.java`
- `services/inventory/src/main/java/com/travelplatform/inventory/api/AdminSeedController.java`
- `services/inventory/src/main/java/com/travelplatform/inventory/domain/ScenicSeedRun.java`
- `services/inventory/src/main/java/com/travelplatform/inventory/domain/ScenicSeedRunRepository.java`
- `services/inventory/src/main/java/com/travelplatform/inventory/domain/ScenicSeedRunStatus.java`
- `services/inventory/src/main/java/com/travelplatform/inventory/domain/ResourceRepository.java`
- `services/inventory/src/main/resources/application.yml`
- `services/inventory/pom.xml`
- `.env.example`

## 2. Approved rule adjustments

| Rule | Implementation |
|---|---|
| `honorTitle` nullable | Blank or missing is stored as NULL; a present value must be at most 10 characters. |
| `level` nullable | Missing or blank is stored as NULL; present values must be `1A`–`5A`. |
| Zombie RUNNING runs | `ScenicSeedRunRecovery` marks leftover RUNNING rows FAILED with `interrupted by service restart` on startup. |
| Historical PENDING count | Report field `historicalPendingCoordinates` is the total PENDING ATTRACTION count when the report is completed. It is informational only; no automatic coordinate compensation exists. |

Other fixed rules:

- 34 provinces, 6 provinces per AI batch (last batch 4), 6 attractions per province.
- JSON mapping failure retries the whole batch exactly once (maximum 2 LLM calls).
- Invalid individual records are dropped; valid records in the same batch still write.
- AMap geocoding is sequential, uses the Web Service key from the environment, and stores PENDING without blocking insertion when it fails or returns no result.
- Upsert skip key is exactly `(name, city, type=ATTRACTION)`. Existing rows are not updated.
- New rows use `type=ATTRACTION`, `seed_managed=1`, `image_url=NULL`, and `ticket_price=0` for free attractions.
- Paid attractions receive one adult `TICKET_TYPE` spec and 365 daily inventory rows; free attractions receive no spec or inventory.

## 3. Gateway administrator API

The administrator token was obtained from `/auth/login` with `role=ADMIN`. The request went
through gateway port 8080 to inventory port 8082.

| Request | Result |
|---|---|
| `POST /admin/seed/scenic-spots` (Yunnan only) | `202`, `runId=f142ad20-7193-4729-aab5-f09acb32ea9b` |
| `GET /admin/seed/scenic-spots/{runId}` | `200`, report returned |
| `POST /admin/seed/scenic-spots` (all provinces) | `202`, `runId=ae212062-d9d0-4e47-a7cf-d099d6f82077` |
| Anonymous `POST /admin/seed/scenic-spots` | `401`, 0-byte body |
| USER token `POST /admin/seed/scenic-spots` | `404`, 0-byte body |
| ADMIN token `POST /admin/seed/scenic-spots` | `202` |

Yunnan-only report:

```json
{
  "status": "SUCCEEDED",
  "summary": {
    "provinces": 1,
    "batches": 1,
    "generated": 6,
    "inserted": 6,
    "skippedExisting": 0,
    "skippedInvalid": 0,
    "failed": 0,
    "coordinateReady": 6,
    "coordinatePending": 0,
    "historicalPendingCoordinates": 0
  }
}
```

Full national report:

```json
{
  "status": "SUCCEEDED",
  "summary": {
    "provinces": 34,
    "batches": 6,
    "generated": 204,
    "inserted": 198,
    "skippedExisting": 6,
    "skippedInvalid": 0,
    "failed": 0,
    "coordinateReady": 190,
    "coordinatePending": 8,
    "historicalPendingCoordinates": 8
  }
}
```

All six batches completed on attempt 1 with `mappingFailed=false`. The full report is stored
in `scenic_seed_run.report_json` for run `ae212062-d9d0-4e47-a7cf-d099d6f82077`.

## 4. Database result

Current `resource` totals:

| Metric | Value |
|---|---:|
| ATTRACTION rows | 204 |
| Provinces with 6 rows | 34 |
| Free attractions (`ticket_price=0`) | 29 |
| Paid attractions | 175 |
| Coordinate READY | 196 |
| Coordinate PENDING | 8 |
| Duplicate `(name,city,type)` groups | 0 |
| ATTRACTION rows with `image_url` set | 0 |

The eight PENDING rows are:

`内蒙古/呼和浩特/大召寺`、`台湾/花莲/太鲁阁峡谷`、`宁夏/银川/西夏王陵`、
`新疆/昌吉/天山天池`、`江苏/南京/中山陵`、`浙江/嘉兴/乌镇古镇`、
`青海/海北/祁连山草原`、`黑龙江/牡丹江/镜泊湖景区`。

Yunnan rows demonstrate the store rules:

| Name | City | Level | Honor title | Price | Coordinate | seed_managed | image_url |
|---|---|---|---:|---|---|---|---|
| 丽江古城 | 丽江 | 5A | 世界文化遗产 | 0 | READY | 1 | NULL |
| 大理古城 | 大理 | 4A | 历史文化名城 | 0 | READY | 1 | NULL |
| 崇圣寺三塔 | 大理 | 5A | 全国重点文保 | 75 | READY | 1 | NULL |
| 玉龙雪山 | 丽江 | 5A | 国家地质公园 | 100 | READY | 1 | NULL |
| 石林风景区 | 昆明 | 5A | 世界地质公园 | 130 | READY | 1 | NULL |
| 西双版纳热带植物园 | 西双版纳 | 5A | 国家科普基地 | 80 | READY | 1 | NULL |

Ticket model totals for ATTRACTION rows:

- 175 paid resources -> 175 adult ticket specs -> 63,875 inventory rows (175 × 365).
- 29 free resources -> 0 specs and 0 inventory rows.

The v1 compatibility boundary is intact:

- `r-attraction-1` remains `TRADABLE`, `云南/丽江`.
- `load-test-resource` remains `TRADABLE`, `province=NULL`, `city=NULL`.
- `玉龙雪山/丽江` now has both a v1 `TRADABLE` row and a seeded `ATTRACTION` row,
  proving the three-column key permits coexistence without overwriting v1.

## 5. Rerun and manual-edit protection

Rerunning Yunnan (`31f02ddc-06b9-41e6-83e6-e5622de9eb5c`):

```json
{
  "generated": 6,
  "inserted": 0,
  "skippedExisting": 6,
  "skippedInvalid": 0,
  "failed": 0
}
```

A manual `image_url` was written to the seeded `玉龙雪山/丽江` row before the rerun.
After the rerun the value was still present; it was then reset to NULL (1 affected row)
so no example URL remains in the database.

The full national rerun would therefore skip all 204 existing rows; the Yunnan rerun is
the required same-province skip evidence.

## 6. Zombie RUNNING recovery

A synthetic row `stale-run-v205-acceptance` was inserted with `status=RUNNING`.

1. `POST /admin/seed/scenic-spots` returned `409` with
   `a scenic seed run is already running`.
2. inventory was stopped and restarted.
3. Startup log:
   `marked interrupted scenic seed run stale-run-v205-acceptance as FAILED`.
4. Database row became `FAILED` with `interrupted by service restart`.
5. The next POST returned `202`, and run `6eaa9afb-c792-46e9-9f77-3e5c431abf75`
   completed `SUCCEEDED` with `inserted=0`, `skippedExisting=6`.

The synthetic row was deleted afterward (1 row); only real seed runs remain.

## 7. Search and 16-city coverage

After the full import, ATTRACTION search through gateway returns:

- `destination=云南` -> 6 rows.
- `destination=丽江` -> 2 rows.
- Anonymous access remains 401; USER access to admin remains hidden as 404.

All 16 fixed hot regions have at least one on-shelf ATTRACTION:

| City | Count | City | Count |
|---|---:|---|---:|
| 北京 | 6 | 上海 | 6 |
| 西安 | 4 | 成都 | 2 |
| 重庆 | 6 | 杭州 | 2 |
| 苏州 | 2 | 南京 | 1 |
| 厦门 | 1 | 青岛 | 1 |
| 长沙 | 1 | 武汉 | 2 |
| 广州 | 2 | 桂林 | 4 |
| 丽江 | 2 | 三亚 | 3 |

V2-04's `orderEntryAllowed=false` guard for ATTRACTION is unchanged.

## 8. Build, tests and operations

- `mvn -pl services/inventory -am test`: common 5 + inventory 57 tests, 0 failures.
- New tests cover nullable level/title, required-field drops, 6-per-province cap,
  retry cap, insert-or-skip, free/paid ticket models, AMap success/zero-result/error
  degradation, zombie recovery, and admin 202/404 behavior.
- Fat jar contains `org/springframework/boot/loader/launch/JarLauncher.class` and
  Spring AI 1.0.9 model artifacts.
- Nacos reports `inventory` at `192.168.150.1:8082`, `healthy=true`.
- The AMap Web Service key, JS key and DeepSeek key occur 0 times in `logs/*.log`.
- `.env` remains untracked; `.env.example` contains names only.

## 9. Cleanup

- 4 temporary acceptance users deleted from `travel_user_auth.app_user`.
- 1 synthetic stale seed run deleted from `scenic_seed_run`.
- 1 temporary manual image URL reset to NULL.
- 4 real SUCCEEDED seed runs remain for audit:
  `f142ad20-...` (Yunnan), `31f02ddc-...` (Yunnan rerun),
  `6eaa9afb-...` (post-restart rerun), `ae212062-...` (national import).

## 10. Remaining boundary

No business code outside V2-05 was changed. T051–T062 are intentionally left unchecked
until this report is accepted. The eight PENDING coordinates are reported but are not
automatically repaired; deleting the row and rerunning the province is the agreed manual
remedy.
