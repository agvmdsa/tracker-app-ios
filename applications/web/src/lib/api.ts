/**
 * Typed client for the TrackerApp Positioning API (../api).
 *
 * Endpoint status against the current backend:
 *   GET  /health             → IMPLEMENTED, used (api.health())
 *   POST /sightings          → IMPLEMENTED (ingestion only, not called from here)
 *   POST /fixes               → IMPLEMENTED (ingestion only, not called from here)
 *   GET  /fixes/stream        → IMPLEMENTED, used — Server-Sent Events, what the map actually
 *                                subscribes to (see hooks/useFixStream.ts). Event-driven, not polled.
 *   GET  /fixes/recent        → IMPLEMENTED, but unused here. recentFixes() below still works (a
 *                                one-shot snapshot) but the map gets its data from the stream instead.
 *   GET  /sightings/recent   → NOT ON THE BACKEND. recentSightings() below is unused dead code.
 *   GET  /stats               → NOT ON THE BACKEND. stats() below is unused dead code.
 *
 * recentSightings()/stats() were written for a monitoring dashboard this app doesn't build yet —
 * nothing in src/app or src/components calls them. Remove them, or implement the endpoints and
 * wire them in, before relying on either.
 */

import { config } from "./config";
import { mockFixes, mockSightings, mockStats } from "./mock";
import type { Fix, Health, IngestionStats, Sighting } from "./types";

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status?: number,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let res: Response;
  try {
    res = await fetch(`${config.apiBaseUrl}${path}`, {
      ...init,
      headers: { "Content-Type": "application/json", ...init?.headers },
      cache: "no-store",
    });
  } catch (cause) {
    throw new ApiError(`Network error reaching ${path}: ${String(cause)}`);
  }
  if (!res.ok) {
    throw new ApiError(`${path} responded ${res.status}`, res.status);
  }
  return (await res.json()) as T;
}

export const api = {
  /** Liveness check — the one GET endpoint that really exists today. */
  health(): Promise<Health> {
    if (config.useMock) return Promise.resolve({ status: "ok" });
    return request<Health>("/health");
  },

  /** Recent sightings. ASSUMED endpoint — served from mock until it exists. */
  recentSightings(limit = 50): Promise<Sighting[]> {
    if (config.useMock) return Promise.resolve(mockSightings(limit));
    return request<Sighting[]>(`/sightings/recent?limit=${limit}`);
  },

  /** A one-shot snapshot of recent fixes. The map itself uses useFixStream (SSE) instead. */
  recentFixes(limit = 200): Promise<Fix[]> {
    if (config.useMock) return Promise.resolve(mockFixes());
    return request<Fix[]>(`/fixes/recent?limit=${limit}`);
  },

  /** Aggregate stats. ASSUMED endpoint — derived from mock until it exists. */
  async stats(): Promise<IngestionStats> {
    if (config.useMock) {
      return mockStats(mockFixes());
    }
    return request<IngestionStats>("/stats");
  },
};
