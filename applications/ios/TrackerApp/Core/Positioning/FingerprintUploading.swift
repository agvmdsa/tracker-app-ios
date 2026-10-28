import Foundation

/// Uploads a finished recording's rows to the positioning API's `POST /fingerprints` — the
/// labelled training data, as opposed to `PositioningReporting`'s computed-estimate reports.
protocol FingerprintUploading: Sendable {
    func upload(_ records: [FingerprintRecord]) async throws
}
