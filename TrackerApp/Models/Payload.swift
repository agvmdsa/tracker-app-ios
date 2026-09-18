import Foundation

/// Generic data chunk sent over the high-bandwidth MultipeerConnectivity link.
/// Wire format is defined in specs/001-ios-tracker-app/contracts/p2p-messages.md.
struct PayloadMessage: Codable, Equatable {
    let messageId: UUID
    let timestamp: Date
    let actionType: String
    let data: Data
}
