import Foundation
import simd

enum ConnectionState: String, Codable {
    case discovered
    case connecting
    case connected
    case tracking
    case disconnected
}

struct TrackerDevice: Identifiable, Equatable {
    let id: UUID
    var displayName: String
    var distance: Float?
    var direction: simd_float3?
    var state: ConnectionState

    static func == (lhs: TrackerDevice, rhs: TrackerDevice) -> Bool {
        lhs.id == rhs.id
    }
}
