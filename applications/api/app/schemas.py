from datetime import datetime

from pydantic import BaseModel


class SightingIn(BaseModel):
    observerID: str
    anchorID: str
    rssi: int
    # Optional and ignored server-side — kept so existing clients (the iOS app) that already
    # compute and send this don't need to change. distance_meters is always recomputed from
    # rssi in path_loss.py instead, so there's one canonical formula instead of one per client
    # (Swift today, Kotlin and ESP32 firmware eventually).
    distanceMeters: float | None = None
    seenAt: datetime
    # Confidence signals, and the statistical basis an RSSI fingerprint would need later.
    # Optional so older clients (and the ESP32, which may not batch multiple samples) aren't
    # forced to send them.
    sampleCount: int | None = None
    rssiStdDev: float | None = None
    deviceModel: str | None = None


class FixIn(BaseModel):
    observerID: str
    x: float
    y: float
    uncertaintyMeters: float
    recordedAt: datetime
    anchorCount: int | None = None


class FixOut(BaseModel):
    # Read-side shape for the web map (GET /fixes/recent). camelCase to match the rest of the
    # API (and the frontend's Fix type) rather than the ORM's snake_case columns, which are
    # mapped over in the endpoint.
    id: int
    observerID: str
    x: float
    y: float
    uncertaintyMeters: float
    recordedAt: datetime
    receivedAt: datetime


class FingerprintRecordIn(BaseModel):
    """One labelled sighting for training a fingerprinting model — same shape as one row of the
    iOS app's FingerprintRecorder CSV, or one `R` record from the ESP32 collector. Unlike
    SightingIn/FixIn, `x`/`y` here are the recording's *known* position (the ground truth a model
    learns from), not a computed estimate.
    """

    nodeID: str
    platform: str
    model: str | None = None
    systemVersion: str | None = None
    beaconID: str
    rssi: int
    # Device motion (iOS only — the ESP32 doesn't have these sensors).
    roll: float | None = None
    pitch: float | None = None
    yaw: float | None = None
    relativeAltitudeM: float | None = None
    pressureKPa: float | None = None
    motionState: str | None = None
    motionConfidence: int | None = None
    # The label: where the node actually was while this row was recorded.
    session: str
    point: str
    x: float
    y: float
    # Optional: the ESP32 has no battery-backed, NTP-synced clock by default, and the model only
    # needs rssi/session/point/x/y — wall-clock time isn't a training feature. Falls back to the
    # server's received-at time when absent.
    seenAt: datetime | None = None
