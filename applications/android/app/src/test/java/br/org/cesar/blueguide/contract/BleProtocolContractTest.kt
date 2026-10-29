package br.org.cesar.blueguide.contract

import br.org.cesar.blueguide.domain.ble.BlueGuideProtocol
import br.org.cesar.blueguide.domain.entity.DeviceId
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

/**
 * The advertisement every platform recognises: the same values as the iOS app's
 * `BeaconProtocol.swift` and the ESP32's `ble_adapter.cpp`/`observation_filter.h`. Where the id
 * is read from (manufacturer data first, then a local name that is an id) is covered by
 * `BlueGuideProtocolTest`.
 */
class BleProtocolContractTest {

    @Test
    fun `the service uuid is the one iOS and the ESP32 advertise and scan for`() {
        assertEquals(UUID.fromString("fc979812-36dd-4722-9316-d2a7908888dd"), BlueGuideProtocol.SERVICE_UUID)
    }

    @Test
    fun `the id travels as 4 bytes of manufacturer data under company 0xFFFF`() {
        assertEquals(0xFFFF, BlueGuideProtocol.MANUFACTURER_ID)
        assertEquals(4, BlueGuideProtocol.ID_SIZE_BYTES)
    }

    @Test
    fun `an id is its bytes as upper-case hex, both ways`() {
        val id = DeviceId("9F3C1A02")
        val bytes = byteArrayOf(0x9F.toByte(), 0x3C, 0x1A, 0x02)

        assertArrayEquals(bytes, BlueGuideProtocol.idToBytes(id))
        assertEquals(id, BlueGuideProtocol.idFromBytes(bytes))
    }
}
