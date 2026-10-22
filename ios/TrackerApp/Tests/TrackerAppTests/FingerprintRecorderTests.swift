import XCTest
@testable import TrackerApp

@MainActor
private final class FakeScanner: BeaconScanning {
    var sightings: [BeaconID: BeaconSighting] = [:]
    var isBluetoothUnavailable = false
    var onSighting: ((BeaconSighting) -> Void)?
    private(set) var isScanning = false

    func startScanning() { isScanning = true }
    func stopScanning() { isScanning = false }

    func deliver(_ beacon: BeaconID, rssi: Int, at date: Date = Date(), monotonicMs: Int64 = MonotonicClock.nowMs()) {
        let sighting = BeaconSighting(beaconID: beacon, rssi: rssi, seenAt: date, monotonicMs: monotonicMs)
        sightings[beacon] = sighting
        onSighting?(sighting)
    }
}

@MainActor
private final class FakeAccelerometer: AccelerometerReading {
    var latest: AccelerometerSample?
    private(set) var isRunning = false

    func start() { isRunning = true }
    func stop() { isRunning = false }
}

@MainActor
final class FingerprintRecorderTests: XCTestCase {
    private let node = BeaconID(hex: "9F3C1A02")!
    private let beaconA = BeaconID(hex: "1A2B3C4D")!
    private let beaconB = BeaconID(hex: "B7E2F910")!

    private var directory: URL!
    private var scanner: FakeScanner!
    private var accelerometer: FakeAccelerometer!
    /// What the recorder sees as the positioned beacons (the app's `AnchorStore.anchors`).
    private var anchors: [Anchor] = []
    private var sut: FingerprintRecorder!

    override func setUp() {
        super.setUp()
        directory = FileManager.default.temporaryDirectory.appending(path: UUID().uuidString)
        scanner = FakeScanner()
        accelerometer = FakeAccelerometer()
        anchors = []
        sut = makeRecorder()
    }

    override func tearDown() {
        sut.stop()
        sut = nil
        try? FileManager.default.removeItem(at: directory)
        super.tearDown()
    }

    private func makeRecorder() -> FingerprintRecorder {
        FingerprintRecorder(
            scanner: scanner,
            accelerometer: accelerometer,
            anchors: { [weak self] in self?.anchors ?? [] },
            directory: directory,
            node: node,
            model: "iPhone15,2",
            systemVersion: "iOS 26.0.1"
        )
    }

    private func lines(of url: URL) throws -> [String] {
        try String(contentsOf: url, encoding: .utf8).split(separator: "\n").map(String.init)
    }

    private let label = FingerprintLabel(session: "S1", point: "P3", x: 2, y: 4.5)!
    /// Short enough to keep the tests fast; `waitForTheRecordingToEnd()` gives it plenty of slack.
    private let shortDuration: TimeInterval = 0.05

    private func waitForTheRecordingToEnd() async throws {
        let deadline = Date().addingTimeInterval(2)
        while sut.isRecording && Date() < deadline {
            try await Task.sleep(for: .milliseconds(10))
        }
        XCTAssertFalse(sut.isRecording, "the recording should have stopped on its own")
    }

    private func recordedFiles() throws -> [String] {
        try FileManager.default.contentsOfDirectory(atPath: directory.path())
    }

    // MARK: - FingerprintLabel

    func test_label_acceptsTheSameTokensAsTheESP32Collector() {
        XCTAssertNotNil(FingerprintLabel(session: "S1", point: "P3", x: 2.0, y: 4.5))
        XCTAssertNotNil(FingerprintLabel(session: "sala_2-b", point: String(repeating: "9", count: 16), x: -1, y: 0))
    }

    func test_label_rejectsTokensThatCouldBreakARowOrAFileName() {
        XCTAssertNil(FingerprintLabel(session: "", point: "P3", x: 0, y: 0))
        XCTAssertNil(FingerprintLabel(session: "S1", point: String(repeating: "9", count: 17), x: 0, y: 0))
        XCTAssertNil(FingerprintLabel(session: "S,1", point: "P3", x: 0, y: 0))
        XCTAssertNil(FingerprintLabel(session: "S1", point: "P 3", x: 0, y: 0))
        XCTAssertNil(FingerprintLabel(session: "S1", point: "P3/x", x: 0, y: 0))
        XCTAssertNil(FingerprintLabel(session: "sessão", point: "P3", x: 0, y: 0))
    }

