package br.org.cesar.blueguide.domain.entity

data class DeviceSighting(
    val device: BleDevice,
    val rssi: Int,
    val lastSeenAtMillis: Long
)
