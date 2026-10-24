import Foundation

/// What the UWB side of a tracking session is doing, derived by `ProximityTracker` from the
/// handshake and `UWBManager` rather than stored, so it can't drift out of sync with them.
enum RangingState: Equatable {
    case idle
    case waitingForPeer
    case ranging
    case failed(String)
}
