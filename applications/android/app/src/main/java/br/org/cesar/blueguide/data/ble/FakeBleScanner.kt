package br.org.cesar.blueguide.data.ble

import android.os.SystemClock
import br.org.cesar.blueguide.domain.ble.BleScanner
import br.org.cesar.blueguide.domain.entity.BleDevice
import br.org.cesar.blueguide.domain.entity.DeviceId
import br.org.cesar.blueguide.domain.entity.DeviceSighting
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeBleScanner: BleScanner {

    /**
     * Each device keeps one id across a rotating address, so the list should settle on
     * three rows however long this runs. The last one advertises no id, standing in for
     * a peer whose build predates it.
     */
    private val devices = listOf(
        { BleDevice(randomAddress(), name = "asd", id = DeviceId("3F8A1C04")) },
        { BleDevice(randomAddress(), name = null, id = DeviceId("9B20E7D1")) },
        { BleDevice("af:75:43", name = "efg", id = null) },
    )

    override fun scan(): Flow<DeviceSighting> = flow {
        while(true) {
            emit(
                DeviceSighting(
                    device = devices.random().invoke(),
                    rssi = (-100..0).random(),
                    lastSeenAtMillis = SystemClock.elapsedRealtime()
                )
            )
            delay(200)
        }
    }

    private fun randomAddress(): String =
        (1..6).joinToString(":") { "%02x".format((0..255).random()) }
}
