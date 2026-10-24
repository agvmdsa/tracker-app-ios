import Foundation

struct PositioningReport: Encodable, Sendable {
    let observerID: String
    let anchorID: String
    let rssi: Int
    let distanceMeters: Double
    let seenAt: Date
    /// How many BLE advertisements this reading was built from, in the refresh cycle that
    /// produced it — more samples means a steadier reading.
    let sampleCount: Int
    /// Standard deviation across those same samples, in dB — a cheap confidence signal (and the
    /// statistical basis an RSSI fingerprint would need): a reading that's jumping around a lot
    /// likely has a wall/obstruction or interference between the two devices.
    let rssiStdDev: Double
    /// Hardware identifier of the observer's device (e.g. "iPhone14,2") — different models' BLE
    /// antennas read RSSI differently for the same physical distance, so mixing device models
    /// without knowing this introduces a systematic error `PathLossModel` can't see or correct.
    let deviceModel: String
}

/// One computed observer position, for server-side aggregation this app has no way to do
/// on-device — e.g. "how many distinct observerIDs were seen in this area recently" needs
/// many phones' fixes collected in one place over time, not just this phone's own readings.
struct ObserverFixReport: Encodable, Sendable {
    let observerID: String
    let x: Double
    let y: Double
    let uncertaintyMeters: Double
    let recordedAt: Date
    /// Anchors that contributed to this fix — a fix built from more anchors is more trustworthy
    /// than one from exactly the 3-anchor minimum, independent of what `uncertaintyMeters` says.
    let anchorCount: Int
}

protocol PositioningReporting: Sendable {
    func send(_ reports: [PositioningReport]) async throws
    func send(_ fix: ObserverFixReport) async throws
}

enum PositioningReportingError: Error, Sendable {
    case invalidResponse
    case serverError(statusCode: Int)
}
