package br.org.cesar.blueguide.domain.position

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/** How far one [Anchor] currently looks, by [PathLossModel]. */
data class AnchorRange(
    val anchor: Anchor,
    val distanceMeters: Double,
    /** The median RSSI (dBm) the distance was computed from, when it came from the radio. Shown
     *  on the map, because calibrating the path loss model needs the dBm and not only the distance. */
    val rssi: Double? = null
)

/**
 * Where the observer came out, plus how much to distrust it.
 *
 * [uncertaintyMeters] is the RMS amount by which the range circles fail to meet at
 * [position]. It measures disagreement between the anchors, not a bias shared by all of
 * them (a mis-calibrated reference RSSI, say): that moves the fix without widening this.
 */
data class ObserverFix(
    val readings: List<AnchorRange>,
    val position: Point2D,
    val uncertaintyMeters: Double
)

/**
 * Weighted least squares over every reading — the same solver as the iOS app's
 * `Multilateration.swift`, so both platforms place an observer the same way. It starts from the
 * unweighted linear solution (which also rejects anchors that are in a line) and refines it by
 * Gauss-Newton, so no single anchor, and in particular not the first one in the list, decides
 * the result.
 */
object Multilateration {

    private const val EPSILON = 1e-9

    /** Typical RSSI noise in dB. A fixed error in dB becomes an error that grows with the
     *  distance, so a reading is trusted in proportion to how near its anchor is. */
    const val RSSI_NOISE_DB = 3.0

    /** How wrong a distance is, as a fraction of itself, for [RSSI_NOISE_DB] of noise: about
     *  0.35, i.e. 0.7 m at 2 m and 2.8 m at 8 m. */
    val RELATIVE_DISTANCE_ERROR = ln(10.0) / (10.0 * PathLossModel.PATH_LOSS_EXPONENT) * RSSI_NOISE_DB

    /** Closer than this the error stops shrinking, which also keeps the weight of a tiny
     *  distance finite. Small, so a beacon a few centimetres away decides the fix: indoors the far
     *  beacons read stronger than the model expects, and with 0.5 m they pulled a phone touching
     *  a corner 0.6 m towards the middle. Below 1.45 m only, so the middle is not affected. */
    const val MINIMUM_SIGMA_METERS = 0.1

    /** A beacon this close by its RSSI (about "RSSI encostado" up to a few dB weaker) holds the
     *  phone near it, whatever the others say. */
    const val PROXIMITY_RADIUS_METERS = 0.5

    private const val MAXIMUM_ITERATIONS = 50
    private const val CONVERGED_STEP_METERS = 1e-6
    private const val SMALLEST_STEP_FRACTION = 1e-3

    /** How much a reading counts: 1 / error², with the error proportional to the distance, so
     *  an anchor at 2 m counts 16 times more than one at 8 m. */
    fun weight(distanceMeters: Double): Double {
        val sigma = sigma(distanceMeters)
        return 1 / (sigma * sigma)
    }

    private fun sigma(distanceMeters: Double) = max(MINIMUM_SIGMA_METERS, RELATIVE_DISTANCE_ERROR * distanceMeters)

    fun solve(readings: List<AnchorRange>): ObserverFix? {
        if (readings.size < 3) return null
        val start = linearSolution(readings) ?: return null

        // Not a search from several starts: with equal readings the linear start is the middle,
        // and other starts would pick one of the symmetric minima around it at random.
        val position = heldByTheNearest(refined(start, readings), readings)

        val residual = sqrt(
            readings.sumOf { sq(it.anchor.position.distanceTo(position) - it.distanceMeters) } / readings.size
        )

        return ObserverFix(readings, position, residual)
    }

