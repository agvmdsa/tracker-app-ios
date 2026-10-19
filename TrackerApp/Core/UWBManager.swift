import Foundation
import NearbyInteraction
import Observation
import simd
import os

/// Wraps a single `NISession` to continuously measure distance and direction to one peer,
/// using the discovery token the peer handed over through a `HandshakeChannel`.
///
/// On iPhone 14 and later, direction is generally only available with camera assistance (ARKit).
/// In `.automatic` mode the manager starts without the camera — unless the hardware can't give
/// a direction any other way — and, if distance arrives but no angle does within
/// `cameraFallbackDelay`, re-runs the session with camera assistance enabled.
@Observable
@MainActor
final class UWBManager: NSObject {
    private static let logger = Logger(subsystem: "com.airtagclone.TrackerApp", category: "UWBManager")

    enum CameraAssistance {
        case never
        case automatic
        case always
    }

    let cameraAssistance: CameraAssistance
    /// How long to wait for an angle without the camera before falling back to it.
    let cameraFallbackDelay: TimeInterval

    nonisolated init(cameraAssistance: CameraAssistance = .automatic, cameraFallbackDelay: TimeInterval = 5) {
        self.cameraAssistance = cameraAssistance
        self.cameraFallbackDelay = cameraFallbackDelay
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
    /// Whether the running session currently uses the camera to work out direction.
    private(set) var isCameraAssisted = false
    /// Why the session stopped working (permission denied, NI failure, ...), if it did.
    private(set) var errorMessage: String?

    /// Called when the peer ended its session, so the owner can look for it again.
    var onPeerEnded: (() -> Void)?
    /// Diagnostic messages (capabilities, camera fallback, ...) for display.
    var onLog: ((String) -> Void)?

    private var session: NISession?
    private var peerDiscoveryToken: NIDiscoveryToken?
    private var configuration: NINearbyPeerConfiguration?
    /// Some newer iPhones don't support direction from the UWB antenna array alone — they need
    /// `NINearbyPeerConfiguration.isCameraAssistanceEnabled` (matching how Find My shows a camera
    /// view when locating a nearby AirTag on these models).
    private var requiresCameraAssistance = false
    private var firstDistanceAt: Date?
    private var hasReceivedAngle = false

    static var isSupported: Bool {
        NISession.deviceCapabilities.supportsPreciseDistanceMeasurement
    }

    var isSupported: Bool { Self.isSupported }

    private var supportsCameraAssistance: Bool {
        NISession.deviceCapabilities.supportsCameraAssistance
    }

    // MARK: - Angles

    /// Azimuth (radians) of the peer from a NearbyInteraction direction vector: 0 = straight
    /// ahead, positive = to the right, negative = to the left.
    /// NI coordinate space: +x right, +y up, -z forward (out of the back of the phone).
    nonisolated static func azimuth(from direction: simd_float3) -> Float {
        atan2(direction.x, -direction.z)
    }

    /// Elevation (radians) of the peer from a NearbyInteraction direction vector.
    nonisolated static func elevation(from direction: simd_float3) -> Float {
        asin(simd_clamp(direction.y, -1, 1))
    }

    /// Horizontal angle to the peer, from `direction` when the antenna array gives one and from the
    /// camera-assisted `horizontalAngle` otherwise.
    var azimuth: Float? {
        direction.map(Self.azimuth(from:)) ?? horizontalAngle
    }

    var elevation: Float? {
        direction.map(Self.elevation(from:))
    }

    // MARK: - Lifecycle (T024a: paused on background, resumed on foreground)

    /// Creates the local `NISession` and returns its archived discovery token, to be sent to the
    /// peer before the actual ranging configuration can start.
    func prepareSession() throws -> Data {
        guard Self.isSupported else { throw UWBError.unsupported }
        stop()

        let capabilities = NISession.deviceCapabilities
        requiresCameraAssistance = !capabilities.supportsDirectionMeasurement && capabilities.supportsCameraAssistance
        supportsDirectionMeasurement = capabilities.supportsDirectionMeasurement || capabilities.supportsCameraAssistance
        log("UWB capabilities: distance=\(capabilities.supportsPreciseDistanceMeasurement), direction=\(capabilities.supportsDirectionMeasurement), camera assistance=\(capabilities.supportsCameraAssistance)")

        let session = NISession()
        session.delegate = self
        self.session = session

        guard let token = session.discoveryToken else { throw UWBError.notPrepared }
        return try NSKeyedArchiver.archivedData(withRootObject: token, requiringSecureCoding: true)
    }

    /// Starts ranging once the peer's archived discovery token has arrived.
    func startSession(withPeerToken peerToken: Data) throws {
        guard session != nil else { throw UWBError.notPrepared }
        guard let token = try NSKeyedUnarchiver.unarchivedObject(ofClass: NIDiscoveryToken.self, from: peerToken) else {
            throw UWBError.invalidPeerToken
        }
        peerDiscoveryToken = token
        run(NINearbyPeerConfiguration(peerToken: token), cameraAssisted: shouldStartWithCamera)
    }

    func pause() {
        session?.pause()
    }

    func resume() {
        guard let session, let configuration else { return }
        session.run(configuration)
    }

    func stop() {
        session?.invalidate()
        session = nil
        peerDiscoveryToken = nil
        configuration = nil
        firstDistanceAt = nil
        hasReceivedAngle = false
        distance = nil
        direction = nil
        horizontalAngle = nil
        isSignalLost = false
        isCameraAssisted = false
        errorMessage = nil
    }

    private var shouldStartWithCamera: Bool {
        guard supportsCameraAssistance else { return false }
        switch cameraAssistance {
        case .never: return false
        case .always: return true
        case .automatic: return requiresCameraAssistance
        }
    }

    private func run(_ configuration: NINearbyPeerConfiguration, cameraAssisted: Bool) {
        configuration.isCameraAssistanceEnabled = cameraAssisted
        self.configuration = configuration
        isCameraAssisted = cameraAssisted
        firstDistanceAt = nil
        hasReceivedAngle = false
        session?.run(configuration)
        log(cameraAssisted ? "Ranging with camera assistance." : "Ranging without camera assistance.")
    }

    /// Switches to camera assistance when distance arrives but no angle does.
    private func checkCameraFallback(at date: Date) {
        guard cameraAssistance == .automatic, let configuration, !isCameraAssisted, !hasReceivedAngle else { return }

        if azimuth != nil {
            hasReceivedAngle = true
            log("Direction available without camera.")
            return
        }
        guard distance != nil else { return }

        let firstDistanceAt = firstDistanceAt ?? date
        self.firstDistanceAt = firstDistanceAt
        guard date.timeIntervalSince(firstDistanceAt) >= cameraFallbackDelay else { return }

        guard supportsCameraAssistance else {
            hasReceivedAngle = true // Stop checking; nothing else to try.
            log("No direction without camera, and camera assistance is not supported. Distance only.")
            return
        }
        log("No direction without camera after \(Int(cameraFallbackDelay))s. Switching to camera assistance.")
        run(configuration, cameraAssisted: true)
    }

    private func log(_ message: String) {
        Self.logger.info("\(message, privacy: .public)")
        onLog?(message)
    }
}

// MARK: - NISessionDelegate

extension UWBManager: NISessionDelegate {
    func session(_ session: NISession, didUpdate nearbyObjects: [NINearbyObject]) {
        guard let object = nearbyObjects.first(where: { $0.discoveryToken == peerDiscoveryToken }) else {
            Self.logger.info("didUpdate: \(nearbyObjects.count, privacy: .public) object(s), none matched our peerDiscoveryToken")
            return
        }
        Self.logger.info("didUpdate: distance=\(object.distance.map { String($0) } ?? "nil", privacy: .public) direction=\(object.direction == nil ? "nil" : "present", privacy: .public) horizontalAngle=\(object.horizontalAngle.map { String($0) } ?? "nil", privacy: .public)")
        apply(distance: object.distance, direction: object.direction, horizontalAngle: object.horizontalAngle)
    }

