# V2-11 Acceptance Report

**Status**: Implemented and self-tested; awaiting product/browser acceptance.

## Delivered

### Ticket-only trading

- `services/inventory/src/main/java/com/travelplatform/inventory/application/InventoryService.java`
  - lock guard now accepts only `ATTRACTION` + matching `TICKET_TYPE` spec + existing
    `(resourceId, date, specId)` inventory unit.
  - hotel/train/legacy `TRADABLE`/content/free attractions return `409` before Redis
    or database mutation.
  - `/inventory/candidates` filters `status=ON_SHELF` while keeping the v1 response
    shape and exact destination matching.
- `services/booking-order/src/main/java/com/travelplatform/bookingorder/application/OrderService.java`
  - read-only ticket admission before order persistence: resource type, spec ownership,
    `TICKET_TYPE`, and `quantity <= current availability`.
  - non-ticket item -> `400`; unavailable/insufficient ticket -> `409`.
  - inventory lock remains the authoritative final guard.
- `services/booking-order/src/main/java/com/travelplatform/bookingorder/api/OrderController.java`
  - `items` `@NotEmpty`; nested `@Valid` cascade; `resourceId/specId/date` required;
    `quantity >= 1`; `unitPrice > 0`.
- `services/inventory/src/main/java/com/travelplatform/inventory/application/ResourceSearchService.java`
  - paid `ATTRACTION` with sellable `TICKET_TYPE` inventory returns
    `orderEntryAllowed=true`; free attractions stay `false`.

### UX dead links

- `frontend/src/views/planning/PlanningResultView.vue`
  - draft-only “确认行程” primary button with confirmation dialog; after confirmation
    the itinerary is replaced by the confirmed response and add/delete controls hide.
- `frontend/src/views/itinerary/ItineraryListView.vue`, `frontend/src/router/index.ts`,
  `frontend/src/App.vue`
  - “我的行程” navigation and list page; rows open `/itineraries/{id}`.
- `services/itinerary/.../ItineraryController.java`, `ItineraryService.java`,
  `ItineraryRepository.java`
  - `GET /itineraries` returns current-user summaries ordered by creation time.
  - other users' detail remains `404`.
- `frontend/src/views/order/OrderCreateView.vue`
  - only `ATTRACTION` + `TICKET_TYPE` rows; free items omitted; quantity defaults to
    `partySize`, clamped to current availability, and remains adjustable (min 1).

### Contracts and task list

- `contracts/inventory.md`: ticket guard status semantics, inventory-unit requirement,
  candidates `ON_SHELF` rule.
- `contracts/booking-order.md`: nested validation and quantity-vs-availability rule.
- `contracts/itinerary.md`: `GET /itineraries` current-user summary contract.
- `tasks.md`: T076–T079 added for the UX and delivery gates.

## Verification evidence

### Build and tests

- `mvn test` (full reactor): SUCCESS, 162 tests, 0 failures/errors.
- `mvn -pl services/inventory,services/booking-order,services/itinerary -am test`:
  inventory 72, booking-order 21, itinerary 10 tests; 0 failures.
- `npm run build`: SUCCESS (Vite production build).

### Deployment gate

All 8 services rebuilt as fat jars containing
`org/springframework/boot/loader/launch/JarLauncher` and `BOOT-INF/classes`:

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

Prometheus targets: `8/8 up`.
Nacos: each of the 8 services has exactly 1 healthy registered instance.

### Ticket order

- Itinerary: `86a7534d-ee1e-40ac-90bd-e9a3f1ffbed5` (西安, mock planner, 2 days).
- Confirmed via `POST /itineraries/{id}/confirm` -> `CONFIRMED`.
- Ticket: 大雁塔, `resourceId=r-attraction-217d5564-7f05-40bb-8335-afbb49eda0d1`,
  `specId=s-ticket-13de2b6d-08eb-481e-b6d5-91b6f3169e82`, unit price 50,
  quantity 2, date 2026-10-18.
- `POST /orders` -> `BOOKED`, total `100`, one `ATTRACTION` item.
- `POST /orders/{id}/pay` -> `PAID`; scheduler auto-confirmed the order ->
  `CONFIRMED`.
- Itinerary remains `LOCKED` while the order is active, which is the existing v1
  Saga behavior; cancellation releases it to `CONFIRMED`.
