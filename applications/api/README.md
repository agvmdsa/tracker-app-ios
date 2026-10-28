# Positioning API

Ingestion API for the TrackerApp indoor positioning feature: receives BLE sighting,
computed-position, and labelled fingerprinting reports from the iOS app (eventually Android, and
an ESP32 acting as a fixed scanning/beacon station too), stores them in Postgres, and exposes that
data to Grafana for dashboards (peak hours, anchor coverage), to the live floor-plan map
(`../web`) via `GET /fixes/stream` (pushed over Server-Sent Events as each fix arrives, not
polled), and — via `/fingerprints`, the one with ground-truth position — to train an
indoor-positioning (fingerprinting) model, which is the actual machine-learning deliverable.

This directory is backend only — the live floor-plan map is a separate Next.js app at `../web`
(a sibling directory, now that this and the iOS app share one repository).

## Stack

- **FastAPI** (Python) — the ingestion endpoints.
- **Postgres** — storage.
- **Mosquitto** (MQTT broker) + `app/mqtt_consumer.py` — the ESP32 collector's continuous,
  unlabelled RSSI telemetry (one MQTT message per advertisement, idle or recording alike; see
  `esp32-esp8266/USAGE.md` chapter 8), as opposed to `POST /sightings`/`/fingerprints`, which are
  HTTP. The consumer subscribes to `tracker/+/sightings`, parses each JSON message, and persists
  it the same way `POST /sightings` does. Optional — does nothing if `MQTT_HOST` isn't set.
- **Grafana** — reads Postgres directly (not through the API) for time-based, historical
  dashboards — the aggregate/time-series views the live floor-plan map (`../web`) doesn't cover.
  Comes pre-provisioned with the Postgres datasource
  (`grafana/provisioning/datasources/datasource.yml`) and a dashboard
  (`grafana/provisioning/dashboards/json/positioning-overview.json`, provider config in
  `grafana/provisioning/dashboards/dashboards.yml`) with 4 panels built from `grafana/queries.sql`:
  peak hours, heatmap density, occupancy, and dwell time per grid cell. No manual setup needed —
  `docker compose up` (or the Render deploy) shows it immediately.

## Running it

```bash
docker compose up --build
```

- API: http://localhost:8000 (docs at http://localhost:8000/docs)
- Grafana: http://localhost:3000 (user `admin` / password `admin`, change it on first login)
- Frontend (live floor-plan map, `../web`): http://localhost:3001
- Postgres: localhost:5432 (user/password/db: `postgres`/`postgres`/`positioning`)

## Authentication

