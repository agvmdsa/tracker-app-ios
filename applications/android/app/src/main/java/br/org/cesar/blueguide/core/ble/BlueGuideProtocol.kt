package br.org.cesar.blueguide.core.ble

import br.org.cesar.blueguide.core.model.DeviceId
import java.security.SecureRandom
import java.util.UUID

/** What one BlueGuide install looks like to another. */
object BlueGuideProtocol {

    /**
     * The service UUID that marks a broadcast as BlueGuide. Broadcast mode advertises it
     * and observer mode scans for it, which is the whole basis for telling other users
     * apart from the rest of the radios in the room.
     *
     * Randomly generated rather than taken from the Bluetooth SIG 16-bit range: that
     * range is assigned, and squatting on a number in it risks matching real hardware.
     */
    val SERVICE_UUID: UUID = UUID.fromString("fc979812-36dd-4722-9316-d2a7908888dd")

    /**
     * Company ID tagging the manufacturer-specific data that carries a [DeviceId].
     * 0xFFFF is the value the SIG reserves for internal and test use, so it belongs to
     * no vendor: the same reasoning that keeps [SERVICE_UUID] out of the assigned range.
     *
     * Plenty of other apps use 0xFFFF too, but an observer only ever reads it off
     * packets that already matched [SERVICE_UUID], so there is nothing to mix it up with.
     */
    const val MANUFACTURER_ID: Int = 0xFFFF

    /**
     * An advertisement is 31 bytes: flags (3) plus the 128-bit service UUID (18) plus
     * the manufacturer-data header (2 framing + 2 company ID) leaves 6. Four of them is
     * enough that two devices in a crowd of a hundred collide about once in a million,
     * and it keeps the id in the primary packet where every sighting carries it.
     */
    const val ID_SIZE_BYTES: Int = 4

    private val random = SecureRandom()

    /** A fresh identity for this install. */
    fun randomId(): DeviceId = DeviceId.ofBytes(
        ByteArray(ID_SIZE_BYTES).also(random::nextBytes)
    )

    fun idToBytes(id: DeviceId): ByteArray {
        require(id.value.length == ID_SIZE_BYTES * 2) {
            "DeviceId must be $ID_SIZE_BYTES bytes of hex, was '${id.value}'"
        }
        return ByteArray(ID_SIZE_BYTES) { i ->
            id.value.substring(i * 2, i * 2 + 2).toInt(radix = 16).toByte()
        }
    }

    /**
     * Null when the field is missing or short, which is what a peer running a build
     * from before the id existed looks like. Callers fall back to the BLE address.
     */
    fun idFromBytes(bytes: ByteArray?): DeviceId? =
        if (bytes == null || bytes.size < ID_SIZE_BYTES) {
            null
        } else {
            DeviceId.ofBytes(bytes.copyOf(ID_SIZE_BYTES))
        }

    /**
     * The id an advertisement of this protocol carries, by the same rules as the iOS app's
     * `BeaconProtocol`. The ESP32 beacons and iOS anchors put it in the local name; Android
     * puts it in the manufacturer data and its device name in the local name. So the
     * manufacturer data wins when present, and the local name is only trusted when it is an
     * id itself.
     */
    fun idFrom(manufacturerData: ByteArray?, localName: String?): DeviceId? =
        idFromBytes(manufacturerData) ?: localName?.let(::idFromHex)

    /** [ID_SIZE_BYTES] bytes of hex, in either case, read in upper case. Null for anything else. */
    fun idFromHex(text: String): DeviceId? =
        if (text.length == ID_SIZE_BYTES * 2 && text.all { it.isDigit() || it.uppercaseChar() in 'A'..'F' }) {
            DeviceId(text.uppercase())
        } else {
            null
        }
}
