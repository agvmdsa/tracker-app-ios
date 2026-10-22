import Foundation
import Observation
import os

/// Records every advertisement the scanner hears into a CSV file, labelled with where the phone
/// is, to train a fingerprinting model. Each recording lasts a set time and stops on its own, so
/// every point gets the same amount of data: one file per recording, one row per advertisement
/// (see `FingerprintRecord`), plus a JSON file with the recording's window and context
/// (`FingerprintRecordingMetadata`) once it ends.
@Observable
@MainActor
final class FingerprintRecorder {
    private static let logger = Logger(subsystem: "com.airtagclone.TrackerApp", category: "FingerprintRecorder")

    /// Inside Documents, so the files also show up in the Files app and in Finder.
    static var defaultDirectory: URL {
        URL.documentsDirectory.appending(path: "Fingerprints", directoryHint: .isDirectory)
    }

    struct ActiveRecording {
        let label: FingerprintLabel
        let startedAt: Date
        let endsAt: Date
        /// `startedAt` on the rows' monotonic clock.
        let startedMonotonicMs: Int64
        /// The beacons' positions when the recording started.
        let beacons: [Anchor]
    }

    /// The recording in progress; nil when not recording.
    private(set) var activeRecording: ActiveRecording?
    /// Rows written so far by the recording in progress.
    private(set) var recordsPerBeacon: [BeaconID: Int] = [:]
    /// What the last recording that ran to the end captured.
    private(set) var lastRecording: FingerprintRecordingSummary?
    /// Finished recordings, newest first.
    private(set) var recordings: [URL]
    private(set) var errorMessage: String?

    let node: BeaconID
    let model: String
    let systemVersion: String

    var isRecording: Bool { activeRecording != nil }
    var recordCount: Int { recordsPerBeacon.values.reduce(0, +) }

    private let scanner: any BeaconScanning
    private let accelerometer: any AccelerometerReading
    /// The beacons with a position (`AnchorStore.anchors` in the app), copied into each recording.
    private let anchors: @MainActor () -> [Anchor]
    private let directory: URL

    private var file: FileHandle?
    private var fileURL: URL?
    private var endTask: Task<Void, Never>?

    init(
        scanner: any BeaconScanning,
        accelerometer: any AccelerometerReading,
        anchors: @escaping @MainActor () -> [Anchor] = { [] },
        directory: URL = FingerprintRecorder.defaultDirectory,
        node: BeaconID = DeviceIdentity.beaconID,
        model: String = DeviceIdentity.model,
        systemVersion: String = DeviceIdentity.systemVersion
    ) {
        self.scanner = scanner
        self.accelerometer = accelerometer
        self.anchors = anchors
        self.directory = directory
        self.node = node
        self.model = model
        self.systemVersion = systemVersion
        recordings = Self.existingRecordings(in: directory)
    }

    /// Positioned beacons that aren't heard right now, and heard beacons without a position.
    var layoutCheck: BeaconLayoutCheck {
        BeaconLayoutCheck(anchors: anchors(), heard: scanner.sightings.keys)
    }

    /// Starts listening to the beacons and the accelerometer; nothing is written until
    /// `startRecording(label:duration:)`.
    func start() {
        scanner.onSighting = { [weak self] sighting in
            self?.record(sighting)
        }
        scanner.startScanning()
        accelerometer.start()
    }

    /// Stops listening. A recording still in progress is discarded, as with `cancelRecording()`.
    func stop() {
        cancelRecording()
        scanner.onSighting = nil
        scanner.stopScanning()
        accelerometer.stop()
    }

