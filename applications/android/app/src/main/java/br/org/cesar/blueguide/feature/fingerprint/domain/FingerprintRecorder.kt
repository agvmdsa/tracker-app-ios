package br.org.cesar.blueguide.feature.fingerprint.domain

import br.org.cesar.blueguide.core.ble.BleScanner
import br.org.cesar.blueguide.core.data.AnchorLayoutCheck
import br.org.cesar.blueguide.core.data.AnchorStore
import br.org.cesar.blueguide.core.dataset.FingerprintLabel
import br.org.cesar.blueguide.core.dataset.FingerprintRecord
import br.org.cesar.blueguide.core.dataset.FingerprintRecordFormat
import br.org.cesar.blueguide.core.dataset.FingerprintRecordingMetadata
import br.org.cesar.blueguide.core.model.Anchor
import br.org.cesar.blueguide.core.model.DeviceId
import br.org.cesar.blueguide.core.model.DeviceSighting
import br.org.cesar.blueguide.core.network.FingerprintUploading
import br.org.cesar.blueguide.core.sensors.AltitudeReading
import br.org.cesar.blueguide.core.sensors.DeviceMotionReading
import br.org.cesar.blueguide.core.sensors.MotionActivityReading
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A recording in progress; null when not recording. Counterpart of the iOS app's
 *  `FingerprintRecorder.ActiveRecording`. */
data class ActiveFingerprintRecording(
    val label: FingerprintLabel,
    val startedAt: Instant,
    val plannedDurationSeconds: Double,
    /** `startedAt` on the monotonic clock injected into the recorder. */
    val startedMonotonicMs: Long,
    /** The beacons' positions when the recording started. */
    val beacons: List<Anchor>
)

/** What a finished recording captured, like the ESP32 collector's `S` lines. */
data class FingerprintRecordingSummary(
    val file: File,
    val label: FingerprintLabel,
    val durationSeconds: Double,
    val recordsPerBeacon: Map<DeviceId, Int>
) {
    val recordCount: Int get() = recordsPerBeacon.values.sum()

    /** Rows logged per second for [beacon]. Compared with how often the beacon advertises
     *  (10 times a second at the ESP32's default 100 ms interval), it shows how many of its
     *  advertisements this phone actually captured. */
    fun ratePerSecond(beacon: DeviceId): Double =
        if (durationSeconds > 0) (recordsPerBeacon[beacon] ?: 0) / durationSeconds else 0.0
}

data class FingerprintRecorderState(
    val activeRecording: ActiveFingerprintRecording? = null,
    /** Rows written so far by the recording in progress. */
    val recordsPerBeacon: Map<DeviceId, Int> = emptyMap(),
    /** What the last recording that ran to the end captured. */
    val lastRecording: FingerprintRecordingSummary? = null,
    /** Finished recordings' CSV files, newest first. */
    val recordings: List<File> = emptyList(),
    val errorMessage: String? = null
) {
    val isRecording: Boolean get() = activeRecording != null
    val recordCount: Int get() = recordsPerBeacon.values.sum()
}

/**
 * Records every advertisement the scanner hears into a CSV file, labelled with where the
 * phone is, to train a fingerprinting model — the counterpart of the iOS app's
 * `FingerprintRecorder`. Each recording lasts a set time and stops on its own, so every
 * point gets the same amount of data: one file per recording, one row per advertisement
 * (`FingerprintRecord`), plus a JSON file with the recording's window and context
 * (`FingerprintRecordingMetadata`) once it ends.
 *
 * All of its work — the scan, the files, the state — runs one thing at a time on the
 * dispatcher it is given, so the commands below return at once and their effect shows up in
 * [state] a moment later.
 */
