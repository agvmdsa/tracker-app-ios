package br.org.cesar.blueguide.domain.uwb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UwbHandshakeProtocolTest {

    @Test
    fun `a random id survives the trip through bytes`() {
        val id = UwbHandshakeProtocol.randomId()

        val bytes = UwbHandshakeProtocol.idToBytes(id)

        assertEquals(UwbHandshakeProtocol.ID_SIZE_BYTES, bytes.size)
        assertEquals(id, UwbHandshakeProtocol.idFromBytes(bytes))
    }

    @Test
    fun `two random ids are not equal`() {
        assertNotEquals(UwbHandshakeProtocol.randomId(), UwbHandshakeProtocol.randomId())
    }

    @Test
    fun `a missing or truncated id reads as no id`() {
        assertNull(UwbHandshakeProtocol.idFromBytes(null))
        assertNull(UwbHandshakeProtocol.idFromBytes(byteArrayOf(0x01, 0x02)))
    }
}
