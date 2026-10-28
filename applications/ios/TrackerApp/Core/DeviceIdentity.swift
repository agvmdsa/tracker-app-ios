import Foundation

/// This install's persistent identity.
enum DeviceIdentity {
    private static let beaconIDKey = "trackerapp.beaconID"

    /// This install's identity on the Positioning BLE protocol — see `BeaconProtocol`.
    static var beaconID: BeaconID {
        if let stored = UserDefaults.standard.string(forKey: beaconIDKey),
           let id = BeaconID(hex: stored) {
            return id
        }
        let generated = BeaconID.random()
        UserDefaults.standard.set(generated.value, forKey: beaconIDKey)
        return generated
    }

    /// The hardware model identifier, e.g. "iPhone15,2" (an iPhone 14 Pro). `UIDevice.model` only
    /// says "iPhone", which can't tell apart radios that report RSSI differently.
    static var model: String {
        var systemInfo = utsname()
        uname(&systemInfo)
        return withUnsafeBytes(of: systemInfo.machine) { bytes in
            String(decoding: bytes.prefix { $0 != 0 }, as: UTF8.self)
        }
    }

    /// e.g. "iOS 26.0.1".
    static var systemVersion: String {
        let version = ProcessInfo.processInfo.operatingSystemVersion
        return "iOS \(version.majorVersion).\(version.minorVersion).\(version.patchVersion)"
    }
}
