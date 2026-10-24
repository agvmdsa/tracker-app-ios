import Foundation

@MainActor
protocol BeaconAdvertising: AnyObject {
    var localID: BeaconID { get }
    var isAdvertising: Bool { get }
    var isBluetoothUnavailable: Bool { get }

    func startAdvertising()
    func stopAdvertising()
}
