import Foundation
import Observation
import os

@Observable
@MainActor
final class PositioningEngine {
    private static let logger = Logger(subsystem: "com.airtagclone.TrackerApp", category: "PositioningEngine")
    private static let refreshInterval: TimeInterval = 1.0
    /// How long an advertisement counts toward a beacon's range. A longer window is steadier but
    /// trails a walking phone (about half the window: 1.5 m at 1 m/s).
    private static let rssiWindowMs: Int64 = 3_000
    /// Room for 3 s of advertisements at up to 40 per second.
    private static let rssiWindowCapacity = 120

    private(set) var anchorRanges: [AnchorRange] = []
    private(set) var fix: ObserverFix?
    /// The RSSI heard from the anchors standing in their middle. It calibrates the model: it is read
    /// as the anchors' mean distance to their middle.
    var centerRSSI: Double = PathLossModel.defaultCenterRSSI
    /// The RSSI heard with the phone touching a beacon, read as `PathLossModel.touchDistanceMeters`.
    /// With the center's it fixes the path loss exponent too.
    var touchRSSI: Double = PathLossModel.defaultTouchRSSI
    /// How fast the phone can move between fixes; nil shows every fix as computed.
    var maxSpeedKmh: Double? = MotionLimit.defaultMaxSpeedKmh

    /// The model the ranges come from, for the anchors configured now. Fewer than two anchors have
    /// no middle, so the bench square's is used.
    var calibration: PathLossCalibration {
        PathLossModel.calibration(
            centerRSSI: centerRSSI,
            centerDistanceMeters: PathLossModel.centerDistanceMeters(of: anchorStore.anchors.map(\.position)) ?? 2.5 / 2.0.squareRoot(),
            touchRSSI: touchRSSI
        )
    }

    /// The strongest anchor's current median, to take as `touchRSSI` while touching it. Nil until an
    /// anchor is ranged.
    var currentTouchRSSI: Double? {
        anchorRanges.compactMap(\.rssi).max()
    }

    /// The mean of the anchors' current median RSSIs, to take as `centerRSSI` while standing in the
    /// middle. Nil until an anchor is ranged.
    var currentCenterRSSI: Double? {
        let rssis = anchorRanges.compactMap(\.rssi)
        guard !rssis.isEmpty else { return nil }
        return rssis.reduce(0, +) / Double(rssis.count)
    }

    private let scanner: any BeaconScanning
    private let anchorStore: AnchorStore
    private let observerID: BeaconID
    private let reporter: (any PositioningReporting)?

    private var rssiWindows: [BeaconID: RSSIWindow] = [:]
    /// The last position shown and when, where the next one is limited from.
    private var lastShown: (position: Point2D, atMs: Int64)?
    private var refreshTimer: Timer?

    init(
        scanner: any BeaconScanning,
        anchorStore: AnchorStore,
        observerID: BeaconID = DeviceIdentity.beaconID,
        reporter: (any PositioningReporting)? = nil
    ) {
        self.scanner = scanner
        self.anchorStore = anchorStore
        self.observerID = observerID
        self.reporter = reporter
    }

    func start() {
        rssiWindows = [:]
        lastShown = nil
        // Every advertisement, not just the latest per beacon: the range comes from their median.
        scanner.onSighting = { [weak self] sighting in
            self?.record(sighting)
        }
        scanner.startScanning()
        refreshTimer?.invalidate()
        refreshTimer = Timer.scheduledTimer(withTimeInterval: Self.refreshInterval, repeats: true) { [weak self] _ in
            self?.refresh()
        }
    }

    func stop() {
        scanner.onSighting = nil
        scanner.stopScanning()
        refreshTimer?.invalidate()
        refreshTimer = nil
    }

    /// `nowMs` is on `MonotonicClock`, the sightings' clock; tests pass their own.
    func refresh(nowMs now: Int64 = MonotonicClock.nowMs()) {
        let calibration = calibration
        var readings: [AnchorRange] = []
        var medianRSSI: [BeaconID: Double] = [:]
        var sampleStats: [BeaconID: (count: Int, stdDev: Double)] = [:]
        for anchor in anchorStore.anchors {
            // Drained every refresh, heard or not, so the scanner's buffer stays bounded.
            let samples = scanner.drainRecentRSSI(for: anchor.id)
            // An anchor not heard within the window has no range this time.
            guard let median = rssiWindows[anchor.id]?.median(atMs: now) else { continue }
            medianRSSI[anchor.id] = median
            sampleStats[anchor.id] = (samples.count, Self.standardDeviation(of: samples))
            let distance = calibration.distanceMeters(rssi: median)
            readings.append(AnchorRange(anchor: anchor, distanceMeters: distance, rssi: median))
        }
        anchorRanges = readings
        fix = Multilateration.solve(readings: readings).map { computed in
            let position = MotionLimit.limited(
                from: lastShown?.position,
                to: computed.position,
                maxSpeedKmh: maxSpeedKmh,
                elapsedSeconds: lastShown.map { Double(now - $0.atMs) / 1_000 } ?? 0
            )
            lastShown = (position, now)
            return ObserverFix(readings: computed.readings, position: position, uncertaintyMeters: computed.uncertaintyMeters)
        }

        guard let reporter, !readings.isEmpty else { return }
        let observerID = observerID
        let deviceModel = DeviceIdentity.model
        let reports = readings.map { reading in
            let stats = sampleStats[reading.anchor.id] ?? (0, 0)
            return PositioningReport(
                observerID: observerID.value,
                anchorID: reading.anchor.id.value,
                rssi: Int((medianRSSI[reading.anchor.id] ?? 0).rounded()),
                distanceMeters: reading.distanceMeters,
                seenAt: Date(),
                sampleCount: stats.count,
                rssiStdDev: stats.stdDev,
                deviceModel: deviceModel
            )
        }
        Task {
            do {
                try await reporter.send(reports)
            } catch {
                Self.logger.error("failed to report sightings: \(error.localizedDescription, privacy: .public)")
            }
        }

        guard let fix else { return }
        let fixReport = ObserverFixReport(
            observerID: observerID.value,
            x: fix.position.x,
            y: fix.position.y,
            uncertaintyMeters: fix.uncertaintyMeters,
            recordedAt: Date(),
            anchorCount: readings.count
        )
        Task {
            do {
                try await reporter.send(fixReport)
            } catch {
                Self.logger.error("failed to report fix: \(error.localizedDescription, privacy: .public)")
            }
        }
    }

    /// Sample standard deviation (n-1 denominator). 0 when there's fewer than 2 samples to
    /// compare — "no variance information yet" rather than a misleading 0.0 "perfectly stable".
    private static func standardDeviation(of samples: [Int]) -> Double {
        guard samples.count > 1 else { return 0 }
        let mean = Double(samples.reduce(0, +)) / Double(samples.count)
        let variance = samples.reduce(0.0) { $0 + pow(Double($1) - mean, 2) } / Double(samples.count - 1)
        return variance.squareRoot()
    }

    /// Only the beacons with a position are kept: the others are not ranged, and this keeps the
    /// memory bounded by the number of anchors however many devices pass by.
    private func record(_ sighting: BeaconSighting) {
        guard anchorStore.anchor(id: sighting.beaconID) != nil else { return }
        rssiWindows[sighting.beaconID, default: RSSIWindow(durationMs: Self.rssiWindowMs, capacity: Self.rssiWindowCapacity)]
            .add(rssi: sighting.rssi, atMs: sighting.monotonicMs)
    }
}
