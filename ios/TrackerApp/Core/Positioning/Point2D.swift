import CoreGraphics
import Foundation

struct Point2D: Hashable, Codable {
    var x: Double
    var y: Double

    func distance(to other: Point2D) -> Double {
        hypot(x - other.x, y - other.y)
    }

    var magnitude: Double { hypot(x, y) }
}

/// Where metres land on a map view: every given point fits inside the margin, one scale serves both
/// axes (a metre is as long to the right as upwards) and y grows upwards. The same idea as the
/// room map of the data-flow animation, but fitted to whatever the anchors and the fix are.
struct MapProjection: Equatable {
    /// Points per metre, the same on both axes.
    let scale: Double
    /// The metres shown at the middle of the view.
    let center: Point2D
    let size: CGSize

    /// `minimumSpan` keeps a lone point (or points on top of each other) from zooming in without limit.
    init(fitting points: [Point2D], in size: CGSize, margin: Double = 28, minimumSpan: Double = 4) {
        let xs = points.map(\.x), ys = points.map(\.y)
        let minX = xs.min() ?? 0, maxX = xs.max() ?? 0, minY = ys.min() ?? 0, maxY = ys.max() ?? 0
        let spanX = max(maxX - minX, minimumSpan), spanY = max(maxY - minY, minimumSpan)
        self.size = size
        self.center = Point2D(x: (minX + maxX) / 2, y: (minY + maxY) / 2)
        self.scale = max(1e-6, min((Double(size.width) - 2 * margin) / spanX, (Double(size.height) - 2 * margin) / spanY))
    }

    func viewPoint(for point: Point2D) -> CGPoint {
        CGPoint(
            x: Double(size.width) / 2 + (point.x - center.x) * scale,
            y: Double(size.height) / 2 - (point.y - center.y) * scale
        )
    }

    /// The metres the view covers, edge to edge, for drawing the grid.
    var visibleX: ClosedRange<Double> {
        let half = Double(size.width) / (2 * scale)
        return (center.x - half)...(center.x + half)
    }
    var visibleY: ClosedRange<Double> {
        let half = Double(size.height) / (2 * scale)
        return (center.y - half)...(center.y + half)
    }

    /// The smallest "round" grid spacing, in metres, that keeps the lines at least 40 points apart
    /// (closer than that the labels crowd each other).
    static func gridStep(forScale scale: Double) -> Double {
        let steps = [0.5, 1, 2, 5, 10, 20, 50, 100]
        return steps.first { $0 * scale >= 40 } ?? steps[steps.count - 1]
    }
}

/// Numbers typed into coordinate fields. A Portuguese keyboard types "2,5", but `Double("2,5")` is
/// nil: Swift's own parser only knows the dot, so the fields used to refuse a valid number (and to
/// accept "nan" and "inf"). Here a dot or a comma both work, and nothing else does.
enum DecimalInput {
    /// A plain decimal number, optionally signed, with at most one separator ("," or "."). Nil for
    /// anything else: empty text, letters, exponents, "nan" or "inf", two separators, or a
    /// thousands separator, which is ambiguous between the two conventions.
    static func parse(_ text: String) -> Double? {
        var body = Substring(text.trimmingCharacters(in: .whitespaces))
        var sign = 1.0
        if body.first == "-" { sign = -1; body = body.dropFirst() } else if body.first == "+" { body = body.dropFirst() }
        guard !body.isEmpty,
              body.allSatisfy({ ("0"..."9").contains($0) || $0 == "." || $0 == "," }),
              body.contains(where: { ("0"..."9").contains($0) }),
              body.filter({ $0 == "." || $0 == "," }).count <= 1,
              let value = Double(body.replacingOccurrences(of: ",", with: "."))
        else { return nil }
        return sign * value
    }

    /// What a field shows for `value`: plain decimal with a dot, up to 3 decimals and no trailing
    /// zeros (2.5, -1, 0.125). Never an exponent, which `parse` would refuse to read back.
    static func text(for value: Double) -> String {
        guard value.isFinite else { return "" }
        var text = String(format: "%.3f", value)
        while text.hasSuffix("0") { text.removeLast() }
        if text.hasSuffix(".") { text.removeLast() }
        return text == "-0" ? "0" : text
    }
}
