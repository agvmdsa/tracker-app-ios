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
