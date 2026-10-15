# booking-order（预订/订单服务）

职责：一键预订、订单聚合、Saga 编排与失败补偿。

| 方法 | 路径 | 说明 | 关键请求/响应 |
|------|------|------|----------------|
| POST | /orders | 一键预订 | `{itineraryId, items:[{resourceId, specId, date, quantity, unitPrice}...], idempotencyKey?}` → 订单 |
| GET | /orders | 我的订单列表 | → `[{orderId, status, totalAmount}]` |
| GET | /orders/{id} | 订单详情 | → 明细/金额/状态 |
| POST | /orders/{id}/pay | 支付（Mock，BOOKED→PAID，自动确认由调度器触发） | → 订单 |
| POST | /orders/{id}/cancel | 取消（可取消状态） | → `{orderId, refundStatus}` |

状态机：`CREATED → BOOKED → PAID → CONFIRMED`；终态 `CANCELLED` / `FAILED`。
未支付超时 → 自动 `CANCELLED`。

Saga 编排（orchestrator）：

1. 校验行程状态与幂等键；创建 Order（CREATED）。
2. 逐项向 inventory `POST /inventory/locks`（`purpose=BOOKING`）。
3. 全部成功 → `BOOKED`；任一失败 → 对已锁定项 `POST /inventory/locks/{lockId}/release`
   补偿，订单 → `FAILED`，并向用户说明失败资源。
4. 补偿幂等，记录于 BookingSettlementLog。

下游调用：inventory（锁/释放）、payment（创建支付）。

事件：`OrderCreated` / `OrderBooked` / `OrderPaid` / `OrderConfirmed` /
`OrderCancelled` / `OrderFailed`。