- `GET /resources/search?type=ATTRACTION&destination=丽江&date=2026-10-18`:
  玉龙雪山 returns `orderEntryAllowed=true` (price 100); 丽江古城 returns
  `orderEntryAllowed=false` (`ticketPrice=0`).
- Idempotent replay with key `v211-ticket-1792320379` returned the same order
  `2de48702-99c8-46e5-825d-563e6dc3798f` in `CONFIRMED`.

### Cancellation and refund

- Order `0e7079c8-33a6-4287-a677-c9c64d6cba02`: `BOOKED -> PAID -> CANCELLED`.
- Refund row: `SUCCEEDED`, amount `50`.
- Inventory lock for the order: `RELEASED`.
- Itinerary `d1f8932b-b74c-45d5-8eaa-006e9fcf17c5`: `LOCKED -> CONFIRMED`.
- Notification consumer produced in-app notifications for the test user; the latest
  rows include `BOOKING_SUCCEEDED` and `ORDER_CANCELLED`, and unread rows remain
  `UNREAD`.

### Guard and validation matrix

| Case | Result |
|---|---|
| hotel `r-hotel-1` lock | `409 / 40900` |
| train `r-train-1` lock | `409 / 40900` |
| legacy `TRADABLE` `r-attraction-1` lock | `409 / 40900` |
| free attraction lock | `409 / 40900` |
| content resource lock | `409 / 40900` |
| hotel order | `400 / 40000` |
| free attraction order | `400 / 40000` (`specId` is not a `TICKET_TYPE` spec) |
| content order | `400 / 40000` |
| empty items | `400 / 40000` |
| null date | `400 / 40000` |
| quantity 0 | `400 / 40000` |
| unitPrice 0 | `400 / 40000` |
| quantity above availability (lock) | `409 / 40900` |
| quantity above availability (order) | `409 / 40900` |

Active inventory-lock rows before and after the rejection matrix: `133 -> 133`;
rejected requests did not create lock rows or stock movements.

### Candidates and ownership

- `GET /inventory/candidates?destination=云南&date=2026-10-18` returned 3 rows:
  `r-attraction-1`, `r-hotel-1`, `r-train-1`.
- Off-shelf `r-off-shelf-1` is absent.
- `GET /itineraries` returns only the logged-in user's 3 itineraries.
- Another user's itinerary detail returns `404 / 40400`.
- Anonymous `GET /itineraries` returns `401`; a USER token on `/admin/**` returns
  `404` (gateway role gate).

### Tracing

- Ticket order trace: `09f3292f180e20b2f725586cf20ea264`.
- Tempo spans: 15, covering `gateway`, `booking-order`, `inventory` (`GET /inventory/query`,
  `GET /resources/{resourceId}`, `POST /inventory/locks`), `itinerary` (lock),
  `payment` (create) and `notification` consume.

## Browser acceptance checklist

1. Log in; confirm the light white-blue theme and navigation rendering are unchanged.
2. Open the planning form: 1–10 day selector, 16 hot-city buttons, other-China input,
   pace pills, preferences, party size default 1.
3. Generate a draft plan; verify the left day/slot accordions, right map panel, AI tips,
   and warnings in the corresponding day headers.
4. In draft state, add a ticket attraction from inventory (choose slot) and delete an
   item; verify slot counts and ordering.
5. Click “确认行程”; after the dialog, verify the status becomes confirmed and add/delete
   controls disappear.
6. Open “我的行程” from navigation; verify destination, days, status, dates, creation
   time; click a row to open `/itineraries/{id}`.
7. Open the booking page for a confirmed itinerary; verify only paid tickets appear,
   quantity defaults to party size, and the value can be adjusted.
8. Submit, open order detail, pay, and verify the order reaches `CONFIRMED`.
9. Cancel a paid order before auto-confirmation; verify `CANCELLED` and refund success.
10. Directly open another user's itinerary URL; verify the page shows not found (404).

## Notes

- The Feign availability call now sends the ISO date as a `String` query parameter; the
  inventory endpoint still parses it as `LocalDate`. This avoids a `LocalDate` query
  encoding failure observed during the first acceptance run.
- No theme, layout, or unrelated v1 behavior was changed in this stage.
- V2-11 tasks are intentionally left `[ ]` until this report is accepted.
