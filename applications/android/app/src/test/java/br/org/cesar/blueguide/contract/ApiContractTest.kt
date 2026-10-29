package br.org.cesar.blueguide.contract

import br.org.cesar.blueguide.contract.ContractSamples.QUOTED_MODEL
import br.org.cesar.blueguide.contract.ContractSamples.fingerprintRecord
import br.org.cesar.blueguide.data.network.PositioningApiClient
import br.org.cesar.blueguide.domain.network.ObserverFixReport
import br.org.cesar.blueguide.domain.network.PositioningReport
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import java.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the app sends to the positioning API, field by field — the API's `SightingIn`, `FixIn`
 * and `FingerprintRecordIn` (`applications/api/app/schemas.py`) accept exactly these. Runs the
 * production HTTP client configuration ([PositioningApiClient.defaultClient]) on a mock engine,
 * so the JSON settings under test are the shipped ones, not a copy in the test. Optional
 * fingerprint fields that are null are left out, which the API reads as absent.
 */
class ApiContractTest {

    private class Recorded(val path: String, val body: String)

    private fun api(onRequest: (Recorded) -> Unit): PositioningApiClient {
        val engine = MockEngine { request ->
            val body = (request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString() ?: ""
            onRequest(Recorded(request.url.encodedPath, body))
            respond(content = "", status = HttpStatusCode.Created, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return PositioningApiClient(
            baseUrl = "https://example.test",
            apiKey = "key",
            client = PositioningApiClient.defaultClient(engine)
        )
    }

    private fun assertSameJson(fixture: String, actual: Recorded?) {
        assertEquals(
            Json.parseToJsonElement(Fixtures.text(fixture)),
            Json.parseToJsonElement(requireNotNull(actual) { "no request was made" }.body)
        )
    }

    @Test
    fun `sightings`() = runTest {
        var recorded: Recorded? = null
        api { recorded = it }.send(
            listOf(
                PositioningReport(
                    observerID = "AABBCCDD",
                    anchorID = "11223344",
                    rssi = -65,
                    distanceMeters = 1.5,
                    seenAt = Instant.parse("2026-01-01T00:00:00Z"),
                    sampleCount = 5,
                    rssiStdDev = 1.2,
                    deviceModel = "Pixel 6"
                )
            )
        )

        assertEquals("/sightings", recorded?.path)
        assertSameJson("api_sightings.json", recorded)
    }

    @Test
    fun `a fix`() = runTest {
        var recorded: Recorded? = null
        api { recorded = it }.send(
            ObserverFixReport(
                observerID = "AABBCCDD",
                x = 1.0,
                y = 2.0,
                uncertaintyMeters = 0.3,
                recordedAt = Instant.parse("2026-01-01T00:00:00Z"),
                anchorCount = 3
            )
        )

        assertEquals("/fixes", recorded?.path)
        assertSameJson("api_fix.json", recorded)
    }

    @Test
    fun `fingerprint rows, with and without sensors`() = runTest {
        var recorded: Recorded? = null
        api { recorded = it }.upload(
            listOf(
                fingerprintRecord(model = QUOTED_MODEL, withSensors = true),
                fingerprintRecord(model = "Pixel 8", withSensors = false)
            )
        )

        assertEquals("/fingerprints", recorded?.path)
        assertSameJson("api_fingerprints.json", recorded)
    }
}
