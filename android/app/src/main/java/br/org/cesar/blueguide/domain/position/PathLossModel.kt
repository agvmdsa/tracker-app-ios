package br.org.cesar.blueguide.domain.position

import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Turns RSSI into a distance with the log-distance path loss model:
 *
 *     rssi = referenceRssi - 10 * n * log10(d)
 *
 * The reference (the RSSI at 1 m) is calibrated from [DEFAULT_CENTER_RSSI], the RSSI heard in
 * the middle of the anchors, which the Observer screen lets the user set again ("RSSI no
 * centro"). Same model and constants as the iOS app's `PathLossModel`.
 */
object PathLossModel {

    /** 2.0 is free space. Indoors, walls and furniture push this towards 3–4. */
    const val PATH_LOSS_EXPONENT: Double = 2.0

    /** What the iPhone reads from each ESP32 beacon in the middle of the 2.5 m bench square,
     *  1.77 m from every corner. */
    const val DEFAULT_CENTER_RSSI: Double = -70.0

    /** The RSSI at 1 m that [DEFAULT_CENTER_RSSI] implies on the bench square: about -65 dBm. */
    val REFERENCE_RSSI: Double = referenceRssi(DEFAULT_CENTER_RSSI, 2.5 / sqrt(2.0))

    fun distanceMeters(rssi: Double, referenceRssi: Double = REFERENCE_RSSI): Double =
        10.0.pow((referenceRssi - rssi) / (10.0 * PATH_LOSS_EXPONENT))

    /** The RSSI at 1 m for which [centerRssi] reads as [centerDistanceMeters]. */
    fun referenceRssi(centerRssi: Double, centerDistanceMeters: Double): Double =
        centerRssi + 10.0 * PATH_LOSS_EXPONENT * log10(centerDistanceMeters)

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

/**
 * The recent RSSI readings of one beacon, answered with their median — the counterpart of the
 * iOS app's `RSSIWindow`.
 *
 * The median ignores a stray reading (one -90 among -60s does not move it) and, unlike following
 * the last reading, it uses every advertisement heard in the window, however many each beacon
 * sends. It trails a moving phone by about half the window. Readings older than the window are
 * dropped and at most [capacity] are kept. Immutable, so it can live in the screen's state.
 */
class RssiWindow(
    val durationMs: Long,
    val capacity: Int,
    private val readings: List<Reading> = emptyList()
) {
    init {
        require(durationMs > 0 && capacity > 0) { "the window needs a duration and a capacity" }
    }

    data class Reading(val rssi: Int, val atMs: Long)

    val count: Int get() = readings.size

    /** [atMs] is the reading's monotonic time, the clock [median] is asked on. */
    fun plus(rssi: Int, atMs: Long): RssiWindow {
        val oldestKept = atMs - durationMs
        val kept = (readings + Reading(rssi, atMs)).filter { it.atMs >= oldestKept }.takeLast(capacity)
        return RssiWindow(durationMs, capacity, kept)
    }

    /** The median of the readings from the last [durationMs] before [nowMs]: the middle one, or
     *  the mean of the two middle ones. Null when nothing was heard in that time. */
    fun median(nowMs: Long): Double? {
        val recent = samples(nowMs - durationMs).sorted()
        if (recent.isEmpty()) return null
        val middle = recent.size / 2
        return if (recent.size % 2 == 1) recent[middle].toDouble()
        else (recent[middle - 1] + recent[middle]) / 2.0
    }

    /** The RSSIs heard from [sinceMs] on, oldest first. */
    fun samples(sinceMs: Long): List<Int> = readings.filter { it.atMs >= sinceMs }.map { it.rssi }
}
