/**
 * Domain types mirroring the TrackerApp Positioning API.
 *
 * Field names are camelCase to match what the API encodes/decodes — the backend
 * deliberately uses Swift's JSONEncoder convention, not Python's snake_case
 * (see api_tracker_app/app/schemas.py).
 */

/** Response of `GET /health`. */
export interface Health {
  status: string; // "ok"
}

/**
 * A single BLE sighting: one anchor a phone (or ESP32 station) currently sees.
 * Mirrors `SightingIn` plus the server-recomputed `distanceMeters` and the row id.
 *
 * `distanceMeters` is always recomputed server-side from `rssi` via path_loss.py,
 * so it is authoritative here even though clients may send their own value.
 */
export interface Sighting {
  id: number;
  observerID: string;
  anchorID: string;
  rssi: number;
  distanceMeters: number;
  seenAt: string; // ISO 8601
  receivedAt: string; // ISO 8601
}

/**
 * An observer's own computed position for a refresh cycle.
 * Mirrors `FixIn` plus the row id and server receive time.
 */
export interface Fix {
  id: number;
  observerID: string;
  x: number;
  y: number;
  uncertaintyMeters: number;
  recordedAt: string; // ISO 8601
  receivedAt: string; // ISO 8601
}

/**
 * Aggregate ingestion stats for the monitoring header.
 *
 * NOTE: there is no `/stats` endpoint on the backend yet — this shape is the
 * contract the dashboard expects once one is added (or once these numbers are
 * derived from `/sightings/recent` + `/fixes/recent`).
 */
export interface IngestionStats {
  activeObservers: number; // distinct observerIDs seen recently
  sightingsPerMinute: number;
  fixesPerMinute: number;
  totalSightings: number;
  totalFixes: number;
  windowSeconds: number; // the "recently" window these numbers cover
}
