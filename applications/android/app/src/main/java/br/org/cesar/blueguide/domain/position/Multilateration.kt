package br.org.cesar.blueguide.domain.position

import kotlin.math.abs
import kotlin.math.sqrt

/** How far one [Anchor] currently looks, by [PathLossModel]. */
data class AnchorRange(
    val anchor: Anchor,
    val distanceMeters: Double
)

/**
 * Where the observer came out, plus how much to distrust it.
 *
 * [uncertaintyMeters] is the RMS amount by which the range circles fail to meet at
 * [position]. It measures disagreement between the anchors, not a bias shared by all of
 * them (a mis-calibrated [PathLossModel.REFERENCE_RSSI], say): that moves the fix without
 * widening this at all.
 */
data class ObserverFix(
    val readings: List<AnchorRange>,
    val position: Point2D,
    val uncertaintyMeters: Double
)

/**
 * Generalized multilateration: any three or more ranges, not a fixed three like the old
 * `Trilateration` this replaces. Mirrors the iOS app's `Multilateration.swift` exactly, so
 * both platforms place an observer the same way regardless of how many anchors are
 * configured.
 *
 * Subtracting the first circle's equation from every other one cancels the quadratic term,
 * leaving one linear equation per remaining anchor; the least-squares solution of that
 * (overdetermined for more than 3 anchors, exactly determined for 3) is a straightforward
 * 2x2 normal-equations solve. With noisy ranges the circles do not truly intersect and this
 * lands somewhere sensible between them rather than failing; [ObserverFix.uncertaintyMeters]
 * then says how far from consistent they were.
 */
object Multilateration {

    private const val EPSILON = 1e-9

    fun solve(readings: List<AnchorRange>): ObserverFix? {
        if (readings.size < 3) return null

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
        if (abs(determinant) < EPSILON) return null

        val position = Point2D(
            x = (c1 * m22 - m12 * c2) / determinant,
            y = (m11 * c2 - c1 * m12) / determinant
        )

        val residual = sqrt(
            readings.sumOf { sq(it.anchor.position.distanceTo(position) - it.distanceMeters) } /
                readings.size
        )

        return ObserverFix(readings, position, residual)
    }

    private fun sq(value: Double) = value * value
}
