package br.org.cesar.blueguide.feature.fingerprint.domain

import br.org.cesar.blueguide.core.dataset.FingerprintLabel
import br.org.cesar.blueguide.core.dataset.FingerprintRecord
import br.org.cesar.blueguide.core.model.DeviceId
import br.org.cesar.blueguide.core.positioning.PositioningSetup
import java.io.File
import java.time.Instant

/** One recording in storage: its CSV and, once it ran to the end, its metadata JSON. */
data class StoredRecording(val csv: File, val metadata: File?) {
    /** The file name without the extension, e.g. `20261022-143005_S1_P3`. */
    val name: String get() = csv.nameWithoutExtension

    /** The metadata is written only when a recording runs to the end: one without it was cut
     *  (the app was killed) and is left out of the dataset, as in the iOS app. */
    val isComplete: Boolean get() = metadata != null

    /** What exporting it sends: the CSV and, when it finished, the JSON — a cut one too, so
     *  nothing is lost by accident. */
    val files: List<File> get() = listOfNotNull(csv, metadata)

    companion object {
        /** What "Exportar todas" sends: every recording's files, oldest first. */
        fun filesOf(recordings: List<StoredRecording>): List<File> = recordings.sortedBy { it.name }.flatMap { it.files }
    }
}

/** What a finished recording's metadata says from the recorder's side: what was recorded, where,
 *  when, and with which positioning setup. */
data class FinishedRecording(
    val label: FingerprintLabel,
    val node: DeviceId,
    val model: String,
    val systemVersion: String,
    val startedAt: Instant,
    val endedAt: Instant,
    /** On the clock of the rows' `t_ms`. */
    val startedMonotonicMs: Long,
    val endedMonotonicMs: Long,
    val plannedDurationSeconds: Double,
    val recordsPerBeacon: Map<DeviceId, Int>,
    val setup: PositioningSetup
)

/**
 * Where the recordings are kept. [FingerprintRecorder] decides what goes into them and when;
 * this knows the files and their format. Every call does I/O and can throw [java.io.IOException]:
 * the recorder makes them from its own thread, never the main one.
 */
interface FingerprintRecordingStore {
    /** The recordings kept, finished and cut, newest first. */
    fun list(): List<StoredRecording>

    /** A new, empty recording, named after [label] and [startedAt]. */
    fun create(label: FingerprintLabel, startedAt: Instant): RecordingWriter

    /** Deletes the recording's files; false when the CSV could not be deleted. */
    fun delete(recording: StoredRecording): Boolean
}

/** A recording being written. Rows are appended as they arrive; [flush] puts them on disk. */
interface RecordingWriter {
    /** The recording as it stands: cut, until [finish] writes its metadata. */
    val recording: StoredRecording

    fun append(record: FingerprintRecord)

    fun flush()

    /** Closes the CSV and writes the metadata next to it, which makes the recording complete. */
    fun finish(info: FinishedRecording): StoredRecording

    /** Closes the CSV and deletes it: a recording that didn't last its full time is not kept. */
    fun discard()
}
