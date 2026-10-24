import Foundation

/// Progress of the out-of-band exchange of NearbyInteraction discovery tokens.
enum HandshakeState: Equatable {
    case idle
    case searching
    case exchanging
    case completed
    case failed(String)
}
