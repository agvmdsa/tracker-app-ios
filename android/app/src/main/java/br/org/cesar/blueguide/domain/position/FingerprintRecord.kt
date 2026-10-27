package br.org.cesar.blueguide.domain.position

import br.org.cesar.blueguide.domain.entity.DeviceId
import java.time.Instant
import java.util.Locale

/**
 * One row of a fingerprinting dataset: a single advertisement as this phone received it, how
 * the phone was being held at the time, where it was, and what it was doing — the
 * counterpart of the iOS app's `FingerprintRecord`. Column names and order are identical, so
 * CSVs from both platforms (and overlapping columns from the ESP32 collector's `R` records)
 * can be concatenated directly.
 */
object FingerprintRecordFormat {
    val CSV_HEADER = listOf(
        "t_ms", "timestamp_ms", "node", "model", "system_version", "beacon", "rssi",
        "roll", "pitch", "yaw", "relative_altitude_m", "pressure_kpa",
        "motion_state", "motion_confidence", "session", "point", "x", "y"
    ).joinToString(",")
}

data class FingerprintRecord(
    /** `t_ms`: monotonic milliseconds (`SystemClock.elapsedRealtime()`), for ordering and
     *  windowing the rows. */
    val monotonicMs: Long,
    /** `timestamp_ms`: Unix time, for lining recordings up across devices. */
    val seenAt: Instant,
    /** The recording phone's own install id. */
    val node: DeviceId,
    val model: String,
    val systemVersion: String,
    val beacon: DeviceId,
    val rssi: Int,
    val orientation: DeviceMotionSample?,
    val altitude: AltitudeSample?,
    val motionActivity: MotionActivitySample?,
    val label: FingerprintLabel
) {
    val csvLine: String
        get() {
            val orientationFields = orientation?.let {
                listOf(decimal(it.roll, 4), decimal(it.pitch, 4), decimal(it.yaw, 4))
            } ?: listOf("", "", "")
            val altitudeFields = altitude?.let {
                listOf(decimal(it.relativeAltitudeM, 3), decimal(it.pressureKPa, 3))
            } ?: listOf("", "")
            val motionFields = motionActivity?.let { listOf(it.state.wireValue, it.confidence.toString()) } ?: listOf("", "")

            val fields = listOf(
                monotonicMs.toString(), seenAt.toEpochMilli().toString(), node.value,
                quoted(model), quoted(systemVersion), beacon.value, rssi.toString()
            ) + orientationFields + altitudeFields + motionFields + listOf(
                label.session, label.point, decimal(label.x, 2), decimal(label.y, 2)
            )
            return fields.joinToString(",")
        }

    /** Model identifiers contain a comma ("Pixel 8,Pro" style names do exist), so free-text
     *  fields are always quoted. */
    private fun quoted(field: String) = "\"" + field.replace("\"", "\"\"") + "\""

    private fun decimal(value: Double, places: Int) = String.format(Locale.ROOT, "%.${places}f", value)
}
