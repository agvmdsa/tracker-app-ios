package br.org.cesar.blueguide.core.ui

import java.util.Locale

/**
 * Numbers typed into coordinate fields — the counterpart of the iOS app's `DecimalInput`. A
 * Portuguese keyboard types "2,5", but `"2,5".toDoubleOrNull()` is null (and it would accept
 * "NaN", "Infinity" and "1e3"). Here a dot or a comma both work, and nothing else does.
 */
object DecimalInput {

    /**
     * A plain decimal number, optionally signed, with at most one separator ("," or "."). Null
     * for anything else: empty text, letters, exponents, "nan" or "inf", two separators, or a
     * thousands separator, which is ambiguous between the two conventions.
     */
    fun parse(text: String): Double? {
        var body = text.trim(' ')
        var sign = 1.0
        if (body.startsWith("-")) {
            sign = -1.0
            body = body.drop(1)
        } else if (body.startsWith("+")) {
            body = body.drop(1)
        }
        if (body.isEmpty() ||
            !body.all { it in '0'..'9' || it == '.' || it == ',' } ||
            body.none { it in '0'..'9' } ||
            body.count { it == '.' || it == ',' } > 1
        ) {
            return null
        }
        return body.replace(',', '.').toDoubleOrNull()?.let { sign * it }
    }

    /** What a field shows for [value]: plain decimal with a dot, up to 3 decimals and no
     *  trailing zeros (2.5, -1, 0.125). Never an exponent, which [parse] would refuse. */
    fun text(value: Double): String {
        if (!value.isFinite()) return ""
        val text = String.format(Locale.ROOT, "%.3f", value).trimEnd('0').trimEnd('.')
        return if (text == "-0") "0" else text
    }
}
