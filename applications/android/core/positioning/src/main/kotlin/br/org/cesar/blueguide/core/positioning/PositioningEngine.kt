package br.org.cesar.blueguide.core.positioning

import br.org.cesar.blueguide.core.model.Anchor
import br.org.cesar.blueguide.core.model.DeviceId
import br.org.cesar.blueguide.core.model.Point2D
import kotlin.math.sqrt

/** What [PositioningEngine] ranges and places with: the anchors and the calibration on screen. */
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

/** What one [PositioningEngine.refresh] worked out. */
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

/**
 * Where the phone is, from every advertisement it hears — the counterpart of the iOS app's
 * `PositioningEngine`, without the scanner and the timer: whoever owns it [record]s the sightings
 * and [refresh]es it once a second. The app does that live, and a replay can do it over a
 * recording, placing the phone the same way.
 *
 * Each anchor's range comes from the median RSSI of its last [RSSI_WINDOW_MS], the position from
 * the [PositionEstimator], and the position shown moves towards that one no faster than
 * [PositioningSetup.maxSpeedKmh].
 *
 * Not thread-safe: record and refresh from one thread.
 */
class PositioningEngine(
    private val estimator: PositionEstimator = Multilateration,
    setup: PositioningSetup = PositioningSetup()
) {
    /** Applies from the next [refresh], or at once with [rangeAgain]. The readings of beacons
     *  that are no longer anchors are dropped. */
    var setup: PositioningSetup = setup
        set(value) {
            field = value
            val ids = value.anchors.map { it.id }.toSet()
            windows.keys.retainAll(ids)
        }

    private val windows = mutableMapOf<DeviceId, RssiWindow>()

    /** The last position shown and when, where the next refresh is limited from. */
    private var lastShown: TimedPosition? = null

    /** Where the last refresh was limited from, so ranging again at the same moment (a new
     *  calibration) starts from there and not from that refresh's own result. */
    private var limitedFrom: TimedPosition? = null

    private var lastRefreshMs: Long? = null

    /** One advertisement, heard at [atMs] on the clock [refresh] is asked on. Every one counts,
     *  not just the latest: the range comes from their median. Only the anchors' are kept, which
     *  bounds the memory by the number of anchors however many devices pass by. */
    fun record(beacon: DeviceId, rssi: Int, atMs: Long) {
        if (setup.anchors.none { it.id == beacon }) return
        val window = windows[beacon] ?: RssiWindow(RSSI_WINDOW_MS, RSSI_WINDOW_CAPACITY)
        windows[beacon] = window.plus(rssi, atMs)
    }

    /** Ranges every anchor heard within the window before [nowMs] and places the phone — the iOS
     *  engine's `refresh()`. */
    fun refresh(nowMs: Long): PositioningUpdate {
        val calibration = setup.calibration
        val stats = mutableMapOf<DeviceId, SampleStats>()
        val ranges = setup.anchors.mapNotNull { anchor ->
            // An anchor not heard within the window has no range this time.
            val window = windows[anchor.id] ?: return@mapNotNull null
            val median = window.median(nowMs) ?: return@mapNotNull null
            val samples = window.samples(sinceMs = nowMs - REFRESH_INTERVAL_MS)
            stats[anchor.id] = SampleStats(samples.size, standardDeviation(samples))
            AnchorRange(anchor, calibration.distanceMeters(median), median)
        }
        // Ranging again at the same moment starts from where the last refresh started.
        val from = if (nowMs == lastRefreshMs) limitedFrom else lastShown
        val fix = estimator.estimate(ranges)?.let { computed ->
            val position = MotionLimit.limited(
                from?.position,
                computed.position,
                setup.maxSpeedKmh,
                from?.let { (nowMs - it.atMs) / 1_000.0 } ?: 0.0
            )
            computed.copy(position = position)
        }
        lastRefreshMs = nowMs
        limitedFrom = from
        lastShown = fix?.let { TimedPosition(it.position, nowMs) } ?: from
        return PositioningUpdate(nowMs, ranges, fix, stats)
    }

    /** The last refresh again, at the same moment and from the same readings, with the current
     *  [setup]: a new calibration shows at once instead of on the next refresh. Null before the
     *  first refresh. */
    fun rangeAgain(): PositioningUpdate? = lastRefreshMs?.let(::refresh)

    /** Forgets every reading and the position shown, as when observing starts over. */
    fun reset() {
        windows.clear()
        lastShown = null
        limitedFrom = null
        lastRefreshMs = null
    }

    private data class TimedPosition(val position: Point2D, val atMs: Long)

    companion object {
        /** How long an advertisement counts toward a beacon's range. A longer window is steadier
         *  but trails a walking phone (about half the window: 1.5 m at 1 m/s). Same as iOS. */
        const val RSSI_WINDOW_MS = 3_000L

        /** Room for 3 s of advertisements at up to 40 per second. */
        const val RSSI_WINDOW_CAPACITY = 120

        /** How often the owner refreshes, and so the span [SampleStats] count. Same as iOS. */
        const val REFRESH_INTERVAL_MS = 1_000L

        /** Sample standard deviation (n-1 denominator). 0 when there are fewer than 2 samples to
         *  compare, same as the iOS app. */
        private fun standardDeviation(samples: List<Int>): Double {
            if (samples.size < 2) return 0.0
            val mean = samples.average()
            return sqrt(samples.sumOf { (it - mean) * (it - mean) } / (samples.size - 1))
        }
    }
}
