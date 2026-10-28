package br.org.cesar.blueguide.domain.position

import kotlin.ConsistentCopyVisibility

/**
 * Where the phone is while recording: the ground truth a fingerprinting model learns from.
 * Same fields and rules as the iOS app's `FingerprintLabel` and the ESP32 collector's
 * `label <session> <point> <x> <y>` command, so datasets from all three can be merged.
 */
@ConsistentCopyVisibility
data class FingerprintLabel private constructor(
    val session: String,
    val point: String,
    /** Metres in the session's local frame. */
    val x: Double,
    val y: Double
) {
    companion object {
        const val MAX_TOKEN_LENGTH = 16

        fun of(session: String, point: String, x: Double, y: Double): FingerprintLabel? {
            if (!isToken(session) || !isToken(point) || !x.isFinite() || !y.isFinite()) return null
            return FingerprintLabel(session, point, x, y)
        }

        /** 1 to 16 characters of [A-Za-z0-9_-], so a token can never break a CSV row or a file name. */
        fun isToken(text: String): Boolean {
            if (text.length !in 1..MAX_TOKEN_LENGTH) return false
            return text.all { it.code < 128 && (it.isLetter() || it.isDigit() || it == '_' || it == '-') }
        }
    }
}
