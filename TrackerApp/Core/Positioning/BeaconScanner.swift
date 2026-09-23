import CoreBluetooth
import Observation
import os

@Observable
@MainActor
final class BeaconScanner: NSObject {
    private static let logger = Logger(subsystem: "com.airtagclone.TrackerApp", category: "BeaconScanner")

    nonisolated override init() {
        super.init()
    }

    private static let staleAfter: TimeInterval = 5.0

    private(set) var sightings: [BeaconID: BeaconSighting] = [:]
    private(set) var isBluetoothUnavailable = false

    private lazy var centralManager = CBCentralManager(
        delegate: self,
        queue: nil,
        options: [CBCentralManagerOptionShowPowerAlertKey: false]
    )
    private var scanRequested = false
    private var pruneTimer: Timer?

    func startScanning() {
        scanRequested = true
        schedulePruning()
        guard centralManager.state == .poweredOn else {
            Self.logger.info("startScanning: waiting for poweredOn (current=\(self.centralManager.state.rawValue, privacy: .public))")
            return
        }
        beginScanning()
    }

    func stopScanning() {
        scanRequested = false
        centralManager.stopScan()
        pruneTimer?.invalidate()
        pruneTimer = nil
    }

    private func schedulePruning() {
        pruneTimer?.invalidate()
        pruneTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
            self?.pruneStaleSightings()
        }
    }

    private func pruneStaleSightings() {
        let cutoff = Date().addingTimeInterval(-Self.staleAfter)
        sightings = sightings.filter { $0.value.seenAt >= cutoff }
    }

    private func beginScanning() {
        centralManager.scanForPeripherals(
            withServices: [BeaconProtocol.serviceUUID],
            options: [CBCentralManagerScanOptionAllowDuplicatesKey: true]
        )
    }
}

extension BeaconScanner: BeaconScanning {}

extension BeaconScanner: CBCentralManagerDelegate {
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        Self.logger.info("centralManagerDidUpdateState: \(central.state.rawValue, privacy: .public)")
        isBluetoothUnavailable = central.state.indicatesBluetoothUnavailable

        if central.state == .poweredOn, scanRequested {
            beginScanning()
        }
    }

    func centralManager(
        _ central: CBCentralManager,
        didDiscover peripheral: CBPeripheral,
        advertisementData: [String: Any],
        rssi RSSI: NSNumber
    ) {
        guard let localName = advertisementData[CBAdvertisementDataLocalNameKey] as? String,
              let beaconID = BeaconID(hex: localName)
        else { return }
        sightings[beaconID] = BeaconSighting(beaconID: beaconID, rssi: RSSI.intValue, seenAt: Date())
    }
}
