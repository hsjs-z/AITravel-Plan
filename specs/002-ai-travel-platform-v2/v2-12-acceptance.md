# V2-12 Acceptance Report

**Status**: Implemented and self-tested; awaiting V2-12 browser acceptance.

## Scope delivered

### T080 — itinerary order summary and status labels

- `services/booking-order/src/main/java/com/travelplatform/bookingorder/api/OrderController.java`
  - new read-only `GET /orders/summaries?itineraryIds=...`.
- `services/booking-order/src/main/java/com/travelplatform/bookingorder/application/OrderService.java`
  - aggregates per-itinerary order counts for the current user.
  - active = `CREATED/BOOKED/PAID`; completed = `CONFIRMED`; cancelled/failed ignored.
- `services/itinerary/src/main/java/com/travelplatform/itinerary/infrastructure/OrderClient.java`
  - Feign client used by itinerary list/detail aggregation.
- `services/itinerary/src/main/java/com/travelplatform/itinerary/api/ItineraryController.java`
  - list and detail responses now include `activeOrderCount/completedOrderCount`.
- `frontend/src/views/itinerary/ItineraryListView.vue`
  - status labels: `预订中`, `已出行`, `已确认 · 未预订`, `草稿`, `已锁定`.
- `frontend/src/views/itinerary/ItineraryView.vue`
  - detail status and order summary use the same counts.

### T081 — itinerary deletion

- `services/itinerary/src/main/java/com/travelplatform/itinerary/domain/ItineraryStateMachine.java`
  - delete is allowed only for `DRAFT`, or `CONFIRMED` with zero active orders.
  - `LOCKED`, `COMPLETED`, or any active order -> `409`.
  - `hasOrder` no longer blocks deletion after cancellation.
- `services/itinerary/src/main/java/com/travelplatform/itinerary/application/ItineraryService.java`
  - owner lookup remains `404`; items are deleted with the itinerary.
- `frontend/src/views/itinerary/ItineraryListView.vue`
  - delete action with second confirmation for allowed rows.
- `frontend/src/views/itinerary/ItineraryView.vue`
  - delete action with second confirmation; redirects to `/itineraries`.

### Explicit product decision

Cancellation/refund keeps the itinerary at `CONFIRMED`; it does not enter a
“rejected” terminal state. With no active or completed orders the list shows
“已确认 · 未预订”, allowing a later rebooking.

## Verification evidence

### Build and tests

```text
mvn test: 168 tests, 0 failures/errors
npm run build: SUCCESS
```

### Deployment gate

All 8 fat jars contain
`org/springframework/boot/loader/launch/JarLauncher` and `BOOT-INF/classes`.

```text
gateway=UP
user-auth=UP
inventory=UP
itinerary=UP
ai-planning=UP
payment=UP
booking-order=UP
notification=UP
```

Nacos: 8/8 services have exactly one healthy instance.

### Main chain

- User `v212check@example.com`; mock planning 西安 2 days; itinerary
  `346cdfe1-5ce5-4a94-95f2-ad645d3e0b28` confirmed.
- Ticket order `67cffd5c-68e7-4e1e-b853-3fd768a9f80b`:
  `BOOKED -> PAID -> CONFIRMED`, total 100, quantity 2.
- While BOOKED: `activeOrderCount=1`, `completedOrderCount=0` -> “预订中”.
- After payment: `activeOrderCount=0`, `completedOrderCount=1` -> “已出行”.
- Idempotent replay with key `v212-main-1792322890` returned the same order ID.

### Rebooking and deletion boundaries

- New confirmed itinerary `6b18e2f4-38ee-4a85-9b55-53f3050f9ef7`, order
  `3999aac4-7add-4e48-a824-59aa70209c32`:
  - `BOOKED` -> summary `1/0`; delete -> `409 / 40900`.
  - cancel -> itinerary `CONFIRMED`, summary `0/0`; delete -> `200`.
- Draft itinerary `d9d7ddb1-388e-480a-9040-5567f595da93`: delete -> `200`.
- Locked itinerary `346cdfe1-...`: delete -> `409 / 40900`.
- Another user's itinerary: delete -> `404 / 40400`.

### Refund regression

- Order `8c3b1bdb-e965-471b-bb7a-4e1a34d5d78e`: `PAID -> CANCELLED`.
- Refund row: `SUCCEEDED`, amount 50.
- Inventory lock: `RELEASED`.
- Itinerary: `CONFIRMED`, summary `0/0`; delete after refund -> `200`.

### V2-11 guard regression

| Case | Result |
|---|---|
| hotel lock | `409 / 40900` |
| empty order items | `400 / 40000` |
| candidates off-shelf row | absent |
| paid attraction `orderEntryAllowed` | `true` |
| free attraction `orderEntryAllowed` | `false` |
| quantity above availability | `409 / 40900` |
| anonymous itinerary request | `401` |
| USER token on `/admin/**` | `404` |

### Observability

```text
Grafana: database ok (11.1.0)
Prometheus targets: 8/8 up
Tempo traceId: 2807134162173cba1034bd986e203957
Tempo services: gateway, booking-order, inventory, itinerary, payment, notification
Tempo spans: 17, including booking-order `GET /orders/summaries`
```

Notification consumer produced in-app notifications; latest rows include
`BOOKING_SUCCEEDED` and `ORDER_CANCELLED`, with unread rows preserved.

## Browser acceptance checklist

1. “我的行程”列表：无订单的已确认行程显示“已确认 · 未预订”；有活动订单显示
   “预订中”；有已完成订单显示“已出行”。
2. 列表和详情页对 DRAFT / 可删除的 CONFIRMED 行程显示删除入口，二次确认后删除。
3. LOCKED 或存在活动订单时删除入口不出现；用接口直接删除仍返回 409。
4. 支付后取消退款：订单变 CANCELLED，行程保持 CONFIRMED，列表回到
   “已确认 · 未预订”，不出现“已拒绝”。
5. 确认行程 → 预订页只显示门票、数量默认出行人数 → 下单支付 → 订单 CONFIRMED。
6. 详情页“订单”摘要与列表标签一致（未预订 / 预订中 / 已出行）。
7. 原有 8 个页面（登录、注册、规划表单、结果页、行程详情、订单列表/详情、
   通知中心）功能与浅色白蓝主题不回退。
8. 越权访问他人行程详情/删除仍为 404。

## Notes

- `GET /orders/summaries` is read-only and user-scoped; it does not change order or
  inventory state.
- Deletion is physical and also deletes itinerary items.
- V2-12 tasks T069–T073 and T080–T082 remain unchecked until this report is accepted.
