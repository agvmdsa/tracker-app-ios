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
}
