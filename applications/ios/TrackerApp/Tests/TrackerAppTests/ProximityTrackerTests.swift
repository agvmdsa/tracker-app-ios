import XCTest
import simd
@testable import TrackerApp

@MainActor
private final class FakeUWB: UWBManaging {
    var distance: Float?
    var direction: simd_float3?
    var horizontalAngle: Float?
    var azimuth: Float?
    var elevation: Float?
    var isSignalLost = false
    var supportsDirectionMeasurement = true
    var isCameraAssisted = false
    var isSupported = true
    var errorMessage: String?

    var onPeerEnded: (() -> Void)?
    var onLog: ((String) -> Void)?

    var prepareError: Error?
    var startError: Error?
    private(set) var prepareCount = 0
    private(set) var startedWithPeerToken: Data?
    private(set) var pauseCount = 0
    private(set) var resumeCount = 0
    private(set) var stopCount = 0

    static let localToken = Data([0xA, 0xB, 0xC])

    func prepareSession() throws -> Data {
        prepareCount += 1
        if let prepareError { throw prepareError }
        return Self.localToken
    }

    func startSession(withPeerToken peerToken: Data) throws {
        if let startError { throw startError }
        startedWithPeerToken = peerToken
    }

    func pause() { pauseCount += 1 }
    func resume() { resumeCount += 1 }
    func stop() { stopCount += 1 }
}

@MainActor
private final class FakeHandshake: HandshakeChannel {
    var state: HandshakeState = .idle
    var onPeerToken: ((Data) -> Void)?
    var onStateChange: ((HandshakeState) -> Void)?

    private(set) var startedWithLocalToken: [Data] = []
    private(set) var stopCount = 0

    func start(localToken: Data) {
        startedWithLocalToken.append(localToken)
        setState(.searching)
    }

    func stop() {
        stopCount += 1
        setState(.idle)
    }

    func setState(_ newState: HandshakeState) {
        state = newState
        onStateChange?(newState)
    }

    func deliverPeerToken(_ token: Data) {
        setState(.completed)
        onPeerToken?(token)
    }
}

@MainActor
final class ProximityTrackerTests: XCTestCase {
    private var uwb: FakeUWB!
    private var handshake: FakeHandshake!
    private var sut: ProximityTracker!

    private let peerToken = Data([1, 2, 3, 4])

    override func setUp() {
        super.setUp()
        uwb = FakeUWB()
        handshake = FakeHandshake()
        sut = ProximityTracker(uwb: uwb, handshake: handshake)
    }

    override func tearDown() {
        sut = nil
        handshake = nil
        uwb = nil
        super.tearDown()
    }

    func test_initialState_isIdle() {
        XCTAssertFalse(sut.isRunning)
        XCTAssertEqual(sut.rangingState, .idle)
        XCTAssertEqual(sut.handshakeState, .idle)
        XCTAssertNil(sut.errorMessage)
    }

    func test_start_offersTheLocalUWBTokenOverTheHandshake() {
        sut.start()

        XCTAssertTrue(sut.isRunning)
        XCTAssertEqual(uwb.prepareCount, 1)
        XCTAssertEqual(handshake.startedWithLocalToken, [FakeUWB.localToken])
        XCTAssertEqual(sut.handshakeState, .searching)
        XCTAssertEqual(sut.rangingState, .waitingForPeer)
    }

    func test_start_whenUWBCantPrepare_reportsTheErrorAndDoesNotRun() {
        uwb.prepareError = UWBError.unsupported

        sut.start()

        XCTAssertFalse(sut.isRunning)
        XCTAssertEqual(sut.errorMessage, UWBError.unsupported.errorDescription)
        XCTAssertTrue(handshake.startedWithLocalToken.isEmpty)
    }

    func test_peerToken_startsRanging() {
        sut.start()

        handshake.deliverPeerToken(peerToken)

        XCTAssertEqual(uwb.startedWithPeerToken, peerToken)
        XCTAssertEqual(sut.rangingState, .ranging)
        XCTAssertEqual(sut.handshakeState, .completed)
    }

    func test_invalidPeerToken_reportsTheError() {
        sut.start()
        uwb.startError = UWBError.invalidPeerToken

        handshake.deliverPeerToken(peerToken)

        XCTAssertEqual(sut.errorMessage, UWBError.invalidPeerToken.errorDescription)
        XCTAssertEqual(sut.rangingState, .failed(UWBError.invalidPeerToken.errorDescription!))
    }

