import Foundation

@MainActor
protocol BeaconScanning: AnyObject {
    /// The latest sighting of each beacon seen in the last few seconds.
    var sightings: [BeaconID: BeaconSighting] { get }
    var isBluetoothUnavailable: Bool { get }

    /// Called for every advertisement received, not just the latest one per beacon.
    var onSighting: ((BeaconSighting) -> Void)? { get set }

    func startScanning()
    func stopScanning()
}
