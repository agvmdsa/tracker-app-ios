import Foundation
import simd

@MainActor
protocol UWBManaging: AnyObject {
    var distance: Float? { get }
    var direction: simd_float3? { get }
    var horizontalAngle: Float? { get }
    var azimuth: Float? { get }
    var elevation: Float? { get }
    var isSignalLost: Bool { get }
    var supportsDirectionMeasurement: Bool { get }
    var isCameraAssisted: Bool { get }
    var isSupported: Bool { get }
    var errorMessage: String? { get }

    var onPeerEnded: (() -> Void)? { get set }
    var onLog: ((String) -> Void)? { get set }

    /// Prepares a new session and returns the local token to send to the peer.
    func prepareSession() throws -> Data
    /// Starts ranging against the peer described by `peerToken`.
    func startSession(withPeerToken peerToken: Data) throws
    func pause()
    func resume()
    func stop()
}

extension UWBManager: UWBManaging {}

enum UWBError: LocalizedError {
    case unsupported
    case notPrepared
    case invalidPeerToken

    var errorDescription: String? {
        switch self {
        case .unsupported: "This iPhone does not support Ultra Wideband ranging."
        case .notPrepared: "Ranging session was not prepared."
        case .invalidPeerToken: "Received an invalid token from the peer."
        }
    }
}
