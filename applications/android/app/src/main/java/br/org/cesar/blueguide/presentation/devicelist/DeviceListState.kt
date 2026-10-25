package br.org.cesar.blueguide.presentation.devicelist

import br.org.cesar.blueguide.domain.ble.BroadcastStatus
import br.org.cesar.blueguide.domain.entity.DeviceId
import br.org.cesar.blueguide.domain.entity.DeviceSighting
import br.org.cesar.blueguide.domain.position.Anchor
import br.org.cesar.blueguide.domain.position.AnchorLayoutCheck
import br.org.cesar.blueguide.domain.position.AnchorRange
import br.org.cesar.blueguide.domain.position.Multilateration
import br.org.cesar.blueguide.domain.position.ObserverFix
import br.org.cesar.blueguide.domain.position.PathLossModel

data class DeviceListState(
    val mode: AppMode = AppMode.OBSERVER,
    /** Keyed by [br.org.cesar.blueguide.domain.entity.BleDevice.key], not by address. */
    val devicesByKey: Map<String, DeviceSighting> = emptyMap(),
    /** Running dB average per device key; see [PathLossModel.smooth]. */
    val smoothedRssiByKey: Map<String, Double> = emptyMap(),
    /** Null until read off disk, which broadcast mode shows on screen. */
    val localDeviceId: DeviceId? = null,
    val broadcastStatus: BroadcastStatus? = null,
    val scanGranted: Boolean = false,
    val advertiseGranted: Boolean = false,
    /** Anchors placed by hand (`AnchorStore`), not auto-assigned by signal strength. */
    val anchors: List<Anchor> = emptyList()
) {
    val visibleDevices: List<DeviceSighting>
        get() = devicesByKey.values.toList()

    /** Whether the permission the *current* mode needs has been granted. */
    val isModePermitted: Boolean
        get() = when (mode) {
            AppMode.OBSERVER -> scanGranted
            AppMode.BROADCAST -> advertiseGranted
        }

    private val heardIds: Set<DeviceId>
        get() = devicesByKey.values.mapNotNull { it.device.id }.toSet()

    /**
     * The observer's own position, from whichever configured [anchors] are currently heard.
     * Null until at least three of them are ([Multilateration.solve]'s minimum) — same rule
     * as the iOS app's `PositioningEngine`, and no longer a fixed three like the old
     * auto-assigned triangle this replaces.
     */
    val observerFix: ObserverFix?
        get() {
            val rssiById = devicesByKey.values
                .mapNotNull { sighting -> sighting.device.id?.let { it to (smoothedRssiByKey[sighting.device.key] ?: sighting.rssi.toDouble()) } }
                .toMap()
            val readings = anchors.mapNotNull { anchor ->
                rssiById[anchor.id]?.let { rssi -> AnchorRange(anchor, PathLossModel.distanceMeters(rssi)) }
            }
            return Multilateration.solve(readings)
        }

    /** How many configured anchors are currently being heard, for the "not enough yet" message. */
    val anchorsHeard: Int
        get() = anchors.count { it.id in heardIds }

    /** Configured-but-unheard and heard-but-unconfigured devices, for the layout check UI. */
    val layoutCheck: AnchorLayoutCheck
        get() = AnchorLayoutCheck.of(anchors, heardIds)
}
