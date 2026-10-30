package br.org.cesar.blueguide.core.positioning

import br.org.cesar.blueguide.core.model.DeviceId
import br.org.cesar.blueguide.core.model.Point2D
import kotlin.math.sqrt

/**
 * The app's [PositioningEngine], the counterpart of the iOS app's `PositioningEngine` without the
 * scanner and the timer: each anchor's range comes from the median RSSI of its last
 * [RSSI_WINDOW_MS] by the path loss model, the position from the [PositionEstimator], and the
 * position shown moves towards that one no faster than [PositioningSetup.maxSpeedKmh]. See this
 * module's README for the steps in order.
 */
class PathLossPositioningEngine(
    private val estimator: PositionEstimator = Multilateration,
    setup: PositioningSetup = PositioningSetup()
) : PositioningEngine {
    /** The readings of beacons that are no longer anchors are dropped. */
    override var setup: PositioningSetup = setup
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

    /** Every advertisement counts, not just the latest: the range comes from their median. Only
     *  the anchors' are kept, which bounds the memory by the number of anchors however many
     *  devices pass by. */
    override fun record(beacon: DeviceId, rssi: Int, atMs: Long) {
        if (setup.anchors.none { it.id == beacon }) return
        val window = windows[beacon] ?: RssiWindow(RSSI_WINDOW_MS, RSSI_WINDOW_CAPACITY)
        windows[beacon] = window.plus(rssi, atMs)
    }

    /** Ranges every anchor heard within the window before [nowMs] and places the phone — the iOS
     *  engine's `refresh()`. */
    override fun refresh(nowMs: Long): PositioningUpdate {
        val calibration = setup.calibration
        val stats = mutableMapOf<DeviceId, SampleStats>()
        val ranges = setup.anchors.mapNotNull { anchor ->
            // An anchor not heard within the window has no range this time.
            val window = windows[anchor.id] ?: return@mapNotNull null
            val median = window.median(nowMs) ?: return@mapNotNull null
            val samples = window.samples(sinceMs = nowMs - PositioningEngine.REFRESH_INTERVAL_MS)
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

    override fun rangeAgain(): PositioningUpdate? = lastRefreshMs?.let(::refresh)

    override fun reset() {
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

        /** Sample standard deviation (n-1 denominator). 0 when there are fewer than 2 samples to
         *  compare, same as the iOS app. */
        private fun standardDeviation(samples: List<Int>): Double {
            if (samples.size < 2) return 0.0
            val mean = samples.average()
            return sqrt(samples.sumOf { (it - mean) * (it - mean) } / (samples.size - 1))
        }
    }
}
