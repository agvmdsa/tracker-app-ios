package br.org.cesar.blueguide.core.ble

import br.org.cesar.blueguide.core.model.DeviceId

/** This install's own [DeviceId], the one broadcast mode puts on the air. */
interface LocalDeviceIdProvider {

    /** Stable across process death, Bluetooth restarts, and address rotation. */
    fun current(): DeviceId
}
