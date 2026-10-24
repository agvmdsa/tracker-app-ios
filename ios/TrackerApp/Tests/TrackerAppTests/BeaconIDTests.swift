import CoreBluetooth
import XCTest
@testable import TrackerApp

final class BeaconIDTests: XCTestCase {
    func test_init_rejectsWrongLength() {
        XCTAssertNil(BeaconID(hex: "1A2B3C4"))
        XCTAssertNil(BeaconID(hex: "1A2B3C4D5"))
    }

    func test_init_rejectsNonHexCharacters() {
        XCTAssertNil(BeaconID(hex: "1A2B3C4Z"))
    }

    func test_init_uppercasesTheValue() {
        XCTAssertEqual(BeaconID(hex: "1a2b3c4d")?.value, "1A2B3C4D")
    }

    func test_random_producesAnEightCharacterHexID() {
        XCTAssertEqual(BeaconID.random().value.count, BeaconID.byteCount * 2)
    }

    func test_random_producesDifferentIDs() {
        XCTAssertNotEqual(BeaconID.random(), BeaconID.random())
    }

    // MARK: - Reading the ID from an advertisement

    /// What CoreBluetooth hands over for `btloc`'s Android advertisement: company ID 0xFFFF (least
    /// significant byte first), then the ID's 4 bytes.
    private let androidManufacturerData = Data([0xFF, 0xFF, 0x1A, 0x2B, 0x3C, 0x4D])

    func test_advertisement_readsTheIDFromTheLocalName_likeTheESP32AndiOSAnchors() {
        XCTAssertEqual(
            BeaconProtocol.beaconID(in: [CBAdvertisementDataLocalNameKey: "9f3c1a02"]),
            BeaconID(hex: "9F3C1A02")
        )
    }

    func test_advertisement_readsTheIDFromTheManufacturerData_likeAndroidAnchors() {
        XCTAssertEqual(
            BeaconProtocol.beaconID(in: [CBAdvertisementDataManufacturerDataKey: androidManufacturerData]),
            BeaconID(hex: "1A2B3C4D")
        )
    }

    func test_advertisement_prefersTheManufacturerData_overAndroidsDeviceNameInTheScanResponse() {
        let advertisement: [String: Any] = [
            CBAdvertisementDataManufacturerDataKey: androidManufacturerData,
            CBAdvertisementDataLocalNameKey: "Pixel 7",
        ]

        XCTAssertEqual(BeaconProtocol.beaconID(in: advertisement), BeaconID(hex: "1A2B3C4D"))
    }

    func test_advertisement_fallsBackToTheLocalName_whenTheManufacturerDataIsSomeoneElses() {
        let advertisement: [String: Any] = [
            CBAdvertisementDataManufacturerDataKey: Data([0x4C, 0x00, 0x1A, 0x2B, 0x3C, 0x4D]),  // Apple's company ID
            CBAdvertisementDataLocalNameKey: "9F3C1A02",
        ]

        XCTAssertEqual(BeaconProtocol.beaconID(in: advertisement), BeaconID(hex: "9F3C1A02"))
    }

    func test_advertisement_ignoresManufacturerDataTooShortForAnID() {
        XCTAssertNil(BeaconProtocol.beaconID(in: [CBAdvertisementDataManufacturerDataKey: Data([0xFF, 0xFF, 0x1A, 0x2B, 0x3C])]))
    }

    func test_advertisement_withoutAnID_isNotABeacon() {
        XCTAssertNil(BeaconProtocol.beaconID(in: [CBAdvertisementDataLocalNameKey: "Pixel 7"]))
        XCTAssertNil(BeaconProtocol.beaconID(in: [:]))
    }
}