    func test_peerToken_afterStop_isIgnored() {
        sut.start()
        sut.stop()

        handshake.onPeerToken?(peerToken)

        XCTAssertNil(uwb.startedWithPeerToken)
        XCTAssertEqual(sut.rangingState, .idle)
    }

    func test_peerEnded_startsTheHandshakeOverWithAFreshSession() {
        sut.start()
        handshake.deliverPeerToken(peerToken)

        uwb.onPeerEnded?()

        XCTAssertEqual(uwb.prepareCount, 2)
        XCTAssertEqual(handshake.startedWithLocalToken.count, 2)
        XCTAssertEqual(sut.rangingState, .waitingForPeer)
        XCTAssertEqual(sut.handshakeState, .searching)
    }

    func test_peerEnded_afterStop_doesNotRestart() {
        sut.start()
        handshake.deliverPeerToken(peerToken)
        sut.stop()

        uwb.onPeerEnded?()

        XCTAssertEqual(uwb.prepareCount, 1)
        XCTAssertFalse(sut.isRunning)
    }

    func test_stop_stopsBothSidesAndClearsErrors() {
        sut.start()
        handshake.setState(.failed("Bluetooth is turned off."))

        sut.stop()

        XCTAssertFalse(sut.isRunning)
        XCTAssertEqual(handshake.stopCount, 1)
        XCTAssertEqual(uwb.stopCount, 1)
        XCTAssertNil(sut.errorMessage)
        XCTAssertEqual(sut.rangingState, .idle)
    }

    func test_handshakeFailure_surfacesAsAnErrorMessage() {
        sut.start()

        handshake.setState(.failed("Bluetooth is turned off."))

        XCTAssertEqual(sut.errorMessage, "Bluetooth is turned off.")
        XCTAssertEqual(sut.rangingState, .failed("Bluetooth is turned off."))
    }

    func test_uwbSessionFailure_surfacesAsAnErrorMessage() {
        sut.start()
        handshake.deliverPeerToken(peerToken)

        uwb.errorMessage = "Nearby Interaction permission denied."

        XCTAssertEqual(sut.errorMessage, "Nearby Interaction permission denied.")
    }

    func test_measurements_areForwardedFromUWB() {
        uwb.distance = 1.5
        uwb.azimuth = 0.25
        uwb.elevation = -0.1
        uwb.isSignalLost = true
        uwb.isCameraAssisted = true
        uwb.supportsDirectionMeasurement = false

        XCTAssertEqual(sut.distance, 1.5)
        XCTAssertEqual(sut.azimuth, 0.25)
        XCTAssertEqual(sut.elevation, -0.1)
        XCTAssertTrue(sut.isSignalLost)
        XCTAssertTrue(sut.isCameraAssisted)
        XCTAssertFalse(sut.supportsDirectionMeasurement)
    }

    func test_logs_recordTheFlowAndAreCapped() {
        sut.start()
        handshake.deliverPeerToken(peerToken)
        XCTAssertTrue(sut.logs.contains { $0.message.contains("Received peer token") })

        for index in 0..<100 {
            uwb.onLog?("message \(index)")
        }
        XCTAssertEqual(sut.logs.count, 50)
        XCTAssertEqual(sut.logs.last?.message, "message 99")
    }

    // MARK: - Background / foreground

    func test_pause_whileRanging_pausesUWBOnly() {
        sut.start()
        handshake.deliverPeerToken(peerToken)

        sut.pause()

        XCTAssertEqual(uwb.pauseCount, 1)
        XCTAssertEqual(handshake.stopCount, 0)
    }

    func test_resume_whileRanging_resumesUWB() {
        sut.start()
        handshake.deliverPeerToken(peerToken)
        sut.pause()

        sut.resume()

        XCTAssertEqual(uwb.resumeCount, 1)
        XCTAssertEqual(uwb.prepareCount, 1)
    }

    func test_pause_beforeAPeerIsFound_stopsTheHandshake_andResumeStartsItAgain() {
        sut.start()

        sut.pause()
        XCTAssertEqual(handshake.stopCount, 1)
        XCTAssertEqual(uwb.pauseCount, 0)

        sut.resume()
        XCTAssertEqual(handshake.startedWithLocalToken.count, 2)
        XCTAssertEqual(sut.handshakeState, .searching)
    }

    func test_pauseAndResume_whenNotRunning_doNothing() {
        sut.pause()
        sut.resume()

        XCTAssertEqual(uwb.pauseCount, 0)
        XCTAssertEqual(uwb.resumeCount, 0)
        XCTAssertEqual(uwb.prepareCount, 0)
        XCTAssertEqual(handshake.stopCount, 0)
    }
}
