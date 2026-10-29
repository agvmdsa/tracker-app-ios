package br.org.cesar.blueguide.presentation.devicelist

import br.org.cesar.blueguide.domain.ble.BroadcastStatus
import br.org.cesar.blueguide.domain.entity.DeviceId
import br.org.cesar.blueguide.domain.entity.DeviceSighting
import br.org.cesar.blueguide.domain.position.Anchor
import br.org.cesar.blueguide.domain.position.SavedLocation

sealed interface DeviceListIntent {
    data class DeviceObserved(val sighting: DeviceSighting) : DeviceListIntent
    data class ModeSelected(val mode: AppMode) : DeviceListIntent
    data class BroadcastStatusChanged(val status: BroadcastStatus) : DeviceListIntent
    data class LocalDeviceIdLoaded(val id: DeviceId) : DeviceListIntent
    data class PermissionsChanged(
        val scanGranted: Boolean,
        val advertiseGranted: Boolean
    ) : DeviceListIntent

    /** Fired whenever `AnchorStore`'s list changes — load at startup, or after an edit. */
    data class AnchorsChanged(val anchors: List<Anchor>) : DeviceListIntent

    /** Once a second while observing: range from the windows' medians and solve the fix.
     *  [nowMs] is on the sightings' clock (`SystemClock.elapsedRealtime`). */
    data class Refreshed(val nowMs: Long) : DeviceListIntent

    /** The "RSSI no centro" setting, loaded from disk or changed on screen. */
    data class CenterRssiChanged(val rssi: Double) : DeviceListIntent

    /** The "RSSI encostado" setting, loaded from disk or changed on screen. */
    data class TouchRssiChanged(val rssi: Double) : DeviceListIntent

    /** The speed limit, loaded from disk or changed on screen; null turns it off (keeping the
     *  speed for when it is turned on again). */
    data class MaxSpeedChanged(val kmh: Double?) : DeviceListIntent

    /** Fired whenever `SavedLocationStore`'s list changes. */
    data class SavedLocationsChanged(val locations: List<SavedLocation>) : DeviceListIntent
}
