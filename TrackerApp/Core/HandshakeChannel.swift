import Foundation

/// Out-of-band transport that finds a nearby peer running this app and swaps one opaque token
/// with it (BLE today; could be Wi-Fi/Bonjour, Multipeer, ...).
///
/// The channel knows nothing about what the token is — `UWBManager` produces and consumes the
/// NearbyInteraction discovery tokens, the channel only moves the bytes.
@MainActor
protocol HandshakeChannel: AnyObject {
    var state: HandshakeState { get }

    /// Called once per `start(localToken:)`, when the peer's token has arrived.
    var onPeerToken: ((Data) -> Void)? { get set }
    var onStateChange: ((HandshakeState) -> Void)? { get set }

    /// Starts looking for a peer and offering `localToken` to it.
    func start(localToken: Data)
    func stop()
}
