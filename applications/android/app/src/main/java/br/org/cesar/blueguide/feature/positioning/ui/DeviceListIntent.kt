package br.org.cesar.blueguide.feature.positioning.ui

import br.org.cesar.blueguide.core.ble.BroadcastStatus
import br.org.cesar.blueguide.core.data.CalibrationSettings
import br.org.cesar.blueguide.core.model.Anchor
import br.org.cesar.blueguide.core.model.DeviceId
import br.org.cesar.blueguide.core.model.DeviceSighting
import br.org.cesar.blueguide.core.positioning.PositioningUpdate
import br.org.cesar.blueguide.feature.positioning.domain.SavedLocation

sealed interface DeviceListIntent {
    data class DeviceObserved(val sighting: DeviceSighting) : DeviceListIntent
    data class ModeSelected(val mode: AppMode) : DeviceListIntent
    data class BroadcastStatusChanged(val status: BroadcastStatus) : DeviceListIntent
    data class LocalDeviceIdLoaded(val id: DeviceId) : DeviceListIntent
    data class PermissionsChanged(
        val scanGranted: Boolean,
        val advertiseGranted: Boolean
    ) : DeviceListIntent

    /** Fired whenever `AnchorStore`'s list changes — read at startup, or after an edit. */
    data class AnchorsChanged(val anchors: List<Anchor>) : DeviceListIntent

    /** Once a second while observing, and at once after a new calibration: what the positioning
     *  engine worked out. */
    data class PositioningUpdated(val update: PositioningUpdate) : DeviceListIntent

    /** Fired whenever `CalibrationStore`'s settings change — read at startup, or edited on screen.
     *  An unset field keeps the default. The engine ranges again with them (the ViewModel). */
    data class CalibrationChanged(val settings: CalibrationSettings) : DeviceListIntent

    /** Fired whenever `SavedLocationStore`'s list changes. */
    data class SavedLocationsChanged(val locations: List<SavedLocation>) : DeviceListIntent
}
