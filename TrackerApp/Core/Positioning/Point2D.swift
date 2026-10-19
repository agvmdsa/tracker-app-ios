import Foundation

struct Point2D: Hashable, Codable {
    var x: Double
    var y: Double

    func distance(to other: Point2D) -> Double {
        hypot(x - other.x, y - other.y)
    }

    var magnitude: Double { hypot(x, y) }
}
