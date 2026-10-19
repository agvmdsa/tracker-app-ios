import CoreBluetooth

enum BeaconProtocol {
    static let serviceUUID = CBUUID(string: "fc979812-36dd-4722-9316-d2a7908888dd")
}

extension CBManagerState {
    var indicatesBluetoothUnavailable: Bool {
        self != .poweredOn && self != .unknown && self != .resetting
    }

    var indicatesPermissionDenied: Bool {
        self == .unauthorized
    }
}
