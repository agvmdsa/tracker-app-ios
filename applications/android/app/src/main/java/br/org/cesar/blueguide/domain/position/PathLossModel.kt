package br.org.cesar.blueguide.domain.position

import kotlin.math.pow

/**
 * Turns RSSI into a distance with the log-distance path loss model:
 *
 *     rssi = REFERENCE_RSSI - 10 * n * log10(d)
 *
 * Both constants are nominal. [REFERENCE_RSSI] in particular varies by several dB
 * between handsets, so a real deployment would calibrate it per broadcaster; until then
 * an error here scales every range at once, which shifts the fix outward or inward
 * rather than distorting its shape.
 */
object PathLossModel {

    /** RSSI expected one metre from a broadcaster, in dBm. */
    const val REFERENCE_RSSI: Double = -59.0

    /** 2.0 is free space. Indoors, walls and furniture push this towards 3–4. */
    const val PATH_LOSS_EXPONENT: Double = 2.0

    /**
     * How much of a new reading to fold into the running average, per sighting.
     *
     * Averaging happens in dB, not in metres: the noise is Gaussian in dB, so
     * converting first and averaging after would bias every range outward.
     */
    const val SMOOTHING: Double = 0.15

    fun distanceMeters(rssi: Double): Double =
        10.0.pow((REFERENCE_RSSI - rssi) / (10.0 * PATH_LOSS_EXPONENT))

    fun smooth(previous: Double?, reading: Int): Double =
        if (previous == null) reading.toDouble()
        else previous + SMOOTHING * (reading - previous)
}
