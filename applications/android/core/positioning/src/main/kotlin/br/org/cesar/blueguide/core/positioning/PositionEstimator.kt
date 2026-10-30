package br.org.cesar.blueguide.core.positioning

import br.org.cesar.blueguide.core.model.Anchor
import br.org.cesar.blueguide.core.model.Point2D

/**
 * Places the phone from the ranges of one refresh. [Multilateration] is the one the app uses; a
 * trained model plugs in here instead — one that corrects the multilateration's error, say, or
 * one that matches the RSSIs against fingerprints recorded at known points (each [AnchorRange]
 * carries the median RSSI as well as the distance it was read as).
 *
 * Each call stands on its own: following the phone over time is [MotionLimit]'s job, which
 * [PositioningEngine] applies to whatever this returns.
 */
fun interface PositionEstimator {
    /** Null when [ranges] can't place the phone (fewer than three, or all in a line). */
    fun estimate(ranges: List<AnchorRange>): ObserverFix?
}

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
