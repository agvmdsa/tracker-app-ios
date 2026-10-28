"use client";

import { useCallback, useEffect, useRef, useState } from "react";

export interface PollingState<T> {
  data: T | null;
  error: Error | null;
  /** True only during the very first load (no data yet). */
  loading: boolean;
  /** True while a refresh is in flight but stale data is still shown. */
  refreshing: boolean;
  lastUpdated: Date | null;
  /** Trigger an out-of-band refresh immediately. */
  refresh: () => void;
}

/**
 * Polls an async fetcher on a fixed interval. Used for `GET /health` — a simple liveness check
 * is a natural fit for polling. Position data isn't polled anymore: see useFixStream.ts, which
 * subscribes to `GET /fixes/stream` (Server-Sent Events) instead. Pausing when the tab is hidden
 * avoids wasted calls.
 */
export function usePolling<T>(
  fetcher: () => Promise<T>,
  intervalMs: number,
): PollingState<T> {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<Error | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);

  // Keep the latest fetcher without re-subscribing the interval each render. Written in an
  // effect, not during render — refs are only safe to write outside of render (event handlers,
  // effects); writing one inline in the render body is undefined behavior under React's rules.
  const fetcherRef = useRef(fetcher);
  useEffect(() => {
    fetcherRef.current = fetcher;
  });

  const inFlight = useRef(false);

  const run = useCallback(async () => {
    if (inFlight.current) return;
    inFlight.current = true;
    setRefreshing(true);
    try {
      const result = await fetcherRef.current();
      setData(result);
      setError(null);
      setLastUpdated(new Date());
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      inFlight.current = false;
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    run();
    const id = setInterval(() => {
      if (document.visibilityState === "visible") run();
    }, intervalMs);

    const onVisible = () => {
      if (document.visibilityState === "visible") run();
    };
    document.addEventListener("visibilitychange", onVisible);

    return () => {
      clearInterval(id);
      document.removeEventListener("visibilitychange", onVisible);
    };
  }, [run, intervalMs]);

  return { data, error, loading, refreshing, lastUpdated, refresh: run };
}
