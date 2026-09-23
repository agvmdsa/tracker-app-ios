import CoreBluetooth
import Observation
import os

@Observable
@MainActor
final class BeaconAdvertiser: NSObject {
    private static let logger = Logger(subsystem: "com.airtagclone.TrackerApp", category: "BeaconAdvertiser")

    let localID: BeaconID
    private(set) var isAdvertising = false
    private(set) var isBluetoothUnavailable = false

    private lazy var peripheralManager = CBPeripheralManager(
        delegate: self,
        queue: nil,
        options: [CBPeripheralManagerOptionShowPowerAlertKey: false]
    )
    private var startRequested = false

    nonisolated init(localID: BeaconID = DeviceIdentity.beaconID) {
        self.localID = localID
        super.init()
    }

    func startAdvertising() {
        startRequested = true
        guard peripheralManager.state == .poweredOn else {
            Self.logger.info("startAdvertising: waiting for poweredOn (current=\(self.peripheralManager.state.rawValue, privacy: .public))")
            return
        }
        beginAdvertising()
    }

    func stopAdvertising() {
        startRequested = false
        peripheralManager.stopAdvertising()
        isAdvertising = false
    }

    private func beginAdvertising() {
        peripheralManager.startAdvertising([
            CBAdvertisementDataServiceUUIDsKey: [BeaconProtocol.serviceUUID],
            CBAdvertisementDataLocalNameKey: localID.value,
        ])
    }
}

extension BeaconAdvertiser: BeaconAdvertising {}

extension BeaconAdvertiser: CBPeripheralManagerDelegate {
    func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        Self.logger.info("peripheralManagerDidUpdateState: \(peripheral.state.rawValue, privacy: .public)")
        isBluetoothUnavailable = peripheral.state.indicatesBluetoothUnavailable

        switch peripheral.state {
        case .poweredOn where startRequested:
            beginAdvertising()
        case .poweredOn:
            break
        default:
            isAdvertising = false
        }
    }

    func peripheralManagerDidStartAdvertising(_ peripheral: CBPeripheralManager, error: Error?) {
        if let error {
            Self.logger.error("peripheralManagerDidStartAdvertising error: \(error.localizedDescription, privacy: .public)")
            isAdvertising = false
            return
        }
        Self.logger.info("peripheralManagerDidStartAdvertising: advertising as \(self.localID.value, privacy: .public)")
        isAdvertising = true
    }
}
