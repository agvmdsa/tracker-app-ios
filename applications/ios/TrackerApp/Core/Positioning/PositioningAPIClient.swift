import Foundation

final class PositioningAPIClient: PositioningReporting, Sendable {
    private let baseURL: URL
    private let apiKey: String
    private let session: URLSession

    /// Deployed on Render (see `applications/api/README.md`) — reachable from any network, so
    /// this no longer depends on the phone and the Mac running `docker compose` being on the
    /// same Wi-Fi. Fall back to a LAN IP (`http://<mac-ip>:8000`) only if testing against a
    /// local `docker compose up` instead.
    ///
    /// `apiKey` must match the server's `API_KEY` environment variable exactly — it's a single
    /// shared secret (not per-device auth), just enough to keep random internet traffic from
    /// writing to the database. See `applications/api/README.md`'s "Authentication" section.
    ///
    /// The default pulls from `Secrets.swift`, which is gitignored and never committed — see
    /// `Secrets.swift.example` for setup. This file itself stays safe to commit no matter what
    /// `Secrets.apiKey` is set to locally.
    init(
        baseURL: URL = URL(string: "https://tracker-app-ios.onrender.com")!,
        apiKey: String = Secrets.apiKey,
        session: URLSession = .shared
    ) {
        self.baseURL = baseURL
        self.apiKey = apiKey
        self.session = session
    }

    func send(_ reports: [PositioningReport]) async throws {
        try await post(reports, to: "sightings")
    }

    func send(_ fix: ObserverFixReport) async throws {
        try await post(fix, to: "fixes")
    }

    private func post(_ body: some Encodable, to path: String) async throws {
        var request = URLRequest(url: baseURL.appendingPathComponent(path))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(apiKey, forHTTPHeaderField: "X-API-Key")

        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        request.httpBody = try encoder.encode(body)

        let (_, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw PositioningReportingError.invalidResponse
        }
        guard (200..<300).contains(httpResponse.statusCode) else {
            throw PositioningReportingError.serverError(statusCode: httpResponse.statusCode)
        }
    }
}

/// `FingerprintRecorder`'s labelled training rows, in the shape the API's `FingerprintRecordIn`
/// expects — only the fields a phone actually has populated; the ESP32 collector sends the same
/// endpoint a narrower payload (no orientation/altitude/motion, which are phone-only sensors).
private struct FingerprintUploadRow: Encodable {
    let nodeID: String
    let platform: String
    let model: String
    let systemVersion: String
    let beaconID: String
    let rssi: Int
    let roll: Double?
    let pitch: Double?
    let yaw: Double?
    let relativeAltitudeM: Double?
    let pressureKPa: Double?
    let motionState: String?
    let motionConfidence: Int?
    let session: String
    let point: String
    let x: Double
    let y: Double
    let seenAt: Date

    init(_ record: FingerprintRecord) {
        nodeID = record.node.value
        platform = "iOS"
        model = record.model
        systemVersion = record.systemVersion
        beaconID = record.beacon.value
        rssi = record.rssi
        roll = record.orientation?.roll
        pitch = record.orientation?.pitch
        yaw = record.orientation?.yaw
        relativeAltitudeM = record.altitude?.relativeAltitudeM
        pressureKPa = record.altitude?.pressureKPa
        motionState = record.motionActivity?.state.rawValue
        motionConfidence = record.motionActivity?.confidence
        session = record.label.session
        point = record.label.point
        x = record.label.x
        y = record.label.y
        seenAt = record.seenAt
    }
}

extension PositioningAPIClient: FingerprintUploading {
    func upload(_ records: [FingerprintRecord]) async throws {
        try await post(records.map(FingerprintUploadRow.init), to: "fingerprints")
    }
}
