package br.org.cesar.blueguide.core.positioning

import br.org.cesar.blueguide.core.model.Anchor
import br.org.cesar.blueguide.core.model.DeviceId

/**
 * Where the phone is, from every advertisement it hears: what the app and the replay depend on,
 * so one engine can be swapped for another — `PositioningModule` in the app, `--engine` in the
 * replay. [PathLossPositioningEngine] is the app's.
 *
 * Whoever owns an engine [record]s every sighting and [refresh]es it every
 * [REFRESH_INTERVAL_MS]: the app does that live, [PositioningReplay] over a recording. An engine
 * holds the readings of one observing session. Not thread-safe: use it from one thread.
 */
interface PositioningEngine {
    /** The anchors and the calibration to position with. A new one applies from the next
     *  [refresh], or at once with [rangeAgain]. */
    var setup: PositioningSetup

    /** One advertisement of [beacon], heard at [atMs] on the clock [refresh] is asked on. */
    fun record(beacon: DeviceId, rssi: Int, atMs: Long)

    /** Where the phone is at [nowMs], from what was recorded. */
    fun refresh(nowMs: Long): PositioningUpdate

    /** The last refresh again, at the same moment and from the same readings, with the current
     *  [setup]: a new calibration shows at once instead of on the next refresh. Null before the
     *  first refresh. */
    fun rangeAgain(): PositioningUpdate?

    /** Forgets every reading and the position shown, as when observing starts over. */
    fun reset()

    companion object {
        /** How often the owner refreshes, and so the span [SampleStats] count. Same as iOS. */
        const val REFRESH_INTERVAL_MS = 1_000L
    }
}

/** What a [PositioningEngine] ranges and places with: the anchors and the calibration on screen. */
data class PositioningSetup(
    val anchors: List<Anchor> = emptyList(),
    /** "RSSI no centro": read as the anchors' mean distance to their middle. */
    val centerRssi: Double = PathLossModel.DEFAULT_CENTER_RSSI,
    /** "RSSI encostado": read as [PathLossModel.TOUCH_DISTANCE_METERS]. With the center's it fixes
     *  the exponent too. */
    val touchRssi: Double = PathLossModel.DEFAULT_TOUCH_RSSI,
    /** How fast the phone can move between fixes; null shows every fix as computed. */
    val maxSpeedKmh: Double? = MotionLimit.DEFAULT_MAX_SPEED_KMH
) {
    /** The model the ranges come from, for these anchors. Fewer than two anchors have no middle,
     *  so the bench square's is used. */
    val calibration: PathLossCalibration = PathLossModel.calibration(
        centerRssi,
        PathLossModel.centerDistanceMeters(anchors.map { it.position }) ?: PathLossModel.BENCH_CENTER_DISTANCE_METERS,
        touchRssi
    )
}

/** How many advertisements one range was built from since the last refresh, and their spread
 *  in dB — reported to the API the same as the iOS app's `sampleCount`/`rssiStdDev`. */
data class SampleStats(val count: Int, val stdDev: Double)

/** What one [PositioningEngine.refresh] worked out. The API gets each range's distance and the
 *  map draws it, so an engine has to give one per anchor heard. */
data class PositioningUpdate(
    /** When, on the sightings' clock. */
    val atMs: Long,
    /** One per anchor heard within the window. */
    val ranges: List<AnchorRange>,
    /** Where the phone is shown: the estimator's position, moved from the last one no further
     *  than the speed limit allows. Null while the ranges can't place it. */
    val fix: ObserverFix?,
    /** For each anchor in [ranges]. */
    val sampleStats: Map<DeviceId, SampleStats>
)
