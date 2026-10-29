package br.org.cesar.blueguide.feature.positioning.ui

import br.org.cesar.blueguide.core.ble.BlueGuideProtocol
import br.org.cesar.blueguide.core.model.BleDevice
import br.org.cesar.blueguide.core.model.DeviceId
import br.org.cesar.blueguide.core.model.DeviceSighting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceIdentityTest {

    @Test
    fun `same id at a new address updates the row instead of adding one`() {
        val state = observing()
            .observe(sighting(address = "A0:11:22:33:44:55", id = "3F8A1C04", rssi = -50))
            .observe(sighting(address = "C7:EE:99:00:11:22", id = "3F8A1C04", rssi = -61))

        assertEquals(1, state.visibleDevices.size)
        // Latest sighting wins, address included.
        assertEquals("C7:EE:99:00:11:22", state.visibleDevices.single().device.address)
        assertEquals(-61, state.visibleDevices.single().rssi)
    }

    @Test
    fun `different ids at the same address stay separate rows`() {
        val state = observing()
            .observe(sighting(address = "A0:11:22:33:44:55", id = "3F8A1C04"))
            .observe(sighting(address = "A0:11:22:33:44:55", id = "9B20E7D1"))

        assertEquals(2, state.visibleDevices.size)
    }

    @Test
    fun `a peer with no id falls back to keying on its address`() {
        val state = observing()
            .observe(sighting(address = "A0:11:22:33:44:55", id = null))
            .observe(sighting(address = "C7:EE:99:00:11:22", id = null))

        assertEquals(2, state.visibleDevices.size)
    }

    @Test
    fun `id survives the trip through the advertisement bytes`() {
        val id = BlueGuideProtocol.randomId()

        val bytes = BlueGuideProtocol.idToBytes(id)

        assertEquals(BlueGuideProtocol.ID_SIZE_BYTES, bytes.size)
        assertEquals(id, BlueGuideProtocol.idFromBytes(bytes))
    }

    @Test
    fun `high bytes are not mangled by sign`() {
        val id = DeviceId("FF80007F")

        assertEquals(id, BlueGuideProtocol.idFromBytes(BlueGuideProtocol.idToBytes(id)))
    }

    @Test
    fun `a missing or truncated id field reads as no id`() {
        assertNull(BlueGuideProtocol.idFromBytes(null))
        assertNull(BlueGuideProtocol.idFromBytes(byteArrayOf(0x01, 0x02)))
    }

    private fun observing() = DeviceListState(mode = AppMode.OBSERVER, scanGranted = true)

    private fun DeviceListState.observe(sighting: DeviceSighting) =
        DeviceListReducer.reduce(this, DeviceListIntent.DeviceObserved(sighting))

    private fun sighting(address: String, id: String?, rssi: Int = -70) = DeviceSighting(
        device = BleDevice(address = address, name = "Phone", id = id?.let(::DeviceId)),
        rssi = rssi,
        lastSeenAtMillis = 0L
    )
}
