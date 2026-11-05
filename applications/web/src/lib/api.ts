/**
 * Typed client for the TrackerApp Positioning API (../api).
 *
 *   GET  /health         → used (api.health()), polled for the liveness badge
 *   GET  /fixes/stream   → what the map subscribes to — Server-Sent Events, see hooks/useFixStream.ts
 *   GET  /fixes/recent   → one-shot snapshot (api.recentFixes()); the map uses the stream instead
 *   POST /fixes, /sightings, /fingerprints → ingestion from the iOS/Android apps and ESP32s, not
 *                          called from here (they need the X-API-Key a browser can't keep secret)
 */

import { config } from "./config";
import type { Fix, Health } from "./types";

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
  /** Liveness check. */
  health(): Promise<Health> {
    return request<Health>("/health");
  },

  /** A one-shot snapshot of recent fixes. The map itself uses useFixStream (SSE) instead. */
  recentFixes(limit = 200): Promise<Fix[]> {
    return request<Fix[]>(`/fixes/recent?limit=${limit}`);
  },
};
