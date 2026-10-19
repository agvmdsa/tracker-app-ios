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
}
