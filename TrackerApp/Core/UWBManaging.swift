import Foundation
import NearbyInteraction
import simd

@MainActor
protocol UWBManaging: AnyObject {
    var distance: Float? { get }
    var direction: simd_float3? { get }
    var horizontalAngle: Float? { get }
    var isSignalLost: Bool { get }
    var supportsDirectionMeasurement: Bool { get }

    @discardableResult
    func prepareSession() -> NIDiscoveryToken?
    func startSession(withPeerToken peerToken: NIDiscoveryToken)
    func pause()
    func resume()
    func stop()
}

extension UWBManager: UWBManaging {}
