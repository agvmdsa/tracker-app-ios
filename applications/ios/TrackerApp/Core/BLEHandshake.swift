import CoreBluetooth
import Foundation
import Observation
import os

/// Exchanges NearbyInteraction discovery tokens between two phones running this app over
/// Bluetooth LE — no Wi-Fi, local-network permission or pairing UI involved.
///
/// Every device acts as both a peripheral (advertising a service whose characteristic can be read
/// for the local token and written with the peer's token) and a central (scanning for that
/// service). To avoid both devices connecting to each other, each advertises a random ID as its
/// local name and only the device with the smaller ID initiates the connection. If the ID is
/// missing from the advertisement both sides may connect, which is harmless because completion
/// only happens once.
///
/// The service UUID is deliberately different from `BeaconProtocol.serviceUUID` so the indoor
/// positioning scanner never mistakes a handshake advertisement for an anchor.
@Observable
@MainActor
final class BLEHandshake: NSObject {
    private static let logger = Logger(subsystem: "com.airtagclone.TrackerApp", category: "BLEHandshake")

    static let serviceUUID = CBUUID(string: "6E1F3A52-7C4B-4B8E-9C1D-2B7A5F0E9A10")
    static let tokenCharacteristicUUID = CBUUID(string: "6E1F3A53-7C4B-4B8E-9C1D-2B7A5F0E9A10")
    /// Maximum size of an ATT attribute value.
    static let maxTokenSize = 512

    nonisolated override init() {
        super.init()
    }

    private(set) var state: HandshakeState = .idle
    var onPeerToken: ((Data) -> Void)?
    var onStateChange: ((HandshakeState) -> Void)?

    private var localID = ""
    private var localToken = Data()
    private var isActive = false
    private var didComplete = false

    private var centralManager: CBCentralManager?
    private var peripheralManager: CBPeripheralManager?
    private var connectedPeripheral: CBPeripheral?
    private var readPeerToken: Data?

    /// Whether the device with `localID` should be the one to open the connection, given the ID the
    /// peer advertised (`nil` if it didn't carry one). The smaller ID initiates; equal or missing
    /// IDs let both sides initiate, which `complete(with:)` tolerates.
    nonisolated static func shouldInitiate(localID: String, peerID: String?) -> Bool {
        guard let peerID else { return true }
        return localID <= peerID
    }

    func start(localToken: Data) {
        stop()
        guard localToken.count <= Self.maxTokenSize else {
            setState(.failed("Local token is too large (\(localToken.count) bytes)."))
            return
        }

        self.localToken = localToken
        localID = String(UUID().uuidString.prefix(8))
        isActive = true
        didComplete = false
        readPeerToken = nil
        Self.logger.info("start() — localID=\(self.localID, privacy: .public) tokenSize=\(localToken.count, privacy: .public)")

        centralManager = CBCentralManager(delegate: self, queue: nil)
        peripheralManager = CBPeripheralManager(delegate: self, queue: nil)
        setState(.searching)
    }

    func stop() {
        isActive = false
        centralManager?.stopScan()
        if let connectedPeripheral {
            centralManager?.cancelPeripheralConnection(connectedPeripheral)
        }
        peripheralManager?.stopAdvertising()
        peripheralManager?.removeAllServices()

        centralManager = nil
        peripheralManager = nil
        connectedPeripheral = nil
        readPeerToken = nil
        setState(.idle)
    }

    private func setState(_ newState: HandshakeState) {
        state = newState
        onStateChange?(newState)
    }

    private func complete(with peerToken: Data) {
        guard isActive, !didComplete else { return }
        didComplete = true
        Self.logger.info("complete — received \(peerToken.count, privacy: .public) byte peer token")
        // Keep the GATT service alive so a peer that is still mid-exchange can finish.
        centralManager?.stopScan()
        peripheralManager?.stopAdvertising()
        setState(.completed)
        onPeerToken?(peerToken)
    }

    private func fail(_ message: String) {
        Self.logger.error("fail: \(message, privacy: .public)")
        isActive = false
        setState(.failed(message))
    }

    private func startScanningIfNeeded() {
        guard isActive, !didComplete, connectedPeripheral == nil,
              let centralManager, centralManager.state == .poweredOn else { return }
        centralManager.scanForPeripherals(withServices: [Self.serviceUUID])
    }

    private func dropConnection(_ peripheral: CBPeripheral) {
        centralManager?.cancelPeripheralConnection(peripheral)
    }

    private static func message(for state: CBManagerState) -> String? {
        switch state {
        case .poweredOn, .unknown, .resetting: nil
        case .poweredOff: "Bluetooth is turned off."
        case .unauthorized: "Bluetooth permission was denied."
        case .unsupported: "Bluetooth LE is not supported on this device."
        @unknown default: "Bluetooth is unavailable."
        }
    }
}

// MARK: - Central role (initiator)

