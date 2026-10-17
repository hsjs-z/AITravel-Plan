# booking-order v2

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

服务端兜底：

- inventory 锁定时校验 `resource.type=ATTRACTION` 与 `spec.specType=TICKET_TYPE`。
- 任一非门票项导致该次联合预订失败，已锁定项按既有 Saga 补偿释放。

## Unchanged

- `POST /orders` 幂等键与同行程去重。
- `POST /orders/{id}/pay`、`/cancel`。
- 订单状态机、Saga、补偿重试、库存释放。
- 支付成功自动确认与终态守卫。
