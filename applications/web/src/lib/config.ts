/** Runtime configuration, read from NEXT_PUBLIC_* env vars with safe defaults. */

export const config = {
  apiBaseUrl: process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8000",
  pollIntervalMs: Number(process.env.NEXT_PUBLIC_POLL_INTERVAL_MS ?? "1000"),
  // Defaults to mock mode because the API has no GET read endpoints yet.
  useMock: (process.env.NEXT_PUBLIC_USE_MOCK ?? "true") === "true",
} as const;
