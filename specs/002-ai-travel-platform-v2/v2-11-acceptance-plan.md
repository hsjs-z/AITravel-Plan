# V2-11 Acceptance Plan

**Status**: Implemented. Self-test evidence is in `v2-11-acceptance.md`; awaiting
product/browser acceptance.

## 1. Scope

V2-11 completes ticket-only trading and closes the three V2-10 UX dead links.

Ticket-only requirements:

1. The booking chain accepts ticket items only.
2. Saga, inventory lock/release, refund, idempotency, and payment terminal guards
   keep their v1 semantics.
3. Order details never contain hotel or train-ticket categories.
4. Ticket quantity defaults to the itinerary party size.
5. The booking page lists only tickets.
6. The server lock boundary remains the final ticket guard.

UX follow-ups:

- Result page draft confirm button.
- “我的行程” navigation/list page backed by a user-scoped itinerary list API.
- Eight-service latest-jar health gate in every delivery record.

## 2. Inventory ticket guard

`POST /inventory/locks` changes from the temporary V2-04 “ATTRACTION is blocked”
guard to the V2-11 ticket guard:

- resource exists and `type=ATTRACTION`
- spec exists, belongs to that resource, and `specType=TICKET_TYPE`
- inventory unit exists for `(resourceId, date, specId)`
- free attractions (no ticket spec) cannot lock
- TRADABLE hotel/train and CONTENT resources cannot lock

Invalid ticket eligibility is a `409 CONFLICT` transaction refusal; missing inventory
unit keeps the existing `404 NOT_FOUND`. The guard runs before Redis Lua/persister,
so no lock row or stock mutation occurs on rejection.

## 3. Order admission and validation

Add a read-only ticket admission check in booking-order before an order row and its
items are persisted:

- call inventory `GET /resources/{id}` and require `type=ATTRACTION`
- if `specId` is present, require a matching `TICKET_TYPE` spec
- reject non-ticket items with `400 BAD_REQUEST`; this prevents a failed hotel/train
  item from becoming an order detail
- the inventory lock guard remains the authoritative final check

T074:

- `POST /orders` items: `@NotEmpty`
- item `resourceId/specId/date`: `@NotNull`
- `quantity`: `@Min(1)`
- `unitPrice`: `@Positive`
- MockMvc tests for empty items and null date return 400 unified error bodies.

T075:

- `/inventory/candidates` filters `status=ON_SHELF`
- keep exact destination matching and the existing spec structure
- add a regression test proving `r-off-shelf-1` is no longer returned

## 4. Frontend ticket-only booking

`frontend/src/views/order/OrderCreateView.vue`:

- load itinerary items and inventory details
- keep only details whose type is `ATTRACTION` and whose selected spec is
  `TICKET_TYPE`
- free attractions (no specId) are not listed
- all ticket rows default `quantity = itinerary.partySize`; user can lower to 1
- show an empty state when no ticket items are available
- retain the existing order submit and failure display

## 5. UX dead-link fixes

### 5.1 Result page confirm

`frontend/src/views/planning/PlanningResultView.vue`:

- `DRAFT`: show a primary “确认行程” button
- confirmation dialog, then call existing `confirmItinerary`
- on success refresh the itinerary; add/delete disappear and the page becomes read-only
- CONFIRMED/LOCKED/COMPLETED: no confirm button

### 5.2 My itineraries

Backend:

- `ItineraryRepository.findByUserIdOrderByCreatedAtDesc`
- `GET /itineraries` with `X-User-Id`; only the current user's rows
- summary fields: `id/destination/startDate/endDate/partySize/status/hasOrder/createdAt`
- detail owner mismatch remains 404

Frontend:

- new `frontend/src/views/itinerary/ItineraryListView.vue`
- route `/itineraries`
- navigation label “我的行程” in `App.vue`
- each row links to `/itineraries/{id}` (existing detail/edit page)

## 6. File plan

- `services/inventory/src/main/java/com/travelplatform/inventory/application/InventoryService.java`
- `services/inventory/src/test/java/com/travelplatform/inventory/application/InventoryServiceTest.java`
- `services/booking-order/src/main/java/com/travelplatform/bookingorder/api/OrderController.java`
- `services/booking-order/src/main/java/com/travelplatform/bookingorder/application/OrderService.java`
- `services/booking-order/src/main/java/com/travelplatform/bookingorder/infrastructure/InventoryClient.java`
- `services/booking-order/src/test/java/com/travelplatform/bookingorder/api/OrderRequestValidationTest.java`
- `services/booking-order/src/test/java/com/travelplatform/bookingorder/application/OrderServiceIdempotencyTest.java`
- `services/itinerary/src/main/java/com/travelplatform/itinerary/api/ItineraryController.java`
- `services/itinerary/src/main/java/com/travelplatform/itinerary/application/ItineraryService.java`
- `services/itinerary/src/main/java/com/travelplatform/itinerary/domain/ItineraryRepository.java`
- `frontend/src/views/order/OrderCreateView.vue`
- `frontend/src/views/planning/PlanningResultView.vue`
- `frontend/src/views/itinerary/ItineraryListView.vue` (new)
- `frontend/src/router/index.ts`
- `frontend/src/App.vue`
- `frontend/src/api/itinerary.ts`
- `frontend/src/api/types.ts`
- contracts: `contracts/inventory.md`, `contracts/booking-order.md`,
  `contracts/itinerary.md`

## 7. Acceptance evidence

Health gate (mandatory from V2-11 onward):

- stop the 8 services
- rebuild all latest jars and verify `JarLauncher`
- restart gateway, user-auth, inventory, itinerary, ai-planning, payment,
  booking-order, notification one by one
- output exactly one conclusion line per service:
  `gateway=UP` … `notification=UP`
- no raw JSON or startup logs in the report

Ticket trading:

| Case | Expected |
|---|---|
| Seeded ATTRACTION + TICKET_TYPE order | BOOKED → PAID → auto CONFIRMED |
| Hotel/train item | 409 at lock, no stock mutation |
| Free attraction / no ticket spec | 409 |
| Order quantity default | partySize in frontend |
| Cancel paid ticket order | CANCELLED + inventory RELEASED + refund SUCCEEDED |
| Same idempotency key | same order id |
| Unauthorized owner | 404 |
| Empty/malformed order items | 400 unified body |
| `/inventory/candidates` | off-shelf row absent |

UX:

| Case | Expected |
|---|---|
| Draft result page | confirm button visible |
| Confirm action | dialog → CONFIRMED → add/delete hidden |
| My itineraries nav/list | current user only, status/date/destination visible |
| Detail click | `/itineraries/{id}` opens existing detail |
| Other user's itinerary detail | 404 |

Regression:

- full `mvn test`
- `npm run build`
- V2-10 browser flow plus existing pages

## 8. Approval points

1. Add the read-only ticket admission check in booking-order before persisting items,
   in addition to the inventory lock guard.
2. Use 409 for non-ticket lock refusals; 400 for malformed order input.
3. `GET /itineraries` returns current-user summaries only; detail keeps 404 discipline.
4. Health gate requires all 8 services with latest jars and one-line UP output.
5. Free attractions remain itinerary-only and never appear in the booking page.