class FingerprintRecorder(
    private val scanner: BleScanner,
    private val deviceMotion: DeviceMotionReading,
    private val altimeter: AltitudeReading,
    private val motionActivity: MotionActivityReading,
    /** Uploads a finished recording's rows to the API for model training. Optional, same as
     *  `DeviceListViewModel`'s reporter — null just means "don't upload", the CSV is still
     *  written either way. */
    private val uploader: FingerprintUploading? = null,
    /** The beacons with a position (`AnchorStore.anchors`), copied into each recording. */
    private val anchors: () -> List<Anchor> = { emptyList() },
    private val directory: File,
    private val node: DeviceId,
    private val model: String,
    private val systemVersion: String,
    /** Single-threaded and off the main thread (`Dispatchers.IO.limitedParallelism(1)`): rows
     *  are written as they arrive, which must not block the UI, and one thread at a time is
     *  what lets the recorder keep its state without locks. */
    dispatcher: CoroutineDispatcher,
    /** Injected rather than read from `android.os.SystemClock` directly, so this file needs
     *  no Android import — pass `SystemClock::elapsedRealtime` from the call site. Same clock
     *  as the sightings' `lastSeenAtMillis`. */
    private val monotonicMs: () -> Long,
    private val now: () -> Instant = Instant::now,
    /** Rows per upload request: a long recording is tens of thousands of rows, too many for
     *  one request. */
    private val uploadBatchSize: Int = DEFAULT_UPLOAD_BATCH_SIZE
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _state = MutableStateFlow(FingerprintRecorderState())
    val state: StateFlow<FingerprintRecorderState> = _state.asStateFlow()

    /**
     * Positioned beacons that aren't heard right now, and heard beacons without a position
     * — same purpose as the iOS app's `layoutCheck`. [heardBeacons] accumulates across the
     * time since [start] rather than being a live "currently visible" snapshot (this class
     * has no access to the observer screen's own last-seen map), which is a close enough
     * substitute for warning about an incomplete layout before recording.
     */
    val layoutCheck: AnchorLayoutCheck get() = AnchorLayoutCheck.of(anchors(), heardBeacons)

    /** Written on the recorder's thread, read by [layoutCheck] from any other. */
    @Volatile private var heardBeacons: Set<DeviceId> = emptySet()
    private var scanJob: Job? = null
    private var endJob: Job? = null
    private var writer: BufferedWriter? = null
    private var file: File? = null
    private var lastFlushMs = 0L
    private val pendingUploadRows = ArrayList<FingerprintRecord>()

    /** Lists the recordings already on disk and starts listening to the beacons and the
     *  sensors; nothing is written until [startRecording]. */
    fun start() {
        scope.launch {
            _state.update { it.copy(recordings = existingRecordings()) }
            scanJob?.cancel()
            scanJob = scope.launch {
                scanner.scan()
                    .catch { cause ->
                        // A scan failure here just means rows stop arriving, same tolerance as
                        // the observer screen's own scan collector — but a real cancellation
                        // (stop() tearing this job down) must still propagate, not be eaten.
                        if (cause is CancellationException) throw cause
                    }
                    .collect { sighting -> onSighting(sighting) }
            }
        }
        deviceMotion.start()
        altimeter.start()
        motionActivity.start()
    }

    /** Stops for good: a recording still in progress is discarded, as with [cancelRecording],
     *  and the recorder can't be started again. */
    fun stop() {
        deviceMotion.stop()
        altimeter.stop()
        motionActivity.stop()
        scope.launch {
            discardActiveRecording()
            scanJob?.cancel()
        }.invokeOnCompletion { scope.cancel() }
    }

    /** Records every advertisement for [durationSeconds], then stops on its own. */
    fun startRecording(label: FingerprintLabel, durationSeconds: Double) {
        scope.launch { beginRecording(label, durationSeconds) }
    }

    /** Stops the recording in progress early and deletes its file: a recording that didn't
     *  last its full time would leave that point with less data than the others. */
    fun cancelRecording() {
        scope.launch { discardActiveRecording() }
    }

    /** Deletes a recording's CSV and its metadata. */
    fun deleteRecording(target: File) {
        scope.launch {
            if (!target.delete()) {
                _state.update { it.copy(errorMessage = "Could not delete ${target.name}") }
                return@launch
            }
            metadataFile(target).delete()
            _state.update { it.copy(recordings = it.recordings.filterNot { f -> f == target }) }
        }
    }

    private fun beginRecording(label: FingerprintLabel, durationSeconds: Double) {
        discardActiveRecording()
        val startedAt = now()
        val target = File(directory, fileName(label, startedAt))
        try {
            directory.mkdirs()
            val newWriter = BufferedWriter(FileWriter(target, false))
            newWriter.write(FingerprintRecordFormat.CSV_HEADER)
            newWriter.newLine()
            newWriter.flush()
            writer = newWriter
        } catch (e: Exception) {
            _state.update { it.copy(errorMessage = "Could not create ${target.name}: ${e.message}") }
            return
        }
        file = target
        lastFlushMs = monotonicMs()
        pendingUploadRows.clear()
        val active = ActiveFingerprintRecording(label, startedAt, durationSeconds, monotonicMs(), anchors())
        _state.update { it.copy(activeRecording = active, recordsPerBeacon = emptyMap(), errorMessage = null) }

        endJob = scope.launch {
            delay((durationSeconds * 1000).toLong())
            finishRecording()
        }
    }

    private fun discardActiveRecording() {
        if (_state.value.activeRecording == null) return
        val target = file
        closeWriter()
        target?.delete()
        pendingUploadRows.clear()
        _state.update { it.copy(activeRecording = null, recordsPerBeacon = emptyMap()) }
    }

    private fun finishRecording() {
        val active = _state.value.activeRecording ?: return
        val target = file ?: return
        closeWriter()
        val endedAt = now()
        val recordsPerBeacon = _state.value.recordsPerBeacon
        writeMetadata(active, target, endedAt, monotonicMs(), recordsPerBeacon)

        val summary = FingerprintRecordingSummary(
            file = target,
            label = active.label,
            durationSeconds = (endedAt.toEpochMilli() - active.startedAt.toEpochMilli()) / 1000.0,
            recordsPerBeacon = recordsPerBeacon
        )
        val rowsToUpload = pendingUploadRows.toList()
        pendingUploadRows.clear()

        _state.update {
            it.copy(
                activeRecording = null,
                recordsPerBeacon = emptyMap(),
                lastRecording = summary,
                recordings = listOf(target) + it.recordings
            )
        }
        uploadIfConfigured(rowsToUpload)
    }

    /** The CSV (already safely on disk) is kept no matter what happens here — a failed
     *  upload just means the training data stays local-only until exported by hand. Batches
     *  go one after another, and the first failure stops the rest. */
    private fun uploadIfConfigured(records: List<FingerprintRecord>) {
        val uploader = uploader
        if (uploader == null || records.isEmpty()) return
        scope.launch {
            var sent = 0
            try {
                for (batch in records.chunked(uploadBatchSize)) {
                    uploader.upload(batch)
                    sent += batch.size
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        errorMessage = "Recording saved locally, but the upload to the API failed after " +
                            "$sent of ${records.size} rows: ${e.message}"
                    )
                }
            }
        }
    }

    private fun onSighting(sighting: DeviceSighting) {
        val beaconId = sighting.device.id
        if (beaconId != null) heardBeacons = heardBeacons + beaconId

        val active = _state.value.activeRecording ?: return
        val currentWriter = writer ?: return
        if (beaconId == null) return // no id, nothing to label the `beacon` column with

        val record = FingerprintRecord(
            monotonicMs = sighting.lastSeenAtMillis,
            seenAt = now(),
            node = node,
            model = model,
            systemVersion = systemVersion,
            beacon = beaconId,
            rssi = sighting.rssi,
            orientation = deviceMotion.latest,
            altitude = altimeter.latest,
            motionActivity = motionActivity.latest,
            label = active.label
        )
        try {
            currentWriter.write(record.csvLine)
            currentWriter.newLine()
            flushIfDue(currentWriter)
        } catch (e: Exception) {
            discardActiveRecording()
            _state.update { it.copy(errorMessage = "Recording interrupted and discarded: ${e.message}") }
            return
        }
        pendingUploadRows.add(record)
        _state.update {
            val updated = it.recordsPerBeacon.toMutableMap()
            updated[beaconId] = (updated[beaconId] ?: 0) + 1
            it.copy(recordsPerBeacon = updated)
        }
    }

    /** At most once a second, not once per row: the rows reach the disk in a few larger
     *  writes. A recording cut short loses at most its last second, which costs nothing — a
     *  CSV without its JSON is left out of the dataset anyway. */
    private fun flushIfDue(writer: BufferedWriter) {
        val nowMs = monotonicMs()
        if (nowMs - lastFlushMs >= FLUSH_INTERVAL_MS) {
            writer.flush()
            lastFlushMs = nowMs
        }
    }

    private fun writeMetadata(
        active: ActiveFingerprintRecording,
        csv: File,
        endedAt: Instant,
        endedMonotonicMs: Long,
        recordsPerBeacon: Map<DeviceId, Int>
    ) {
        val metadata = FingerprintRecordingMetadata(
            platform = "Android",
            node = node.value,
            model = model,
            systemVersion = systemVersion,
            session = active.label.session,
            point = active.label.point,
            x = active.label.x,
            y = active.label.y,
            csvFile = csv.name,
            startedTMs = active.startedMonotonicMs,
            endedTMs = endedMonotonicMs,
            startedTimestampMs = active.startedAt.toEpochMilli(),
            endedTimestampMs = endedAt.toEpochMilli(),
            plannedDurationS = active.plannedDurationSeconds,
            recordsPerBeacon = recordsPerBeacon.mapKeys { it.key.value },
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
            beacons = active.beacons.sortedBy { it.id.value }.map(FingerprintRecordingMetadata.BeaconSnapshot::of)
        )
        try {
            metadataFile(csv).writeText(metadata.toJson())
        } catch (e: Exception) {
            _state.update { it.copy(errorMessage = "Recording saved, but without metadata: ${e.message}") }
        }
    }

    private fun closeWriter() {
        endJob?.cancel()
        endJob = null
        try {
            writer?.close()
        } catch (_: Exception) {
            // Already on its way out; nothing more useful to do than drop the reference below.
        }
        writer = null
        file = null
    }

    private fun existingRecordings(): List<File> =
        (directory.listFiles { candidate -> candidate.extension == "csv" } ?: emptyArray())
            .sortedByDescending { it.name }

    companion object {
        /** `DeviceMotionReading`'s requested sensor rate — see `AndroidOrientationReader`. */
        private const val ORIENTATION_RATE_HZ = 50.0

        private const val FLUSH_INTERVAL_MS = 1_000L

        const val DEFAULT_UPLOAD_BATCH_SIZE = 1_000

        private val FILE_NAME_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT).withZone(ZoneId.systemDefault())

        fun metadataFile(csv: File): File = File(csv.parentFile, csv.nameWithoutExtension + ".json")

        /** The JSON is written only when a recording runs to the end: a CSV without it was cut
         *  (the app was killed) and is left out of the dataset, as in the iOS app. */
        fun isComplete(csv: File): Boolean = metadataFile(csv).exists()

        /** What exporting a recording sends: its CSV and, when it finished, its JSON. */
        fun filesOf(csv: File): List<File> = listOf(csv) + listOfNotNull(metadataFile(csv).takeIf { it.exists() })

        /** What "Exportar todas" sends: every recording's files, oldest first. */
        fun filesOf(recordings: List<File>): List<File> = recordings.sortedBy { it.name }.flatMap(::filesOf)

        /** Starts with the date so the files sort chronologically, e.g. `20261022-143005_S1_P3.csv`. */
        private fun fileName(label: FingerprintLabel, startedAt: Instant): String =
            "${FILE_NAME_FORMAT.format(startedAt)}_${label.session}_${label.point}.csv"
    }
}
