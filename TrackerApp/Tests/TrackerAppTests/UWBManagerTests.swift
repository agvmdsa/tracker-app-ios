import XCTest
import NearbyInteraction
import simd
@testable import TrackerApp

@MainActor
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
        XCTAssertNil(sut.azimuth)
        XCTAssertNil(sut.elevation)
        XCTAssertFalse(sut.isSignalLost)
        XCTAssertFalse(sut.isCameraAssisted)
        XCTAssertNil(sut.errorMessage)
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

    func test_handleInvalidation_marksSignalLostAndReportsTheError() {
        sut.handleInvalidation(NIError(.invalidConfiguration))

        XCTAssertTrue(sut.isSignalLost)
        XCTAssertNotNil(sut.errorMessage)
    }

    func test_didInvalidateWith_ignoresASessionThatIsNotTheCurrentOne() {
        // e.g. the late invalidation of a session `prepareSession()` already replaced.
        sut.session(NISession(), didInvalidateWith: NIError(.invalidConfiguration))

        XCTAssertFalse(sut.isSignalLost)
        XCTAssertNil(sut.errorMessage)
    }

    func test_stop_resetsAllPublishedState() {
        sut.apply(distance: 3.0, direction: simd_float3(0, 1, 0), horizontalAngle: 0.3)
        sut.handleInvalidation(NIError(.invalidConfiguration))

        sut.stop()

        XCTAssertNil(sut.distance)
        XCTAssertNil(sut.direction)
        XCTAssertNil(sut.horizontalAngle)
        XCTAssertFalse(sut.isSignalLost)
        XCTAssertNil(sut.errorMessage)
    }

    func test_isSupported_isExposedForCapabilityChecks() {
        _ = UWBManager.isSupported
    }

    func test_startSession_withoutPrepareSession_throwsNotPrepared() {
        XCTAssertThrowsError(try sut.startSession(withPeerToken: Data([1, 2, 3]))) { error in
            XCTAssertEqual(error as? UWBError, .notPrepared)
        }
    }

    func test_prepareSession_onUnsupportedHardware_throwsUnsupported() throws {
        try XCTSkipIf(UWBManager.isSupported, "needs a device without UWB, e.g. the Simulator")

        XCTAssertThrowsError(try sut.prepareSession()) { error in
            XCTAssertEqual(error as? UWBError, .unsupported)
        }
    }

    // MARK: - Azimuth / elevation

    func test_azimuth_isZeroWhenThePeerIsStraightAhead() {
        // NI space: -z is out of the back of the phone.
        XCTAssertEqual(UWBManager.azimuth(from: simd_float3(0, 0, -1)), 0, accuracy: 1e-6)
    }

    func test_azimuth_isPositiveWhenThePeerIsToTheRight() {
        let direction = simd_normalize(simd_float3(1, 0, -1))
        XCTAssertEqual(UWBManager.azimuth(from: direction), .pi / 4, accuracy: 1e-5)
    }

    func test_azimuth_isNegativeWhenThePeerIsToTheLeft() {
        let direction = simd_normalize(simd_float3(-1, 0, -1))
        XCTAssertEqual(UWBManager.azimuth(from: direction), -.pi / 4, accuracy: 1e-5)
    }

    func test_azimuth_isHalfTurnWhenThePeerIsBehind() {
        XCTAssertEqual(abs(UWBManager.azimuth(from: simd_float3(0, 0, 1))), .pi, accuracy: 1e-5)
    }

    func test_elevation_isPositiveWhenThePeerIsAbove() {
        XCTAssertEqual(UWBManager.elevation(from: simd_float3(0, 1, 0)), .pi / 2, accuracy: 1e-5)
        XCTAssertEqual(UWBManager.elevation(from: simd_float3(0, 0, -1)), 0, accuracy: 1e-6)
    }

    func test_azimuth_comesFromDirectionWhenAvailable() throws {
        sut.apply(distance: 1, direction: simd_normalize(simd_float3(1, 0, -1)), horizontalAngle: 0.1)

        XCTAssertEqual(try XCTUnwrap(sut.azimuth), .pi / 4, accuracy: 1e-5)
        XCTAssertNotNil(sut.elevation)
    }

    func test_azimuth_fallsBackToHorizontalAngleOnCameraAssistedOnlyDevices() throws {
        sut.apply(distance: 1, direction: nil, horizontalAngle: 0.4)

        XCTAssertEqual(try XCTUnwrap(sut.azimuth), 0.4, accuracy: 1e-6)
        XCTAssertNil(sut.elevation)
    }
}
