"use client";

import { useMemo } from "react";
import { HEATMAP_CELL_SIZE_M, computeHeatmapCells } from "@/lib/heatmap";
import { PX_PER_M, toPx } from "@/lib/floor";
import type { Fix } from "@/lib/types";

const CELL_PX = HEATMAP_CELL_SIZE_M * PX_PER_M;

/**
 * Occupancy/density heatmap — a grid of semi-transparent cells behind the grid lines and the
 * tracked dots, so the two layers read as "where people are now" (dots) and "where they tend to
 * be" (heat) at once. Opacity is `weightedDensity` (see heatmap.ts) scaled by the busiest cell
 * currently in view, square-rooted so one very dense cell doesn't wash the rest out to near-zero.
 */
export function HeatmapLayer({ fixes }: { fixes: Fix[] }) {
  const cells = useMemo(() => computeHeatmapCells(fixes), [fixes]);
  const maxDensity = useMemo(
    () => cells.reduce((max, c) => Math.max(max, c.weightedDensity), 0),
    [cells],
  );

  if (maxDensity === 0) return null;

  return (
    <g aria-hidden="true">
      {cells.map((cell) => {
        const { px, py } = toPx(cell.gridX, cell.gridY);
        const intensity = Math.sqrt(cell.weightedDensity / maxDensity);
        return (
          <rect
            key={`${cell.gridX},${cell.gridY}`}
            x={px - CELL_PX / 2}
            y={py - CELL_PX / 2}
            width={CELL_PX}
            height={CELL_PX}
            fill="#f0883e"
            opacity={0.08 + intensity * 0.55}
          >
            <title>
              {cell.distinctObservers} observer{cell.distinctObservers === 1 ? "" : "s"} near (
              {cell.gridX}, {cell.gridY})
            </title>
          </rect>
        );
      })}
    </g>
  );
}
