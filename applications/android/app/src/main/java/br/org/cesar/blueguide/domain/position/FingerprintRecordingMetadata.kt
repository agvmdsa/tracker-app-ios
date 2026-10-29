package br.org.cesar.blueguide.domain.position

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Written next to each finished recording's CSV, as `<same name>.json` — the counterpart of
 * the iOS app's `FingerprintRecordingMetadata`, same keys (so either platform's tooling can
 * read either file) with `platform` set to `"Android"`.
 *
 * A beacon that was never heard leaves no CSV row at all, so only this file's window tells
 * "not heard for the whole recording" apart from "not part of it". Written only when a
 * recording ends normally, so a CSV without a matching metadata file was cut short (the app
 * was killed) and should be discarded.
 */
@Serializable
data class FingerprintRecordingMetadata(
    @SerialName("schema_version") val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val platform: String,
    val node: String,
    val model: String,
    @SerialName("system_version") val systemVersion: String,
    val session: String,
    val point: String,
    val x: Double,
    val y: Double,
    @SerialName("csv_file") val csvFile: String,
    /** Same clock as the rows' `t_ms`. */
    @SerialName("started_t_ms") val startedTMs: Long,
    @SerialName("ended_t_ms") val endedTMs: Long,
    /** Same clock as the rows' `timestamp_ms`. */
    @SerialName("started_timestamp_ms") val startedTimestampMs: Long,
    @SerialName("ended_timestamp_ms") val endedTimestampMs: Long,
    @SerialName("planned_duration_s") val plannedDurationS: Double,
    @SerialName("records_per_beacon") val recordsPerBeacon: Map<String, Int>,
    /** How advertisements were received, since it changes how many arrive. */
    @SerialName("scan_api") val scanApi: String,
    /** Units, axes and reference frame of `roll`/`pitch`/`yaw`, which other platforms have
     *  to convert to — see `DeviceMotionSample`. */
    @SerialName("orientation_convention") val orientationConvention: String,
    @SerialName("orientation_rate_hz") val orientationRateHz: Double,
    /** What `relative_altitude_m` is relative to, and that it isn't a floor number. */
    @SerialName("altitude_convention") val altitudeConvention: String,
    /** What the `motion_state`/`motion_confidence` values mean, and their scale. */
    @SerialName("motion_activity_convention") val motionActivityConvention: String,
    /** Where each beacon was when the recording started, in the same frame as `x`/`y`. A
     *  copy, not a reference: moving a beacon later doesn't rewrite recordings made before. */
    val beacons: List<BeaconSnapshot>
) {
    @Serializable
    data class BeaconSnapshot(
        val id: String,
        val label: String,
        val x: Double,
        val y: Double,
        /** Metres above the floor; `null` when it wasn't entered. */
        @SerialName("height_m") val heightM: Double?
    ) {
        companion object {
            fun of(anchor: Anchor) = BeaconSnapshot(anchor.id.value, anchor.label, anchor.position.x, anchor.position.y, anchor.height)
        }
    }

    fun toJson(): String = json.encodeToString(this)

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1

        /** `encodeDefaults`: [schemaVersion] has a default, and without this it would be left
         *  out of the file, unlike the iOS app's, which always writes `schema_version`. */
        private val json = Json {
            prettyPrint = true
            encodeDefaults = true
        }
    }
}
