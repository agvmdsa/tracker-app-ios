import XCTest
import NearbyInteraction
import simd
@testable import TrackerApp

final class UWBManagerTests: XCTestCase {
    private var sut: UWBManager!

    override func setUp() {
        super.setUp()
        sut = UWBManager()
    }

    override func tearDown() {
        sut = nil
        super.tearDown()
    }

    func test_initialState_hasNoDistanceOrDirection() {
        XCTAssertNil(sut.distance)
        XCTAssertNil(sut.direction)
        XCTAssertFalse(sut.isSignalLost)
    }

    func test_apply_withDirection_updatesDistanceAndDirection() {
        let direction = simd_float3(0, 0, 1)

        sut.apply(distance: 1.5, direction: direction)

        XCTAssertEqual(sut.distance, 1.5)
        XCTAssertEqual(sut.direction, direction)
        XCTAssertFalse(sut.isSignalLost)
    }

    func test_apply_withNilDirection_marksSignalLostButKeepsLastDistance() {
        sut.apply(distance: 2.0, direction: simd_float3(1, 0, 0))
        sut.apply(distance: 2.0, direction: nil)

        XCTAssertEqual(sut.distance, 2.0, "Last known distance must be retained per spec Edge Cases: UWB Signal Loss")
        XCTAssertNil(sut.direction)
        XCTAssertTrue(sut.isSignalLost)
    }

    func test_apply_withHorizontalAngleOnly_updatesAngleAndDoesNotMarkSignalLost() {
        // Camera-assisted-only devices (iPhone 14 Pro and later) never populate `direction`;
        // the angle instead arrives via `horizontalAngle`. This must not be treated as signal loss.
        sut.apply(distance: 1.2, direction: nil, horizontalAngle: 0.5)

        XCTAssertEqual(sut.distance, 1.2)
        XCTAssertNil(sut.direction)
        XCTAssertEqual(sut.horizontalAngle, 0.5)
        XCTAssertFalse(sut.isSignalLost)
    }

    func test_apply_withNeitherDirectionNorHorizontalAngle_marksSignalLost() {
        sut.apply(distance: 1.2, direction: nil, horizontalAngle: 0.5)
        sut.apply(distance: 1.2, direction: nil, horizontalAngle: nil)

        XCTAssertNil(sut.horizontalAngle)
        XCTAssertTrue(sut.isSignalLost)
    }

    func test_sessionWasSuspended_marksSignalLost() {
        let session = NISession()
        sut.sessionWasSuspended(session)
        XCTAssertTrue(sut.isSignalLost)
    }

    func test_didInvalidateWith_marksSignalLost() {
        let session = NISession()
        sut.session(session, didInvalidateWith: NIError(.invalidConfiguration))
        XCTAssertTrue(sut.isSignalLost)
    }

    func test_stop_resetsAllPublishedState() {
        sut.apply(distance: 3.0, direction: simd_float3(0, 1, 0), horizontalAngle: 0.3)

        sut.stop()

        XCTAssertNil(sut.distance)
        XCTAssertNil(sut.direction)
        XCTAssertNil(sut.horizontalAngle)
        XCTAssertFalse(sut.isSignalLost)
    }

    func test_isSupported_isExposedForCapabilityChecks() {
        _ = UWBManager.isSupported
    }
}
