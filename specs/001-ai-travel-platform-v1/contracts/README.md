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

## 端点索引（等价 OpenAPI，M4 T072 收尾）

实现事实来源为各服务 Controller；本索引与各 `*.md` 并列，作为宪章 IX 的「等价接口说明」。

| 服务 | 方法 | 路径 | 说明 |
|------|------|------|------|
| user-auth | POST | /auth/register | 邮箱注册 → `{token,userId}` |
| user-auth | POST | /auth/login | 登录 → `{token,userId}` |
| user-auth | GET | /users/me | 当前用户 → `{id,email,phone}` |
| ai-planning | POST | /planning/tasks | 提交规划 → `{taskId}` |
| ai-planning | GET | /planning/tasks/{taskId} | 查询状态/结果 |
| ai-planning | POST | /planning/tasks/{taskId}/retry | 重试 |
| inventory | GET | /resources/search | 检索资源（含 contentCategory/description/category） |
| inventory | GET | /resources/{resourceId} | 资源详情 |
| inventory | GET | /inventory/query | 可用库存查询 |
| inventory | GET | /inventory/candidates | 候选清单（内部 Feign） |
| inventory | POST | /inventory/locks | 原子锁定 |
| inventory | POST | /inventory/locks/{lockId}/release | 幂等释放 |
| itinerary | POST | /itineraries | 创建草稿行程 |
| itinerary | GET | /itineraries/{itineraryId} | 查看行程与明细 |
| itinerary | PUT | /itineraries/{itineraryId} | 编辑（回 DRAFT） |
| itinerary | POST | /itineraries/{itineraryId}/confirm | 行程确认 |
| itinerary | DELETE | /itineraries/{itineraryId} | 删除（有订单拒绝） |
| itinerary | POST | /itineraries/{itineraryId}/lock | 锁定行程（booking-order 触发） |
| itinerary | POST | /itineraries/{itineraryId}/release | 解锁回 CONFIRMED |
| itinerary | POST | /itineraries/{itineraryId}/complete | 完成行程 |
| booking-order | POST | /orders | 一键预订（items 为 resourceId/specId/date/quantity/unitPrice） |
| booking-order | GET | /orders | 我的订单列表 |
| booking-order | GET | /orders/{orderId} | 订单详情 |
| booking-order | POST | /orders/{orderId}/pay | 支付（Mock，BOOKED→PAID） |
| booking-order | POST | /orders/{orderId}/cancel | 取消（自动退款） |
| payment | POST | /payments | 创建支付 |
| payment | POST | /payments/{paymentId}/confirm | 确认（Mock） |
| payment | POST | /payments/callback | 通道回调（对账） |
| payment | GET | /payments/{paymentId} | 查询支付 |
| payment | POST | /refunds | 发起退款 |
| payment | GET | /refunds/order/{orderId} | 查询某订单退款 |
| notification | GET | /notifications | 通知列表 |
| notification | GET | /notifications/unread-count | 未读数 |
| notification | POST | /notifications/{notificationId}/read | 标记已读 |

鉴权：除 `/auth/register`、`/auth/login` 外，经 gateway 均需 JWT；gateway 校验后透传
`X-User-Id`，业务服务按该 header 做 owner 隔离（越权统一 404）。
