import asyncio
import json
import os
from collections.abc import AsyncIterator
from datetime import datetime, timezone

from fastapi import Depends, FastAPI, Header, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.broadcast import broadcaster
from app.database import Base, engine, get_db
from app.models import Fix, FingerprintRecord, Sighting
from app.mqtt_consumer import start_mqtt_consumer, stop_mqtt_consumer
from app.path_loss import distance_meters
from app.schemas import FingerprintRecordIn, FixIn, FixOut, SightingIn

app = FastAPI(title="TrackerApp Positioning API")
_mqtt_client = None

# The web map (applications/web) is a browser app on a different origin, so it needs CORS to
# read GET /fixes/recent. CORS_ORIGINS is a comma-separated allowlist; default "*" is fine for
# local dev but should be set to the real frontend origin in prod.
_origins = os.environ.get("CORS_ORIGINS", "*").split(",")
app.add_middleware(
    CORSMiddleware,
    allow_origins=[o.strip() for o in _origins],
    allow_methods=["*"],
    allow_headers=["*"],
)

# Required, no default: a missing API_KEY should fail loudly at startup rather than silently
# accept every request once deployed.
API_KEY = os.environ["API_KEY"]


def require_api_key(x_api_key: str | None = Header(default=None, alias="X-API-Key")) -> None:
    if x_api_key != API_KEY:
        raise HTTPException(status_code=401, detail="Invalid or missing API key")


@app.on_event("startup")
def create_tables() -> None:
    Base.metadata.create_all(bind=engine)


@app.on_event("startup")
def start_mqtt() -> None:
    global _mqtt_client
    _mqtt_client = start_mqtt_consumer()


@app.on_event("shutdown")
def stop_mqtt() -> None:
    stop_mqtt_consumer(_mqtt_client)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/sightings", status_code=201, dependencies=[Depends(require_api_key)])
def create_sightings(reports: list[SightingIn], db: Session = Depends(get_db)) -> dict[str, int]:
    received_at = datetime.now(timezone.utc)
    rows = [
        Sighting(
            observer_id=report.observerID,
            anchor_id=report.anchorID,
            rssi=report.rssi,
            distance_meters=distance_meters(report.rssi),
            seen_at=report.seenAt,
            received_at=received_at,
            sample_count=report.sampleCount,
            rssi_std_dev=report.rssiStdDev,
            device_model=report.deviceModel,
        )
        for report in reports
    ]
    db.add_all(rows)
    db.commit()
    return {"inserted": len(rows)}


def _fix_out(row: Fix) -> FixOut:
    return FixOut(
        id=row.id,
        observerID=row.observer_id,
        x=row.x,
        y=row.y,
        uncertaintyMeters=row.uncertainty_meters,
        recordedAt=row.recorded_at,
        receivedAt=row.received_at,
    )


@app.post("/fixes", status_code=201, dependencies=[Depends(require_api_key)])
def create_fix(report: FixIn, db: Session = Depends(get_db)) -> dict[str, int]:
    row = Fix(
        observer_id=report.observerID,
        x=report.x,
        y=report.y,
        uncertainty_meters=report.uncertaintyMeters,
        recorded_at=report.recordedAt,
        received_at=datetime.now(timezone.utc),
        anchor_count=report.anchorCount,
    )
    db.add(row)
    db.commit()
    db.refresh(row)
    broadcaster.publish(_fix_out(row).model_dump(mode="json"))
    return {"inserted": 1}


@app.get("/fixes/recent")
def recent_fixes(
    limit: int = Query(200, ge=1, le=1000),
    db: Session = Depends(get_db),
) -> list[FixOut]:
    """Most recent position fixes, newest first. No `X-API-Key` here, unlike the POST endpoints:
    a browser app can't keep the key secret (anyone can open dev tools and read it out of the
    JS), so this stays open like `/health` — it only reads, and a fix's
    `x`/`y`/`uncertaintyMeters` aren't sensitive. Superseded by `GET /fixes/stream` for the live
    map (push instead of poll) — kept for anything that wants a one-shot snapshot instead.
    """
    rows = (
        db.execute(select(Fix).order_by(Fix.recorded_at.desc()).limit(limit))
        .scalars()
        .all()
    )
    return [_fix_out(row) for row in rows]


@app.get("/fixes/stream")
async def stream_fixes(
    limit: int = Query(200, ge=1, le=1000),
    db: Session = Depends(get_db),
) -> StreamingResponse:
    """Server-Sent Events: an initial snapshot (event `snapshot`, a JSON array, oldest first so
    the client can append in order), then one `fix` event per new position as `POST /fixes`
    receives it — the push side of what `GET /fixes/recent` reads as a one-shot pull. No
    `X-API-Key`, same reasoning as `/fixes/recent`.
    """
    rows = (
        db.execute(select(Fix).order_by(Fix.recorded_at.desc()).limit(limit))
        .scalars()
        .all()
    )
    snapshot = [_fix_out(row).model_dump(mode="json") for row in reversed(rows)]

    async def events() -> AsyncIterator[str]:
        yield f"event: snapshot\ndata: {json.dumps(snapshot)}\n\n"
        queue = broadcaster.subscribe()
        try:
            while True:
                fix = await queue.get()
                yield f"event: fix\ndata: {json.dumps(fix)}\n\n"
        except asyncio.CancelledError:
            pass
        finally:
            broadcaster.unsubscribe(queue)

    return StreamingResponse(events(), media_type="text/event-stream")


@app.post("/fingerprints", status_code=201, dependencies=[Depends(require_api_key)])
def create_fingerprint_records(
    reports: list[FingerprintRecordIn], db: Session = Depends(get_db)
) -> dict[str, int]:
    received_at = datetime.now(timezone.utc)
    rows = [
        FingerprintRecord(
            node_id=report.nodeID,
            platform=report.platform,
            model=report.model,
            system_version=report.systemVersion,
            beacon_id=report.beaconID,
            rssi=report.rssi,
            roll=report.roll,
            pitch=report.pitch,
            yaw=report.yaw,
            relative_altitude_m=report.relativeAltitudeM,
            pressure_kpa=report.pressureKPa,
            motion_state=report.motionState,
            motion_confidence=report.motionConfidence,
            session=report.session,
            point=report.point,
            x=report.x,
            y=report.y,
            seen_at=report.seenAt or received_at,
            received_at=received_at,
        )
        for report in reports
    ]
    db.add_all(rows)
    db.commit()
    return {"inserted": len(rows)}