    func test_label_rejectsNonFiniteCoordinates() {
        XCTAssertNil(FingerprintLabel(session: "S1", point: "P3", x: .nan, y: 0))
        XCTAssertNil(FingerprintLabel(session: "S1", point: "P3", x: 0, y: .infinity))
    }

    // MARK: - FingerprintRecord

    func test_csvLine_quotesTheModelIdentifier_whichContainsAComma() {
        let record = FingerprintRecord(
            monotonicMs: 86_400_123,
            seenAt: Date(timeIntervalSince1970: 1_700_000_000.123),
            node: node,
            model: "iPhone15,2",
            systemVersion: "iOS 26.0.1",
            beacon: beaconA,
            rssi: -61,
            acceleration: AccelerometerSample(x: 0.01, y: -0.02, z: -0.999),
            label: FingerprintLabel(session: "S1", point: "P3", x: 2, y: 4.5)!
        )

        XCTAssertEqual(
            record.csvLine,
            "86400123,1700000000123,9F3C1A02,\"iPhone15,2\",\"iOS 26.0.1\",1A2B3C4D,-61,0.0100,-0.0200,-0.9990,S1,P3,2.00,4.50"
        )
    }

    func test_csvLine_leavesTheAccelerationEmpty_whenThereIsNoReading() {
        let record = FingerprintRecord(
            monotonicMs: 5,
            seenAt: Date(timeIntervalSince1970: 0),
            node: node,
            model: "iPhone15,2",
            systemVersion: "iOS 26.0.1",
            beacon: beaconA,
            rssi: -61,
            acceleration: nil,
            label: FingerprintLabel(session: "S1", point: "P3", x: 0, y: 0)!
        )

        XCTAssertEqual(record.csvLine, "5,0,9F3C1A02,\"iPhone15,2\",\"iOS 26.0.1\",1A2B3C4D,-61,,,,S1,P3,0.00,0.00")
    }

    func test_csvLine_hasAsManyFieldsAsTheHeader() {
        let record = FingerprintRecord(
            monotonicMs: 0,
            seenAt: Date(),
            node: node,
            model: "iPhone",
            systemVersion: "iOS 26.0.1",
            beacon: beaconA,
            rssi: -61,
            acceleration: nil,
            label: FingerprintLabel(session: "S1", point: "P3", x: 0, y: 0)!
        )

        XCTAssertEqual(
            record.csvLine.split(separator: ",", omittingEmptySubsequences: false).count,
            FingerprintRecord.csvHeader.split(separator: ",").count
        )
    }

    // MARK: - FingerprintRecorder

    func test_start_listensToTheBeaconsAndTheAccelerometer() {
        sut.start()

        XCTAssertTrue(scanner.isScanning)
        XCTAssertNotNil(scanner.onSighting)
        XCTAssertTrue(accelerometer.isRunning)
        XCTAssertFalse(sut.isRecording)
    }

    func test_stop_detachesFromTheScanner_andStopsTheSensors() {
        sut.start()

        sut.stop()

        XCTAssertFalse(scanner.isScanning)
        XCTAssertNil(scanner.onSighting)
        XCTAssertFalse(accelerometer.isRunning)
    }

    func test_recording_lastsTheRequestedTime() throws {
        sut.startRecording(label: label, duration: 120)

        let recording = try XCTUnwrap(sut.activeRecording)
        XCTAssertEqual(recording.label, label)
        XCTAssertEqual(recording.endsAt.timeIntervalSince(recording.startedAt), 120, accuracy: 1e-6)
    }

    func test_recording_stopsOnItsOwn_whenTheTimeIsUp() async throws {
        sut.start()
        sut.startRecording(label: label, duration: shortDuration)
        XCTAssertTrue(sut.isRecording)

        try await waitForTheRecordingToEnd()

        XCTAssertEqual(sut.recordings.count, 1)
        XCTAssertTrue(try XCTUnwrap(sut.recordings.first).lastPathComponent.hasSuffix("_S1_P3.csv"))
    }

