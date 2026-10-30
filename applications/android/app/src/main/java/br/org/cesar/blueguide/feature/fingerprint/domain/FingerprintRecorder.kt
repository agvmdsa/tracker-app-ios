package br.org.cesar.blueguide.feature.fingerprint.domain

import br.org.cesar.blueguide.core.ble.BleScanner
import br.org.cesar.blueguide.core.data.AnchorLayoutCheck
import br.org.cesar.blueguide.core.data.AnchorStore
import br.org.cesar.blueguide.core.dataset.FingerprintLabel
import br.org.cesar.blueguide.core.dataset.FingerprintRecord
import br.org.cesar.blueguide.core.model.DeviceId
import br.org.cesar.blueguide.core.model.DeviceSighting
import br.org.cesar.blueguide.core.positioning.PositioningSetup
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
import java.time.Instant

/** A recording in progress; null when not recording. Counterpart of the iOS app's
 *  `FingerprintRecorder.ActiveRecording`. */
data class ActiveFingerprintRecording(
    val label: FingerprintLabel,
    val startedAt: Instant,
    val plannedDurationSeconds: Double,
    /** `startedAt` on the monotonic clock injected into the recorder. */
    val startedMonotonicMs: Long,
    /** The beacons' positions and the calibration when the recording started. */
    val setup: PositioningSetup
)

/** What a finished recording captured, like the ESP32 collector's `S` lines. */
data class FingerprintRecordingSummary(
    val recording: StoredRecording,
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
    /** The recordings kept, finished and cut, newest first. */
    val recordings: List<StoredRecording> = emptyList(),
    val errorMessage: String? = null
) {
    val isRecording: Boolean get() = activeRecording != null
    val recordCount: Int get() = recordsPerBeacon.values.sum()
}

/**
 * Records every advertisement the scanner hears, labelled with where the phone is, to train a
 * fingerprinting model — the counterpart of the iOS app's `FingerprintRecorder`. Each recording
 * lasts a set time and stops on its own, so every point gets the same amount of data: one row
 * per advertisement ([FingerprintRecord]), and the recording's window and context once it ends.
 * Where and how they are kept is the [store]'s business.
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
    /** Sends each finished recording to the API for model training, in the background, from its
     *  file. Null means "don't upload": the recording is kept either way. */
    private val uploads: FingerprintUploadScheduler? = null,
    private val store: FingerprintRecordingStore,
    /** The beacons with a position (`AnchorStore.anchors`) and the calibration on the
     *  Positioning screen, copied into each recording so it can be replayed as the app would
     *  have positioned it. */
    private val positioningSetup: () -> PositioningSetup = { PositioningSetup() },
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
    private val now: () -> Instant = Instant::now
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
    val layoutCheck: AnchorLayoutCheck get() = AnchorLayoutCheck.of(positioningSetup().anchors, heardBeacons)

    /** Written on the recorder's thread, read by [layoutCheck] from any other. */
    @Volatile private var heardBeacons: Set<DeviceId> = emptySet()
    private var scanJob: Job? = null
    private var endJob: Job? = null
    private var writer: RecordingWriter? = null
    private var lastFlushMs = 0L

    /** Lists the recordings already on disk and starts listening to the beacons and the
     *  sensors; nothing is written until [startRecording]. */
    fun start() {
        scope.launch {
            listRecordings()
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

    /** Deletes a recording's CSV and its metadata, and stops its upload. */
    fun deleteRecording(target: StoredRecording) {
        scope.launch {
            uploads?.cancel(target)
            val deleted = try {
                store.delete(target)
            } catch (e: Exception) {
                false
            }
            if (!deleted) {
                _state.update { it.copy(errorMessage = "Could not delete ${target.name}") }
                return@launch
            }
            _state.update { it.copy(recordings = it.recordings.filterNot { recording -> recording == target }) }
        }
    }

    private fun listRecordings() {
        try {
            val recordings = store.list()
            _state.update { it.copy(recordings = recordings) }
        } catch (e: Exception) {
            _state.update { it.copy(errorMessage = "Could not list the recordings: ${e.message}") }
        }
    }

    private fun beginRecording(label: FingerprintLabel, durationSeconds: Double) {
        discardActiveRecording()
        val startedAt = now()
        writer = try {
            store.create(label, startedAt)
        } catch (e: Exception) {
            _state.update { it.copy(errorMessage = "Could not create the recording: ${e.message}") }
            return
        }
        lastFlushMs = monotonicMs()
        val active = ActiveFingerprintRecording(label, startedAt, durationSeconds, monotonicMs(), positioningSetup())
        _state.update { it.copy(activeRecording = active, recordsPerBeacon = emptyMap(), errorMessage = null) }

        endJob = scope.launch {
            delay((durationSeconds * 1000).toLong())
            finishRecording()
        }
    }

    private fun discardActiveRecording() {
        if (_state.value.activeRecording == null) return
        endJob?.cancel()
        endJob = null
        writer?.discard()
        writer = null
        _state.update { it.copy(activeRecording = null, recordsPerBeacon = emptyMap()) }
    }

    private fun finishRecording() {
        val active = _state.value.activeRecording ?: return
        val currentWriter = writer ?: return
        endJob = null
        writer = null
        val endedAt = now()
        val recordsPerBeacon = _state.value.recordsPerBeacon
        val info = FinishedRecording(
            label = active.label,
            node = node,
            model = model,
            systemVersion = systemVersion,
            startedAt = active.startedAt,
            endedAt = endedAt,
            startedMonotonicMs = active.startedMonotonicMs,
            endedMonotonicMs = monotonicMs(),
            plannedDurationSeconds = active.plannedDurationSeconds,
            recordsPerBeacon = recordsPerBeacon,
            setup = active.setup
        )
        val recording = try {
            currentWriter.finish(info)
        } catch (e: Exception) {
            _state.update { it.copy(errorMessage = "Recording saved, but without metadata: ${e.message}") }
            currentWriter.recording
        }

        val summary = FingerprintRecordingSummary(
            recording = recording,
            label = active.label,
            durationSeconds = (endedAt.toEpochMilli() - active.startedAt.toEpochMilli()) / 1000.0,
            recordsPerBeacon = recordsPerBeacon
        )
        _state.update {
            it.copy(
                activeRecording = null,
                recordsPerBeacon = emptyMap(),
                lastRecording = summary,
                recordings = listOf(recording) + it.recordings
            )
        }
        uploads?.schedule(recording)
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
            currentWriter.append(record)
            flushIfDue(currentWriter)
        } catch (e: Exception) {
            discardActiveRecording()
            _state.update { it.copy(errorMessage = "Recording interrupted and discarded: ${e.message}") }
            return
        }
        _state.update {
            val updated = it.recordsPerBeacon.toMutableMap()
            updated[beaconId] = (updated[beaconId] ?: 0) + 1
            it.copy(recordsPerBeacon = updated)
        }
    }

    /** At most once a second, not once per row: the rows reach the disk in a few larger
     *  writes. A recording cut short loses at most its last second, which costs nothing — a
     *  CSV without its JSON is left out of the dataset anyway. */
    private fun flushIfDue(writer: RecordingWriter) {
        val nowMs = monotonicMs()
        if (nowMs - lastFlushMs >= FLUSH_INTERVAL_MS) {
            writer.flush()
            lastFlushMs = nowMs
        }
    }

    companion object {
        private const val FLUSH_INTERVAL_MS = 1_000L
    }
}
