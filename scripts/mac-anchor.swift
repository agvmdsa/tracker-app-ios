import CoreBluetooth
import Foundation

final class Advertiser: NSObject, CBPeripheralManagerDelegate {
    // Mesmo serviceUUID do BeaconProtocol.swift do app — não mude isso.
    let serviceUUID = CBUUID(string: "fc979812-36dd-4722-9316-d2a7908888dd")
    // Precisa ter exatamente 8 caracteres hexadecimais (0-9, A-F). Pode trocar o valor,
    // mas mantenha 8 caracteres hex — é o que BeaconID(hex:) exige no app.
    let localID = "DEADBEEF"
    var manager: CBPeripheralManager!

    override init() {
        super.init()
        manager = CBPeripheralManager(delegate: self, queue: nil)
    }

    func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        switch peripheral.state {
        case .poweredOn:
            peripheral.startAdvertising([
                CBAdvertisementDataServiceUUIDsKey: [serviceUUID],
                CBAdvertisementDataLocalNameKey: localID,
            ])
            print("Anunciando como âncora \(localID) — deixe este terminal aberto.")
        default:
            print("Bluetooth não está pronto (estado: \(peripheral.state.rawValue)). Verifique Privacidade e Segurança > Bluetooth.")
        }
    }
}

let advertiser = Advertiser()
RunLoop.main.run()