`POST /sightings`, `POST /fixes` and `POST /fingerprints` require an `X-API-Key` header matching the server's `API_KEY`
environment variable (no default — the app fails to start if it's not set). `GET /health`,
`GET /fixes/recent` and `GET /fixes/stream` stay open: health checks and the browser-based map
can't send a header a browser's dev tools would expose anyway, and none of the three write
anything or expose anything sensitive. This is a single shared key, not per-client auth — enough
to stop randos on the internet from writing to the database, not a real auth system.

`CORS_ORIGINS` (comma-separated, default `*`) controls which origins may call the `GET` endpoints
above from a browser — set it to the real frontend's origin in production instead of the open
default.

## Endpoints

Payload shapes mirror exactly what `PositioningAPIClient.swift` (in the iOS app) encodes — field
names are camelCase to match Swift's default `JSONEncoder` output, not Python convention.

### `POST /sightings`

One row per anchor a phone currently sees, batched. Mirrors `[PositioningReport]`.

```json
[
  {
    "observerID": "B7E2F910",
    "anchorID": "9F3C1A02",
    "rssi": -58,
    "seenAt": "2026-01-01T12:00:00Z"
  }
]
```

`distanceMeters` is accepted (the iOS app still sends it) but **always ignored** — the server
recomputes it from `rssi` via `app/path_loss.py`, the same log-distance formula as the iOS app's
`PathLossModel.swift`. This is so there's one canonical formula instead of one per client as more
of them show up (an ESP32 sending raw RSSI over Wi-Fi is the reason this changed — firmware
shouldn't have to replicate the math). `observerID` doesn't have to be a real `BeaconID` — any
short string identifying the sender works, so a fixed ESP32 station can use its own id here.

### `POST /fixes`

The observer's own computed position for that refresh cycle. Mirrors `ObserverFixReport`. This is
the one that matters for a heatmap or an occupancy count ("how many distinct `observerID`s were
seen in this area recently") — `/sightings` alone doesn't carry a computed position.

```json
{
  "observerID": "B7E2F910",
  "x": 1.84,
  "y": -0.62,
  "uncertaintyMeters": 0.31,
  "recordedAt": "2026-01-01T12:00:00Z"
}
```

### `GET /fixes/recent`

```
GET /fixes/recent?limit=200   # limit 1..1000, default 200
```

Most recent fixes, newest first, as a one-shot JSON array. Doesn't need `X-API-Key` (see
"Authentication" above). Each row is the same shape as `POST /fixes`'s body, plus `id` and
`receivedAt`. Superseded by `GET /fixes/stream` for the live map — kept for anything that just
wants a snapshot (e.g. a quick `curl`) rather than an open connection.

### `GET /fixes/stream`

```
GET /fixes/stream?limit=200   # limit 1..1000, default 200 — size of the initial snapshot
```

Server-Sent Events (`text/event-stream`) — the read side the live floor-plan map (`../web`)
actually subscribes to, in place of polling. On connect, sends one `snapshot` event (a JSON
array of recent fixes, oldest first); after that, one `fix` event per new position as `POST
/fixes` receives it, for as long as the connection stays open:

```
event: snapshot
data: [{"id":1,"observerID":"B7E2F910","x":1.84,"y":-0.62,"uncertaintyMeters":0.31,"recordedAt":"2026-01-01T12:00:00Z","receivedAt":"2026-01-01T12:00:00.1Z"}]

event: fix
data: {"id":2,"observerID":"B7E2F910","x":1.9,"y":-0.55,"uncertaintyMeters":0.28,"recordedAt":"2026-01-01T12:00:01Z","receivedAt":"2026-01-01T12:00:01.1Z"}
```

In-process pub/sub (`app/broadcast.py`), not a message broker — works because this API runs as a
single process. A subscriber's queue is bounded (100 pending fixes); if a client stalls long
enough to fill it, further fixes are dropped for that one client rather than blocking the
`POST /fixes` request that's trying to publish them. Doesn't need `X-API-Key`, same reasoning as
`/fixes/recent`.

### `POST /fingerprints`

Labelled training data for an indoor-positioning (fingerprinting) model — this is the actual ML
deliverable; `/sightings` and `/fixes` carry a computed *estimate*, not ground truth, so they
can't train a model that's supposed to predict position. Mirrors one row of the iOS app's
`FingerprintRecorder` CSV, or one `R` record from the ESP32 collector, batched.

```json
[
  {
    "nodeID": "9F3C1A02",
    "platform": "iOS",
    "model": "iPhone15,2",
    "systemVersion": "iOS 26.0.1",
    "beaconID": "1A2B3C4D",
    "rssi": -61,
    "roll": 0.01,
    "pitch": -0.02,
    "yaw": -0.999,
    "relativeAltitudeM": 1.2,
    "pressureKPa": 101.3,
    "motionState": "stationary",
    "motionConfidence": 2,
    "session": "S1",
    "point": "P3",
    "x": 2.0,
    "y": 4.5,
    "seenAt": "2026-01-01T12:00:00Z"
  }
]
```

`model`/`systemVersion`/`roll`/`pitch`/`yaw`/`relativeAltitudeM`/`pressureKPa`/`motionState`/
`motionConfidence` are all optional — the ESP32 collector has none of a phone's motion/orientation
sensors, so its rows only ever send `nodeID`, `platform` (`"ESP32"`), `beaconID`, `rssi`, `session`,
`point`, `x`, `y`, `seenAt`. `x`/`y` here are the **known** position entered by whoever was
recording, not an estimate — that's what makes this table usable as training data and `fixes`
not.

### `GET /health`

Plain liveness check, `{"status": "ok"}`.

## Deploying to Render

Switched from Railway to Render after Railway started showing "Limited Access — Deploys have
been paused due to an upstream issue" across *all* services in the project at once (including
ones that had been running fine, and the Postgres instance, which isn't even GitHub-connected) —
that pattern means it wasn't anything misconfigured on our end, so rather than wait on an
unexplained outage, we moved. The Railway steps are kept below for reference/rollback, but Render
is the one actually in use.

Three separate Render services, same shape as before: a managed Postgres, and two Docker-based
Web Services built from this one repo (API from the root `Dockerfile`, Grafana from
`grafana/Dockerfile`).

1. **Postgres** — New → PostgreSQL. Render provisions it and shows connection details (host,
   port, user, password, database name, and a ready-made `Internal Database URL`) on its page
   once created.
2. **API** — New → Web Service → connect this repo. Render auto-detects the root `Dockerfile`.
   Set `DATABASE_URL` to the Postgres service's **Internal Database URL**, with `+psycopg` added
   to the scheme (same adjustment as Railway — Render's is `postgresql://...`, this code needs
   `postgresql+psycopg://...`). Also set `API_KEY` to the same value `PositioningAPIClient.swift`
   sends in its `X-API-Key` header — the two have to match exactly, there's no shared secret
   generation, it's just a string you pick and paste in both places. Render gives the service a
   public `onrender.com` URL automatically — that's what `PositioningAPIClient.swift` (iOS) should
   point at.
3. **Grafana** — New → Web Service → connect this repo *again*. Render builds from a Dockerfile
   it finds via **Root Directory**/**Dockerfile Path** in the service's settings — point it at
   `grafana` (exact field layout may differ from what's described here; Render separates "build
   context directory" from "Dockerfile path" rather than having one combined "Root Directory"
   like Railway, so this may need adjusting once the actual form is visible). Environment
   variables, reading the same values off the Postgres service's page as step 2:
   - `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`
   - `POSTGRES_SSLMODE=require`
   - `GF_SECURITY_ADMIN_USER`/`GF_SECURITY_ADMIN_PASSWORD` — not `admin`/`admin`, this is
     reachable from the public internet once Render assigns it a domain.

> ✅ **The `$VARIABLE` expansion in `datasource.yml` was confirmed working** — verified on a real
> deployment (Railway, before the outage): `GET /api/datasources` showed the Postgres datasource
> provisioned with the env var values correctly substituted, and `GET
> /api/datasources/uid/.../health` returned `"Database Connection OK"`. This part of the setup is
> known-good; it doesn't depend on which host runs the containers.

> ⚠️ **Render's free tier has real limits worth knowing before relying on this**: free web
> services spin down after a period of inactivity and take ~30-60s to wake up on the next
> request (the API and Grafana will feel "slow" on the first hit after a while); free Postgres
> instances are deleted after a time limit (historically 30-90 days, but check Render's current
> terms — this changes). Fine for a class project's testing/demo use, not for anything meant to
> keep data indefinitely without upgrading.

## Deploying to Railway (previous — kept for reference)

Three separate Railway services in one project — Railway doesn't run `docker-compose.yml`
directly, each piece is added individually.

1. **Postgres** — in the Railway project, "+ New" → "Database" → "Add PostgreSQL". Railway
   provisions and manages it (including the persistent volume) — no docker-compose equivalent
   needed here.
2. **API** — "+ New" → "GitHub Repo" → this repo. Railway detects the root `Dockerfile`
   automatically. Set the `DATABASE_URL` environment variable to Railway's Postgres connection
   string, but **add `+psycopg` to the scheme** — Railway's variable is `postgresql://...`, this
   code needs `postgresql+psycopg://...` (reference the Postgres service's variable, then edit the
   scheme, rather than retyping the whole URL). Under Settings → Networking, generate a public
   domain — that's the URL `PositioningAPIClient.swift` (iOS) should point at instead of the LAN
   IP placeholder.
3. **Grafana** — "+ New" → "GitHub Repo" → this repo again, but set the service's **Root
   Directory** to `grafana` so it builds `grafana/Dockerfile` (which bakes the provisioning files
   from `grafana/provisioning` into the image — Railway can't bind-mount a volume from the repo
   the way local `docker compose` does) instead of the API's Dockerfile. Set these environment
   variables, pointing at the same Postgres service as the API:
   - `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` —
     reference them from the Postgres service's own variables.
   - `POSTGRES_SSLMODE=require` (Railway's Postgres expects TLS; the local default is `disable`).
   - `GF_SECURITY_ADMIN_USER`/`GF_SECURITY_ADMIN_PASSWORD` — **set these to something other than
     `admin`/`admin` here.** Unlike local `docker compose`, this Grafana is reachable from the
     public internet once you generate a domain for it — leaving the default credentials would let
     anyone who finds the URL into it.
   - Also generate a public domain for this service (Settings → Networking) if you want to open
     dashboards without `docker compose up` running locally.

## Known gaps / next steps

- **Auth is a single shared API key**, not per-device — fine for a class project (stops random
  internet traffic, not a motivated attacker who gets the key), but it's not what a real
  multi-device deployment would use (that'd need per-device keys/tokens, so a leaked or revoked
  key doesn't affect every phone).
- **No migrations** — tables are created with `Base.metadata.create_all()` on startup, which only
  creates tables that don't exist yet; it never alters an existing one. A local Postgres volume
  from before a column was added keeps missing it until the volume is reset (`docker compose down
  -v`) — this bit the Grafana dashboard panels below during their own validation. Switch to
  Alembic once the schema stabilizes.
- **The floor-plan map (`../web`) is a skeleton** — the live dots/tracks and the occupancy heatmap
  layer both work, but the room layout (`src/lib/floor.ts`) still has placeholder coordinates, not
  a real deployment's measurements.
- **`GET /fixes/stream`'s pub/sub is in-process memory** (`app/broadcast.py`) — correct for the
  single Render instance this runs as today, but if this API is ever scaled to more than one
  worker/instance, a client connected to one process won't see fixes published on another. That'd
  need a real broker (Redis pub/sub, or Postgres `LISTEN`/`NOTIFY`) instead.