    /// Records every advertisement for `duration` seconds, then stops on its own.
    func startRecording(label: FingerprintLabel, duration: TimeInterval) {
        cancelRecording()
        errorMessage = nil
        let startedAt = Date()
        let url = directory.appending(path: Self.fileName(for: label, startedAt: startedAt))
        do {
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            try Data((FingerprintRecord.csvHeader + "\n").utf8).write(to: url)
            let handle = try FileHandle(forWritingTo: url)
            try handle.seekToEnd()
            file = handle
        } catch {
            Self.logger.error("could not create \(url.lastPathComponent, privacy: .public): \(error.localizedDescription, privacy: .public)")
            errorMessage = "Não foi possível criar o arquivo: \(error.localizedDescription)"
            return
        }
        fileURL = url
        activeRecording = ActiveRecording(
            label: label,
            startedAt: startedAt,
            endsAt: startedAt.addingTimeInterval(duration),
            startedMonotonicMs: MonotonicClock.nowMs(),
            beacons: anchors()
        )
        endTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(duration))
            guard !Task.isCancelled else { return }
            self?.finishRecording()
        }
    }

    /// Stops the recording in progress early and deletes its file: a recording that didn't last
    /// its full time would leave that point with less data than the others.
    func cancelRecording() {
        guard activeRecording != nil, let fileURL else { return }
        closeFile()
        do {
            try FileManager.default.removeItem(at: fileURL)
        } catch {
            Self.logger.error("could not delete \(fileURL.lastPathComponent, privacy: .public): \(error.localizedDescription, privacy: .public)")
        }
        activeRecording = nil
        recordsPerBeacon = [:]
    }

    /// Deletes a recording's CSV and its metadata.
    func deleteRecording(_ url: URL) {
        do {
            try FileManager.default.removeItem(at: url)
            recordings.removeAll { $0 == url }
        } catch {
            errorMessage = "Não foi possível apagar \(url.lastPathComponent): \(error.localizedDescription)"
            return
        }
        try? FileManager.default.removeItem(at: Self.metadataURL(for: url))
    }

    /// A recording's files, to share or export together: the CSV, then its metadata when it has
    /// some (recordings made before the metadata existed don't).
    func files(of recording: URL) -> [URL] {
        let metadata = Self.metadataURL(for: recording)
        return FileManager.default.fileExists(atPath: metadata.path()) ? [recording, metadata] : [recording]
    }

    static func metadataURL(for recording: URL) -> URL {
        recording.deletingPathExtension().appendingPathExtension("json")
    }

    private func finishRecording() {
        guard let activeRecording, let fileURL else { return }
        closeFile()
        let endedAt = Date()
        writeMetadata(for: activeRecording, csv: fileURL, endedAt: endedAt, endedMonotonicMs: MonotonicClock.nowMs())
        recordings.insert(fileURL, at: 0)
        lastRecording = FingerprintRecordingSummary(
            file: fileURL,
            label: activeRecording.label,
            duration: endedAt.timeIntervalSince(activeRecording.startedAt),
            recordsPerBeacon: recordsPerBeacon
        )
        self.activeRecording = nil
        recordsPerBeacon = [:]
    }

    /// The CSV is kept even if this fails: its rows are fine, it just lacks the window and context.
    private func writeMetadata(for recording: ActiveRecording, csv: URL, endedAt: Date, endedMonotonicMs: Int64) {
        let metadata = FingerprintRecordingMetadata(
            schemaVersion: FingerprintRecordingMetadata.currentSchemaVersion,
            platform: "iOS",
            node: node.value,
            model: model,
            systemVersion: systemVersion,
            session: recording.label.session,
            point: recording.label.point,
            x: recording.label.x,
            y: recording.label.y,
            csvFile: csv.lastPathComponent,
            startedTMs: recording.startedMonotonicMs,
            endedTMs: endedMonotonicMs,
            startedTimestampMs: Self.unixMs(recording.startedAt),
            endedTimestampMs: Self.unixMs(endedAt),
            plannedDurationS: recording.endsAt.timeIntervalSince(recording.startedAt),
            recordsPerBeacon: Dictionary(uniqueKeysWithValues: recordsPerBeacon.map { ($0.key.value, $0.value) }),
            scanAPI: "CoreBluetooth scanForPeripherals, service UUID filter, allow duplicates, foreground",
            accelerationConvention: "g, gravity included, device axes; lying still face up reads z = -1",
            accelerationRateHz: 1 / AccelerometerReader.updateInterval,
            beacons: recording.beacons
                .sorted { $0.id.value < $1.id.value }
                .map(FingerprintRecordingMetadata.Beacon.init)
        )
        let url = Self.metadataURL(for: csv)
        do {
            try metadata.jsonData().write(to: url)
        } catch {
            Self.logger.error("could not write \(url.lastPathComponent, privacy: .public): \(error.localizedDescription, privacy: .public)")
            errorMessage = "Gravação salva, mas sem os metadados: \(error.localizedDescription)"
        }
    }

    private static func unixMs(_ date: Date) -> Int64 {
        Int64((date.timeIntervalSince1970 * 1000).rounded())
    }

    private func closeFile() {
        endTask?.cancel()
        endTask = nil
        do {
            try file?.close()
        } catch {
            Self.logger.error("could not close the recording: \(error.localizedDescription, privacy: .public)")
        }
        file = nil
        fileURL = nil
    }

    private func record(_ sighting: BeaconSighting) {
        guard let activeRecording, let file else { return }
        let record = FingerprintRecord(
            monotonicMs: sighting.monotonicMs,
            seenAt: sighting.seenAt,
            node: node,
            model: model,
            systemVersion: systemVersion,
            beacon: sighting.beaconID,
            rssi: sighting.rssi,
            acceleration: accelerometer.latest,
            label: activeRecording.label
        )
        do {
            try file.write(contentsOf: Data((record.csvLine + "\n").utf8))
        } catch {
            Self.logger.error("write failed, discarding the recording: \(error.localizedDescription, privacy: .public)")
            cancelRecording()
            errorMessage = "Gravação interrompida e descartada: \(error.localizedDescription)"
            return
        }
        recordsPerBeacon[sighting.beaconID, default: 0] += 1
    }

    /// Starts with the date so the files sort chronologically, e.g. `20261022-143005_S1_P3.csv`.
    private static func fileName(for label: FingerprintLabel, startedAt date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyyMMdd-HHmmss"
        return "\(formatter.string(from: date))_\(label.session)_\(label.point).csv"
    }

    private static func existingRecordings(in directory: URL) -> [URL] {
        let files = (try? FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil)) ?? []
        return files
            .filter { $0.pathExtension == "csv" }
            .sorted { $0.lastPathComponent > $1.lastPathComponent }
    }
}

/// What a finished recording captured, like the ESP32 collector's `S` lines.
struct FingerprintRecordingSummary: Equatable {
    let file: URL
    let label: FingerprintLabel
    /// Seconds actually recorded.
    let duration: TimeInterval
    let recordsPerBeacon: [BeaconID: Int]

    var recordCount: Int { recordsPerBeacon.values.reduce(0, +) }

    /// Rows logged per second for `beacon`. Compared with how often the beacon advertises (10 times
    /// a second at the ESP32's default 100 ms interval), it shows how many of its advertisements
    /// iOS actually delivered.
    func ratePerSecond(of beacon: BeaconID) -> Double {
        duration > 0 ? Double(recordsPerBeacon[beacon] ?? 0) / duration : 0
    }
}
