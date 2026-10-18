# V2-09 Acceptance Evidence

**Date**: 2026-10-18

**Status**: PASS, awaiting user acceptance. T033/T034/T035/T036/T037 remain
unchecked.

## 1. Scope

V2-09 adds the draft itinerary item APIs, display snapshots, ordered item queries,
and capacity/read-only tests. V2-10 frontend work is not included.

Changed areas:

- `services/itinerary/src/main/resources/db/migration/V5__itinerary_item_display.sql`
- `services/itinerary/.../domain/ItineraryItem.java`
- `services/itinerary/.../domain/ItineraryItemRepository.java`
- `services/itinerary/.../infrastructure/InventoryClient.java`
- `services/itinerary/.../application/ItineraryService.java`
- `services/itinerary/.../api/ItineraryController.java`
- `services/ai-planning/.../infrastructure/ItineraryClient.java`
- `services/ai-planning/.../application/PlanningService.java`
- `contracts/itinerary.md`

## 2. Manual add semantics

`POST /itineraries/{itineraryId}/items`:

- owner mismatch -> 404
- itinerary must be `DRAFT`; CONFIRMED/LOCKED/COMPLETED -> 409 unified error body
- slot already has 3 items -> 409
- `resourceId` must exist in inventory and be `type=ATTRACTION`; otherwise 400
- provided `specId` must belong to the resource and be `TICKET_TYPE`; otherwise 400
- paid attraction requires a ticket specId; free attraction allows empty specId
- AI whitelist is deliberately not applied on this manual path; it only constrains
  AI output
- server snapshots `name/suggestedHours/ticketPrice` from inventory at write time;
  later inventory price changes do not rewrite the snapshot
- `sortOrder` is appended to the end of the selected slot

`DELETE /itineraries/{itineraryId}/items/{itemId}`:

- owner mismatch -> 404
- non-DRAFT -> 409
- item must belong to the itinerary; otherwise 404
- remaining items in the same slot are renumbered 1..n

## 3. Display fields

`itinerary_item` now stores:

- `name`
- `suggested_hours`
- `ticket_price`

Old v1 rows keep these fields null; the frontend can fall back to description.
New AI/manual writes populate them.

## 4. Real HTTP evidence

Direct itinerary service HTTP on 8083 with `X-User-Id` (gateway was not needed for
this isolated acceptance):

| Check | Result |
|---|---|
| Create draft | `200` |
| Add paid attraction 3 times | `200`, items 1→2→3 |
| Snapshot | `玉龙雪山`, ticketPrice `100`, hours `4`, MORNING |
| Fourth add to same slot | `409`, code `40900` |
| Add free attraction with empty specId | `200`, ticketPrice `0`, specId null |
| Delete one morning item | `200`, remaining sortOrder `1,2` |
| Confirm itinerary | `200`, `CONFIRMED` |
| Add/delete after confirm | `409`, code `40900` both |
| Invalid resource type (TRADABLE) | `400`, code `40000` |
| Invalid/non-ticket spec | `400`, code `40000` |
| Different user | `404`, code `40400` |

## 5. Tests and schema

- `mvn -pl services/ai-planning,services/itinerary -am test`: itinerary 9 +
  ai-planning 25 + common 5, 0 failures.
- Root `mvn test`: exit 0, BUILD SUCCESS.
- Flyway V5 applied; `itinerary_item` has `name/suggested_hours/ticket_price`.
- New tests:
  - `ItinerarySlotCapacityTest` (capacity, snapshot, validation, reorder, owner)
  - `ItineraryReadOnlyTest` (409 for CONFIRMED/LOCKED/COMPLETED)
  - `ItinerarySlotsTest` (slot/warnings persistence)

## 6. Boundary

T033–T037 remain unchecked until review. V2-10 frontend form/result page is not
implemented in this round.
