import Foundation

enum PathLossModel {
    static let referenceRSSI: Double = -59.0
    static let pathLossExponent: Double = 2.0
    static let smoothing: Double = 0.15

    static func distanceMeters(rssi: Double) -> Double {
        pow(10.0, (referenceRSSI - rssi) / (10.0 * pathLossExponent))
    }

    static func smooth(previous: Double?, reading: Int) -> Double {
        guard let previous else { return Double(reading) }
        return previous + smoothing * (Double(reading) - previous)
    }
}
