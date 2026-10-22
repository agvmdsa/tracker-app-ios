import Foundation

struct Anchor: Identifiable, Hashable, Codable {
    var id: BeaconID
    var label: String
    var position: Point2D
    /// Metres above the floor, when entered. Optional so anchors saved before it existed still load.
    var height: Double?

    /// e.g. "(2.50, 0.75) m · h 2.10 m".
    var positionDescription: String {
        let position = String(format: "(%.2f, %.2f) m", position.x, position.y)
        return height.map { position + String(format: " · h %.2f m", $0) } ?? position
    }
}
