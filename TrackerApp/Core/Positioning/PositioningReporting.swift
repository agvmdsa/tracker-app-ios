import Foundation

struct PositioningReport: Encodable, Sendable {
    let observerID: String
    let anchorID: String
    let rssi: Int
    let distanceMeters: Double
    let seenAt: Date
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
}

protocol PositioningReporting: Sendable {
    func send(_ reports: [PositioningReport]) async throws
    func send(_ fix: ObserverFixReport) async throws
}

enum PositioningReportingError: Error, Sendable {
    case invalidResponse
    case serverError(statusCode: Int)
}
