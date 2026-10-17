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
  "orderEntryAllowed": true,
  "priceFrom": 120,
  "priceTo": 120,
  "contentCategory": "ATTRACTION"
}
```

免费景区：

- `ticketPrice=0`
- `priceLabel`/前端显示“免费”
- `orderEntryAllowed=false`
- 但可以加入行程，不进入支付。

`ATTRACTION` 的 `tradable` 为 true（它不是 `CONTENT`），但只有付费且存在
可售门票库存时 `orderEntryAllowed=true`。免费景区仍可被行程页选择和加入。

未匹配景区不来自本接口；由 AI 生成并在规划结果中标记。

## GET /resources/{resourceId}

在 v1 字段上增加：

- `province/city/level/honorTitle/summary/suggestedHours/ticketPrice`
- `latitude/longitude/coordinateStatus/imageUrl`
- `matched` 不需要在资源详情中返回；由规划结果决定。

## GET /inventory/attractions

内部工具查询，供 ai-planning 的 `searchAttractions` 使用。

Query:

```text
destination=西安&date=2026-10-20
```

Response:

```json
[
  {
    "resourceId": "r-...",
    "name": "秦始皇兵马俑",
    "level": "5A",
    "honorTitle": "世界第八大奇迹",
    "ticketPrice": 120,
    "latitude": 34.384,
    "longitude": 109.278,
    "specId": "s-...",
    "specName": "成人票",
    "price": 120,
    "available": 200
  }
]
```

Rules:

- 只返回 `status=ON_SHELF` 且 `type=ATTRACTION`。
- 付费景区返回可交易门票规格。
- 免费景区返回 `specId=null`、`price=0`。
- 坐标待补时 `latitude/longitude=null`。
- 不支持未匹配资源。

## Ticket-only lock guard

`POST /inventory/locks` 增加服务端校验：

- resource 必须是 `ATTRACTION`。
- spec 必须是 `TICKET_TYPE`。
- 否则返回业务错误，不执行 Redis Lua 扣减。

该守卫不改变资源+日期+规格三维锁定契约，也不改变释放幂等。
