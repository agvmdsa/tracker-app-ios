import Foundation

struct BeaconID: Hashable, Codable, Identifiable {
    var id: String { value }

    static let byteCount = 4

    let value: String

    init?(hex: String) {
        guard hex.utf8.count == BeaconID.byteCount * 2,
              hex.allSatisfy(\.isHexDigit)
        else { return nil }
        value = hex.uppercased()
    }

    private init(uncheckedValue: String) {
        value = uncheckedValue
    }

    static func random() -> BeaconID {
        let bytes = (0..<byteCount).map { _ in UInt8.random(in: .min ... .max) }
        let hex = bytes.map { String(format: "%02X", $0) }.joined()
        return BeaconID(uncheckedValue: hex)
    }
}

extension BeaconID: CustomStringConvertible {
    var description: String { value }
}
