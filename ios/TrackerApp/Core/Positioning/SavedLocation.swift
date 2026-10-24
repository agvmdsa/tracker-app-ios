import Foundation

struct SavedLocation: Identifiable, Hashable, Codable {
    var id: UUID
    var label: String
    var position: Point2D
    var savedAt: Date
}
