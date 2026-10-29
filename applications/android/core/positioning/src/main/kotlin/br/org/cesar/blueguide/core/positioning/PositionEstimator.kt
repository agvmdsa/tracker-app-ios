package br.org.cesar.blueguide.core.positioning

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
