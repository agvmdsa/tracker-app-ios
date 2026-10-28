"""Subscribes to the ESP32 collectors' live RSSI telemetry over MQTT and persists it the same
way `POST /sightings` does. This is the project's IoT/pub-sub data path — distinct from the
HTTP-only paths: `/sightings`/`/fixes` are phones reporting computed results, `/fingerprints` is
a one-shot batch upload after a finished recording, and this is the ESP32 collector's continuous,
unlabelled telemetry (the same data its `L`/idle-view serial lines carry), which a pub/sub broker
fits better than a request per reading would.

Optional: does nothing if `MQTT_HOST` isn't set, same convention `API_KEY` does NOT follow (that
one's required) — MQTT is additive telemetry, not core to the API working, so a missing broker
shouldn't block startup or break the rest of the API.
"""

import json
import logging
import os
from datetime import datetime, timezone

import paho.mqtt.client as mqtt

from app.database import SessionLocal
from app.models import Sighting
from app.path_loss import distance_meters

logger = logging.getLogger("mqtt_consumer")

MQTT_HOST = os.environ.get("MQTT_HOST", "")
MQTT_PORT = int(os.environ.get("MQTT_PORT", "1883"))
# Matches embedded/include/config.example.h's TOPIC_PREFIX default ("tracker") and the node-scoped
# shape a collector would publish under, e.g. tracker/A1B2C3/sightings.
MQTT_TOPIC = os.environ.get("MQTT_TOPIC", "tracker/+/sightings")


def _insert_sighting(payload: dict) -> None:
    """One JSON object per MQTT message, same field names/shapes as `SightingIn` (see
    app/schemas.py) — camelCase, not the serial protocol's CSV-ish `R`/`L` line format. A
    malformed message is logged and dropped, not raised: one bad reading from one board
    shouldn't take down the subscriber thread.

    `seenAt` is optional, same reasoning as `FingerprintRecordIn.seenAt`: the ESP32 has no
    battery-backed, NTP-synced clock by default, so it may not send one — falls back to the
    received-at time when absent.
    """
    received_at = datetime.now(timezone.utc)
    seen_at_field = payload.get("seenAt")
    seen_at = datetime.fromisoformat(seen_at_field.replace("Z", "+00:00")) if seen_at_field else received_at
    db = SessionLocal()
    try:
        db.add(
            Sighting(
                observer_id=payload["observerID"],
                anchor_id=payload["anchorID"],
                rssi=payload["rssi"],
                distance_meters=distance_meters(payload["rssi"]),
                seen_at=seen_at,
                received_at=received_at,
                sample_count=payload.get("sampleCount"),
                rssi_std_dev=payload.get("rssiStdDev"),
                device_model=payload.get("deviceModel"),
            )
        )
        db.commit()
    finally:
        db.close()


def _on_connect(client: mqtt.Client, userdata: object, flags: dict, reason_code: object, properties: object = None) -> None:
    logger.info("connected to MQTT broker %s:%s, subscribing to %s", MQTT_HOST, MQTT_PORT, MQTT_TOPIC)
    client.subscribe(MQTT_TOPIC)


def _on_message(client: mqtt.Client, userdata: object, msg: mqtt.MQTTMessage) -> None:
    try:
        payload = json.loads(msg.payload.decode("utf-8"))
        _insert_sighting(payload)
    except Exception:
        logger.exception("dropping unprocessable MQTT message on %s", msg.topic)


def start_mqtt_consumer() -> mqtt.Client | None:
    """Starts the subscriber in a background thread (`loop_start`) and returns the client, or
    None when `MQTT_HOST` isn't set. `connect_async` + `loop_start` retry the connection on their
    own if the broker isn't reachable yet — this never blocks or raises on startup.
    """
    if not MQTT_HOST:
        logger.info("MQTT_HOST not set, MQTT telemetry consumer disabled")
        return None
    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2)
    client.on_connect = _on_connect
    client.on_message = _on_message
    client.connect_async(MQTT_HOST, MQTT_PORT, keepalive=60)
    client.loop_start()
    return client


def stop_mqtt_consumer(client: mqtt.Client | None) -> None:
    if client is None:
        return
    client.loop_stop()
    client.disconnect()
