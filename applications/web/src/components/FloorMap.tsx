"use client";

import { FLOOR, PX_PER_M, STATIONS, floorHeight, floorWidth, observerColor, toPx } from "@/lib/floor";
import type { Fix } from "@/lib/types";
import type { Track } from "@/lib/tracks";
import { HeatmapLayer } from "./HeatmapLayer";
import mapStyles from "./FloorMap.module.css";

const W = floorWidth() * PX_PER_M;
const H = floorHeight() * PX_PER_M;

function gridLines() {
  const lines = [];
  for (let x = Math.ceil(FLOOR.minX); x <= FLOOR.maxX; x++) {
    const { px } = toPx(x, 0);
    lines.push(
      <line key={`vx${x}`} x1={px} y1={0} x2={px} y2={H} className={mapStyles.grid} />,
    );
  }
  for (let y = Math.ceil(FLOOR.minY); y <= FLOOR.maxY; y++) {
    const { py } = toPx(0, y);
    lines.push(
      <line key={`hy${y}`} x1={0} y1={py} x2={W} y2={py} className={mapStyles.grid} />,
    );
  }
  return lines;
}

export function FloorMap({
  tracks,
  fixes,
  showHeatmap,
}: {
  tracks: Track[];
  /** The flat fix list the heatmap bins — independent of `tracks`, which only keeps each
   * observer's recent trail, not the full window. */
  fixes: Fix[];
  showHeatmap: boolean;
}) {
  return (
    <svg
      className={mapStyles.svg}
      viewBox={`0 0 ${W} ${H}`}
      preserveAspectRatio="xMidYMid meet"
      role="img"
      aria-label="Live floor-plan tracking map"
    >
      {/* floor */}
      <rect x={0} y={0} width={W} height={H} className={mapStyles.floor} />
      {showHeatmap ? <HeatmapLayer fixes={fixes} /> : null}
      {gridLines()}

      {/* fixed ESP scanning stations */}
      {STATIONS.map((s) => {
        const { px, py } = toPx(s.x, s.y);
        return (
          <g key={s.id} transform={`translate(${px} ${py})`}>
            <rect x={-6} y={-6} width={12} height={12} className={mapStyles.station} />
            <text x={0} y={-10} className={mapStyles.stationLabel} textAnchor="middle">
              {s.id}
            </text>
          </g>
        );
      })}

      {/* tracked observers */}
      {tracks.map((t) => {
        const color = observerColor(t.observerID);
        const pts = t.history.map((f) => toPx(f.x, f.y));
        const trail = pts.map((p) => `${p.px},${p.py}`).join(" ");
        const head = pts[0];
        const uncertaintyPx = t.current.uncertaintyMeters * PX_PER_M;
        return (
          <g key={t.observerID}>
            {pts.length > 1 ? (
              <polyline
                points={trail}
                fill="none"
                stroke={color}
                strokeWidth={2}
                strokeLinejoin="round"
                strokeLinecap="round"
                opacity={0.4}
              />
            ) : null}
            {/* current position — transform transition makes it glide between polls */}
            <g className={mapStyles.mover} transform={`translate(${head.px} ${head.py})`}>
              <circle r={uncertaintyPx} fill={color} opacity={0.12} />
              <circle r={6} fill={color} stroke="#0b0e14" strokeWidth={1.5} />
              <text x={10} y={4} className={mapStyles.moverLabel}>
                {t.observerID}
              </text>
            </g>
          </g>
        );
      })}
    </svg>
  );
}
