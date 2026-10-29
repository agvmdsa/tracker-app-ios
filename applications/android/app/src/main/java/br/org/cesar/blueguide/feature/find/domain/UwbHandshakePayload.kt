package br.org.cesar.blueguide.feature.find.domain

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * What the BLE out-of-band handshake carries for UWB session setup: this device's address,
 * and — controller side only — the channel/session info the controlee needs to start
 * ranging (`androidx.core.uwb.UwbControllerSessionScope.uwbComplexChannel` plus a session id
 * and key the controller generates). NearbyInteraction's discovery tokens are symmetric, so
 * the iOS app's handshake just hands one over; UWB's controller/controlee roles are not, so
 * this payload's shape depends on which role produced it (see [UwbProximityTracker]).
 *
 * Pure byte packing — no Android or `androidx.core.uwb` types — so it's testable on the host
 * JVM; the real implementations only ever call [encode]/[decode], never hand-roll parsing.
 */
object UwbHandshakePayload {

    data class ControllerInfo(
        val channel: Int,
        val preambleIndex: Int,
        val sessionId: Int,
        val sessionKeyInfo: ByteArray
    )

    data class Decoded(
        val address: ByteArray,
        /** Present only when this payload came from the controller. */
        val controllerInfo: ControllerInfo?
    )

    fun encode(address: ByteArray, controllerInfo: ControllerInfo?): ByteArray {
        val controllerBytes = controllerInfo?.let { info ->
            ByteBuffer.allocate(1 + 1 + 4 + 1 + info.sessionKeyInfo.size).order(ByteOrder.BIG_ENDIAN).apply {
                put(info.channel.toByte())
                put(info.preambleIndex.toByte())
                putInt(info.sessionId)
                put(info.sessionKeyInfo.size.toByte())
                put(info.sessionKeyInfo)
            }.array()
        }
        val capacity = 1 + address.size + 1 + (controllerBytes?.size ?: 0)
        return ByteBuffer.allocate(capacity).order(ByteOrder.BIG_ENDIAN).apply {
            put(address.size.toByte())
            put(address)
            put(if (controllerBytes != null) 1 else 0)
            controllerBytes?.let { put(it) }
        }.array()
    }

    /** Null on malformed input (truncated, impossible lengths) rather than throwing — a bad
     *  peer payload should fail the handshake gracefully, not crash the app. */
    fun decode(bytes: ByteArray): Decoded? = try {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val addressLength = buffer.get().toInt() and 0xFF
        val address = ByteArray(addressLength).also { buffer.get(it) }
        val hasControllerInfo = buffer.get() == 1.toByte()
        val controllerInfo = if (hasControllerInfo) {
            val channel = buffer.get().toInt() and 0xFF
            val preambleIndex = buffer.get().toInt() and 0xFF
            val sessionId = buffer.int
            val keyLength = buffer.get().toInt() and 0xFF
            val sessionKeyInfo = ByteArray(keyLength).also { buffer.get(it) }
            ControllerInfo(channel, preambleIndex, sessionId, sessionKeyInfo)
        } else {
            null
        }
        Decoded(address, controllerInfo)
    } catch (_: Exception) {
        null
    }
}