    /** Subtracts the first anchor's circle from the others, which leaves linear equations. It
     *  counts every reading the same and trusts the first one's distance in every equation, so
     *  it is only the starting point. Null when the anchors are (almost) in a line. */
    private fun linearSolution(readings: List<AnchorRange>): Point2D? {
        val first = readings.first()
        val p1 = first.anchor.position
        val d1 = first.distanceMeters

        var m11 = 0.0
        var m12 = 0.0
        var m22 = 0.0
        var c1 = 0.0
        var c2 = 0.0

        for (reading in readings.drop(1)) {
            val p = reading.anchor.position
            val a1 = 2 * (p.x - p1.x)
            val a2 = 2 * (p.y - p1.y)
            val b = sq(d1) - sq(reading.distanceMeters) + sq(p.x) - sq(p1.x) + sq(p.y) - sq(p1.y)

            m11 += a1 * a1
            m12 += a1 * a2
            m22 += a2 * a2
            c1 += a1 * b
            c2 += a2 * b
        }

        val determinant = m11 * m22 - m12 * m12
        if (abs(determinant) <= EPSILON) return null

        return Point2D(
            x = (c1 * m22 - m12 * c2) / determinant,
            y = (m11 * c2 - c1 * m12) / determinant
        )
    }

    /** Within [PROXIMITY_RADIUS_METERS] of an anchor by its RSSI, and clearly nearer it than any
     *  other (the next at more than twice the range), the fix is kept within the range counted
     *  from touching the anchor: reading "RSSI encostado" ([PathLossModel.TOUCH_DISTANCE_METERS])
     *  or stronger puts the phone on the anchor, and 0.3 m by the model at most 0.2 m from it.
     *  Several anchors all reading that near contradict each other, and hold nothing. */
    private fun heldByTheNearest(position: Point2D, readings: List<AnchorRange>): Point2D {
        val sorted = readings.sortedBy { it.distanceMeters }
        val nearest = sorted[0]
        if (nearest.distanceMeters > PROXIMITY_RADIUS_METERS || sorted[1].distanceMeters <= 2 * nearest.distanceMeters) return position
        val anchor = nearest.anchor.position
        val radius = max(0.0, nearest.distanceMeters - PathLossModel.TOUCH_DISTANCE_METERS)
        val away = position.distanceTo(anchor)
        if (away <= radius) return position
        if (radius == 0.0) return anchor
        return Point2D(anchor.x + (position.x - anchor.x) * radius / away, anchor.y + (position.y - anchor.y) * radius / away)
    }

    /** Gauss-Newton on the weighted range residuals. A step that makes the weighted error worse is
     *  halved until it does not; it stops when even a tiny step does, or the equations degenerate. */
    private fun refined(start: Point2D, readings: List<AnchorRange>): Point2D {
        var position = start
        var cost = weightedCost(position, readings)

        repeat(MAXIMUM_ITERATIONS) {
            var n11 = 0.0
            var n12 = 0.0
            var n22 = 0.0
            var g1 = 0.0
            var g2 = 0.0
            for (reading in readings) {
                val dx = position.x - reading.anchor.position.x
                val dy = position.y - reading.anchor.position.y
                val range = max(hypot(dx, dy), EPSILON)
                val ux = dx / range
                val uy = dy / range
                val weight = weight(reading.distanceMeters)
                val error = reading.distanceMeters - range

                n11 += weight * ux * ux
                n12 += weight * ux * uy
                n22 += weight * uy * uy
                g1 += weight * ux * error
                g2 += weight * uy * error
            }

            val determinant = n11 * n22 - n12 * n12
            if (abs(determinant) <= EPSILON) return position
            val step = Point2D(
                x = (g1 * n22 - n12 * g2) / determinant,
                y = (n11 * g2 - g1 * n12) / determinant
            )

            var fraction = 1.0
            while (true) {
                val candidate = Point2D(position.x + fraction * step.x, position.y + fraction * step.y)
                val candidateCost = weightedCost(candidate, readings)
                if (candidateCost <= cost) {
                    position = candidate
                    cost = candidateCost
                    break
                }
                fraction /= 2
                if (fraction < SMALLEST_STEP_FRACTION) return position
            }

            if (fraction * step.magnitude < CONVERGED_STEP_METERS) return position
        }
        return position
    }

    private fun weightedCost(position: Point2D, readings: List<AnchorRange>): Double =
        readings.sumOf { weight(it.distanceMeters) * sq(it.anchor.position.distanceTo(position) - it.distanceMeters) }

    private fun sq(value: Double) = value * value
}
