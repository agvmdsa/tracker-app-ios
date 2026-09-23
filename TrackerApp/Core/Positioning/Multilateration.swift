import Foundation

struct AnchorRange {
    let anchor: Anchor
    let distanceMeters: Double
}

struct ObserverFix {
    let readings: [AnchorRange]
    let position: Point2D
    let uncertaintyMeters: Double
}

enum Multilateration {
    private static let epsilon = 1e-9

    static func solve(readings: [AnchorRange]) -> ObserverFix? {
        guard readings.count >= 3 else { return nil }

        let first = readings[0]
        let p1 = first.anchor.position
        let d1 = first.distanceMeters

        var m11 = 0.0, m12 = 0.0, m22 = 0.0
        var c1 = 0.0, c2 = 0.0

        for reading in readings.dropFirst() {
            let p = reading.anchor.position
            let a1 = 2 * (p.x - p1.x)
            let a2 = 2 * (p.y - p1.y)
            let b = sq(d1) - sq(reading.distanceMeters) + sq(p.x) - sq(p1.x) + sq(p.y) - sq(p1.y)

            m11 += a1 * a1
            m12 += a1 * a2
            m22 += a2 * a2
            c1 += a1 * b
            c2 += a2 * b
        }

        let determinant = m11 * m22 - m12 * m12
        guard abs(determinant) > epsilon else { return nil }

        let position = Point2D(
            x: (c1 * m22 - m12 * c2) / determinant,
            y: (m11 * c2 - c1 * m12) / determinant
        )

        let residual = sqrt(
            readings.reduce(0.0) { partial, reading in
                partial + sq(reading.anchor.position.distance(to: position) - reading.distanceMeters)
            } / Double(readings.count)
        )

        return ObserverFix(readings: readings, position: position, uncertaintyMeters: residual)
    }

    private static func sq(_ value: Double) -> Double { value * value }
}
