# V2-10 Self-Test Record

**Date**: 2026-10-18

**Status**: Browser acceptance passed. Three UX follow-ups were deferred to V2-11:
result-page confirm, my-itineraries navigation/list, and the 8-service health gate.

## 1. Build

Command:

```text
cd frontend
npm run build
```

Result: PASS

- `vue-tsc -b` passed.
- Vite transformed 1720 modules.
- Output: `dist/index.html`, one CSS bundle and one JS bundle.
- JS bundle is about 1.16 MB (gzip 382 kB); Vite reported the standard
  500 kB chunk-size warning, not a build failure.

## 2. Dev server smoke

`npm run dev -- --host 127.0.0.1` started Vite 5.4.21 on port 5173.

`GET http://127.0.0.1:5173/` returned `200` with `text/html`.

## 3. Static checks

- No AMap JS Web Service or JS key literal exists in `frontend/src`; the map reads
  `import.meta.env.VITE_AMAP_JS_KEY`.
- `frontend/vite.config.ts` uses `envDir: '..'`, so the root `.env` is the source.
- No `分享`, `地名`, hardcoded `r-...` resource binding, or manual resourceId input
  exists in the V2-10 planning components.
- `AttractionPicker.vue` obtains candidates only through `searchAttractions`
  (`GET /inventory/attractions`).
- Result page route is `/planning/tasks/:taskId/result`; the existing itinerary
  route and edit page remain.

## 4. Backend compatibility checks

- Itinerary Flyway version 7 applied.
- V2-10 support schema:
  - V5: item `name/suggested_hours/ticket_price`
  - V6: itinerary budget nullable
  - V7: item latitude/longitude snapshot for the map
- `mvn -pl services/ai-planning,services/itinerary -am test`:
  itinerary 9 + ai-planning 25 + common 5, 0 failures.
- Manual add/delete/read-only API behavior was independently verified in V2-09;
  V2-10 reuses those endpoints.

## 5. Reviewer browser checklist

1. Planning page: 1–10 day pills, 16 city pills, free domestic input, three pace
   pills, party size default 1, no budget field.
2. Submit a mock planning task for a non-Yunnan city; confirm polling and navigation
   to the result route.
3. Result page: dark glass layout, day accordion, MORNING/AFTERNOON/EVENING sections,
   warnings in the matching day header, AI tips, map panel.
4. Draft: add a free item and a paid item through the picker; delete an item; verify
   the 3-per-slot limit and refreshed ordering.
5. Confirm itinerary; reopen result page and verify add/delete controls are hidden and
   the page is read-only.
6. Verify free “免费/无需购票” and unmatched “暂不可预订 / 票价以官方为准” are
   visually distinct.
7. Temporarily clear the JS key or block the AMap script; verify the map disappears
   and the plan list becomes full width without a white screen.
8. Recheck the existing accepted pages for navigation/request behavior.

## 6. Limitation

No Vitest/Playwright dependency was added per the approved plan. Visual/browser
behavior is intentionally left for the reviewer checklist; the self-test covers
type/build, dev serving, source policy, routes, and backend compatibility.
