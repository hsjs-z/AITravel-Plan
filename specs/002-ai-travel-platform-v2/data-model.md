# Data Model: AI 旅行平台 v2

**Feature**: `002-ai-travel-platform-v2`

每个服务仍拥有独立 schema；跨服务只通过标识符引用，不共享表。

## inventory

### resource

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `varchar(255)` | no | 主键 |
| `type` | `enum('TRADABLE','CONTENT','ATTRACTION')` | no | v2 新增 `ATTRACTION` |
| `name` | `varchar(255)` | no | 景区/资源名称 |
| `destination` | `varchar(255)` | no | 省级行政区或旧 v1 目的地 |
| `province` | `varchar(64)` | yes | 省级行政区 |
| `city` | `varchar(64)` | yes | 城市 |
| `level` | `varchar(8)` | yes | `5A/4A/3A/2A/1A` |
| `honor_title` | `varchar(10)` | yes | 核心称号，按 Unicode 字符数校验 ≤10 |
| `summary` | `varchar(512)` | yes | 一句话简介 |
| `suggested_hours` | `decimal(4,1)` | yes | 建议游玩小时 |
| `ticket_price` | `bigint` | no, default 0 | 0 表示免费；展示/参考价 |
| `latitude` | `decimal(10,7)` | yes | GCJ-02 |
| `longitude` | `decimal(10,7)` | yes | GCJ-02 |
| `coordinate_status` | `enum('READY','PENDING')` | no, default `PENDING` | 查不到坐标为 `PENDING` |
| `image_url` | `varchar(512)` | yes | 人工填写；重跑不得覆盖 |
| `seed_managed` | `bit(1)` | no, default 0 | 是否由种子导入创建/管理 |
| `seeded_at` | `datetime(6)` | yes | 最近一次种子写入时间 |
| `attributes` | `text` | yes | 保留旧 JSON；景区写 `category=ATTRACTION` |
| `status` | `enum('ON_SHELF','OFF_SHELF')` | no | 旧语义不变 |

Indexes:

- `UNIQUE KEY uk_resource_name_city (name, city)`
- `KEY idx_resource_province_city_status (province, city, status)`

Validation:

- `level` 只能是五个 A 级值之一。
- `honor_title` 按 code point 计数 ≤ 10。
- `suggested_hours` ∈ [0.5, 12]。
- `ticket_price` ≥ 0。
- `city`/`province` 对种子记录必填。
- `latitude/longitude` 要么同时为空，要么同时有值。

### spec

结构不变。v2 为付费景区创建：

- `spec_type = TICKET_TYPE`
- `name = 成人票`
- `price = resource.ticket_price`

免费景区不创建交易规格；未匹配景区没有 spec。

### inventory_unit

结构不变。v2 为付费景区生成未来 365 天库存单元：

- `total` / `available`：可配置，默认 200
- `locked = 0`
- 唯一性由 `(resource_id, date, spec_id)` 查询保证

### scenic_seed_run

| Column | Type | Notes |
|---|---|---|
| `id` | `varchar(255)` | 主键，runId |
| `status` | `enum('RUNNING','SUCCEEDED','PARTIAL_FAILED','FAILED')` | 运行状态 |
| `requested_by` | `varchar(255)` | 管理员 userId |
| `started_at` | `datetime(6)` | 开始时间 |
| `finished_at` | `datetime(6)` | 结束时间，可空 |
| `report_json` | `longtext` | 批次与记录明细 |

Concurrent rule: 同一时间只允许一个 `RUNNING` 运行；重复触发返回 409。

## ai-planning

### planning_task

新增/变更：

- `pace enum('RELAXED','BALANCED','VIBRANT') NOT NULL DEFAULT 'BALANCED'`
- `budget bigint NULL`
- `preferences text`

`result` 保存归一化 JSON：按天、按时段、带排序和匹配状态。

## itinerary

### itinerary

- `budget bigint NULL`：v2 表单不再要求预算，旧数据保持可读。

### itinerary_item

| Column | Type | Null | Notes |
|---|---|---|---|
| `name` | `varchar(255)` | yes | 景点名；旧数据为空时前端回退 description |
| `description` | `varchar(1024)` | no | 简介/安排文本 |
| `suggested_hours` | `decimal(4,1)` | yes | 建议时长 |
| `time_slot` | `enum('MORNING','AFTERNOON','EVENING')` | yes | v1 旧项可为空 |
| `sort_order` | `int` | no, default 0 | 时段内 1-based |
| `resource_type` | `varchar(255)` | yes | 匹配时 `ATTRACTION` |
| `resource_id` | `varchar(255)` | yes | 未匹配必须为空 |
| `spec_id` | `varchar(255)` | yes | 未匹配/免费可为空 |
| `source` | `enum('AI','MANUAL')` | no | 不变 |

Index:

- `KEY idx_itinerary_item_slot (itinerary_id, day_date, time_slot, sort_order)`

Rules:

- 每行程每日每时段最多 3 个。
- 添加/删除只允许 `DRAFT`。
- 确认/锁定后接口拒绝修改。

## user-auth

### app_user

| Column | Type | Notes |
|---|---|---|
| `id` | `varchar(255)` | 主键 |
| `email` | `varchar(255)` | 唯一 |
| `password_hash` | `varchar(255)` | 只存散列 |
| `phone` | `varchar(32)` | 可空 |
| `role` | `enum('USER','ADMIN')` | 默认 `USER` |
| `created_at` | `datetime(6)` | 注册时间 |

`travel_user_auth` 数据库已由 `deploy/init/01-databases.sql` 创建。

## Cross-service relationships

- `PlanningTask.itineraryId → Itinerary.id`
- `ItineraryItem.resourceId/specId → Resource.id / Spec.id`
- `OrderItem.resourceId/specId/date → InventoryUnit`
- `ScenicSeedRun` 只属于 inventory；不跨服务共享。
