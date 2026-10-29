package br.org.cesar.blueguide.core.ble

import kotlinx.coroutines.flow.Flow

/** The broadcast-mode counterpart of [BleScanner]: puts this device on the air. */
interface BleAdvertiser {

    /**
     * Cold flow that starts advertising when collected and stops when collection is
     * cancelled. Emits [BroadcastStatus.Starting], then [BroadcastStatus.Advertising]
     * once the radio confirms it, and stays open until cancelled.
     *
     * Fails with a [BleAdvertiseException] if the advertisement cannot start.
     */
    fun advertise(): Flow<BroadcastStatus>
}
