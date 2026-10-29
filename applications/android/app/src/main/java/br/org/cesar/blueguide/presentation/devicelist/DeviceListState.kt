package br.org.cesar.blueguide.presentation.devicelist

import br.org.cesar.blueguide.domain.ble.BroadcastStatus
import br.org.cesar.blueguide.domain.entity.DeviceId
import br.org.cesar.blueguide.domain.entity.DeviceSighting
import br.org.cesar.blueguide.domain.position.Anchor
import br.org.cesar.blueguide.domain.position.AnchorLayoutCheck
import br.org.cesar.blueguide.domain.position.AnchorRange
import br.org.cesar.blueguide.domain.position.MotionLimit
import br.org.cesar.blueguide.domain.position.ObserverFix
import br.org.cesar.blueguide.domain.position.PathLossCalibration
import br.org.cesar.blueguide.domain.position.PathLossModel
import br.org.cesar.blueguide.domain.position.Point2D
import br.org.cesar.blueguide.domain.position.RssiWindow
import br.org.cesar.blueguide.domain.position.SavedLocation

/** A position shown on the map, and when (on the sightings' clock). */
data class TimedPosition(val position: Point2D, val atMs: Long)

/** How many advertisements one range was built from since the last refresh, and their spread
 *  in dB — reported to the API the same as the iOS app's `sampleCount`/`rssiStdDev`. */
data class SampleStats(val count: Int, val stdDev: Double)

data class DeviceListState(
    val mode: AppMode = AppMode.OBSERVER,
    /** Keyed by [br.org.cesar.blueguide.domain.entity.BleDevice.key], not by address. */
    val devicesByKey: Map<String, DeviceSighting> = emptyMap(),
    /** The recent RSSIs of each configured anchor; the ranges come from their medians. */
    val rssiWindows: Map<DeviceId, RssiWindow> = emptyMap(),
    /** The RSSI heard from the anchors standing in their middle ("RSSI no centro"). It
     *  calibrates the model: it is read as the anchors' mean distance to their middle. */
    val centerRssi: Double = PathLossModel.DEFAULT_CENTER_RSSI,
    /** The RSSI heard touching a beacon ("RSSI encostado"), read as
     *  [PathLossModel.TOUCH_DISTANCE_METERS]. With the center's it fixes the exponent too. */
    val touchRssi: Double = PathLossModel.DEFAULT_TOUCH_RSSI,
    /** "Limitar pela velocidade": the position shown moves at most [maxSpeedKmh] towards each fix. */
    val isSpeedLimited: Boolean = true,
    val maxSpeedKmh: Double = MotionLimit.DEFAULT_MAX_SPEED_KMH,
    /** The last position shown and when, where the next refresh is limited from. */
    val lastShown: TimedPosition? = null,
    /** Where the last refresh was limited from, so ranging again at the same moment (a new
     *  calibration) starts from there and not from that refresh's own result. */
    val limitedFrom: TimedPosition? = null,
    /** The ranges of the last refresh, one per anchor heard within the window. */
    val anchorRanges: List<AnchorRange> = emptyList(),
    /** The observer's own position from [anchorRanges], null until three anchors are ranged. */
    val observerFix: ObserverFix? = null,
    val sampleStats: Map<DeviceId, SampleStats> = emptyMap(),
    /** When the last refresh ran, so a new [centerRssi] can range again at once. */
    val lastRefreshMs: Long? = null,
    /** Null until read off disk, which broadcast mode shows on screen. */
    val localDeviceId: DeviceId? = null,
    val broadcastStatus: BroadcastStatus? = null,
    val scanGranted: Boolean = false,
    val advertiseGranted: Boolean = false,
    /** Anchors placed by hand (`AnchorStore`), not auto-assigned by signal strength. */
    val anchors: List<Anchor> = emptyList(),
    /** Named points to walk back to ("Locais salvos"). */
    val savedLocations: List<SavedLocation> = emptyList()
) {
    val visibleDevices: List<DeviceSighting>
        get() = devicesByKey.values.toList()

    /** Heard devices that are not anchors yet, strongest first: the "Sinais próximos" list. */
    val unconfiguredSightings: List<DeviceSighting>
        get() {
            val configured = anchors.map { it.id }.toSet()
            return devicesByKey.values.filter { it.device.id != null && it.device.id !in configured }.sortedByDescending { it.rssi }
        }

    val speedLimitKmh: Double?
        get() = if (isSpeedLimited) maxSpeedKmh else null

    /** The model the ranges come from, for the anchors configured now. Fewer than two anchors
     *  have no middle, so the bench square's is used. */
    val calibration: PathLossCalibration
        get() = PathLossModel.calibration(
            centerRssi,
            PathLossModel.centerDistanceMeters(anchors.map { it.position }) ?: PathLossModel.BENCH_CENTER_DISTANCE_METERS,
            touchRssi
        )

    /** The strongest anchor's current median, to take as [touchRssi] while touching it. Null
     *  until an anchor is ranged. */
    val currentTouchRssi: Double?
        get() = anchorRanges.mapNotNull { it.rssi }.maxOrNull()

    /** The mean of the anchors' current median RSSIs, to take as [centerRssi] while standing in
     *  the middle. Null until an anchor is ranged. */
    val currentCenterRssi: Double?
        get() = anchorRanges.mapNotNull { it.rssi }.takeIf { it.isNotEmpty() }?.average()

    /** Whether the permission the *current* mode needs has been granted. */
    val isModePermitted: Boolean
        get() = when (mode) {
            AppMode.OBSERVER -> scanGranted
            AppMode.BROADCAST -> advertiseGranted
        }

    private val heardIds: Set<DeviceId>
        get() = devicesByKey.values.mapNotNull { it.device.id }.toSet()

    /** How many configured anchors are currently being heard, for the "not enough yet" message. */
    val anchorsHeard: Int
        get() = anchors.count { it.id in heardIds }

    /** Configured-but-unheard and heard-but-unconfigured devices, for the layout check UI. */
    val layoutCheck: AnchorLayoutCheck
        get() = AnchorLayoutCheck.of(anchors, heardIds)
}
