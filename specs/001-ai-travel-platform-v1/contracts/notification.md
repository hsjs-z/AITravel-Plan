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

事件：`NotificationSent`。
