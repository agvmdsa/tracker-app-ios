import Foundation
import NearbyInteraction
import Observation
import simd
import os

/// Wraps a single `NISession` to continuously measure distance and direction to one peer,
/// using the discovery token exchanged over MultipeerConnectivity (P2P Contract 1).
@Observable
@MainActor
final class UWBManager: NSObject {
    private static let logger = Logger(subsystem: "com.airtagclone.TrackerApp", category: "UWBManager")

    nonisolated override init() {
        super.init()
    }

    private(set) var distance: Float?
    private(set) var direction: simd_float3?
    /// Fallback for devices that only support camera-assisted direction (e.g. iPhone 14 Pro and
    /// later): `NINearbyObject.direction` stays nil on these models even with
    /// `isCameraAssistanceEnabled`, and the angle instead arrives via `horizontalAngle` (radians).
    private(set) var horizontalAngle: Float?
    /// True when the UWB signal is temporarily blocked/lost but a distance was previously known
    /// (Edge Case: UWB Signal Loss → UI shows "Searching for signal...").
    private(set) var isSignalLost: Bool = false
    /// Not every U1-equipped iPhone can measure direction (e.g. the base iPhone 11 measures
    /// distance only — iPhone 11 Pro/Pro Max and all iPhone 12+ models support both). This is a
    /// per-device hardware capability, not a transient signal issue, so the UI must tell it apart
    /// from `isSignalLost` instead of showing "Searching for signal..." forever.
    private(set) var supportsDirectionMeasurement: Bool = true

    private var session: NISession?
    private var peerDiscoveryToken: NIDiscoveryToken?
    /// Some newer iPhones (e.g. iPhone 14 Pro and later) don't support direction from the UWB
    /// antenna array alone — they need `NINearbyPeerConfiguration.isCameraAssistanceEnabled`
    /// (matching how Find My shows a camera view when locating a nearby AirTag on these models).
    private var requiresCameraAssistance = false

    var discoveryToken: NIDiscoveryToken? {
        session?.discoveryToken
    }

    static var isSupported: Bool {
        NISession.isSupported
    }

    // MARK: - Lifecycle (T024a: paused on background, resumed on foreground)

    /// Creates the local `NISession` and returns its discovery token, to be sent to the peer
    /// over MultipeerConnectivity before the actual ranging configuration can start.
    @discardableResult
    func prepareSession() -> NIDiscoveryToken? {
        Self.logger.info("prepareSession() — NISession.isSupported=\(NISession.isSupported, privacy: .public)")
        let session = NISession()
        session.delegate = self
        self.session = session
        // `NISession.deviceCapabilities` needs iOS 16+; our deployment target is 15.0. On older
        // OS versions we can't query this up front and just infer it from whether `didUpdate`
        // ever hands us a real direction (see `apply`).
        if #available(iOS 16.0, *) {
            let capabilities = NISession.deviceCapabilities
            requiresCameraAssistance = !capabilities.supportsDirectionMeasurement && capabilities.supportsCameraAssistance
            supportsDirectionMeasurement = capabilities.supportsDirectionMeasurement || capabilities.supportsCameraAssistance
        }
        Self.logger.info("prepareSession() — supportsDirectionMeasurement=\(self.supportsDirectionMeasurement, privacy: .public) requiresCameraAssistance=\(self.requiresCameraAssistance, privacy: .public)")
        let token = session.discoveryToken
        Self.logger.info("prepareSession() — discoveryToken=\(token == nil ? "nil" : "present", privacy: .public)")
        return token
    }

    /// Starts ranging once the peer's discovery token has arrived (P2P Contract 1).
    func startSession(withPeerToken peerToken: NIDiscoveryToken) {
        Self.logger.info("startSession(withPeerToken:) — running NINearbyPeerConfiguration")
        peerDiscoveryToken = peerToken
        guard let session else {
            Self.logger.info("startSession(withPeerToken:) — no local NISession, aborting")
            return
        }
        let config = makeConfiguration(peerToken: peerToken)
        session.run(config)
    }

    func pause() {
        session?.pause()
    }

    func resume() {
        guard let session, let peerDiscoveryToken else { return }
        session.run(makeConfiguration(peerToken: peerDiscoveryToken))
    }

    private func makeConfiguration(peerToken: NIDiscoveryToken) -> NINearbyPeerConfiguration {
        let config = NINearbyPeerConfiguration(peerToken: peerToken)
        if #available(iOS 16.0, *), requiresCameraAssistance {
            config.isCameraAssistanceEnabled = true
        }
        return config
    }

    func stop() {
        session?.invalidate()
        session = nil
        peerDiscoveryToken = nil
        distance = nil
        direction = nil
        horizontalAngle = nil
        isSignalLost = false
    }
}

// MARK: - NISessionDelegate

extension UWBManager: NISessionDelegate {
    func session(_ session: NISession, didUpdate nearbyObjects: [NINearbyObject]) {
        guard let object = nearbyObjects.first(where: { $0.discoveryToken == peerDiscoveryToken }) else {
            Self.logger.info("didUpdate: \(nearbyObjects.count, privacy: .public) object(s), none matched our peerDiscoveryToken")
            return
        }
        var horizontalAngleValue: Float?
        if #available(iOS 16.0, *) {
            horizontalAngleValue = object.horizontalAngle
            Self.logger.info("didUpdate: distance=\(object.distance.map { String($0) } ?? "nil", privacy: .public) direction=\(object.direction == nil ? "nil" : "present", privacy: .public) horizontalAngle=\(horizontalAngleValue.map { String($0) } ?? "nil", privacy: .public) verticalEstimate=\(String(describing: object.verticalDirectionEstimate), privacy: .public)")
        } else {
            Self.logger.info("didUpdate: distance=\(object.distance.map { String($0) } ?? "nil", privacy: .public) direction=\(object.direction == nil ? "nil" : "present", privacy: .public)")
        }
        apply(distance: object.distance, direction: object.direction, horizontalAngle: horizontalAngleValue)
    }

    func session(_ session: NISession, didRemove nearbyObjects: [NINearbyObject], reason: NINearbyObject.RemovalReason) {
        Self.logger.info("didRemove: reason=\(String(describing: reason), privacy: .public)")
        guard nearbyObjects.contains(where: { $0.discoveryToken == peerDiscoveryToken }) else { return }
        apply(distance: distance, direction: nil, horizontalAngle: nil)
    }

    func sessionWasSuspended(_ session: NISession) {
        Self.logger.info("sessionWasSuspended")
        isSignalLost = true
    }

    func sessionSuspensionEnded(_ session: NISession) {
        Self.logger.info("sessionSuspensionEnded — resuming")
        resume()
    }

    func session(_ session: NISession, didInvalidateWith error: Error) {
        Self.logger.info("didInvalidateWith error=\(error.localizedDescription, privacy: .public)")
        self.session = nil
        isSignalLost = true
    }

    /// Testable core of the UWB update logic, decoupled from the un-constructible `NINearbyObject`
    /// (Apple provides no public initializer for it, so tests call this directly instead).
    func apply(distance: Float?, direction: simd_float3?, horizontalAngle: Float? = nil) {
        self.distance = distance
        self.direction = direction
        self.horizontalAngle = horizontalAngle
        // Obstruction between devices: both direction and horizontalAngle go nil while distance
        // may still be known.
        isSignalLost = (direction == nil && horizontalAngle == nil)
    }
}