    func test_recording_writesTheHeader_thenOneRowPerAdvertisement() async throws {
        sut.start()
        scanner.deliver(beaconA, rssi: -50)  // before recording: not written
        accelerometer.latest = AccelerometerSample(x: 0, y: 0, z: -1)

        sut.startRecording(label: label, duration: shortDuration)
        scanner.deliver(beaconA, rssi: -61, at: Date(timeIntervalSince1970: 1), monotonicMs: 501)
        scanner.deliver(beaconB, rssi: -72, at: Date(timeIntervalSince1970: 2), monotonicMs: 1502)
        scanner.deliver(beaconA, rssi: -63, at: Date(timeIntervalSince1970: 3), monotonicMs: 2503)
        try await waitForTheRecordingToEnd()
        scanner.deliver(beaconA, rssi: -50)  // after recording: not written

        let url = try XCTUnwrap(sut.recordings.first)
        XCTAssertEqual(try lines(of: url), [
            FingerprintRecord.csvHeader,
            "501,1000,9F3C1A02,\"iPhone15,2\",\"iOS 26.0.1\",1A2B3C4D,-61,0.0000,0.0000,-1.0000,S1,P3,2.00,4.50",
            "1502,2000,9F3C1A02,\"iPhone15,2\",\"iOS 26.0.1\",B7E2F910,-72,0.0000,0.0000,-1.0000,S1,P3,2.00,4.50",
            "2503,3000,9F3C1A02,\"iPhone15,2\",\"iOS 26.0.1\",1A2B3C4D,-63,0.0000,0.0000,-1.0000,S1,P3,2.00,4.50",
        ])
    }

