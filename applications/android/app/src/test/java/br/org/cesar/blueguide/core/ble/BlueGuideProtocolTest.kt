package br.org.cesar.blueguide.core.ble

import br.org.cesar.blueguide.core.model.DeviceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Where an advertisement carries its id: the same rules as the iOS app's `BeaconProtocol`. */
class BlueGuideProtocolTest {

    @Test
    fun `the ESP32 beacons and iOS anchors carry the id in the local name`() {
        assertEquals(DeviceId("9F3C1A02"), BlueGuideProtocol.idFrom(manufacturerData = null, localName = "9F3C1A02"))
    }

    @Test
    fun `an id in the local name is read in upper case`() {
        assertEquals(DeviceId("9F3C1A02"), BlueGuideProtocol.idFrom(manufacturerData = null, localName = "9f3c1a02"))
    }

    @Test
    fun `the manufacturer data wins, since Android sends its device name in the local name`() {
        val bytes = byteArrayOf(0x1A, 0x2B, 0x3C, 0x4D)

        assertEquals(DeviceId("1A2B3C4D"), BlueGuideProtocol.idFrom(manufacturerData = bytes, localName = "Pixel 8"))
    }

    @Test
    fun `a local name that is not an id is no id`() {
        assertNull(BlueGuideProtocol.idFrom(manufacturerData = null, localName = "Pixel 8"))
        assertNull(BlueGuideProtocol.idFrom(manufacturerData = null, localName = "9F3C1A0"))
        assertNull(BlueGuideProtocol.idFrom(manufacturerData = null, localName = "9F3C1A02F"))
        assertNull(BlueGuideProtocol.idFrom(manufacturerData = null, localName = "9F3C1AZZ"))
        assertNull(BlueGuideProtocol.idFrom(manufacturerData = null, localName = null))
    }

    @Test
    fun `short manufacturer data falls back to the local name`() {
        assertEquals(
            DeviceId("9F3C1A02"),
            BlueGuideProtocol.idFrom(manufacturerData = byteArrayOf(0x01), localName = "9F3C1A02")
        )
    }
}
