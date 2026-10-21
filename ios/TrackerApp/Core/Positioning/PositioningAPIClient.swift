import Foundation

final class PositioningAPIClient: PositioningReporting, Sendable {
    private let baseURL: URL
    private let session: URLSession

    init(
        baseURL: URL = URL(string: "https://TODO-replace-with-real-heatmap-api.example.com")!,
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
