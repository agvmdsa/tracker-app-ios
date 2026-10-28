package br.org.cesar.blueguide.domain.ble

import br.org.cesar.blueguide.domain.entity.DeviceId

/** This install's own [DeviceId], the one broadcast mode puts on the air. */
interface LocalDeviceIdProvider {

    /** Stable across process death, Bluetooth restarts, and address rotation. */
    fun current(): DeviceId
}
