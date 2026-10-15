# Data Model: AI 旅行规划平台 v1

每个服务拥有独立 schema；跨服务只通过标识符引用（不共享表）。字段为领域字段，
非物理表结构。状态机用 `→` 表示合法流转。

## user-auth

- **User**: `id`, `email`(唯一), `passwordHash`, `phone`(可选), `createdAt`。
  账号唯一概念（GLOSSARY「用户」），数据隔离边界。

## ai-planning

- **PlanningTask**: `id`, `userId`, `input`（目的地、天数、人数、预算、偏好）,
  `status`, `itineraryId`(成功后可空), `failureReason`, `retryOf`(可选),
  `createdAt`, `updatedAt`。
  - 状态机：`PENDING → RUNNING → SUCCEEDED | FAILED`；FAILED 可重试回 PENDING。
  - 同一需求重试只保留最后成功的草稿行程（GLOSSARY「规划任务」）。
- **Recommendation**（随任务结果的快照）: 每项含 `resourceId + specId`（建议规格）、
  日期归属、价格；生成时以 `purpose=PLANNING` 软占位候选库存。

## itinerary

- **Itinerary**: `id`, `userId`, `destination`, `startDate`, `endDate`,
  `partySize`, `budget`, `status`, `createdAt`, `updatedAt`。
  - 状态机：`DRAFT → CONFIRMED → LOCKED → COMPLETED`；
    `CONFIRMED/LOCKED` 在订单取消/失败后回 `CONFIRMED`；
    订单支付成功后，已预订项只读、未预订项解锁；全部项完成 → `COMPLETED`。
- **ItineraryItem**: `id`, `itineraryId`, `dayDate`, `description`,
  `resourceType`(可空，纯文本项为空), `resourceId`(可空), `specId`(可空),
  `source`(`AI` | `MANUAL`)。
  - 一个行程项至多绑定一个资源；住宿按夜归属入住日、门票/车票按日归属使用/乘车日。

## inventory

- **Resource**: `id`, `type`(`TRADABLE` | `CONTENT`), `name`, `destination`,
  `status`(`ON_SHELF` | `OFF_SHELF`), `attributes`(JSON)。
  - 可交易：酒店、门票、车票；内容：景点、线路、美食、文化体验。
- **Spec**: `id`, `resourceId`, `specType`(`ROOM_TYPE` | `TICKET_TYPE` | `SCHEDULE`),
  `name`, `occupancy`(房型可住人数，仅属性，不决定默认间数)。
- **InventoryUnit**: `id`, `resourceId`, `date`, `specId`, `total`,
  `available`, `locked`。
  - 库存单元 = 资源 + 日期 + 规格（ADR-0002）。
- **InventoryLock**: `id`, `inventoryUnitId`, `quantity`, `purpose`
  (`PLANNING` | `BOOKING`), `ttlSeconds`, `status`
  (`ACTIVE` | `RELEASED` | `EXPIRED`), `holderType`, `holderId`。
  - 释放幂等：重复释放不虚增库存。
  - 售罄 = 某单元 `available == 0`（临时）；下架 = Resource 状态为 OFF_SHELF（运营动作）。

## booking-order

- **Order**: `id`, `userId`, `itineraryId`, `status`, `totalAmount`,
  `createdAt`, `bookedAt`, `paidAt`, `confirmedAt`, `failureReason`。
  - 状态机：`CREATED → BOOKED → PAID → CONFIRMED`；终态 `CANCELLED`、`FAILED`。
  - `CONFIRMED` 为预留状态位，v1 由支付成功自动触发（ADR-0001）。
  - 未支付超时 → 自动 `CANCELLED`（无退款）。
- **OrderItem**: `id`, `orderId`, `resourceId`, `specId`, `date`, `quantity`,
  `unitPrice`, `status`。保存下单时的价格快照与数量。
- **BookingSettlementLog**: `id`, `orderId`, `step`(各资源的锁定/占用),
  `status`(`PENDING` | `SUCCEEDED` | `COMPENSATING` | `COMPENSATED` | `FAILED`),
  `updatedAt`。记录每次正向动作与补偿收敛状态，补偿幂等。

## payment

- **Payment**: `id`, `orderId`, `idempotencyKey`(唯一), `amount`, `status`
  (`PENDING` | `SUCCEEDED` | `FAILED`), `channel`(`MOCK`), `createdAt`, `paidAt`。
  - 重复请求返回首次结果；`PaymentGateway` 抽象 + Mock 实现。
- **Refund**: `id`, `paymentId`, `orderId`, `idempotencyKey`(唯一), `amount`,
  `status`(`PENDING` | `SUCCEEDED` | `FAILED`), `reason`, `createdAt`。
  - 金额 ≤ 实付；重复退款只生效一次。
- **CallbackRecord**: `id`, `paymentId`, `channelRef`, `payloadHash`,
  `reconciled`(bool), `receivedAt`。用于回调对账与去重。

## notification

- **Notification**: `id`, `userId`, `type`(预订成功/失败、支付成功、订单确认、行程变更),
  `channel`(`IN_APP` | `EMAIL`), `businessRef`, `dedupKey`(唯一),
  `readStatus`(`UNREAD` | `READ`), `title`, `body`,
  `deliveryStatus`(`PENDING` | `SENT` | `FAILED`), `deliveryAttempts`, `lastError`,
  `createdAt`, `readAt`。
  - `type` 为 GLOSSARY 列表的超集（见 contracts/notification.md）。
  - 站内信必达并带未读/已读与未读数；邮件为可选副本，`PENDING` 后由调度器重试至 `SENT`
    （补齐，FR-034）；同一业务事件对同一用户只产生一条站内信（dedupKey 唯一）。
- **ProcessedEvent**: `eventId`(主键), `eventType`, `topic`, `outcome`, `processedAt`。
  - 消费侧幂等：同一事件 `id` 只处理一次（宪法 IV、FR-035）。

## 跨服务标识符关系

- `PlanningTask.itineraryId → Itinerary.id`
- `ItineraryItem.resourceId/specId → Resource.id / Spec.id`
- `Order.itineraryId → Itinerary.id`；`OrderItem` 的 (resourceId, specId, date)
  → `InventoryUnit`
- `Payment.orderId → Order.id`；`Refund.paymentId → Payment.id`
