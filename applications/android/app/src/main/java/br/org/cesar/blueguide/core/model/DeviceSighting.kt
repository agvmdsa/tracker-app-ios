package br.org.cesar.blueguide.core.model

data class DeviceSighting(
    val device: BleDevice,
    val rssi: Int,
    /** When the radio saw the advertisement, on the `SystemClock.elapsedRealtime()` clock — not
     *  when the app got to it, which can be later and in bursts. A fingerprint row's `t_ms`. */
    val lastSeenAtMillis: Long
)
