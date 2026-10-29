package br.org.cesar.blueguide.core.network.http

import br.org.cesar.blueguide.core.dataset.FingerprintRecord
import br.org.cesar.blueguide.core.network.FingerprintUploading
import br.org.cesar.blueguide.core.network.ObserverFixReport
import br.org.cesar.blueguide.core.network.PositioningReport
import br.org.cesar.blueguide.core.network.PositioningReporting
import br.org.cesar.blueguide.core.network.PositioningReportingError
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Talks to the same positioning API the iOS app and the ESP32 firmware use
 * (counterpart of `PositioningAPIClient.swift`): `POST /sightings`, `POST /fixes`,
 * `POST /fingerprints`, all behind the shared `X-API-Key` secret. Deployed on Render; see
 * `applications/api/README.md`'s "Authentication" section.
 *
 * The default key pulls from [Secrets], which is gitignored and never committed — see
 * `Secrets.kt.example` for setup. This file itself stays safe to commit no matter what
 * `Secrets.API_KEY` is set to locally.
 */
class PositioningApiClient(
    private val baseUrl: String = "https://tracker-app-ios.onrender.com",
    private val apiKey: String = Secrets.API_KEY,
    private val client: HttpClient = defaultClient()
) : PositioningReporting, FingerprintUploading {

    override suspend fun send(reports: List<PositioningReport>) {
        post("sightings", reports.map(SightingDto::of))
    }

    override suspend fun send(fix: ObserverFixReport) {
        post("fixes", FixDto.of(fix))
    }

    override suspend fun upload(records: List<FingerprintRecord>) {
        post("fingerprints", records.map(FingerprintUploadDto::of))
    }

    private suspend inline fun <reified T> post(path: String, body: T) {
        val response = client.post("$baseUrl/$path") {
            header("X-API-Key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        if (!response.status.isSuccess()) {
            throw PositioningReportingError.ServerError(response.status.value)
        }
    }

    companion object {
        /** The engine is a parameter so the contract tests can run this exact configuration
         *  against a mock engine: what goes over the wire is decided here, not in the test. */
        internal fun defaultClient(engine: HttpClientEngine = OkHttp.create()) = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
    }
}

@Serializable
private data class SightingDto(
    val observerID: String,
    val anchorID: String,
    val rssi: Int,
    val distanceMeters: Double,
    val seenAt: String,
    val sampleCount: Int,
    val rssiStdDev: Double,
    val deviceModel: String
) {
    companion object {
        fun of(report: PositioningReport) = SightingDto(
            observerID = report.observerID,
            anchorID = report.anchorID,
            rssi = report.rssi,
            distanceMeters = report.distanceMeters,
            seenAt = report.seenAt.toString(),
            sampleCount = report.sampleCount,
            rssiStdDev = report.rssiStdDev,
            deviceModel = report.deviceModel
        )
    }
}

@Serializable
private data class FixDto(
    val observerID: String,
    val x: Double,
    val y: Double,
    val uncertaintyMeters: Double,
    val recordedAt: String,
    val anchorCount: Int
) {
    companion object {
        fun of(fix: ObserverFixReport) = FixDto(
            observerID = fix.observerID,
            x = fix.x,
            y = fix.y,
            uncertaintyMeters = fix.uncertaintyMeters,
            recordedAt = fix.recordedAt.toString(),
            anchorCount = fix.anchorCount
        )
    }
}

/**
 * `FingerprintRecord`'s labelled training rows, in the shape the API's `FingerprintRecordIn`
 * expects — same field set as the iOS client's `FingerprintUploadRow`.
 */
@Serializable
private data class FingerprintUploadDto(
    val nodeID: String,
    val platform: String,
    val model: String,
    val systemVersion: String,
    val beaconID: String,
    val rssi: Int,
    val roll: Double? = null,
    val pitch: Double? = null,
    val yaw: Double? = null,
    val relativeAltitudeM: Double? = null,
    val pressureKPa: Double? = null,
    val motionState: String? = null,
    val motionConfidence: Int? = null,
    val session: String,
    val point: String,
    val x: Double,
    val y: Double,
    val seenAt: String
) {
    companion object {
        fun of(record: FingerprintRecord) = FingerprintUploadDto(
            nodeID = record.node.value,
            platform = "Android",
            model = record.model,
            systemVersion = record.systemVersion,
            beaconID = record.beacon.value,
            rssi = record.rssi,
            roll = record.orientation?.roll,
            pitch = record.orientation?.pitch,
            yaw = record.orientation?.yaw,
            relativeAltitudeM = record.altitude?.relativeAltitudeM,
            pressureKPa = record.altitude?.pressureKPa,
            motionState = record.motionActivity?.state?.wireValue,
            motionConfidence = record.motionActivity?.confidence,
            session = record.label.session,
            point = record.label.point,
            x = record.label.x,
            y = record.label.y,
            seenAt = record.seenAt.toString()
        )
    }
}
