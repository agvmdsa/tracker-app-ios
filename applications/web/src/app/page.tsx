"use client";

import { useCallback, useMemo, useState } from "react";
import styles from "./dashboard.module.css";
import { FloorMap } from "@/components/FloorMap";
import { HealthBadge } from "@/components/HealthBadge";
import { ObserverLegend } from "@/components/ObserverLegend";
import { StatCard } from "@/components/StatCard";
import { useFixStream } from "@/hooks/useFixStream";
import { usePolling } from "@/hooks/usePolling";
import { api } from "@/lib/api";
import { config } from "@/lib/config";
import { toTracks } from "@/lib/tracks";

export default function DashboardPage() {
  const health = usePolling(useCallback(() => api.health(), []), config.pollIntervalMs);
  // Event-driven, not polled: subscribes once to GET /fixes/stream (Server-Sent Events) and gets
  // pushed each new fix as the backend receives it, instead of re-fetching on a timer.
  const fixes = useFixStream();
  const [showHeatmap, setShowHeatmap] = useState(true);

  const tracks = useMemo(() => toTracks(fixes.data ?? []), [fixes.data]);

  const healthStatus = health.error
    ? "down"
    : health.data?.status === "ok"
      ? "ok"
      : "unknown";
  const streamStatus = fixes.error ? "down" : fixes.connected ? "ok" : "unknown";

  return (
    <main className={styles.page}>
      <header className={styles.header}>
        <div>
          <h1 className={styles.title}>TrackerApp — Live Position Map</h1>
          <p className={styles.subtitle}>
            Tracking observer positions from <span className={styles.mono}>/fixes/stream</span>{" "}
            (Server-Sent Events) at{" "}
            <span className={styles.mono}>{config.apiBaseUrl}</span>
          </p>
        </div>
        <div className={styles.meta}>
          <HealthBadge status={healthStatus} />
          <span className={styles.statusRow}>
            <span
              className={`${styles.dot} ${streamStatus === "ok" ? styles.dotOk : streamStatus === "down" ? styles.dotBad : styles.dotWarn}`}
            />
            {streamStatus === "ok" ? "Live" : streamStatus === "down" ? "Reconnecting…" : "Connecting…"}
          </span>
          <span>Updated {fixes.lastUpdated ? fixes.lastUpdated.toLocaleTimeString() : "—"}</span>
          <button className={styles.button} onClick={() => health.refresh()}>
            Refresh health
          </button>
        </div>
      </header>

      {config.useMock ? (
        <div className={styles.banner}>
          <strong>Mock mode.</strong> The dots below are simulated tracks, not real
          data — <span className={styles.mono}>GET /fixes/stream</span> exists on the
          backend now, this is just the default while developing without it running. Set{" "}
          <span className={styles.mono}>NEXT_PUBLIC_USE_MOCK=false</span> (the
          docker-compose setup already does) to read real positions from{" "}
          <span className={styles.mono}>{config.apiBaseUrl}</span>.
        </div>
      ) : null}

      {fixes.error ? (
        <div className={styles.errorBanner}>{fixes.error.message}</div>
      ) : null}

      <section className={styles.mapLayout}>
        <div className={styles.card}>
          <label className={styles.checkboxRow}>
            <input
              type="checkbox"
              checked={showHeatmap}
              onChange={(e) => setShowHeatmap(e.target.checked)}
            />
            Occupancy heatmap
          </label>
          <FloorMap tracks={tracks} fixes={fixes.data ?? []} showHeatmap={showHeatmap} />
        </div>

        <div className={styles.side}>
          <section className={styles.statStack}>
            <StatCard label="Tracked observers" value={tracks.length} />
            <StatCard
              label="Position fixes"
              value={fixes.data?.length ?? "—"}
              hint="in current window"
            />
          </section>
          <div className={styles.card}>
            <div className={styles.panelHead}>
              <h2 className={styles.panelTitle}>Observers</h2>
            </div>
            <ObserverLegend tracks={tracks} />
          </div>
        </div>
      </section>
    </main>
  );
}
