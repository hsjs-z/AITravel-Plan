# Service Contracts: AI 旅行平台 v2

v2 契约在 v1 契约基础上增量扩展。v1 文档保留为历史事实来源；
本目录只描述 v2 新增或改变的接口。

通用约定：

- 同步调用走 OpenFeign + REST（JSON）；异步协作走 Kafka 事件。
- 所有受保护请求经 gateway 校验 JWT；下游继续按 `X-User-Id` 做 owner 隔离。
- 管理员请求由 gateway 注入 `X-User-Role`，业务服务二次校验。
- 越权统一 404；未认证 401。
- 关键写操作保留幂等键语义。
- 接口变更必须同步本目录与 `tasks.md`。

索引：

- [ai-planning.md](./ai-planning.md)
- [inventory.md](./inventory.md)
- [itinerary.md](./itinerary.md)
- [booking-order.md](./booking-order.md)
- [user-auth.md](./user-auth.md)
- [admin-seed.md](./admin-seed.md)

保持不变：

- payment 的 `PaymentGateway`、支付/退款幂等、回调对账、终态守卫。
- notification 的事件消费、站内信、未读/已读。
- Outbox 信封与 Kafka 主题；v2 不新增业务事件。
