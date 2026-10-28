from datetime import datetime

from sqlalchemy import DateTime, Float, Integer, String
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


class Sighting(Base):
    __tablename__ = "sightings"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    # Not fixed at 8 chars (the length of a BeaconID) — an ESP32 station or other non-phone
    # sender identifies itself with its own, differently-shaped id.
    observer_id: Mapped[str] = mapped_column(String(64), index=True)
    anchor_id: Mapped[str] = mapped_column(String(64), index=True)
    rssi: Mapped[int] = mapped_column(Integer)
    distance_meters: Mapped[float] = mapped_column(Float)
    seen_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), index=True)
    received_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    # Nullable: optional on the wire too, so older clients and the ESP32 aren't forced to send
    # them.
    sample_count: Mapped[int | None] = mapped_column(Integer, nullable=True)
    rssi_std_dev: Mapped[float | None] = mapped_column(Float, nullable=True)
    device_model: Mapped[str | None] = mapped_column(String(64), nullable=True)


class Fix(Base):
    __tablename__ = "fixes"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    observer_id: Mapped[str] = mapped_column(String(64), index=True)
    x: Mapped[float] = mapped_column(Float)
    y: Mapped[float] = mapped_column(Float)
    uncertainty_meters: Mapped[float] = mapped_column(Float)
    recorded_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), index=True)
    received_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    anchor_count: Mapped[int | None] = mapped_column(Integer, nullable=True)


class FingerprintRecord(Base):
    """One labelled training row — see `FingerprintRecordIn` for why `x`/`y` here are ground
    truth, unlike `Fix.x`/`Fix.y`."""

    __tablename__ = "fingerprint_records"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    node_id: Mapped[str] = mapped_column(String(64), index=True)
    platform: Mapped[str] = mapped_column(String(16))
    model: Mapped[str | None] = mapped_column(String(64), nullable=True)
    system_version: Mapped[str | None] = mapped_column(String(32), nullable=True)
    beacon_id: Mapped[str] = mapped_column(String(64), index=True)
    rssi: Mapped[int] = mapped_column(Integer)
    roll: Mapped[float | None] = mapped_column(Float, nullable=True)
    pitch: Mapped[float | None] = mapped_column(Float, nullable=True)
    yaw: Mapped[float | None] = mapped_column(Float, nullable=True)
    relative_altitude_m: Mapped[float | None] = mapped_column(Float, nullable=True)
    pressure_kpa: Mapped[float | None] = mapped_column(Float, nullable=True)
    motion_state: Mapped[str | None] = mapped_column(String(16), nullable=True)
    motion_confidence: Mapped[int | None] = mapped_column(Integer, nullable=True)
    session: Mapped[str] = mapped_column(String(16), index=True)
    point: Mapped[str] = mapped_column(String(16))
    x: Mapped[float] = mapped_column(Float)
    y: Mapped[float] = mapped_column(Float)
    seen_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), index=True)
    received_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
