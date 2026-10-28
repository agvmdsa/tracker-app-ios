-- Grafana panel queries for the Positioning API's `fixes` table.
-- Paste the SELECT directly into a Grafana panel's "Code" query editor (Postgres datasource).
-- $__timeFilter(recorded_at) is a Grafana macro — it expands to a WHERE clause using the
-- dashboard's time range picker, so these queries respect whatever window you select in Grafana.

-- ============================================================
-- 1. Heatmap density — grid cells weighted by fix confidence
-- ============================================================
-- Panel type: Heatmap (or Geomap with a static floor-plan image as the base layer, x/y as
-- lon/lat-like coordinates). 0.5 is the grid cell size in metres — shrink it for a sharper
-- heatmap once there's enough data density to support it, grow it if cells look too sparse.
-- `greatest(uncertainty_meters, 0.1)` floors the weight so a near-zero uncertainty fix doesn't
-- dominate the whole map by itself.
SELECT
    round(x / 0.5) * 0.5 AS grid_x,
    round(y / 0.5) * 0.5 AS grid_y,
    count(*) AS fix_count,
    sum(1.0 / greatest(uncertainty_meters, 0.1)) AS weighted_density
FROM fixes
WHERE $__timeFilter(recorded_at)
GROUP BY grid_x, grid_y
ORDER BY weighted_density DESC;

-- ============================================================
-- 2. Occupancy — distinct observers seen per grid cell
-- ============================================================
-- Panel type: Heatmap or a table. This is the "how many different phones were in this area"
-- view — counts distinct observer_id, not raw fix count, so one phone sending fixes every
-- second doesn't look like a crowd.
SELECT
    round(x / 0.5) * 0.5 AS grid_x,
    round(y / 0.5) * 0.5 AS grid_y,
    count(DISTINCT observer_id) AS distinct_observers
FROM fixes
WHERE $__timeFilter(recorded_at)
GROUP BY grid_x, grid_y
ORDER BY distinct_observers DESC;

-- ============================================================
-- 3. Peak hours — traffic over time (not spatial)
-- ============================================================
-- Panel type: Time series. date_trunc('hour', ...) buckets into hourly columns; swap for
-- 'day'/'minute' depending on how long a time range the dashboard is set to.
SELECT
    date_trunc('hour', recorded_at) AS time,
    count(DISTINCT observer_id) AS distinct_observers,
    count(*) AS fix_count
FROM fixes
WHERE $__timeFilter(recorded_at)
GROUP BY time
ORDER BY time;

-- ============================================================
-- 4. Dwell time per cell — "stopped here" vs "walked through"
-- ============================================================
-- Panel type: Heatmap or table. No new phone data needed for this — it's inferred purely from
-- consecutive fixes already being sent: if an observer's position barely moves between two
-- fixes (< 0.5m), the elapsed time between them counts as "dwelling" at that cell. A phone that
-- jumps between distant fixes contributes nothing (it was just passing through).
WITH ordered_fixes AS (
    SELECT
        observer_id,
        x,
        y,
        recorded_at,
        lag(x) OVER (PARTITION BY observer_id ORDER BY recorded_at) AS prev_x,
        lag(y) OVER (PARTITION BY observer_id ORDER BY recorded_at) AS prev_y,
        lag(recorded_at) OVER (PARTITION BY observer_id ORDER BY recorded_at) AS prev_time
    FROM fixes
    WHERE $__timeFilter(recorded_at)
),
movement AS (
    SELECT
        x,
        y,
        sqrt(power(x - prev_x, 2) + power(y - prev_y, 2)) AS distance_moved,
        extract(epoch FROM (recorded_at - prev_time)) AS seconds_elapsed
    FROM ordered_fixes
    WHERE prev_x IS NOT NULL
)
SELECT
    round(x / 0.5) * 0.5 AS grid_x,
    round(y / 0.5) * 0.5 AS grid_y,
    sum(CASE WHEN distance_moved < 0.5 THEN seconds_elapsed ELSE 0 END) AS dwell_seconds
FROM movement
GROUP BY grid_x, grid_y
ORDER BY dwell_seconds DESC;
