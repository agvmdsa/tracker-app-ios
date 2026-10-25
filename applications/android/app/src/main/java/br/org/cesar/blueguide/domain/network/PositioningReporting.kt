package br.org.cesar.blueguide.domain.network

import java.time.Instant

/** The counterpart of the iOS app's `PositioningReport` — one observer-to-anchor range. */
data class PositioningReport(
    val observerID: String,
    val anchorID: String,
    val rssi: Int,
    val distanceMeters: Double,
    val seenAt: Instant
)

/**
 * One computed observer position, for server-side aggregation this app has no way to do
 * on-device (same reasoning as the iOS app's `ObserverFixReport`) — e.g. "how many distinct
 * observers were seen in this area recently" needs many phones' fixes collected in one
 * place over time, not just this phone's own readings.
 */
data class ObserverFixReport(
    val observerID: String,
    val x: Double,
    val y: Double,
    val uncertaintyMeters: Double,
    val recordedAt: Instant
)

/** Sends readings to the positioning API. See `PositioningApiClient` for the real implementation. */
interface PositioningReporting {
    suspend fun send(reports: List<PositioningReport>)
    suspend fun send(fix: ObserverFixReport)
}

sealed class PositioningReportingError(message: String) : Exception(message) {
    data object InvalidResponse : PositioningReportingError("invalid response")
    data class ServerError(val statusCode: Int) : PositioningReportingError("server returned $statusCode")
}