    func session(_ session: NISession, didRemove nearbyObjects: [NINearbyObject], reason: NINearbyObject.RemovalReason) {
        Self.logger.info("didRemove: reason=\(String(describing: reason), privacy: .public)")
        guard nearbyObjects.contains(where: { $0.discoveryToken == peerDiscoveryToken }) else { return }
        apply(distance: distance, direction: nil, horizontalAngle: nil)
        switch reason {
        case .timeout:
            // Peer may come back in range; keep trying with the same configuration.
            log("Peer timed out, retrying.")
            if let configuration { session.run(configuration) }
        case .peerEnded:
            log("Peer ended the session.")
            onPeerEnded?()
        @unknown default:
            onPeerEnded?()
        }
    }

    func sessionWasSuspended(_ session: NISession) {
        log("Session suspended.")
        isSignalLost = true
    }

    func sessionSuspensionEnded(_ session: NISession) {
        log("Session resumed.")
        resume()
    }

    func session(_ session: NISession, didInvalidateWith error: Error) {
        // Ignore the late invalidation of a session we already replaced via `prepareSession()`.
        guard session === self.session else { return }
        self.session = nil
        handleInvalidation(error)
    }

    /// Testable core of the invalidation handling, decoupled from the live `NISession`.
    func handleInvalidation(_ error: Error) {
        log("Session invalidated: \(error.localizedDescription)")
        configuration = nil
        isSignalLost = true
        errorMessage = error.localizedDescription
    }

    /// Testable core of the UWB update logic, decoupled from the un-constructible `NINearbyObject`
    /// (Apple provides no public initializer for it, so tests call this directly instead).
    func apply(distance: Float?, direction: simd_float3?, horizontalAngle: Float? = nil, at date: Date = Date()) {
        self.distance = distance
        self.direction = direction
        self.horizontalAngle = horizontalAngle
        // Obstruction between devices: both direction and horizontalAngle go nil while distance
        // may still be known.
        isSignalLost = (direction == nil && horizontalAngle == nil)
        checkCameraFallback(at: date)
    }
}
