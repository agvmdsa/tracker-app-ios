package br.org.cesar.blueguide.presentation.devicelist

import br.org.cesar.blueguide.domain.position.PathLossModel

object DeviceListReducer {
    fun reduce(state: DeviceListState, intent: DeviceListIntent): DeviceListState =
        when (intent) {
            is DeviceListIntent.DeviceObserved -> {
                // A sighting from a scan that is still winding down after a mode switch
                // must not add rows while we are broadcasting.
                if (state.mode != AppMode.OBSERVER) {
                    state
                } else {
                    val sighting = intent.sighting
                    val key = sighting.device.key
                    // Keying on the device key rather than the address is what stops one
                    // phone from growing a new row every time it rotates its address:
                    // the id stays put, so this replaces the row in place.
                    state.copy(
                        devicesByKey = state.devicesByKey + (key to sighting),
                        smoothedRssiByKey = state.smoothedRssiByKey + (
                            key to PathLossModel.smooth(state.smoothedRssiByKey[key], sighting.rssi)
                            )
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
                        // Averages over a room we are no longer listening to would seed
                        // the next observer session with stale ranges.
                        smoothedRssiByKey = emptyMap(),
                        broadcastStatus = null
                    )
                }

            is DeviceListIntent.BroadcastStatusChanged ->
                if (state.mode != AppMode.BROADCAST) state
                else state.copy(broadcastStatus = intent.status)

            is DeviceListIntent.LocalDeviceIdLoaded -> state.copy(localDeviceId = intent.id)

            is DeviceListIntent.PermissionsChanged -> state.copy(
                scanGranted = intent.scanGranted,
                advertiseGranted = intent.advertiseGranted
            )

            is DeviceListIntent.AnchorsChanged -> state.copy(anchors = intent.anchors)
        }
}
