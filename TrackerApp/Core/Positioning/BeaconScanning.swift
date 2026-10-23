import Foundation

@MainActor
protocol BeaconScanning: AnyObject {
    var sightings: [BeaconID: BeaconSighting] { get }
    var isBluetoothUnavailable: Bool { get }

    func startScanning()
    func stopScanning()
    /// RSSI readings for `beaconID` collected since the last call to this method, then clears
    /// them — each caller sees only what arrived in its own window, not a growing history.
    func drainRecentRSSI(for beaconID: BeaconID) -> [Int]
}
