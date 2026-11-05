"use client";

import { useEffect, useRef, useState } from "react";
import { config } from "@/lib/config";
import type { Fix } from "@/lib/types";

export interface FixStreamState {
  data: Fix[] | null;
  /** True once the snapshot has arrived and the stream is open. */
  connected: boolean;
  error: Error | null;
  lastUpdated: Date | null;
}

/** How many fixes to keep in memory for the trail — bounds growth on a long-running tab. */
const MAX_FIXES = 500;

/**
 * Event-driven position feed: subscribes to `GET /fixes/stream` (Server-Sent Events) instead of
 * polling `GET /fixes/recent` on an interval. The browser's EventSource handles reconnection on
 * its own (with backoff) when the connection drops — nothing here needs to retry manually.
 */
export function useFixStream(): FixStreamState {
  const [data, setData] = useState<Fix[] | null>(null);
  const [connected, setConnected] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
  const fixesRef = useRef<Fix[]>([]);

  useEffect(() => {
    const source = new EventSource(`${config.apiBaseUrl}/fixes/stream`);

    source.addEventListener("snapshot", (event) => {
      fixesRef.current = JSON.parse((event as MessageEvent<string>).data) as Fix[];
      setData(fixesRef.current);
      setConnected(true);
      setError(null);
      setLastUpdated(new Date());
    });

    source.addEventListener("fix", (event) => {
      const fix = JSON.parse((event as MessageEvent<string>).data) as Fix;
      fixesRef.current = [...fixesRef.current, fix].slice(-MAX_FIXES);
      setData(fixesRef.current);
      setConnected(true);
      setError(null);
      setLastUpdated(new Date());
    });

    // EventSource retries on its own; this only reflects the current state, it doesn't need to
    // (and shouldn't) call source.close() and reconnect manually.
    source.onerror = () => {
      setConnected(false);
      setError(new Error("Lost connection to the position stream — reconnecting…"));
    };

    return () => source.close();
  }, []);

  return { data, connected, error, lastUpdated };
}
