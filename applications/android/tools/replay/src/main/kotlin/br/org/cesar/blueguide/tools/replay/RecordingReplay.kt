package br.org.cesar.blueguide.tools.replay

import br.org.cesar.blueguide.core.dataset.FingerprintCsv
import br.org.cesar.blueguide.core.dataset.FingerprintCsvException
import br.org.cesar.blueguide.core.dataset.FingerprintRecordingMetadata
import br.org.cesar.blueguide.core.model.Point2D
import br.org.cesar.blueguide.core.positioning.PositioningReplay
import br.org.cesar.blueguide.core.positioning.PositioningSetup
import br.org.cesar.blueguide.core.positioning.PositioningUpdate
import br.org.cesar.blueguide.core.positioning.RecordedSighting
import java.io.File
import kotlin.math.ceil

/** A finished recording on disk: `<name>.csv` and the `<name>.json` written when it ran to the end. */
class Recording(val csv: File, val metadata: FingerprintRecordingMetadata) {

    val name: String get() = csv.nameWithoutExtension

    /** Where the phone stood: the recording's label. */
    val truth: Point2D get() = Point2D(metadata.x, metadata.y)

    /** Its rows' advertisements, in the order they were heard. Read only when replayed, so a
     *  folder of recordings is held in memory one at a time. */
    fun sightings(): List<RecordedSighting> = try {
        csv.useLines { lines -> FingerprintCsv.records(lines).map { RecordedSighting(it.beacon, it.rssi, it.monotonicMs) }.toList() }
    } catch (e: FingerprintCsvException) {
        throw IllegalArgumentException("${csv.name}: ${e.message}", e)
    }

    companion object {
        /** Null when the CSV has no JSON next to it: it was cut before its time was up, and is left
         *  out of the dataset, as the app does. Fails with [IllegalArgumentException] naming the
         *  file when the JSON isn't a recording's. */
        fun open(csv: File): Recording? {
            val json = File(csv.parentFile, "${csv.nameWithoutExtension}.json")
            if (!json.exists()) return null
            val metadata = try {
                FingerprintRecordingMetadata.fromJson(json.readText())
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("${json.name}: ${e.message}", e)
            }
            return Recording(csv, metadata)
        }
    }
}

/** What to change in a recording's setup: another calibration to try, or the one a version 1
 *  recording didn't keep. Null keeps the recording's. */
data class CalibrationOverride(
    val centerRssi: Double? = null,
    val touchRssi: Double? = null,
    val maxSpeedKmh: Double? = null,
    val noSpeedLimit: Boolean = false
) {
    val setsCalibration: Boolean get() = centerRssi != null && touchRssi != null

    fun applied(setup: PositioningSetup): PositioningSetup = setup.copy(
        centerRssi = centerRssi ?: setup.centerRssi,
        touchRssi = touchRssi ?: setup.touchRssi,
        maxSpeedKmh = if (noSpeedLimit) null else maxSpeedKmh ?: setup.maxSpeedKmh
    )
}

/** One second of a replayed recording. */
class ReplayedSecond(val recording: Recording, val update: PositioningUpdate) {
    /** How far the position the app would have shown was from where the phone stood; null
     *  without a fix. */
    val errorMeters: Double? get() = update.fix?.position?.distanceTo(recording.truth)
}

/** [recording] through the app's positioning engine with [setup]: a refresh every second from the
 *  start of the recording to its end. */
fun replay(recording: Recording, setup: PositioningSetup): List<ReplayedSecond> =
    PositioningReplay.replay(recording.sightings(), setup, startMs = recording.metadata.startedTMs, endMs = recording.metadata.endedTMs)
        .map { ReplayedSecond(recording, it) }

/** The errors of the seconds with a fix. The worst case is [max]; [p95] is the error 95% of
 *  them stay within (nearest rank). */
data class ErrorSummary(val seconds: Int, val fixes: Int, val mean: Double?, val p95: Double?, val max: Double?) {
    companion object {
        fun of(seconds: List<ReplayedSecond>): ErrorSummary {
            val errors = seconds.mapNotNull { it.errorMeters }.sorted()
            if (errors.isEmpty()) return ErrorSummary(seconds.size, 0, null, null, null)
            val p95 = errors[(ceil(0.95 * errors.size).toInt() - 1).coerceIn(errors.indices)]
            return ErrorSummary(seconds.size, errors.size, errors.average(), p95, errors.last())
        }
    }
}
