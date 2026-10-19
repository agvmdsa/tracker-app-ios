import Foundation

/// `UIDevice.current.name` returns a generic "iPhone" for third-party apps since iOS 16 — Apple
/// restricts the real user-assigned device name behind an entitlement ordinary apps can't get.
/// We generate and persist our own friendly identifier instead, so peers can be told apart, and
/// let the user rename it from `MainView`.
enum DeviceIdentity {
    private static let key = "trackerapp.displayName"

    static var displayName: String {
        get {
            if let stored = UserDefaults.standard.string(forKey: key), !stored.isEmpty {
                return stored
            }
            let generated = "iPhone-\(String(UUID().uuidString.prefix(4)))"
            UserDefaults.standard.set(generated, forKey: key)
            return generated
        }
        set {
            UserDefaults.standard.set(newValue, forKey: key)
        }
    }

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
}
