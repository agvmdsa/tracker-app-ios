package br.org.cesar.blueguide.domain.entity

import java.util.Locale

/**
 * Identity of one BlueGuide install, carried in its advertisement.
 *
 * The BLE address cannot serve as identity: Android and iOS both rotate the private
 * address they advertise from (roughly every 15 minutes), so the same phone shows up
 * as a stream of unrelated devices. This is what survives that rotation.
 *
 * Held as uppercase hex so it can be a map key, compared, and shown as-is.
 */
@JvmInline
value class DeviceId(val value: String) {

    companion object {
        fun ofBytes(bytes: ByteArray): DeviceId = DeviceId(
            bytes.joinToString("") { byte ->
                "%02X".format(Locale.ROOT, byte.toInt() and 0xFF)
            }
        )
    }
}
