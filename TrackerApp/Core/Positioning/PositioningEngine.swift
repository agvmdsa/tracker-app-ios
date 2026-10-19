import Foundation
import Observation
import os

@Observable
@MainActor
final class PositioningEngine {
    private static let logger = Logger(subsystem: "com.airtagclone.TrackerApp", category: "PositioningEngine")
    private static let refreshInterval: TimeInterval = 1.0

    private(set) var anchorRanges: [AnchorRange] = []
    private(set) var fix: ObserverFix?

    private let scanner: any BeaconScanning
    private let anchorStore: AnchorStore
    private let observerID: BeaconID
    private let reporter: (any PositioningReporting)?

    private var smoothedRSSI: [BeaconID: Double] = [:]
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
        scanner.startScanning()
        refreshTimer?.invalidate()
        refreshTimer = Timer.scheduledTimer(withTimeInterval: Self.refreshInterval, repeats: true) { [weak self] _ in
            self?.refresh()
        }
    }

    func stop() {
        scanner.stopScanning()
        refreshTimer?.invalidate()
        refreshTimer = nil
    }

    func refresh() {
        var readings: [AnchorRange] = []
        for anchor in anchorStore.anchors {
            guard let sighting = scanner.sightings[anchor.id] else { continue }
            let smoothed = PathLossModel.smooth(previous: smoothedRSSI[anchor.id], reading: sighting.rssi)
            smoothedRSSI[anchor.id] = smoothed
            readings.append(AnchorRange(anchor: anchor, distanceMeters: PathLossModel.distanceMeters(rssi: smoothed)))
        }
        anchorRanges = readings
        fix = Multilateration.solve(readings: readings)

        guard let reporter, !readings.isEmpty else { return }
        let observerID = observerID
        let reports = readings.map { reading in
            PositioningReport(
                observerID: observerID.value,
                anchorID: reading.anchor.id.value,
                rssi: scanner.sightings[reading.anchor.id]?.rssi ?? 0,
                distanceMeters: reading.distanceMeters,
                seenAt: Date()
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
            recordedAt: Date()
        )
        Task {
            do {
                try await reporter.send(fixReport)
            } catch {
                Self.logger.error("failed to report fix: \(error.localizedDescription, privacy: .public)")
            }
        }
    }
}
