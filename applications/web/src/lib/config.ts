/** Runtime configuration, read from NEXT_PUBLIC_* env vars with safe defaults. */

export const config = {
  apiBaseUrl: process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8000",
  /** `GET /health` poll cadence. Positions aren't polled — they're pushed over `/fixes/stream`. */
  pollIntervalMs: Number(process.env.NEXT_PUBLIC_POLL_INTERVAL_MS ?? "1000"),
} as const;
