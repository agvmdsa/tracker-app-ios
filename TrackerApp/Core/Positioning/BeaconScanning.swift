import Foundation

@MainActor
protocol BeaconScanning: AnyObject {
    var sightings: [BeaconID: BeaconSighting] { get }
    var isBluetoothUnavailable: Bool { get }

    func startScanning()
    func stopScanning()
}
