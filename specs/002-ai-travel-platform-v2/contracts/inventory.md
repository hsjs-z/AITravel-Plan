# inventory v2

## Resource type

`TRADABLE | CONTENT | ATTRACTION`

`ATTRACTION` 是 v2 的一级资源类型；付费景区拥有默认 `TICKET_TYPE` 规格。

## GET /resources/search

参数保持 v1，并支持：

- `type=ATTRACTION`
- `destination=西安`（城市匹配）
- `destination=陕西`（省级匹配）
- `date=2026-10-20`

响应 `Summary` 增加：

```json
{
  "id": "r-...",
  "type": "ATTRACTION",
  "name": "秦始皇兵马俑",
  "destination": "陕西",
  "province": "陕西",
  "city": "西安",
  "level": "5A",
  "honorTitle": "世界第八大奇迹",
  "summary": "...",
  "suggestedHours": 4.0,
  "ticketPrice": 120,
  "imageUrl": null,
  "latitude": 34.384,
  "longitude": 109.278,
  "coordinateStatus": "READY",
  "tradable": true,
  "orderEntryAllowed": false,
  "priceFrom": 120,
  "priceTo": 120,
  "contentCategory": "ATTRACTION"
}
```

上例的 `orderEntryAllowed=false` 是 V2-06 的实际状态；只有到 V2-11 打开门票交易后，
付费且存在可售库存的 ATTRACTION 才可能变为 `true`。

免费景区：

- `ticketPrice=0`
- `priceLabel`/前端显示“免费”
- `orderEntryAllowed=false`
- 但可以加入行程，不进入支付。

`ATTRACTION` 的 `tradable` 为 true（它不是 `CONTENT`），但只有付费且存在
可售门票库存时 `orderEntryAllowed=true`。免费景区仍可被行程页选择和加入。

### V2-04 staged semantics

- V2-04 只建立 `ATTRACTION` 资源模型，不打开交易。
- V2-04 的 `ATTRACTION` 同时返回 `tradable=true` 与 `orderEntryAllowed=false`。
- `tradable=true` 仅表示该资源存在票务模型；前端必须以下单门禁
  `orderEntryAllowed` 为准，不得仅凭 `tradable` 展示下单入口。
- V2-11 打开门票交易后，付费且有库存的 ATTRACTION 才允许
  `orderEntryAllowed=true`；免费景区仍为 false。

未匹配景区不来自本接口；由 AI 生成并在规划结果中标记。

## GET /resources/{resourceId}

在 v1 字段上增加：

- `province/city/level/honorTitle/summary/suggestedHours/ticketPrice`
- `latitude/longitude/coordinateStatus/imageUrl`
- `matched` 不需要在资源详情中返回；由规划结果决定。

## GET /inventory/attractions

内部只读工具查询，供 ai-planning 的 `searchAttractions` 使用。该端点不打开下单入口，
不改变 `POST /inventory/locks` 的 ATTRACTION `409` 守卫。

Query:

```text
destination=西安&province=陕西省&city=西安&date=2026-10-20&limit=20&offset=0
```

参数：

| 参数 | 必填 | 默认 | 语义 |
|---|---|---|---|
| `destination` | 位置过滤三选一 | - | 对 `destination/province/city` 做大小写不敏感包含匹配 |
| `province` | 位置过滤三选一 | - | 只对 `province` 做大小写不敏感包含匹配 |
| `city` | 位置过滤三选一 | - | 只对 `city` 做大小写不敏感包含匹配 |
| `date` | 否 | - | ISO 日期；传入时返回日期维度的 `available`，不传时为 `null` |
| `limit` | 否 | `20` | 每页数量，范围 `1..50` |
| `offset` | 否 | `0` | 非负偏移 |

**多参数语义（MUST）**：`destination`、`province`、`city` 同时提供时为 AND 组合，
所有条件都必须满足才命中。三者至少提供一个，否则返回 `400`。

Response:

```json
{
  "total": 1,
  "offset": 0,
  "limit": 20,
  "hasMore": false,
  "items": [
    {
      "resourceId": "r-...",
      "name": "秦始皇兵马俑",
      "province": "陕西省",
      "city": "西安",
      "level": "5A",
      "honorTitle": "世界第八大奇迹",
      "summary": "秦始皇陵陪葬坑，世界文化遗产。",
      "suggestedHours": 4.0,
      "ticketPrice": 120,
      "free": false,
      "latitude": 34.384,
      "longitude": 109.278,
      "coordinateStatus": "READY",
      "specId": "s-...",
      "specName": "成人票",
      "price": 120,
      "available": 200
    }
  ]
}
```

Rules:

- 只返回 `status=ON_SHELF` 且 `type=ATTRACTION`。
- 排序固定为：级别降序（`5A > 4A > 3A > 2A > 1A > null`），再按
  `ticketPrice` 升序（免费在前、null 最后），再按 `name`、`resourceId` 升序。
- 分页在排序后执行；`total` 是分页前命中数，`hasMore = offset + items.size() < total`。
- 付费景区返回最低价的 `TICKET_TYPE` 规格；价格相同按 `specId` 升序取第一个。
- 免费景区返回 `specId=null`、`specName=null`、`price=0`、`available=null`；
  `ticketPrice=0`、`free=true`。
- 付费但没有 `TICKET_TYPE` 规格时，`specId/specName/price/available` 均为 `null`，
  该资源只能展示，不能绑定库存。
- 坐标待补时 `latitude/longitude=null`。
- `date` 缺省时 `available=null`；传入时按 `(resourceId, date, specId)` 读取库存，
  无库存行时为 `0`。
- 不支持未匹配资源。
- 该端点只读：不得调用锁定/释放、不得写 `inventory_lock/inventory_unit/outbox`。

## Ticket-only lock guard

`POST /inventory/locks` 增加服务端校验：

- resource 必须是 `ATTRACTION`。
- spec 必须是 `TICKET_TYPE`。
- 否则返回业务错误，不执行 Redis Lua 扣减。

该守卫不改变资源+日期+规格三维锁定契约，也不改变释放幂等。
