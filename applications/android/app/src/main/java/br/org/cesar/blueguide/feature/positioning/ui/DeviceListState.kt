package br.org.cesar.blueguide.feature.positioning.ui

import br.org.cesar.blueguide.core.ble.BroadcastStatus
import br.org.cesar.blueguide.core.data.AnchorLayoutCheck
import br.org.cesar.blueguide.core.data.CalibrationSettings
import br.org.cesar.blueguide.core.model.Anchor
import br.org.cesar.blueguide.core.model.DeviceId
import br.org.cesar.blueguide.core.model.DeviceSighting
import br.org.cesar.blueguide.core.positioning.AnchorRange
import br.org.cesar.blueguide.core.positioning.ObserverFix
import br.org.cesar.blueguide.core.positioning.PathLossCalibration
import br.org.cesar.blueguide.core.positioning.PathLossModel
import br.org.cesar.blueguide.core.positioning.PositioningSetup
import br.org.cesar.blueguide.feature.positioning.domain.SavedLocation

data class DeviceListState(
    val mode: AppMode = AppMode.OBSERVER,
    /** Keyed by [br.org.cesar.blueguide.core.model.BleDevice.key], not by address. */
    val devicesByKey: Map<String, DeviceSighting> = emptyMap(),
    /** The calibration as saved (`CalibrationStore`): "RSSI no centro", "RSSI encostado" and the
     *  speed limit. */
    val calibrationSettings: CalibrationSettings = CalibrationSettings(),
    /** The ranges of the last refresh, one per anchor heard within the window. */
    val anchorRanges: List<AnchorRange> = emptyList(),
    /** The observer's own position from [anchorRanges], null until three anchors are ranged. */
    val observerFix: ObserverFix? = null,
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

    /** The RSSI heard from the anchors standing in their middle. It calibrates the model: it is
     *  read as the anchors' mean distance to their middle. */
    val centerRssi: Double get() = calibrationSettings.centerRssiOrDefault

    /** The RSSI heard touching a beacon, read as [PathLossModel.TOUCH_DISTANCE_METERS]. With the
     *  center's it fixes the exponent too. */
    val touchRssi: Double get() = calibrationSettings.touchRssiOrDefault

    /** The position shown moves at most [maxSpeedKmh] towards each fix. */
    val isSpeedLimited: Boolean get() = calibrationSettings.isSpeedLimitedOrDefault

    val maxSpeedKmh: Double get() = calibrationSettings.maxSpeedKmhOrDefault

    /** What the positioning engine ranges and places with: the anchors and calibration shown. */
    val positioningSetup: PositioningSetup
        get() = calibrationSettings.positioningSetup(anchors)

    /** The model the ranges come from, for the anchors configured now. */
    val calibration: PathLossCalibration
        get() = positioningSetup.calibration

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
