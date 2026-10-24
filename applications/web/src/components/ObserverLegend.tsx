import styles from "@/app/dashboard.module.css";
import { observerColor } from "@/lib/floor";
import type { Track } from "@/lib/tracks";

function age(iso: string): string {
  const s = Math.round((Date.now() - new Date(iso).getTime()) / 1000);
  return s < 1 ? "now" : `${s}s ago`;
}

export function ObserverLegend({ tracks }: { tracks: Track[] }) {
  if (tracks.length === 0) {
    return <div className={styles.empty}>No observers currently reporting a position.</div>;
  }
  return (
    <ul className={styles.legend}>
      {tracks.map((t) => (
        <li key={t.observerID} className={styles.legendItem}>
          <span
            className={styles.swatch}
            style={{ background: observerColor(t.observerID) }}
          />
          <div className={styles.legendBody}>
            <span className={styles.mono}>{t.observerID}</span>
            <span className={styles.legendMeta}>
              ({t.current.x.toFixed(2)}, {t.current.y.toFixed(2)}) m · ±
              {t.current.uncertaintyMeters.toFixed(2)} · {age(t.current.recordedAt)}
            </span>
          </div>
        </li>
      ))}
    </ul>
  );
}
