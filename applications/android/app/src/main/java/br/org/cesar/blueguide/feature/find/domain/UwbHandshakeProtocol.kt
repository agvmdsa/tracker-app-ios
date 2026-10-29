package br.org.cesar.blueguide.feature.find.domain

import java.security.SecureRandom
import java.util.UUID

/**
 * What one BlueGuide install's UWB handshake looks like to another — the counterpart of the
 * iOS app's constants on `BLEHandshake` (`serviceUUID`, `tokenCharacteristicUUID`).
 *
 * The service UUID is deliberately different from both [br.org.cesar.blueguide.core.ble.BlueGuideProtocol.SERVICE_UUID]
 * (the position-beacon protocol) and the iOS app's own handshake UUID: this one carries a
 * [UwbHandshakePayload], a format with no NearbyInteraction equivalent, so there is nothing
 * for the two platforms' handshakes to usefully interoperate on even though both exist for
 * the same "find this phone" feature — UWB ranging itself is Android-to-Android only here,
 * the same way it would be iOS-to-iOS only on that side (Apple's and Android's UWB stacks
 * don't share a ranging protocol either).
 */
object UwbHandshakeProtocol {
    val SERVICE_UUID: UUID = UUID.fromString("8C2D4E61-9B3A-4F7C-A1E5-6D0B2F9C7A33")
    val PAYLOAD_CHARACTERISTIC_UUID: UUID = UUID.fromString("8C2D4E62-9B3A-4F7C-A1E5-6D0B2F9C7A33")

    /** Company ID tagging the manufacturer-specific data that carries the local id in the
     *  advertisement — same reasoning as [br.org.cesar.blueguide.core.ble.BlueGuideProtocol.MANUFACTURER_ID]
     *  for picking 0xFFFF, just a different value so the two protocols' manufacturer data is
     *  never ambiguous even if a scan result somehow matched both filters. */
    const val MANUFACTURER_ID: Int = 0xFFFE
    const val ID_SIZE_BYTES: Int = 4

    /** Matches `BLEHandshake.maxTokenSize` on iOS: the largest ATT attribute value either
     *  side will read or write. A controller's payload (address + channel + session id/key)
     *  is at most a few dozen bytes, well under this. */
    const val MAX_PAYLOAD_SIZE: Int = 512

    private val random = SecureRandom()

    fun randomId(): String {
        val bytes = ByteArray(ID_SIZE_BYTES)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02X".format(it) }
    }

    fun idToBytes(id: String): ByteArray {
        require(id.length == ID_SIZE_BYTES * 2) { "id must be $ID_SIZE_BYTES bytes of hex, was '$id'" }
        return ByteArray(ID_SIZE_BYTES) { i -> id.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }

    fun idFromBytes(bytes: ByteArray?): String? {
        if (bytes == null || bytes.size < ID_SIZE_BYTES) return null
        return bytes.copyOf(ID_SIZE_BYTES).joinToString("") { "%02X".format(it) }
    }
}
