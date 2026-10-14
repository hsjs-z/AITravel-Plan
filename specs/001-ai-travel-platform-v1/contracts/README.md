# Service Contracts

本目录定义 v1 各服务对外暴露的 REST 接口与 Kafka 事件契约。约定：

- 同步调用走 OpenFeign + REST（JSON）；异步协作走 Kafka 事件（见 `events.md`）。
- 所有写操作的关键动作带幂等键（`idempotencyKey` / `dedupKey`）。
- 所有请求与事件消息头透传 `traceId`（HTTP header `X-Trace-Id`，Kafka header `traceId`）。
- 数据隔离：除 user-auth 外，所有查询请求必须携带已验证的 `userId`（由 gateway 透传）。
- 错误约定：4xx 业务错误（可重试与否在响应中区分）、5xx 系统错误。

各服务契约：

- [user-auth.md](./user-auth.md)
- [ai-planning.md](./ai-planning.md)
- [itinerary.md](./itinerary.md)
- [inventory.md](./inventory.md)
- [booking-order.md](./booking-order.md)
- [payment.md](./payment.md)
- [notification.md](./notification.md)
- [events.md](./events.md)
