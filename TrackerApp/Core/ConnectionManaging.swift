import Foundation
import NearbyInteraction

@MainActor
protocol ConnectionManaging: AnyObject {
    var discoveredDevices: [TrackerDevice] { get }
    var transferError: String? { get set }

    var onDiscoveryTokenReceived: ((DiscoveryTokenWrapper) -> Void)? { get set }
    var onPayloadReceived: ((PayloadMessage) -> Void)? { get set }

    func start()
    func stop()
    func consumePendingDiscoveryToken(fromPeerNamed peerName: String) -> DiscoveryTokenWrapper?
    func sendDiscoveryToken(_ token: NIDiscoveryToken, toDeviceWithID deviceID: UUID)
    func sendPayload(_ payload: PayloadMessage, toDeviceWithID deviceID: UUID)
}

extension ConnectionManager: ConnectionManaging {}
