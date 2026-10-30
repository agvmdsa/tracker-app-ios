package br.org.cesar.blueguide.core.dataset

import br.org.cesar.blueguide.core.model.Anchor
import br.org.cesar.blueguide.core.model.DeviceId
import br.org.cesar.blueguide.core.model.DeviceMotionSample
import br.org.cesar.blueguide.core.model.Point2D
import br.org.cesar.blueguide.core.positioning.PathLossPositioningEngine
import br.org.cesar.blueguide.core.positioning.PositioningEngine
import br.org.cesar.blueguide.core.positioning.PositioningSetup
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

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
     *  to convert to — see [DeviceMotionSample]. */
    @SerialName("orientation_convention") val orientationConvention: String,
    @SerialName("orientation_rate_hz") val orientationRateHz: Double,
    /** What `relative_altitude_m` is relative to, and that it isn't a floor number. */
    @SerialName("altitude_convention") val altitudeConvention: String,
    /** What the `motion_state`/`motion_confidence` values mean, and their scale. */
    @SerialName("motion_activity_convention") val motionActivityConvention: String,
    /** Where each beacon was when the recording started, in the same frame as `x`/`y`. A
     *  copy, not a reference: moving a beacon later doesn't rewrite recordings made before. */
    val beacons: List<BeaconSnapshot>,
    /** How the app would have placed the phone during the recording, so it can be replayed
     *  exactly. Since version 2; null in older files and in the iOS app's, which doesn't write
     *  it yet. */
    val positioning: PositioningSnapshot? = null
) {
    /** The beacons as the anchors the positioning engine ranges. */
    val anchors: List<Anchor>
        get() = beacons.map { Anchor(DeviceId(it.id), it.label, Point2D(it.x, it.y), it.heightM) }

    /** What the app would have positioned with during the recording; null before version 2. */
    val positioningSetup: PositioningSetup?
        get() = positioning?.let { PositioningSetup(anchors, it.centerRssi, it.touchRssi, it.maxSpeedKmh) }

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

    /** The calibration at the start of the recording ("RSSI no centro", "RSSI encostado", the
     *  speed limit) and what the engine made of it. */
    @Serializable
    data class PositioningSnapshot(
        @SerialName("center_rssi") val centerRssi: Double,
        @SerialName("touch_rssi") val touchRssi: Double,
        /** Null when the speed limit was off. */
        @SerialName("max_speed_kmh") val maxSpeedKmh: Double?,
        /** The path loss model those give for [beacons]: the RSSI at 1 m and the exponent, for
         *  tooling that doesn't recompute them. */
        @SerialName("reference_rssi") val referenceRssi: Double,
        @SerialName("path_loss_exponent") val pathLossExponent: Double,
        /** False when the touching RSSI was ignored and the center alone calibrated. */
        @SerialName("uses_touch_rssi") val usesTouchRssi: Boolean,
        /** The app engine's RSSI window ([PathLossPositioningEngine]) and refresh interval, in case
         *  they change later. */
        @SerialName("rssi_window_ms") val rssiWindowMs: Long,
        @SerialName("refresh_interval_ms") val refreshIntervalMs: Long
    ) {
        companion object {
            fun of(setup: PositioningSetup) = PositioningSnapshot(
                centerRssi = setup.centerRssi,
                touchRssi = setup.touchRssi,
                maxSpeedKmh = setup.maxSpeedKmh,
                referenceRssi = setup.calibration.referenceRssi,
                pathLossExponent = setup.calibration.exponent,
                usesTouchRssi = setup.calibration.usesTouchRssi,
                rssiWindowMs = PathLossPositioningEngine.RSSI_WINDOW_MS,
                refreshIntervalMs = PositioningEngine.REFRESH_INTERVAL_MS
            )
        }
    }

    fun toJson(): String = json.encodeToString(this)

    companion object {
        /** 2 added [positioning]. */
        const val CURRENT_SCHEMA_VERSION = 2

        /** Reads a recording's JSON, from either app. Keys this version doesn't know are skipped,
         *  and a file without `schema_version` is version 1: Android wrote none until it was
         *  fixed. Anything that isn't a recording's metadata fails with [IllegalArgumentException]. */
        fun fromJson(text: String): FingerprintRecordingMetadata = try {
            val fields = json.parseToJsonElement(text).jsonObject
            val versioned = if ("schema_version" in fields) fields else JsonObject(fields + ("schema_version" to JsonPrimitive(1)))
            json.decodeFromJsonElement(serializer(), versioned)
        } catch (e: SerializationException) {
            throw IllegalArgumentException("not a recording's metadata: ${e.message}", e)
        }

        /** `encodeDefaults`: [schemaVersion] has a default, and without this it would be left
         *  out of the file, unlike the iOS app's, which always writes `schema_version`. */
        private val json = Json {
            prettyPrint = true
            encodeDefaults = true
            ignoreUnknownKeys = true
        }
    }
}
