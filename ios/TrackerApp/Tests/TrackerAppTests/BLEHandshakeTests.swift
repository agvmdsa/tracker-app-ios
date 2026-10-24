import XCTest
@testable import TrackerApp

@MainActor
final class BLEHandshakeTests: XCTestCase {
    func test_shouldInitiate_whenLocalIDIsSmaller() {
        XCTAssertTrue(BLEHandshake.shouldInitiate(localID: "AAAAAAAA", peerID: "BBBBBBBB"))
    }

    func test_shouldNotInitiate_whenPeerIDIsSmaller() {
        XCTAssertFalse(BLEHandshake.shouldInitiate(localID: "BBBBBBBB", peerID: "AAAAAAAA"))
    }

    func test_shouldInitiate_onlyOneSideEverDoes() {
        XCTAssertNotEqual(
            BLEHandshake.shouldInitiate(localID: "12345678", peerID: "87654321"),
            BLEHandshake.shouldInitiate(localID: "87654321", peerID: "12345678")
        )
    }

    func test_shouldInitiate_whenIDsAreEqual_soTheHandshakeCantDeadlock() {
        XCTAssertTrue(BLEHandshake.shouldInitiate(localID: "AAAAAAAA", peerID: "AAAAAAAA"))
    }

    func test_shouldInitiate_whenThePeerAdvertisedNoID() {
        XCTAssertTrue(BLEHandshake.shouldInitiate(localID: "AAAAAAAA", peerID: nil))
    }

    func test_start_withATokenLargerThanAnATTValue_fails() {
        let sut = BLEHandshake()

        sut.start(localToken: Data(count: BLEHandshake.maxTokenSize + 1))

        guard case .failed = sut.state else {
            return XCTFail("expected .failed, got \(sut.state)")
        }
    }

    func test_stop_onAnIdleHandshake_staysIdle() {
        let sut = BLEHandshake()

        sut.stop()

        XCTAssertEqual(sut.state, .idle)
    }

    func test_stateChangesAreReported() {
        let sut = BLEHandshake()
        var reported: [HandshakeState] = []
        sut.onStateChange = { reported.append($0) }

        sut.stop()

        XCTAssertEqual(reported, [.idle])
    }
}
