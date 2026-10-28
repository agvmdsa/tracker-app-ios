/**
 * Client-side mock data generator.
 *
 * The API is ingestion-only today — nothing returns stored fixes — so the map
 * can't show real tracks yet. This fabricates a few observers moving along
 * smooth, continuous paths (a function of wall-clock time), so that polling
 * reads back coherent movement and a trail rather than random jumps. Each poll
 * samples the trajectory at `now` and a handful of past instants.
 *
 * Delete (or stop importing) once `/fixes/recent` exists on the backend.
 */

import { FLOOR } from "./floor";
import type { Fix, IngestionStats, Sighting } from "./types";

interface Mover {
  observerID: string;
  cx: number; // path center
  cy: number;
  ax: number; // amplitude
  ay: number;
  wx: number; // angular speed (rad/s)
  wy: number;
  phase: number;
}

const MOVERS: Mover[] = [
  { observerID: "B7E2F910", cx: -2, cy: 1, ax: 3, ay: 2.5, wx: 0.25, wy: 0.33, phase: 0 },
  { observerID: "A1C4D230", cx: 2.5, cy: -1, ax: 2.5, ay: 2, wx: 0.3, wy: 0.21, phase: 1.7 },
  { observerID: "9F0E1122", cx: 0, cy: 0, ax: 4, ay: 3, wx: 0.17, wy: 0.27, phase: 3.1 },
];

function clamp(v: number, lo: number, hi: number): number {
  return Math.max(lo, Math.min(hi, v));
}

/** Position of a mover at absolute time `tMs`. */
function positionAt(m: Mover, tMs: number): { x: number; y: number } {
  const t = tMs / 1000;
  return {
    x: clamp(m.cx + m.ax * Math.sin(t * m.wx + m.phase), FLOOR.minX, FLOOR.maxX),
    y: clamp(m.cy + m.ay * Math.cos(t * m.wy + m.phase), FLOOR.minY, FLOOR.maxY),
  };
}

/**
 * Recent fixes across all movers, newest first — `trailLength` samples each,
 * spaced `stepMs` apart. The map groups these back by observer into tracks.
 */
export function mockFixes(trailLength = 12, stepMs = 1000): Fix[] {
  const now = Date.now();
  const out: Fix[] = [];
  for (const m of MOVERS) {
    for (let k = 0; k < trailLength; k++) {
      const t = now - k * stepMs;
      const { x, y } = positionAt(m, t);
      out.push({
        id: Number(`${m.observerID.charCodeAt(0)}${t}`) % Number.MAX_SAFE_INTEGER,
        observerID: m.observerID,
        x: Number(x.toFixed(2)),
        y: Number(y.toFixed(2)),
        uncertaintyMeters: Number((0.2 + 0.3 * Math.abs(Math.sin(t / 900))).toFixed(2)),
        recordedAt: new Date(t).toISOString(),
        receivedAt: new Date(t + 40).toISOString(),
      });
    }
  }
  return out.sort((a, b) => b.recordedAt.localeCompare(a.recordedAt));
}

const ANCHORS = ["ESP-NW", "ESP-NE", "ESP-SE", "ESP-SW", "ESP-MID"];

/** A few raw sightings — the RSSI readings the fixes are derived from. */
export function mockSightings(count = 20): Sighting[] {
  const now = Date.now();
  return Array.from({ length: count }, (_, i) => {
    const rssi = -45 - Math.floor(Math.random() * 45);
    return {
      id: 900000 - i,
      observerID: MOVERS[i % MOVERS.length].observerID,
      anchorID: ANCHORS[i % ANCHORS.length],
      rssi,
      distanceMeters: Number(Math.pow(10, (-59 - rssi) / 20).toFixed(2)),
      seenAt: new Date(now - i * 300).toISOString(),
      receivedAt: new Date(now - i * 300 + 30).toISOString(),
    };
  });
}

export function mockStats(fixes: Fix[]): IngestionStats {
  const observers = new Set(fixes.map((f) => f.observerID));
  return {
    activeObservers: observers.size,
    sightingsPerMinute: 40 + Math.floor(Math.random() * 20),
    fixesPerMinute: observers.size * 60,
    totalSightings: 100000 + Math.floor(Math.random() * 5000),
    totalFixes: 50000 + Math.floor(Math.random() * 2000),
    windowSeconds: 60,
  };
}