    func test_finishedRecording_writesItsMetadataNextToTheCSV() async throws {
        sut.start()
        let before = MonotonicClock.nowMs()
        sut.startRecording(label: label, duration: shortDuration)
        scanner.deliver(beaconA, rssi: -61)
        scanner.deliver(beaconA, rssi: -62)
        scanner.deliver(beaconB, rssi: -70)
        try await waitForTheRecordingToEnd()

        let csv = try XCTUnwrap(sut.recordings.first)
        let json = FingerprintRecorder.metadataURL(for: csv)
        XCTAssertEqual(json.lastPathComponent, csv.deletingPathExtension().lastPathComponent + ".json")
        XCTAssertEqual(sut.files(of: csv), [csv, json])

        let metadata = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: json)) as? [String: Any])
        XCTAssertEqual(metadata["schema_version"] as? Int, FingerprintRecordingMetadata.currentSchemaVersion)
        XCTAssertEqual(metadata["platform"] as? String, "iOS")
        XCTAssertEqual(metadata["node"] as? String, "9F3C1A02")
        XCTAssertEqual(metadata["model"] as? String, "iPhone15,2")
        XCTAssertEqual(metadata["session"] as? String, "S1")
        XCTAssertEqual(metadata["point"] as? String, "P3")
        XCTAssertEqual(metadata["x"] as? Double, 2)
        XCTAssertEqual(metadata["y"] as? Double, 4.5)
        XCTAssertEqual(metadata["csv_file"] as? String, csv.lastPathComponent)
        XCTAssertEqual(metadata["records_per_beacon"] as? [String: Int], ["1A2B3C4D": 2, "B7E2F910": 1])
        XCTAssertEqual(try XCTUnwrap(metadata["planned_duration_s"] as? Double), shortDuration, accuracy: 1e-6)

        let startedTMs = try XCTUnwrap(metadata["started_t_ms"] as? Int64)
        let endedTMs = try XCTUnwrap(metadata["ended_t_ms"] as? Int64)
        XCTAssertGreaterThanOrEqual(startedTMs, before)
        XCTAssertGreaterThanOrEqual(endedTMs - startedTMs, Int64(shortDuration * 1000))
        let startedTimestampMs = try XCTUnwrap(metadata["started_timestamp_ms"] as? Int64)
        let endedTimestampMs = try XCTUnwrap(metadata["ended_timestamp_ms"] as? Int64)
        XCTAssertGreaterThanOrEqual(endedTimestampMs, startedTimestampMs)
        XCTAssertNotNil(metadata["acceleration_convention"] as? String)
        XCTAssertEqual(metadata["acceleration_rate_hz"] as? Double, 50)
    }

    func test_metadata_copiesTheBeaconPositionsAsTheyWereWhenTheRecordingStarted() async throws {
        anchors = [
            Anchor(id: beaconB, label: "direita", position: Point2D(x: 4.25, y: 2.5)),
            Anchor(id: beaconA, label: "baixo", position: Point2D(x: 2.5, y: 0.75), height: 2.1),
        ]
        sut.start()
        sut.startRecording(label: label, duration: shortDuration)
        // Moved once the recording is under way: the recording keeps the position it started with.
        anchors[1] = Anchor(id: beaconA, label: "baixo", position: Point2D(x: 9, y: 9))
        try await waitForTheRecordingToEnd()

        let metadata = try metadataObject(of: XCTUnwrap(sut.recordings.first))
        let beacons = try XCTUnwrap(metadata["beacons"] as? [[String: Any]])
        XCTAssertEqual(beacons.map { $0["id"] as? String }, ["1A2B3C4D", "B7E2F910"], "sorted by ID")
        XCTAssertEqual(beacons[0]["label"] as? String, "baixo")
        XCTAssertEqual(beacons[0]["x"] as? Double, 2.5)
        XCTAssertEqual(beacons[0]["y"] as? Double, 0.75)
        XCTAssertEqual(beacons[0]["height_m"] as? Double, 2.1)
        XCTAssertEqual(beacons[1]["label"] as? String, "direita")
        XCTAssertEqual(beacons[1]["x"] as? Double, 4.25)
        XCTAssertTrue(beacons[1]["height_m"] is NSNull, "an unknown height is written as null, not left out")
    }

    func test_metadata_hasAnEmptyBeaconList_whenNoPositionWasEntered() async throws {
        sut.start()
        sut.startRecording(label: label, duration: shortDuration)
        try await waitForTheRecordingToEnd()

        let metadata = try metadataObject(of: XCTUnwrap(sut.recordings.first))
        XCTAssertEqual((metadata["beacons"] as? [Any])?.count, 0)
    }

    private func metadataObject(of csv: URL) throws -> [String: Any] {
        let data = try Data(contentsOf: FingerprintRecorder.metadataURL(for: csv))
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
    }

    // MARK: - Beacon layout

    func test_layoutCheck_reportsHeardBeaconsWithoutAPosition_andPositionedOnesNotHeard() {
        let beaconC = BeaconID(hex: "BEAC0003")!
        anchors = [
            Anchor(id: beaconA, label: "A", position: Point2D(x: 0, y: 0)),
            Anchor(id: beaconC, label: "C", position: Point2D(x: 1, y: 1)),
        ]
        scanner.deliver(beaconA, rssi: -60)
        scanner.deliver(beaconB, rssi: -70)

        let check = sut.layoutCheck

        XCTAssertEqual(check.withoutPosition, [beaconB])
        XCTAssertEqual(check.notHeard, [beaconC])
        XCTAssertFalse(check.isComplete)
    }

    func test_layoutCheck_isComplete_whenTheHeardAndPositionedBeaconsMatch() {
        anchors = [Anchor(id: beaconA, label: "A", position: Point2D(x: 0, y: 0))]
        scanner.deliver(beaconA, rssi: -60)

        XCTAssertTrue(sut.layoutCheck.isComplete)
    }

    /// The shape anchors were saved in before `height` existed.
    private struct AnchorWithoutHeight: Encodable {
        let id: BeaconID
        let label: String
        let position: Point2D
    }

    func test_anchorsSavedBeforeTheHeightExisted_stillLoad() throws {
        let saved = try JSONEncoder().encode([AnchorWithoutHeight(id: beaconA, label: "A", position: Point2D(x: 2.5, y: 0.75))])

        let loaded = try JSONDecoder().decode([Anchor].self, from: saved)

        XCTAssertEqual(loaded, [Anchor(id: beaconA, label: "A", position: Point2D(x: 2.5, y: 0.75), height: nil)])
    }

    func test_recordingWithoutMetadata_isTheOnlyFileOfItself() throws {
        // A CSV left behind by an app that was killed mid-recording.
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let orphan = directory.appending(path: "20261022-143005_S1_P3.csv")
        try Data((FingerprintRecord.csvHeader + "\n").utf8).write(to: orphan)

        let relaunched = makeRecorder()

        XCTAssertEqual(relaunched.recordings.map(\.lastPathComponent), [orphan.lastPathComponent])
        XCTAssertEqual(relaunched.files(of: orphan).map(\.lastPathComponent), [orphan.lastPathComponent])
    }

    func test_finishedRecording_summarisesWhatEachBeaconDelivered() async throws {
        sut.start()
        sut.startRecording(label: label, duration: shortDuration)
        scanner.deliver(beaconA, rssi: -61)
        scanner.deliver(beaconA, rssi: -62)
        scanner.deliver(beaconB, rssi: -70)
        XCTAssertEqual(sut.recordCount, 3)

        try await waitForTheRecordingToEnd()

        let summary = try XCTUnwrap(sut.lastRecording)
        XCTAssertEqual(summary.label, label)
        XCTAssertEqual(summary.file, sut.recordings.first)
        XCTAssertEqual(summary.recordsPerBeacon, [beaconA: 2, beaconB: 1])
        XCTAssertGreaterThanOrEqual(summary.duration, shortDuration)
        XCTAssertEqual(summary.ratePerSecond(of: beaconA), 2 / summary.duration, accuracy: 1e-9)
        XCTAssertEqual(sut.recordCount, 0, "the live counts belong to the next recording")
    }

    func test_summary_ratePerSecond() {
        let summary = FingerprintRecordingSummary(
            file: directory,
            label: label,
            duration: 60,
            recordsPerBeacon: [beaconA: 540]
        )

        XCTAssertEqual(summary.ratePerSecond(of: beaconA), 9.0)
        XCTAssertEqual(summary.ratePerSecond(of: beaconB), 0.0)
    }

    func test_cancelRecording_discardsTheFile() throws {
        sut.start()
        sut.startRecording(label: label, duration: 120)
        scanner.deliver(beaconA, rssi: -61)

        sut.cancelRecording()

        XCTAssertFalse(sut.isRecording)
        XCTAssertEqual(sut.recordCount, 0)
        XCTAssertNil(sut.lastRecording)
        XCTAssertTrue(sut.recordings.isEmpty)
        XCTAssertEqual(try recordedFiles(), [])
    }

    func test_cancelledRecording_doesNotFinishLater() async throws {
        sut.start()
        sut.startRecording(label: label, duration: shortDuration)
        sut.cancelRecording()

        try await Task.sleep(for: .seconds(shortDuration * 4))

        XCTAssertNil(sut.lastRecording)
        XCTAssertTrue(sut.recordings.isEmpty)
    }

    func test_stop_whileRecording_discardsIt() throws {
        sut.start()
        sut.startRecording(label: label, duration: 120)
        scanner.deliver(beaconA, rssi: -61)

        sut.stop()

        XCTAssertFalse(sut.isRecording)
        XCTAssertEqual(try recordedFiles(), [])
    }

    func test_previousRecordings_areListedOnTheNextLaunch() async throws {
        sut.start()
        sut.startRecording(label: label, duration: shortDuration)
        try await waitForTheRecordingToEnd()
        let recorded = try XCTUnwrap(sut.recordings.first)

        let relaunched = makeRecorder()

        XCTAssertEqual(relaunched.recordings.map(\.lastPathComponent), [recorded.lastPathComponent])
    }

    func test_deleteRecording_removesTheFile_andItsMetadata() async throws {
        sut.start()
        sut.startRecording(label: label, duration: shortDuration)
        try await waitForTheRecordingToEnd()
        let url = try XCTUnwrap(sut.recordings.first)

        sut.deleteRecording(url)

        XCTAssertTrue(sut.recordings.isEmpty)
        XCTAssertFalse(FileManager.default.fileExists(atPath: url.path()))
        XCTAssertEqual(try recordedFiles(), [])
    }
}
