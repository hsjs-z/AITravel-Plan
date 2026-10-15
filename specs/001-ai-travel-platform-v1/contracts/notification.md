# notification（通知服务）

职责：消费业务事件生成通知；站内信/邮件投递；未读/已读。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /notifications | 我的通知列表（owner 隔离，含未读/已读） |
| GET | /notifications/unread-count | 未读数 |
| POST | /notifications/{id}/read | 标记已读 |

消费事件：ItineraryChanged、OrderBooked、OrderPaid、OrderConfirmed、
OrderCancelled、OrderFailed、PaymentSucceeded、PaymentFailed、RefundSucceeded。

约束：按事件 `id` 去重（一个事件对同一用户只一条站内信）；站内信必达、邮件可选；
下游不可用时重投补齐（Outbox 消费侧）。

映射与业务级去重：

- `OrderPaid` 与 `PaymentSucceeded` 都表示同一笔「支付成功」，统一映射为
  `PAYMENT_SUCCEEDED`，并以 `dedupKey = type:userId:orderId` 去重，
  避免一次支付产生两条站内信（同一业务事件对同一用户不重复，FR-035）。
- 邮件为可选渠道：落库为 `PENDING` 后由调度器重试至 `SENT`，下游短时不可用
  恢复后补齐投递（FR-034）；站内信落库即视为已投递。
- 通知类型为 GLOSSARY 列表的超集：`BOOKING_SUCCEEDED`、`BOOKING_FAILED`、
  `PAYMENT_SUCCEEDED`、`PAYMENT_FAILED`、`REFUND_SUCCEEDED`、`ORDER_CONFIRMED`、
  `ORDER_CANCELLED`、`ITINERARY_CHANGED`。

事件：`NotificationSent`。
