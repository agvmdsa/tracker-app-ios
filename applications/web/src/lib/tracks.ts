import type { Fix } from "./types";

export interface Track {
  observerID: string;
  /** Newest fix (current position). */
  current: Fix;
  /** Fixes newest → oldest, including `current`, for drawing a trail. */
  history: Fix[];
}

/**
 * Group a flat list of fixes (any order) into one track per observer, each
 * sorted newest → oldest. `maxHistory` caps the trail length per observer.
 */
export function toTracks(fixes: Fix[], maxHistory = 15): Track[] {
  const byObserver = new Map<string, Fix[]>();
  for (const f of fixes) {
    const list = byObserver.get(f.observerID) ?? [];
    list.push(f);
    byObserver.set(f.observerID, list);
  }

  const tracks: Track[] = [];
  for (const [observerID, list] of byObserver) {
    list.sort((a, b) => b.recordedAt.localeCompare(a.recordedAt));
    const history = list.slice(0, maxHistory);
    tracks.push({ observerID, current: history[0], history });
  }
  return tracks.sort((a, b) => a.observerID.localeCompare(b.observerID));
}
