package br.org.cesar.blueguide.tools.replay

import br.org.cesar.blueguide.core.model.DeviceId
import java.util.Locale

/**
 * The replay's CSV: one row per second of each recording and engine — where the engine would have
 * placed the phone, where it stood and how far apart, then the median RSSI and the distance of
 * every beacon (empty when not heard that second). The features and the target for a model that corrects the
 * error, and the measure of how far the solver is from the goal.
 */
object ReplayTable {

    val FIXED_COLUMNS = listOf(
        "recording", "engine", "session", "point", "true_x", "true_y", "t_ms", "elapsed_ms",
        "x", "y", "error_m", "uncertainty_m", "anchors_ranged"
    )

    /** [beacons]: every beacon of the recordings in the table, so each has its columns. */
    fun header(beacons: List<DeviceId>): String =
        (FIXED_COLUMNS + beacons.flatMap { listOf("rssi_${it.value}", "distance_${it.value}") }).joinToString(",")

    fun line(second: ReplayedSecond, beacons: List<DeviceId>): String {
        val metadata = second.recording.metadata
        val update = second.update
        val fix = update.fix
        val ranges = update.ranges.associateBy { it.anchor.id }
        val fields = listOf(
            quotedIfNeeded(second.recording.name),
            second.engine,
            metadata.session,
            metadata.point,
            decimal(metadata.x, 3),
            decimal(metadata.y, 3),
            update.atMs.toString(),
            (update.atMs - metadata.startedTMs).toString(),
            fix?.let { decimal(it.position.x, 3) }.orEmpty(),
            fix?.let { decimal(it.position.y, 3) }.orEmpty(),
            second.errorMeters?.let { decimal(it, 3) }.orEmpty(),
            fix?.let { decimal(it.uncertaintyMeters, 3) }.orEmpty(),
            update.ranges.size.toString()
        ) + beacons.flatMap { beacon ->
            val range = ranges[beacon]
            listOf(range?.rssi?.let { decimal(it, 1) }.orEmpty(), range?.let { decimal(it.distanceMeters, 3) }.orEmpty())
        }
        return fields.joinToString(",")
    }

    private fun decimal(value: Double, places: Int) = String.format(Locale.ROOT, "%.${places}f", value)

    /** The app names recordings with safe characters only, but a file can be renamed. */
    private fun quotedIfNeeded(text: String) = if (text.any { it == ',' || it == '"' }) "\"" + text.replace("\"", "\"\"") + "\"" else text
}
