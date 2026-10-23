import Foundation

final class PositioningAPIClient: PositioningReporting, Sendable {
    private let baseURL: URL
    private let session: URLSession

    /// Deployed on Railway (see the `api` repo's README) — reachable from any network, so this
    /// no longer depends on the phone and the Mac running `docker compose` being on the same
    /// Wi-Fi. Fall back to a LAN IP (`http://<mac-ip>:8000`) only if testing against a local
    /// `docker compose up` instead.
    init(
        baseURL: URL = URL(string: "https://api-production-5c3f8.up.railway.app")!,
        session: URLSession = .shared
    ) {
        self.baseURL = baseURL
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
