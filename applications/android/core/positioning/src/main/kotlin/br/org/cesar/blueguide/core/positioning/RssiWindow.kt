package br.org.cesar.blueguide.core.positioning

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
        return if (recent.size % 2 == 1) {
            recent[middle].toDouble()
        } else {
            (recent[middle - 1] + recent[middle]) / 2.0
        }
    }

    /** The RSSIs heard from [sinceMs] on, oldest first. */
    fun samples(sinceMs: Long): List<Int> = readings.filter { it.atMs >= sinceMs }.map { it.rssi }
}
