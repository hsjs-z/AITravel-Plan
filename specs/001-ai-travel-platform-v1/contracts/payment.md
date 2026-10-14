# payment（支付服务）

职责：PaymentGateway 抽象 + Mock 实现、支付/退款幂等、回调对账。

| 方法 | 路径 | 说明 | 关键请求/响应 |
|------|------|------|----------------|
| POST | /payments | 创建支付 | `{orderId, amount, idempotencyKey}` → `{paymentId}` |
| POST | /payments/{id}/confirm | 完成支付（Mock） | → `{status}` |
| POST | /payments/callback | 通道回调 | `{channelRef, amount, status, signature?}` |
| GET | /payments/{id} | 查询支付 | → 状态/金额 |
| POST | /refunds | 发起退款 | `{paymentId, orderId, amount, idempotencyKey}` → `{refundId}` |

约束：`PaymentGateway` 接口，Mock 实现 + 预留微信实现位；重复支付/退款返回首次结果；
回调做金额+订单一致性对账，不一致不置已支付并记异常。

事件：`PaymentSucceeded` / `PaymentFailed` / `RefundSucceeded`。
