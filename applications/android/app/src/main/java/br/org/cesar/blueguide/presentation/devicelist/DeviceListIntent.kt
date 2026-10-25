package br.org.cesar.blueguide.presentation.devicelist

import br.org.cesar.blueguide.domain.ble.BroadcastStatus
import br.org.cesar.blueguide.domain.entity.DeviceId
import br.org.cesar.blueguide.domain.entity.DeviceSighting
import br.org.cesar.blueguide.domain.position.Anchor

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
}
