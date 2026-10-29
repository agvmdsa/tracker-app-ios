package br.org.cesar.blueguide.feature.find.domain

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UwbHandshakePayloadTest {

    @Test
    fun `a controlee payload round-trips with no controller info`() {
        val address = byteArrayOf(0x01, 0x02)

        val encoded = UwbHandshakePayload.encode(address, controllerInfo = null)
        val decoded = requireNotNull(UwbHandshakePayload.decode(encoded))

        assertArrayEquals(address, decoded.address)
        assertNull(decoded.controllerInfo)
    }

    @Test
    fun `a controller payload round-trips with its channel, session id and key`() {
        val address = byteArrayOf(0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11)
        val info = UwbHandshakePayload.ControllerInfo(
            channel = 9,
            preambleIndex = 11,
            sessionId = 123456,
            sessionKeyInfo = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        )

        val encoded = UwbHandshakePayload.encode(address, info)
        val decoded = requireNotNull(UwbHandshakePayload.decode(encoded))

        assertArrayEquals(address, decoded.address)
        val decodedInfo = requireNotNull(decoded.controllerInfo)
        assertEquals(info.channel, decodedInfo.channel)
        assertEquals(info.preambleIndex, decodedInfo.preambleIndex)
        assertEquals(info.sessionId, decodedInfo.sessionId)
        assertArrayEquals(info.sessionKeyInfo, decodedInfo.sessionKeyInfo)
    }

    @Test
    fun `a negative session id round-trips too`() {
        // SecureRandom#nextInt() (the real caller) can produce negative ints; the wire format
        // must not quietly truncate or misread the sign.
        val info = UwbHandshakePayload.ControllerInfo(1, 2, sessionId = -987654321, sessionKeyInfo = byteArrayOf(9))

        val decoded = requireNotNull(UwbHandshakePayload.decode(UwbHandshakePayload.encode(byteArrayOf(1), info))?.controllerInfo)

        assertEquals(-987654321, decoded.sessionId)
    }

    @Test
    fun `truncated bytes decode to null instead of throwing`() {
        assertNull(UwbHandshakePayload.decode(byteArrayOf()))
        assertNull(UwbHandshakePayload.decode(byteArrayOf(5, 1, 2))) // claims 5 address bytes, has 2
    }

    @Test
    fun `an empty address is valid`() {
        val decoded = requireNotNull(UwbHandshakePayload.decode(UwbHandshakePayload.encode(byteArrayOf(), null)))
        assertTrue(decoded.address.isEmpty())
    }
}
