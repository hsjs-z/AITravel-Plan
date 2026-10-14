# Event Contracts (Kafka)

事件信封（所有主题统一）：

```json
{
  "id": "uuid（幂等去重键）",
  "type": "PlanningSucceeded",
  "aggregateId": "业务聚合标识",
  "occurredAt": "2026-10-14T10:00:00Z",
  "traceId": "跨服务链路标识",
  "payload": {}
}
```

主题与事件：

| 主题 | 事件 | 生产者 | 消费者 |
|------|------|--------|--------|
| planning.events | PlanningSucceeded / PlanningFailed | ai-planning | 无（结果由查询获取，事件用于审计/通知） |
| itinerary.events | ItineraryConfirmed / ItineraryChanged | itinerary | notification |
| order.events | OrderCreated / OrderBooked / OrderPaid / OrderConfirmed / OrderCancelled / OrderFailed | booking-order | payment、notification、inventory（释放） |
| payment.events | PaymentSucceeded / PaymentFailed / RefundSucceeded | payment | booking-order、notification |
| inventory.events | InventoryLocked / InventoryReleased / InventorySoldOut | inventory | booking-order（补偿）、notification（可选） |
| notification.events | NotificationSent | notification | 无 |

投递约束：生产者用 Outbox（业务写与事件同事务落库后投递）；消费者按 `id` 幂等，
同一 `id` 只处理一次。