extension BLEHandshake: CBCentralManagerDelegate {
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        if let message = Self.message(for: central.state) {
            fail(message)
            return
        }
        startScanningIfNeeded()
    }

    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral,
                        advertisementData: [String: Any], rssi RSSI: NSNumber) {
        guard isActive, !didComplete, connectedPeripheral == nil else { return }

        let peerID = advertisementData[CBAdvertisementDataLocalNameKey] as? String
        guard Self.shouldInitiate(localID: localID, peerID: peerID) else {
            // The peer has the smaller ID and will connect to us.
            return
        }

        Self.logger.info("didDiscover peer \(peerID ?? "(no id)", privacy: .public) rssi=\(RSSI.intValue, privacy: .public) — connecting")
        central.stopScan()
        connectedPeripheral = peripheral
        setState(.exchanging)
        central.connect(peripheral)
    }

    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        peripheral.delegate = self
        peripheral.discoverServices([Self.serviceUUID])
    }

    func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        Self.logger.info("didFailToConnect: \(error?.localizedDescription ?? "no error", privacy: .public)")
        connectedPeripheral = nil
        startScanningIfNeeded()
    }

    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        connectedPeripheral = nil
        readPeerToken = nil
        startScanningIfNeeded()
    }
}

extension BLEHandshake: CBPeripheralDelegate {
    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        guard error == nil,
              let service = peripheral.services?.first(where: { $0.uuid == Self.serviceUUID }) else {
            dropConnection(peripheral)
            return
        }
        peripheral.discoverCharacteristics([Self.tokenCharacteristicUUID], for: service)
    }

    func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        guard error == nil,
              let characteristic = service.characteristics?.first(where: { $0.uuid == Self.tokenCharacteristicUUID }) else {
            dropConnection(peripheral)
            return
        }
        // Step 1: read the peer's token.
        peripheral.readValue(for: characteristic)
    }

    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        guard error == nil, let value = characteristic.value, !value.isEmpty else {
            dropConnection(peripheral)
            return
        }
        // Step 2: send our token so the peer can range against us too.
        readPeerToken = value
        peripheral.writeValue(localToken, for: characteristic, type: .withResponse)
    }

    func peripheral(_ peripheral: CBPeripheral, didWriteValueFor characteristic: CBCharacteristic, error: Error?) {
        let peerToken = readPeerToken
        dropConnection(peripheral)
        guard error == nil, let peerToken else { return }
        complete(with: peerToken)
    }
}

// MARK: - Peripheral role (responder)

extension BLEHandshake: CBPeripheralManagerDelegate {
    func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        if let message = Self.message(for: peripheral.state) {
            fail(message)
            return
        }
        guard peripheral.state == .poweredOn, isActive else { return }

        let characteristic = CBMutableCharacteristic(
            type: Self.tokenCharacteristicUUID,
            properties: [.read, .write],
            value: nil,
            permissions: [.readable, .writeable]
        )
        let service = CBMutableService(type: Self.serviceUUID, primary: true)
        service.characteristics = [characteristic]
        peripheral.removeAllServices()
        peripheral.add(service)
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, didAdd service: CBService, error: Error?) {
        if let error {
            fail("Could not publish BLE service: \(error.localizedDescription)")
            return
        }
        guard isActive, !didComplete else { return }
        peripheral.startAdvertising([
            CBAdvertisementDataServiceUUIDsKey: [Self.serviceUUID],
            CBAdvertisementDataLocalNameKey: localID,
        ])
    }

    func peripheralManagerDidStartAdvertising(_ peripheral: CBPeripheralManager, error: Error?) {
        if let error {
            fail("Could not advertise: \(error.localizedDescription)")
        }
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, didReceiveRead request: CBATTRequest) {
        guard request.characteristic.uuid == Self.tokenCharacteristicUUID else {
            peripheral.respond(to: request, withResult: .attributeNotFound)
            return
        }
        guard request.offset <= localToken.count else {
            peripheral.respond(to: request, withResult: .invalidOffset)
            return
        }
        request.value = localToken.subdata(in: request.offset..<localToken.count)
        peripheral.respond(to: request, withResult: .success)
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, didReceiveWrite requests: [CBATTRequest]) {
        guard let first = requests.first else { return }

        // Long writes arrive as multiple requests with increasing offsets.
        var peerToken = Data()
        for request in requests {
            guard request.characteristic.uuid == Self.tokenCharacteristicUUID else {
                peripheral.respond(to: first, withResult: .attributeNotFound)
                return
            }
            guard request.offset == peerToken.count, let value = request.value else {
                peripheral.respond(to: first, withResult: .invalidOffset)
                return
            }
            peerToken.append(value)
        }

        peripheral.respond(to: first, withResult: .success)
        if !peerToken.isEmpty {
            complete(with: peerToken)
        }
    }
}

extension BLEHandshake: HandshakeChannel {}
