# booking-order v2

## GET /orders/summaries

供 itinerary 列表/详情聚合行程状态，按当前 `X-User-Id` 只读查询：

```text
GET /orders/summaries?itineraryIds=it-1,it-2
```

响应：

```json
[
  {
    "itineraryId": "it-1",
    "activeOrderCount": 1,
    "completedOrderCount": 0
  }
]
```

- 活动订单：`CREATED/BOOKED/PAID`。
- 已完成订单：`CONFIRMED`。
- `CANCELLED/FAILED` 不计入任一计数。
- 只返回当前用户的订单；支持批量行程 ID。
- 无订单的请求行程返回 `0/0`。
- 该端点只读，不改变订单状态或库存。

## POST /orders

请求结构保持 v1：

```json
{
  "itineraryId": "...",
  "idempotencyKey": "optional",
  "items": [
    {
      "resourceId": "r-...",
      "specId": "s-...",
      "date": "2026-10-20",
      "quantity": 2,
      "unitPrice": 120
    }
  ]
}
```

v2 约束：

- 每个 item 必须是可交易门票规格。
- 酒店/车票/内容资源不得进入订单。
- 门票默认数量由前端设为出行人数；用户仍可调整，下限 1。
- 订单明细不新增酒店类目字段。
- 价格快照仍来自下单时 `unitPrice`。

入参校验（Bean Validation，失败返回 `400` 统一错误体）：

- `items` 必须非空（`@NotEmpty`）。
- 每个 item 的 `resourceId`、`specId`、`date` 必须非空。
- `quantity >= 1`，`unitPrice > 0`。
- 列表元素必须级联校验；空 items 不得创建 `totalAmount=0` 的订单。

服务端兜底：

- 落单前只读调用 inventory 准入检查：resource 必须为 `ATTRACTION`，spec 必须
  属于该资源且为 `TICKET_TYPE`，请求数量不得超过该日期该规格的当前可用库存。
  非门票项返回 `400`；库存不足或库存不可用返回 `409`。
- 只读准入检查不替代库存锁：`POST /inventory/locks` 仍是最终事实源，两步都通过
  才允许 Saga 继续。
- inventory 锁定时校验 `resource.type=ATTRACTION` 与 `spec.specType=TICKET_TYPE`。
- 任一非门票项导致该次联合预订失败，已锁定项按既有 Saga 补偿释放。

## Unchanged

- `POST /orders` 幂等键与同行程去重。
- `POST /orders/{id}/pay`、`/cancel`。
- 订单状态机、Saga、补偿重试、库存释放。
- 支付成功自动确认与终态守卫。
