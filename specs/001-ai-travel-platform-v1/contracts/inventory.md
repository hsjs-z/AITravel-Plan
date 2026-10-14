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

事件：`InventoryLocked` / `InventoryReleased` / `InventorySoldOut`。
