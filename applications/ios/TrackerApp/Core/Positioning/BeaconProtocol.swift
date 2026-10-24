import CoreBluetooth

enum BeaconProtocol {
    static let serviceUUID = CBUUID(string: "fc979812-36dd-4722-9316-d2a7908888dd")

    /// Company ID of the manufacturer data that carries the ID in Android advertisements (`btloc`'s
    /// `MANUFACTURER_ID`): 0xFFFF, the SIG's value for internal and test use. Android can't put a
    /// custom local name in an advertisement, so the ID goes here instead.
    static let manufacturerID: UInt16 = 0xFFFF

    /// The ID an advertisement of this protocol carries. iOS anchors and the ESP32 beacons put it in
    /// the local name; Android puts it in the manufacturer data, followed by its device name in the
    /// scan response, which lands in the same local-name field. So the manufacturer data wins when
    /// present, and the local name is only trusted when it is an ID itself.
    static func beaconID(in advertisementData: [String: Any]) -> BeaconID? {
        if let data = advertisementData[CBAdvertisementDataManufacturerDataKey] as? Data,
           let id = beaconID(inManufacturerData: data) {
            return id
        }
        guard let localName = advertisementData[CBAdvertisementDataLocalNameKey] as? String else { return nil }
        return BeaconID(hex: localName)
    }

    /// CoreBluetooth hands manufacturer data over with the company ID still in front, least
    /// significant byte first, then the payload: the ID's bytes.
    static func beaconID(inManufacturerData data: Data) -> BeaconID? {
        let bytes = [UInt8](data)
        guard bytes.count >= 2 + BeaconID.byteCount,
              UInt16(bytes[0]) | UInt16(bytes[1]) << 8 == manufacturerID
        else { return nil }
        let hex = bytes[2..<(2 + BeaconID.byteCount)].map { String(format: "%02X", $0) }.joined()
        return BeaconID(hex: hex)
    }
}

extension CBManagerState {
    var indicatesBluetoothUnavailable: Bool {
        self != .poweredOn && self != .unknown && self != .resetting
    }

    var indicatesPermissionDenied: Bool {
        self == .unauthorized
    }
}
