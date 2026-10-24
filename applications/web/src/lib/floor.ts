/**
 * Static floor-plan configuration.
 *
 * The API stores positions as raw metric coordinates (`x`, `y` in meters) but
 * carries NO information about the physical space or where the fixed ESP
 * scanning stations sit. That's a deployment-specific layout, so it lives here
 * as client config. Edit these to match your real room and station placement.
 */

export interface FloorBounds {
  minX: number;
  maxX: number;
  minY: number;
  maxY: number;
}

export interface Station {
  id: string;
  x: number;
  y: number;
}

/** Room extent in meters. Origin (0,0) is the middle of the floor. */
export const FLOOR: FloorBounds = {
  minX: -6,
  maxX: 6,
  minY: -4.5,
  maxY: 4.5,
};

/**
 * Fixed ESP32 scanning stations — the anchors whose RSSI readings the backend
 * turns into the positions plotted on the map. Placeholder coordinates.
 */
export const STATIONS: Station[] = [
  { id: "ESP-NW", x: -5.5, y: 4 },
  { id: "ESP-NE", x: 5.5, y: 4 },
  { id: "ESP-SE", x: 5.5, y: -4 },
  { id: "ESP-SW", x: -5.5, y: -4 },
  { id: "ESP-MID", x: 0, y: 0 },
];

export const floorWidth = (b: FloorBounds = FLOOR) => b.maxX - b.minX;
export const floorHeight = (b: FloorBounds = FLOOR) => b.maxY - b.minY;

/** Pixels per meter for the SVG viewBox — shared by FloorMap and HeatmapLayer so both agree on scale. */
export const PX_PER_M = 48;

/** World (meters) → SVG pixel coordinates. Y is flipped (screen y grows down). */
export function toPx(x: number, y: number, b: FloorBounds = FLOOR): { px: number; py: number } {
  return {
    px: (x - b.minX) * PX_PER_M,
    py: (b.maxY - y) * PX_PER_M,
  };
}

/** Categorical palette for per-observer dots — readable in light and dark. */
const PALETTE = [
  "#4f9cf9",
  "#3fb950",
  "#d29922",
  "#db61a2",
  "#a371f7",
  "#f0883e",
  "#2dd4bf",
];

/** Stable color for an observer id (hash → palette slot). */
export function observerColor(observerID: string): string {
  let hash = 0;
  for (let i = 0; i < observerID.length; i++) {
    hash = (hash * 31 + observerID.charCodeAt(i)) | 0;
  }
  return PALETTE[Math.abs(hash) % PALETTE.length];
}
