import Foundation

struct AnchorRange {
    let anchor: Anchor
    let distanceMeters: Double
    /// The median RSSI (dBm) the distance was computed from, when it came from the radio. Shown on the
    /// map, because calibrating the path loss model needs the dBm and not only the distance.
    var rssi: Double? = nil
}

struct ObserverFix {
    let readings: [AnchorRange]
    let position: Point2D
    let uncertaintyMeters: Double
}

enum Multilateration {
    private static let epsilon = 1e-9

    /// Typical RSSI noise in dB. A fixed error in dB becomes an error that grows with the distance
    /// (the RSSI changes less per metre the further away the anchor is), so a reading is trusted in
    /// proportion to how near its anchor is.
    static let rssiNoiseDb = 3.0
    /// How wrong a distance is, as a fraction of itself, for `rssiNoiseDb` of noise: about 0.35,
    /// i.e. 0.7 m at 2 m and 2.8 m at 8 m.
    static let relativeDistanceError = log(10.0) / (10.0 * PathLossModel.pathLossExponent) * rssiNoiseDb
    /// Closer than this the error stops shrinking (the model, the antenna and the phone's own
    /// position are never that exact), which also keeps the weight of a tiny distance finite.
    static let minimumSigmaMeters = 0.5

    private static let maximumIterations = 20
    private static let convergedStepMeters = 1e-6

    /// How much a reading counts: 1 / error², with the error proportional to the distance, so an
    /// anchor at 2 m counts 16 times more than one at 8 m.
    static func weight(forDistance distance: Double) -> Double {
        let sigma = max(minimumSigmaMeters, relativeDistanceError * distance)
        return 1 / (sigma * sigma)
    }

    /// Weighted least squares over every reading: it starts from the unweighted linear solution
    /// (which also rejects anchors that are in a line) and refines it by Gauss-Newton, so no single
    /// anchor, and in particular not the first one in the list, decides the result.
    static func solve(readings: [AnchorRange]) -> ObserverFix? {
        guard readings.count >= 3, let start = linearSolution(readings: readings) else { return nil }

        let position = refined(start, readings: readings)

        let residual = sqrt(
            readings.reduce(0.0) { partial, reading in
                partial + sq(reading.anchor.position.distance(to: position) - reading.distanceMeters)
            } / Double(readings.count)
        )

        return ObserverFix(readings: readings, position: position, uncertaintyMeters: residual)
    }

    /// Subtracts the first anchor's circle from the others, which leaves linear equations. It
    /// counts every reading the same and trusts the first one's distance in every equation, so it is
    /// only the starting point. Nil when the anchors are (almost) in a line.
    private static func linearSolution(readings: [AnchorRange]) -> Point2D? {
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

        return Point2D(
            x: (c1 * m22 - m12 * c2) / determinant,
            y: (m11 * c2 - c1 * m12) / determinant
        )
    }

    /// Gauss-Newton on the weighted range residuals. Keeps the starting point if a step ever makes
    /// the weighted error worse or the equations degenerate.
    private static func refined(_ start: Point2D, readings: [AnchorRange]) -> Point2D {
        var position = start
        var cost = weightedCost(at: position, readings: readings)

        for _ in 0..<maximumIterations {
            var n11 = 0.0, n12 = 0.0, n22 = 0.0
            var g1 = 0.0, g2 = 0.0
            for reading in readings {
                let dx = position.x - reading.anchor.position.x
                let dy = position.y - reading.anchor.position.y
                let range = max(hypot(dx, dy), epsilon)
                let ux = dx / range
                let uy = dy / range
                let weight = weight(forDistance: reading.distanceMeters)
                let error = reading.distanceMeters - range

                n11 += weight * ux * ux
                n12 += weight * ux * uy
                n22 += weight * uy * uy
                g1 += weight * ux * error
                g2 += weight * uy * error
            }

            let determinant = n11 * n22 - n12 * n12
            guard abs(determinant) > epsilon else { break }
            let step = Point2D(
                x: (g1 * n22 - n12 * g2) / determinant,
                y: (n11 * g2 - g1 * n12) / determinant
            )

            let candidate = Point2D(x: position.x + step.x, y: position.y + step.y)
            let candidateCost = weightedCost(at: candidate, readings: readings)
            guard candidateCost <= cost else { break }
            position = candidate
            cost = candidateCost

            if step.magnitude < convergedStepMeters { break }
        }
        return position
    }

    private static func weightedCost(at position: Point2D, readings: [AnchorRange]) -> Double {
        readings.reduce(0.0) { partial, reading in
            let error = reading.anchor.position.distance(to: position) - reading.distanceMeters
            return partial + weight(forDistance: reading.distanceMeters) * sq(error)
        }
    }

    private static func sq(_ value: Double) -> Double { value * value }
}
