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
}
