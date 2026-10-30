package br.org.cesar.blueguide.feature.positioning.ui

/** What the Positioning screen shows. The position itself is worked out by the
 *  `PositioningEngine` the ViewModel feeds; this only keeps what it says. */
object DeviceListReducer {

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
                    // Keying on the device key rather than the address is what stops one
                    // phone from growing a new row every time it rotates its address:
                    // the id stays put, so this replaces the row in place.
                    state.copy(devicesByKey = state.devicesByKey + (sighting.device.key to sighting))
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
                        anchorRanges = emptyList(),
                        observerFix = null,
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

            is DeviceListIntent.AnchorsChanged -> state.copy(anchors = intent.anchors)

            is DeviceListIntent.SavedLocationsChanged -> state.copy(savedLocations = intent.locations)

            is DeviceListIntent.PositioningUpdated ->
                // An update computed just before a mode switch must not bring the fix back.
                if (state.mode != AppMode.OBSERVER) {
                    state
                } else {
                    val update = intent.update
                    state.copy(
                        devicesByKey = state.devicesByKey.filterValues { it.lastSeenAtMillis >= update.atMs - STALE_AFTER_MS },
                        anchorRanges = update.ranges,
                        observerFix = update.fix
                    )
                }

            is DeviceListIntent.CalibrationChanged -> state.copy(calibrationSettings = intent.settings)
        }
}
