package br.org.cesar.blueguide.core.dataset

import br.org.cesar.blueguide.core.model.AltitudeSample
import br.org.cesar.blueguide.core.model.DeviceId
import br.org.cesar.blueguide.core.model.DeviceMotionSample
import br.org.cesar.blueguide.core.model.MotionActivitySample
import br.org.cesar.blueguide.core.model.MotionActivityState
import java.time.Instant
import java.util.Locale

/**
 * A recording's CSV: one [FingerprintRecord] per row. Column names and order are the iOS app's,
 * so CSVs from both platforms (and overlapping columns from the ESP32 collector's `R` records)
 * can be concatenated directly.
 */
object FingerprintCsv {

    val COLUMNS = listOf(
        "t_ms", "timestamp_ms", "node", "model", "system_version", "beacon", "rssi",
        "roll", "pitch", "yaw", "relative_altitude_m", "pressure_kpa",
        "motion_state", "motion_confidence", "session", "point", "x", "y"
    )

    val HEADER: String = COLUMNS.joinToString(",")

    /** [record] as a row: free text quoted, decimals with a dot, a sensor without a reading left
     *  empty. Orientation keeps 4 places, altitude and pressure 3, the label's position 2. */
    fun line(record: FingerprintRecord): String {
        val orientationFields = record.orientation?.let {
            listOf(decimal(it.roll, 4), decimal(it.pitch, 4), decimal(it.yaw, 4))
        } ?: listOf("", "", "")
        val altitudeFields = record.altitude?.let {
            listOf(decimal(it.relativeAltitudeM, 3), decimal(it.pressureKPa, 3))
        } ?: listOf("", "")
        val motionFields = record.motionActivity?.let { listOf(it.state.wireValue, it.confidence.toString()) } ?: listOf("", "")

        val fields = listOf(
            record.monotonicMs.toString(),
            record.seenAt.toEpochMilli().toString(),
            record.node.value,
            quoted(record.model),
            quoted(record.systemVersion),
            record.beacon.value,
            record.rssi.toString()
        ) + orientationFields + altitudeFields + motionFields + listOf(
            record.label.session,
            record.label.point,
            decimal(record.label.x, 2),
            decimal(record.label.y, 2)
        )
        return fields.joinToString(",")
    }

    /**
     * The rows of a CSV's [lines], header first, as they come: a long recording is tens of
     * thousands of rows, so nothing is read ahead. A header other than [HEADER], or a row that
     * isn't one, fails with [FingerprintCsvException] naming the line.
     */
    fun records(lines: Sequence<String>): Sequence<FingerprintRecord> = sequence {
        var number = 0
        for (line in lines) {
            number++
            if (number == 1) {
                if (line.removePrefix(BYTE_ORDER_MARK) != HEADER) throw FingerprintCsvException(1, "not the fingerprint header")
                continue
            }
            if (line.isEmpty()) continue
            yield(record(line, number))
        }
        if (number == 0) throw FingerprintCsvException(1, "empty file")
    }

    private fun record(line: String, number: Int): FingerprintRecord {
        val fields = fields(line, number)
        if (fields.size != COLUMNS.size) throw FingerprintCsvException(number, "${fields.size} fields, not ${COLUMNS.size}")
        val column = COLUMNS.withIndex().associate { (index, name) -> name to fields[index] }

        fun text(name: String): String = column.getValue(name)
        fun optional(name: String): String? = text(name).ifEmpty { null }
        fun long(name: String): Long = text(name).toLongOrNull() ?: throw FingerprintCsvException(number, "$name is not a whole number")
        fun double(name: String): Double =
            text(name).toDoubleOrNull()?.takeIf { it.isFinite() } ?: throw FingerprintCsvException(number, "$name is not a number")
        fun optionalDouble(name: String): Double? = optional(name)?.let { double(name) }

        val roll = optionalDouble("roll")
        val pitch = optionalDouble("pitch")
        val yaw = optionalDouble("yaw")
        val relativeAltitude = optionalDouble("relative_altitude_m")
        val pressure = optionalDouble("pressure_kpa")
        val motionState = optional("motion_state")
        val motionConfidence = optional("motion_confidence")
        return FingerprintRecord(
            monotonicMs = long("t_ms"),
            seenAt = Instant.ofEpochMilli(long("timestamp_ms")),
            node = DeviceId(text("node")),
            model = text("model"),
            systemVersion = text("system_version"),
            beacon = DeviceId(text("beacon")),
            rssi = long("rssi").toInt(),
            orientation = if (roll != null && pitch != null && yaw != null) DeviceMotionSample(roll, pitch, yaw) else null,
            altitude = if (relativeAltitude != null && pressure != null) AltitudeSample(relativeAltitude, pressure) else null,
            motionActivity = if (motionState != null && motionConfidence != null) {
                MotionActivitySample(
                    MotionActivityState.ofWireValue(motionState)
                        ?: throw FingerprintCsvException(number, "unknown motion_state $motionState"),
                    long("motion_confidence").toInt()
                )
            } else {
                null
            },
            label = FingerprintLabel.of(text("session"), text("point"), double("x"), double("y"))
                ?: throw FingerprintCsvException(number, "not a valid session/point")
        )
    }

    /** Splits a row at the commas outside quotes; a quoted field's `""` is one quote. */
    private fun fields(line: String, number: Int): List<String> {
        val fields = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                quoted && char == '"' && line.getOrNull(index + 1) == '"' -> {
                    field.append('"')
                    index++
                }
                char == '"' -> quoted = !quoted
                !quoted && char == ',' -> {
                    fields += field.toString()
                    field.clear()
                }
                else -> field.append(char)
            }
            index++
        }
        if (quoted) throw FingerprintCsvException(number, "a quote is not closed")
        fields += field.toString()
        return fields
    }

    /** Model identifiers contain a comma ("Pixel 8,Pro" style names do exist), so free-text
     *  fields are always quoted. */
    private fun quoted(field: String) = "\"" + field.replace("\"", "\"\"") + "\""

    private fun decimal(value: Double, places: Int) = String.format(Locale.ROOT, "%.${places}f", value)

    /** Spreadsheet apps add one when they save a CSV; it is not part of the header. */
    private const val BYTE_ORDER_MARK = "\uFEFF"
}

class FingerprintCsvException(val line: Int, reason: String) : IllegalArgumentException("line $line: $reason")
