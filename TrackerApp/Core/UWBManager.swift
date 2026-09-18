import Foundation
import NearbyInteraction
import simd

/// Wraps a single `NISession` to continuously measure distance and direction to one peer,
/// using the discovery token exchanged over MultipeerConnectivity (P2P Contract 1).
final class UWBManager: NSObject, ObservableObject {
    @Published private(set) var distance: Float?
    @Published private(set) var direction: simd_float3?
    /// True when the UWB signal is temporarily blocked/lost but a distance was previously known
    /// (Edge Case: UWB Signal Loss → UI shows "Searching for signal...").
    @Published private(set) var isSignalLost: Bool = false

    private var session: NISession?
    private var peerDiscoveryToken: NIDiscoveryToken?

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
        let session = NISession()
        session.delegate = self
        self.session = session
        return session.discoveryToken
    }

    /// Starts ranging once the peer's discovery token has arrived (P2P Contract 1).
    func startSession(withPeerToken peerToken: NIDiscoveryToken) {
        peerDiscoveryToken = peerToken
        guard let session else { return }
        let config = NINearbyPeerConfiguration(peerToken: peerToken)
        session.run(config)
    }

    func pause() {
        session?.pause()
    }

    func resume() {
        guard let session, let peerDiscoveryToken else { return }
        let config = NINearbyPeerConfiguration(peerToken: peerDiscoveryToken)
        session.run(config)
    }

    func stop() {
        session?.invalidate()
        session = nil
        peerDiscoveryToken = nil
        distance = nil
        direction = nil
        isSignalLost = false
    }
}

// MARK: - NISessionDelegate

extension UWBManager: NISessionDelegate {
    func session(_ session: NISession, didUpdate nearbyObjects: [NINearbyObject]) {
        guard let object = nearbyObjects.first(where: { $0.discoveryToken == peerDiscoveryToken }) else { return }
        apply(distance: object.distance, direction: object.direction)
    }

    func session(_ session: NISession, didRemove nearbyObjects: [NINearbyObject], reason: NINearbyObject.RemovalReason) {
        guard nearbyObjects.contains(where: { $0.discoveryToken == peerDiscoveryToken }) else { return }
        apply(distance: distance, direction: nil)
    }

    func sessionWasSuspended(_ session: NISession) {
        isSignalLost = true
    }

    func sessionSuspensionEnded(_ session: NISession) {
        resume()
    }

    func session(_ session: NISession, didInvalidateWith error: Error) {
        self.session = nil
        isSignalLost = true
    }

    /// Testable core of the UWB update logic, decoupled from the un-constructible `NINearbyObject`
    /// (Apple provides no public initializer for it, so tests call this directly instead).
    func apply(distance: Float?, direction: simd_float3?) {
        self.distance = distance
        if let direction {
            self.direction = direction
            isSignalLost = false
        } else {
            // Obstruction between devices: direction becomes nil while distance may still be known.
            self.direction = nil
            isSignalLost = true
        }
    }
}
