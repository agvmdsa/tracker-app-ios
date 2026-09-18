import Foundation

/// Local container for a peer's `NIDiscoveryToken` (serialized) plus the identity of the
/// MultipeerConnectivity peer it came from. `peerId` is never part of the wire payload — it is
/// attached locally from `MCSessionDelegate.session(_:didReceive:fromPeer:)` when decoding.
/// See specs/001-ios-tracker-app/contracts/p2p-messages.md.
struct DiscoveryTokenWrapper {
    let tokenData: Data
    let peerId: String
}
