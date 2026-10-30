package br.org.cesar.blueguide.core.positioning

import br.org.cesar.blueguide.core.model.Point2D
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The two numbers of the log-distance model, `rssi = referenceRssi - 10 * exponent * log10(d)`,
 * as calibrated from what the phone reads in the middle of the anchors and touching one — the
 * counterpart of the iOS app's `PathLossCalibration`.
 */
data class PathLossCalibration(
    /** The RSSI at 1 m. */
    val referenceRssi: Double,
    val exponent: Double,
    /** False when the touching reading could not be used (missing, or not stronger than the
     *  center's) and the center alone calibrated, with the nominal exponent. */
    val usesTouchRssi: Boolean
) {
    fun distanceMeters(rssi: Double): Double = 10.0.pow((referenceRssi - rssi) / (10.0 * exponent))

    fun rssi(atDistance: Double): Double = referenceRssi - 10.0 * exponent * log10(atDistance)
}

/** Same model and constants as the iOS app's `PathLossModel`. */
object PathLossModel {

    /** The free-space exponent, used when only the center RSSI is known. [Multilateration] also
     *  sizes its RSSI noise with it. */
    const val PATH_LOSS_EXPONENT: Double = 2.0

    /** What the iPhone reads from each ESP32 beacon in the middle of the 2.5 m bench square,
     *  1.77 m from every corner. */
    const val DEFAULT_CENTER_RSSI: Double = -70.0

    /** What the phone reads touching a beacon. Measured between -34 and -42 dBm; -30 is the base. */
    const val DEFAULT_TOUCH_RSSI: Double = -30.0

    /** How far apart the antennas are with the phone touching a beacon: a few centimetres inside
     *  each case. */
    const val TOUCH_DISTANCE_METERS: Double = 0.1

    /** The bench square's mean distance from the corners to the middle. */
    val BENCH_CENTER_DISTANCE_METERS: Double = 2.5 / sqrt(2.0)

    /** Exponents outside this are not a room but a wrong reading (the phone was not touching, or
     *  not in the middle), so the touching RSSI is then ignored. */
    val PLAUSIBLE_EXPONENTS: ClosedFloatingPointRange<Double> = 1.0..6.0

    /** The bench calibration: the default readings on the 2.5 m square. */
    val DEFAULT_CALIBRATION: PathLossCalibration =
        calibration(DEFAULT_CENTER_RSSI, BENCH_CENTER_DISTANCE_METERS, DEFAULT_TOUCH_RSSI)

    /** The RSSI at 1 m of [DEFAULT_CALIBRATION]: about -62 dBm. */
    val REFERENCE_RSSI: Double get() = DEFAULT_CALIBRATION.referenceRssi

    fun distanceMeters(rssi: Double, calibration: PathLossCalibration = DEFAULT_CALIBRATION): Double =
        calibration.distanceMeters(rssi)

    /**
     * Reads [centerRssi] as [centerDistanceMeters] and [touchRssi] as [TOUCH_DISTANCE_METERS], which
     * fixes both the RSSI at 1 m and the exponent. Without a usable touching reading, the center
     * alone fixes the RSSI at 1 m, with the nominal exponent.
     */
    fun calibration(centerRssi: Double, centerDistanceMeters: Double, touchRssi: Double?): PathLossCalibration {
        if (touchRssi != null && centerDistanceMeters > TOUCH_DISTANCE_METERS) {
            val exponent = (touchRssi - centerRssi) / (10.0 * log10(centerDistanceMeters / TOUCH_DISTANCE_METERS))
            if (exponent in PLAUSIBLE_EXPONENTS) {
                return PathLossCalibration(centerRssi + 10.0 * exponent * log10(centerDistanceMeters), exponent, usesTouchRssi = true)
            }
        }
        return PathLossCalibration(
            centerRssi + 10.0 * PATH_LOSS_EXPONENT * log10(centerDistanceMeters),
            PATH_LOSS_EXPONENT,
            usesTouchRssi = false
        )
    }

    /**
     * How far the anchors are, on average, from their middle (the mean of their positions).
     * Null for fewer than two anchors, which have no middle to be away from.
     */
    fun centerDistanceMeters(anchors: List<Point2D>): Double? {
        if (anchors.size < 2) return null
        val middle = Point2D(anchors.sumOf { it.x } / anchors.size, anchors.sumOf { it.y } / anchors.size)
        val distance = anchors.sumOf { it.distanceTo(middle) } / anchors.size
        return distance.takeIf { it > 1e-6 }
    }
}
