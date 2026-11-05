# TrackerApp — Live Position Map (skeleton)

A Next.js (App Router) + TypeScript front end that shows a **2D floor-plan map**
with a moving dot per tracked observer, updated in real time from the
[TrackerApp Positioning API](../api). Each dot is a computed
position fix (`x`, `y` in meters); the fixed ESP32 scanning stations are drawn
as square markers. This is the floor-plan view the API's README says "doesn't
exist yet."

It's a **skeleton**: the map, coordinate mapping, polling, per-observer tracks
and trails are in place and runnable, but intentionally minimal.

## Quick start

```bash
cp .env.local.example .env.local   # defaults are fine for local dev
npm install
npm run dev                        # http://localhost:3000
```

The map needs the API running (see "Run with the full stack" below, or just
`docker compose up postgres api` from `../api`). Until a phone posts a fix it
stays empty — to see dots move without a phone, post one by hand:

```bash
curl -X POST http://localhost:8000/fixes \
  -H 'X-API-Key: dev-only-local-key' -H 'Content-Type: application/json' \
  -d '{"observerID":"demo","x":3.2,"y":1.5,"uncertaintyMeters":0.8,"recordedAt":"2026-11-05T12:00:00Z"}'
```

`npm run lint` (ESLint, `eslint-config-next`'s flat config — `next lint` itself
is deprecated as of Next 16) and `npm run typecheck` (`tsc --noEmit`) are both
wired up; run them before committing.

## Data source

The map plots position fixes, so it reads them from the backend:

| Endpoint         | Method | Status                                         |
| ----------------- | ------ | ----------------------------------------------- |
| `/health`         | GET    | ✅ implemented                                  |
| `/sightings`      | POST   | ✅ implemented (ingest)                         |
| `/fixes`          | POST   | ✅ implemented (ingest)                         |
| `/fixes/stream`   | GET    | ✅ implemented — **what the map actually uses** (Server-Sent Events) |
| `/fixes/recent`   | GET    | ✅ implemented, but unused here (one-shot snapshot; superseded by the stream for this app) |

The edge devices (the iOS and Android apps) compute their own position and
`POST /fixes` it with their `X-API-Key`; the backend stores it and pushes it
over `GET /fixes/stream`, which this app subscribes to once (see "Real-time
approach" below) instead of re-fetching on a timer.

## Run with the full stack

From `../api` (a sibling directory in this monorepo), `docker compose up --build`
starts Postgres, the API, Grafana, and this map together. The map is served on
**http://localhost:3001** (3000 is Grafana).

## The floor layout is client config

The API stores raw metric coordinates but nothing about the physical room or
where the ESP stations sit — that's deployment-specific. It lives in
`src/lib/floor.ts`: edit `FLOOR` (room extent in meters) and `STATIONS` (ESP
positions) to match your real deployment.

## Configuration (`.env.local`)

| Variable                       | Default                 | Meaning                              |
| ------------------------------ | ----------------------- | ------------------------------------ |
| `NEXT_PUBLIC_API_BASE_URL`     | `http://localhost:8000` | FastAPI base URL                     |
| `NEXT_PUBLIC_POLL_INTERVAL_MS` | `1000`                  | `/health` poll cadence (positions are pushed, not polled) |

## Layout

```
src/
  app/
    page.tsx                dashboard: map + observer side panel
    layout.tsx              root layout
    globals.css             theme tokens (light + dark)
    dashboard.module.css    dashboard styles
  components/
    FloorMap.tsx            the 2D SVG map (floor, stations, dots, trails, heatmap layer)
    FloorMap.module.css
    HeatmapLayer.tsx        occupancy/density grid cells, toggleable
    ObserverLegend.tsx      per-observer list with live coordinates
    StatCard.tsx            KPI tile
    HealthBadge.tsx         API liveness indicator
  hooks/
    useFixStream.ts         subscribes to GET /fixes/stream (SSE) — positions, event-driven
    usePolling.ts           interval polling — only GET /health uses this now
  lib/
    types.ts                types mirroring the API payloads
    floor.ts                room + ESP station layout, observer colors, meters↔pixels (toPx)
    tracks.ts               group fixes → per-observer tracks/trails
    heatmap.ts              bin fixes into grid cells (occupancy + weighted density)
    config.ts               env-driven runtime config
    api.ts                  typed API client (GET /health, GET /fixes/recent — not the stream)
```

## How the map works

- `FloorMap` renders an SVG whose viewBox is the room scaled by `PX_PER_M`.
  World meters → pixels via `toPx`, with the Y axis flipped (screen Y grows down).
- `toTracks` groups the flat fix list into one track per observer (newest =
  current dot, the rest = a fading trail polyline).
- The current-position marker is positioned with a CSS `transform` transition,
  so between updates each dot **glides** to its new spot instead of snapping.
- Each observer gets a stable color (`observerColor`); the uncertainty radius is
  drawn as a translucent halo.
- `HeatmapLayer` (toggleable, on by default) bins the same fix list into
  `HEATMAP_CELL_SIZE_M` (0.5m) grid cells — `heatmap.ts`'s `computeHeatmapCells`,
  which deliberately mirrors `../api/grafana/queries.sql`'s binning, so the live
  map and the historical Grafana panels agree on what a "cell" is. Each cell's
  opacity is its weighted density (closer/more confident fixes count for more —
  `1/max(uncertaintyMeters, 0.1)`) relative to the busiest cell currently in
  view, square-rooted so one very dense cell doesn't wash the rest out.

## Real-time approach

Event-driven, not polled: `useFixStream` opens one `EventSource` to
`GET /fixes/stream` and keeps it open. The backend sends an initial `snapshot`
event (recent fixes, so the map isn't empty on load), then one `fix` event per
new position as `POST /fixes` receives it — pushed, not re-fetched on a timer.
The browser's `EventSource` reconnects on its own (with backoff) if the
connection drops; `useFixStream` just reflects that as `connected`/`error`,
it doesn't need to retry manually. `GET /health` is still polled (`usePolling`)
— a plain liveness check is a reasonable fit for that on its own, and doesn't
need its own push channel.

## Next steps

- Overlay a real floor-plan image behind the grid.
- Add an occupancy heatmap layer and per-observer position history/playback.