package br.org.cesar.blueguide.presentation.devicelist

import br.org.cesar.blueguide.domain.entity.DeviceId
import br.org.cesar.blueguide.domain.position.AnchorRange
import br.org.cesar.blueguide.domain.position.MotionLimit
import br.org.cesar.blueguide.domain.position.Multilateration
import br.org.cesar.blueguide.domain.position.RssiWindow
import kotlin.math.sqrt

object DeviceListReducer {

    /** How long an advertisement counts toward a beacon's range. A longer window is steadier
     *  but trails a walking phone (about half the window: 1.5 m at 1 m/s). Same as iOS. */
    const val RSSI_WINDOW_MS = 3_000L

    /** Room for 3 s of advertisements at up to 40 per second. */
    const val RSSI_WINDOW_CAPACITY = 120

    /** How often the ranges and the fix are worked out again, and the API reported to. */
    const val REFRESH_INTERVAL_MS = 1_000L

    /** A device not heard for this long leaves the list, like the iOS scanner's `staleAfter`. */
    const val STALE_AFTER_MS = 5_000L

    fun reduce(state: DeviceListState, intent: DeviceListIntent): DeviceListState =
        when (intent) {
            is DeviceListIntent.DeviceObserved -> {
                // A sighting from a scan that is still winding down after a mode switch
                // must not add rows while we are broadcasting.
                if (state.mode != AppMode.OBSERVER) {
                    state
                } else {
                    val sighting = intent.sighting
                    val id = sighting.device.id
                    // Every advertisement of an anchor goes into its window, not just the
                    // latest: the range comes from their median. Only anchors are kept, which
                    // bounds the memory by the number of anchors however many devices pass by.
                    val windows = if (id != null && state.anchors.any { it.id == id }) {
                        val window = state.rssiWindows[id] ?: RssiWindow(RSSI_WINDOW_MS, RSSI_WINDOW_CAPACITY)
                        state.rssiWindows + (id to window.plus(sighting.rssi, sighting.lastSeenAtMillis))
                    } else {
                        state.rssiWindows
                    }
                    // Keying on the device key rather than the address is what stops one
                    // phone from growing a new row every time it rotates its address:
                    // the id stays put, so this replaces the row in place.
                    state.copy(
                        devicesByKey = state.devicesByKey + (sighting.device.key to sighting),
                        rssiWindows = windows
                    )
                }
            }

            is DeviceListIntent.ModeSelected ->
                // Whatever the old mode produced is dropped: the radio stops feeding it,
                // so a frozen device list or a stale "advertising" badge would be a lie.
                if (intent.mode == state.mode) {
                    state
                } else {
                    state.copy(
                        mode = intent.mode,
                        devicesByKey = emptyMap(),
                        // Readings from a room we are no longer listening to would seed the
                        // next observer session with stale ranges.
                        rssiWindows = emptyMap(),
                        anchorRanges = emptyList(),
                        observerFix = null,
                        sampleStats = emptyMap(),
                        lastRefreshMs = null,
                        lastShown = null,
                        limitedFrom = null,
                        broadcastStatus = null
                    )
                }

            is DeviceListIntent.BroadcastStatusChanged ->
                if (state.mode != AppMode.BROADCAST) {
                    state
                } else {
                    state.copy(broadcastStatus = intent.status)
                }

            is DeviceListIntent.LocalDeviceIdLoaded -> state.copy(localDeviceId = intent.id)

            is DeviceListIntent.PermissionsChanged -> state.copy(
                scanGranted = intent.scanGranted,
                advertiseGranted = intent.advertiseGranted
            )

            is DeviceListIntent.AnchorsChanged -> {
                val ids = intent.anchors.map { it.id }.toSet()
                state.copy(anchors = intent.anchors, rssiWindows = state.rssiWindows.filterKeys { it in ids })
            }

            is DeviceListIntent.SavedLocationsChanged -> state.copy(savedLocations = intent.locations)

            is DeviceListIntent.Refreshed -> refreshed(state, intent.nowMs)

            is DeviceListIntent.CenterRssiChanged -> rangedAgain(state.copy(centerRssi = intent.rssi))

            is DeviceListIntent.TouchRssiChanged -> rangedAgain(state.copy(touchRssi = intent.rssi))

            is DeviceListIntent.MaxSpeedChanged ->
                state.copy(isSpeedLimited = intent.kmh != null, maxSpeedKmh = intent.kmh ?: state.maxSpeedKmh)
        }

    /** A new calibration ranges again at once, from the last refresh's windows. */
    private fun rangedAgain(state: DeviceListState): DeviceListState =
        state.lastRefreshMs?.let { refreshed(state, it) } ?: state

    /** The counterpart of the iOS app's `PositioningEngine.refresh()`. */
    private fun refreshed(state: DeviceListState, nowMs: Long): DeviceListState {
        val calibration = state.calibration
        val stats = mutableMapOf<DeviceId, SampleStats>()
        val ranges = state.anchors.mapNotNull { anchor ->
            // An anchor not heard within the window has no range this time.
            val window = state.rssiWindows[anchor.id] ?: return@mapNotNull null
            val median = window.median(nowMs) ?: return@mapNotNull null
            val samples = window.samples(sinceMs = nowMs - REFRESH_INTERVAL_MS)
            stats[anchor.id] = SampleStats(samples.size, standardDeviation(samples))
            AnchorRange(anchor, calibration.distanceMeters(median), median)
        }
        // Ranging again at the same moment starts from where the last refresh started.
        val from = if (nowMs == state.lastRefreshMs) state.limitedFrom else state.lastShown
        val fix = Multilateration.solve(ranges)?.let { computed ->
            val position = MotionLimit.limited(
                from?.position,
                computed.position,
                state.speedLimitKmh,
                from?.let { (nowMs - it.atMs) / 1_000.0 } ?: 0.0
            )
            computed.copy(position = position)
        }
        return state.copy(
            devicesByKey = state.devicesByKey.filterValues { it.lastSeenAtMillis >= nowMs - STALE_AFTER_MS },
            anchorRanges = ranges,
            observerFix = fix,
            sampleStats = stats,
            lastRefreshMs = nowMs,
            lastShown = fix?.let { TimedPosition(it.position, nowMs) } ?: from,
            limitedFrom = from
        )
    }

    /** Sample standard deviation (n-1 denominator). 0 when there are fewer than 2 samples to
     *  compare, same as the iOS app. */
    private fun standardDeviation(samples: List<Int>): Double {
        if (samples.size < 2) return 0.0
        val mean = samples.average()
        return sqrt(samples.sumOf { (it - mean) * (it - mean) } / (samples.size - 1))
    }
}
