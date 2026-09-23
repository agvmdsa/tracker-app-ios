import Foundation

struct Anchor: Identifiable, Hashable, Codable {
    var id: BeaconID
    var label: String
    var position: Point2D
}
