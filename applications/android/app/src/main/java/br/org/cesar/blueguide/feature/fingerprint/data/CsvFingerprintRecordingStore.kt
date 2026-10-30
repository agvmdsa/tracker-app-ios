package br.org.cesar.blueguide.feature.fingerprint.data

import br.org.cesar.blueguide.core.dataset.FingerprintCsv
import br.org.cesar.blueguide.core.dataset.FingerprintLabel
import br.org.cesar.blueguide.core.dataset.FingerprintRecord
import br.org.cesar.blueguide.core.dataset.FingerprintRecordingMetadata
import br.org.cesar.blueguide.di.RecordingsDirectory
import br.org.cesar.blueguide.feature.fingerprint.domain.FingerprintRecordingStore
import br.org.cesar.blueguide.feature.fingerprint.domain.FinishedRecording
import br.org.cesar.blueguide.feature.fingerprint.domain.RecordingWriter
import br.org.cesar.blueguide.feature.fingerprint.domain.StoredRecording
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/**
 * The recordings as files in the app's private storage, the way the iOS app keeps them: one CSV
 * per recording, one row per advertisement ([FingerprintCsv]), and `<same name>.json` with the
 * recording's window and context ([FingerprintRecordingMetadata]) once it ends.
 */
class CsvFingerprintRecordingStore @Inject constructor(
    @RecordingsDirectory private val directory: File
) : FingerprintRecordingStore {

    override fun list(): List<StoredRecording> =
        directory.listFiles { file -> file.extension == "csv" }.orEmpty()
            .sortedByDescending { it.name }
            .map { csv -> StoredRecording(csv, metadataFile(csv).takeIf { it.exists() }) }

    override fun create(label: FingerprintLabel, startedAt: Instant): RecordingWriter {
        directory.mkdirs()
        val csv = File(directory, fileName(label, startedAt))
        val writer = BufferedWriter(FileWriter(csv, false))
        try {
            writer.write(FingerprintCsv.HEADER)
            writer.newLine()
            writer.flush()
        } catch (e: Exception) {
            writer.close()
            csv.delete()
            throw e
        }
        return CsvRecordingWriter(csv, writer)
    }

    override fun delete(recording: StoredRecording): Boolean {
        if (!recording.csv.delete()) return false
        metadataFile(recording.csv).delete()
        return true
    }

    private class CsvRecordingWriter(private val csv: File, private val writer: BufferedWriter) : RecordingWriter {

        override val recording = StoredRecording(csv, metadata = null)

        override fun append(record: FingerprintRecord) {
            writer.write(FingerprintCsv.line(record))
            writer.newLine()
        }

        override fun flush() = writer.flush()

        override fun finish(info: FinishedRecording): StoredRecording {
            writer.close()
            val metadata = metadataFile(csv)
            metadata.writeText(metadataOf(info, csv).toJson())
            return StoredRecording(csv, metadata)
        }

        override fun discard() {
            try {
                writer.close()
            } catch (_: Exception) {
                // On its way to being deleted; nothing more useful to do.
            }
            csv.delete()
        }
    }

    companion object {
        /** `DeviceMotionReading`'s requested sensor rate — see `AndroidOrientationReader`. */
        private const val ORIENTATION_RATE_HZ = 50.0

        private val FILE_NAME_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT).withZone(ZoneId.systemDefault())

        private fun metadataFile(csv: File): File = File(csv.parentFile, csv.nameWithoutExtension + ".json")

        /** Starts with the date so the files sort chronologically, e.g. `20261022-143005_S1_P3.csv`. */
        private fun fileName(label: FingerprintLabel, startedAt: Instant): String =
            "${FILE_NAME_FORMAT.format(startedAt)}_${label.session}_${label.point}.csv"

        /** The recorder's side of the metadata, and what only this Android implementation knows:
         *  how the advertisements were received and what the sensor columns mean here. */
        private fun metadataOf(info: FinishedRecording, csv: File) = FingerprintRecordingMetadata(
            platform = "Android",
            node = info.node.value,
            model = info.model,
            systemVersion = info.systemVersion,
            session = info.label.session,
            point = info.label.point,
            x = info.label.x,
            y = info.label.y,
            csvFile = csv.name,
            startedTMs = info.startedMonotonicMs,
            endedTMs = info.endedMonotonicMs,
            startedTimestampMs = info.startedAt.toEpochMilli(),
            endedTimestampMs = info.endedAt.toEpochMilli(),
            plannedDurationS = info.plannedDurationSeconds,
            recordsPerBeacon = info.recordsPerBeacon.mapKeys { it.key.value },
            scanApi = "Android BluetoothLeScanner.startScan, service UUID filter, SCAN_MODE_LOW_LATENCY",
            orientationConvention = "radians, SensorManager TYPE_ROTATION_VECTOR via getOrientation() " +
                "(yaw=azimuth, then pitch, then roll, Android's own axis convention — see AndroidOrientationReader); " +
                "yaw is relative, not tied to true/magnetic north",
            orientationRateHz = ORIENTATION_RATE_HZ,
            altitudeConvention = "metres, SensorManager.getAltitude() relative to the barometric pressure " +
                "recorded when this recording started — not sea level or a floor number; pressure in kPa",
            motionActivityConvention = "DetectedActivity type mapped to stationary/walking/running/automotive/" +
                "cycling/unknown; confidence 0-100 (Android's own scale — NOT the iOS app's 0/1/2); an empty " +
                "row value means no update had arrived yet or permission wasn't granted, not literally 'unknown'",
            beacons = info.setup.anchors.sortedBy { it.id.value }.map(FingerprintRecordingMetadata.BeaconSnapshot::of),
            positioning = FingerprintRecordingMetadata.PositioningSnapshot.of(info.setup)
        )
    }
}
