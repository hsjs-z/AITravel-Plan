# V2-10 Acceptance Plan

**Status**: Proposed. No V2-10 frontend code has been written.

## 1. Scope and boundary

V2-10 covers the Vue 3 planning form and the new planning result page.

In scope:

- Planning form: 1–10 days, 16 hot destinations or domestic free input, pace,
  preferences, party size default 1, no budget field.
- New planning result page with day/slot accordion, item cards, draft add/delete,
  Amap panel, AI tips, free/unmatched copy, and warnings.
- Frontend API/types/router/env updates for the V2-09 backend.

Out of scope:

- Ticket-only booking (V2-11).
- New backend fields or endpoints. If a missing backend field is discovered, stop and
  report it rather than inventing a client-side workaround.
- Share functionality and the “地名” block (explicitly excluded).

## 2. Form behavior

`frontend/src/views/planning/PlanningView.vue`:

- Days: button group 1–10 only; no 11–30 input.
- Destination: two modes:
  1. 16 fixed hot-city buttons: 北京、上海、西安、成都、重庆、杭州、苏州、南京、
     厦门、青岛、长沙、武汉、广州、桂林、丽江、三亚.
  2. Free input for a domestic province/city; validate against a curated domestic
     destination list and reject foreign/non-domestic input with a field error.
- Pace: three buttons `慵懒 / 散步 / 精彩` mapped to
  `RELAXED / BALANCED / VIBRANT`.
- Preferences: free text (style/interests/budget/special requests).
- Party size: default 1, range 1–20.
- No independent budget input in the v2 form; the legacy API budget remains optional
  and is not sent by the new form.
- Submit calls `POST /planning/tasks` and polls `GET /planning/tasks/{taskId}`;
  on success navigate to the new result route.

## 3. Result route and layout

Add route:

```text
/planning/tasks/:taskId/result
```

New view:

```text
frontend/src/views/planning/PlanningResultView.vue
```

The page loads:

- `GET /planning/tasks/{taskId}` for `tips` and task status.
- `GET /itineraries/{itineraryId}` for the live items, slot fields, status, and
  `warnings` (so add/delete changes are reflected without re-running AI).

Layout follows the supplied reference:

- Left: day accordions; each day has MORNING / AFTERNOON / EVENING sections.
- Each item card shows name, suggested-hours badge, description, ticket/free/
  unavailable label, and “点击查看地图位置”.
- Right: persistent Amap panel.
- Bottom: AI travel tips block.
- The reference’s share control is omitted; no “地名” block is added.
- The reference’s pencil edit icon is omitted from the result page; editing remains
  on the existing itinerary edit page.

## 4. Draft add/delete and read-only

- `DRAFT`: each slot shows `添加` (max 3 items); each card shows `删除`.
- `CONFIRMED/LOCKED/COMPLETED`: no add/delete controls; page is read-only.
- Add uses `AttractionPicker`:
  - searches inventory `ATTRACTION` for the itinerary destination and selected date;
  - shows name/level/title/price/coordinate state;
  - free attractions can be chosen without a specId;
  - paid attractions require a ticket specId;
- Add calls `POST /itineraries/{id}/items` with the selected slot.
- Delete calls `DELETE /itineraries/{id}/items/{itemId}`; after delete, refresh the
  itinerary (backend renumbers `sortOrder`).
- 409 capacity/read-only responses show the backend message; no silent failure.
- Warnings from `itinerary.warnings` render as a visible banner/list.

## 5. Copy and map semantics

Item display:

| State | UI |
|---|---|
| Matched paid | real price (`¥{ticketPrice}`), bookable styling |
| Matched free | `免费/无需购票`, addable to itinerary, never payable |
| Unmatched | `暂不可预订`, `票价以官方为准`, no resource binding |

Map:

- `VITE_AMAP_JS_KEY` comes from the root `.env`; no hardcoded key.
- Items with coordinates render markers; clicking the card locates the marker.
- `coordinateStatus=PENDING` shows `坐标待补` and does not throw.
- Missing key shows a configuration hint while the rest of the page remains usable.

## 6. Real files to change

- `frontend/src/views/planning/PlanningView.vue`
- `frontend/src/views/planning/PlanningResultView.vue` (new)
- `frontend/src/components/planning/DayAccordion.vue` (new)
- `frontend/src/components/planning/AttractionCard.vue` (new)
- `frontend/src/components/planning/AttractionPicker.vue` (new)
- `frontend/src/components/planning/AmapPanel.vue` (new)
- `frontend/src/components/planning/TravelTips.vue` (new)
- `frontend/src/api/planning.ts`
- `frontend/src/api/itinerary.ts`
- `frontend/src/api/resources.ts`
- `frontend/src/api/types.ts`
- `frontend/src/router/index.ts`
- `frontend/src/env.d.ts`
- `frontend/vite.config.ts`
- `frontend/src/views/itinerary/ItineraryView.vue` (show slot/order; edit page stays)

## 7. Acceptance evidence

Build/type:

- `npm run build` (`vue-tsc -b && vite build`) succeeds.
- No new component library or request-layer replacement; existing axios base is kept.

Real browser/API flow:

1. Open the planning page; verify 1–10 day buttons, 16 cities, free-input validation,
   three pace buttons, default party size 1, and no budget field.
2. Submit a mock planning task for a non-Yunnan city; verify polling and navigation
   to the result route.
3. Result page: day/slot layout, warnings, tips, item cards, map panel.
4. Draft: add a free item, add a paid item with spec, delete one item; verify slot
   limit errors and refreshed ordering.
5. Confirm the itinerary; reopen result page and verify add/delete are hidden and
   the page is read-only.
6. Verify free and unmatched labels are visibly different.
7. Verify Amap marker/pan behavior with the local JS key and the missing-key hint.
8. Recheck the existing eight accepted pages' behavior; no route or request-layer
   regression.

## 8. Approval points

1. Result route `/planning/tasks/:taskId/result`, with tips from the planning task and
   live items/warnings from the itinerary API.
2. Keep the existing itinerary edit page; the result page has no edit pencil.
3. No share control and no “地名” block.
4. Add/delete only in DRAFT; confirmed/locked/completed pages are read-only.
5. Free and unmatched copy stay distinct.
6. No new test framework; use `npm run build` plus browser/API acceptance. If the
   reviewer wants Vitest/Playwright, that is a separate dependency decision.
