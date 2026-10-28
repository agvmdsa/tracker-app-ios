package br.org.cesar.blueguide.data.network

import br.org.cesar.blueguide.domain.network.ObserverFixReport
import br.org.cesar.blueguide.domain.network.PositioningReport
import br.org.cesar.blueguide.domain.network.PositioningReportingError
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import java.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PositioningApiClientTest {

    /** Captures the path, the `X-API-Key` header and the raw JSON body of the one request
     *  each test makes, so assertions don't need to touch Ktor's request-building internals. */
    private class RecordedRequest(val path: String, val apiKey: String?, val body: String)

    private fun clientRecording(
        status: HttpStatusCode = HttpStatusCode.Created,
        onRequest: (RecordedRequest) -> Unit
    ): HttpClient {
        val engine = MockEngine { request: HttpRequestData ->
            // ContentNegotiation serializes the body to a TextContent (ByteArrayContent), not
            // a streamed ChannelProvider, so reading its bytes directly is both correct and
            // simpler than going through a channel.
            val bodyText = (request.body as? OutgoingContent.ByteArrayContent)
                ?.bytes()?.decodeToString()
                ?: ""
            onRequest(RecordedRequest(request.url.encodedPath, request.headers["X-API-Key"], bodyText))
            respond(content = "", status = status, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
    }

    @Test
    fun `sends sightings to POST slash sightings with the api key header and the right fields`() = runTest {
        var recorded: RecordedRequest? = null
        val client = clientRecording { recorded = it }
        val api = PositioningApiClient(baseUrl = "https://example.test", apiKey = "secret-key", client = client)

        api.send(
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

        val result = requireNotNull(recorded)
        assertEquals("/sightings", result.path)
        assertEquals("secret-key", result.apiKey)
        assertTrue(result.body.contains("\"observerID\":\"AABBCCDD\""))
        assertTrue(result.body.contains("\"anchorID\":\"11223344\""))
        assertTrue(result.body.contains("\"rssi\":-65"))
    }

    @Test
    fun `sends a fix to POST slash fixes`() = runTest {
        var recorded: RecordedRequest? = null
        val client = clientRecording { recorded = it }
        val api = PositioningApiClient(baseUrl = "https://example.test", apiKey = "secret-key", client = client)

        api.send(
            ObserverFixReport(
                observerID = "AABBCCDD",
                x = 1.0,
                y = 2.0,
                uncertaintyMeters = 0.3,
                recordedAt = Instant.parse("2026-01-01T00:00:00Z"),
                anchorCount = 3
            )
        )

        assertEquals("/fixes", requireNotNull(recorded).path)
    }

    @Test(expected = PositioningReportingError.ServerError::class)
    fun `a non-2xx response is reported as a ServerError`() = runTest {
        val client = clientRecording(status = HttpStatusCode.Unauthorized) {}
        val api = PositioningApiClient(baseUrl = "https://example.test", apiKey = "wrong-key", client = client)

        api.send(emptyList())
    }
}
