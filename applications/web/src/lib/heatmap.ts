import type { Fix } from "./types";

/** Metres per grid cell — matches ../api/grafana/queries.sql's binning exactly, so the live map
 * and the historical Grafana panels agree on what a "cell" is. */
export const HEATMAP_CELL_SIZE_M = 0.5;

export interface HeatmapCell {
  gridX: number;
  gridY: number;
  /** Distinct observerIDs seen in this cell — "how many different phones/stations", not how
   * many fixes (one phone sending often shouldn't look like a crowd). */
  distinctObservers: number;
  /** Sum of 1/max(uncertaintyMeters, 0.1) over the cell's fixes — a confident (low-uncertainty)
   * fix counts for more than a noisy one, same weighting as the Grafana density query. */
  weightedDensity: number;
}

function snapToGrid(value: number, cellSize: number): number {
  return Math.round(value / cellSize) * cellSize;
}

/**
 * Bins `fixes` into `cellSize`-metre grid cells (occupancy + weighted density per cell), the
 * same two measures ../api/grafana/queries.sql computes over all of Postgres — here, computed
 * client-side over whatever window of fixes useFixStream currently holds in memory.
 */
export function computeHeatmapCells(fixes: Fix[], cellSize = HEATMAP_CELL_SIZE_M): HeatmapCell[] {
  const cells = new Map<
    string,
    { gridX: number; gridY: number; observers: Set<string>; weightedDensity: number }
  >();

  for (const fix of fixes) {
    const gridX = snapToGrid(fix.x, cellSize);
    const gridY = snapToGrid(fix.y, cellSize);
    const key = `${gridX},${gridY}`;
    let cell = cells.get(key);
    if (!cell) {
      cell = { gridX, gridY, observers: new Set(), weightedDensity: 0 };
      cells.set(key, cell);
    }
    cell.observers.add(fix.observerID);
    cell.weightedDensity += 1 / Math.max(fix.uncertaintyMeters, 0.1);
  }

  return Array.from(cells.values()).map((cell) => ({
    gridX: cell.gridX,
    gridY: cell.gridY,
    distinctObservers: cell.observers.size,
    weightedDensity: cell.weightedDensity,
  }));
}
