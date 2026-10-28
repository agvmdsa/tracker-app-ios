package br.org.cesar.blueguide.domain.network

import java.time.Instant

/** The counterpart of the iOS app's `PositioningReport` — one observer-to-anchor range. */
data class PositioningReport(
    val observerID: String,
    val anchorID: String,
    val rssi: Int,
    val distanceMeters: Double,
    val seenAt: Instant,
    /** How many advertisements arrived for this anchor since the last report: more samples
     *  means a steadier reading. */
    val sampleCount: Int,
    /** Their standard deviation in dB — a reading jumping around a lot likely has a wall or
     *  interference in the way. */
    val rssiStdDev: Double,
    /** The observer's phone model: different antennas read RSSI differently at the same distance. */
    val deviceModel: String
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
    val recordedAt: Instant,
    /** Anchors that contributed to this fix: more than the 3-anchor minimum is more trustworthy. */
    val anchorCount: Int
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
