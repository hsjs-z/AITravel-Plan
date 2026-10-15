# inventory（资源目录与库存服务）

职责：资源（可交易/内容）目录、规格、库存单元、原子锁定。

| 方法 | 路径 | 说明 | 关键请求/响应 |
|------|------|------|----------------|
| GET | /resources/search | 检索资源 | `{type?, destination?, date?, priceMin?, priceMax?, sort?}` → 列表 |
| GET | /resources/{id} | 资源详情 | → 价格/可预订状态/规格；内容资源无下单入口 |
| GET | /inventory/query | 可用库存查询 | `{resourceId, date, specId}` → `{available}` |
| POST | /inventory/locks | 原子锁定 | `{resourceId, date, specId, quantity, purpose, ttlSeconds}` → `{lockId}` |
| POST | /inventory/locks/{lockId}/release | 幂等释放 | → `{released}` |

约束：锁定/释放必须原子（Redis Lua）；重复释放不虚增库存；
`purpose=PLANNING` 短 TTL 软占位、`purpose=BOOKING` 硬占用。

检索语义（US6）：

- 检索只返回 `status=ON_SHELF` 的资源；下架资源不出现在列表，但 `GET /resources/{id}`
  仍如实返回 `OFF_SHELF` 状态且 `orderEntryAllowed=false`（下架 ≠ 售罄）。
- 可预订状态按「资源 + 日期 + 规格」判定：未传 `date` 时 `availabilityKnown=false`、
  `availableTotal=null`、`orderEntryAllowed=false`，并在 `message` 提示需选择日期，
  不得凭空断言可预订。
- `orderEntryAllowed` 是唯一的「是否展示下单入口」开关：内容资源恒为 false（FR-039），
  下架或售罄资源为 false（FR-038）。
- 无匹配结果时返回 `total=0` 且 `message` 携带明确空结果提示（FR-040），不返回静默空白。
- 非法 `type`/`sort`/价格区间返回 400 与明确原因，不做默认值静默兜底。

事件：`InventoryLocked` / `InventoryReleased` / `InventorySoldOut`。
