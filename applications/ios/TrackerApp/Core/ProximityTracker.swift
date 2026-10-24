import Foundation
import Observation
import os

/// Glues a `HandshakeChannel` to a `UWBManager`: finds a nearby phone running this app, swaps
/// NearbyInteraction discovery tokens with it, then ranges it. When the peer ends its session the
/// handshake starts over, so the pairing recovers on its own.
@Observable
@MainActor
final class ProximityTracker {
    private static let logger = Logger(subsystem: "com.airtagclone.TrackerApp", category: "ProximityTracker")
    private static let maxLogs = 50

    private(set) var isRunning = false
    private(set) var logs: [TrackingLogEntry] = []

    private let uwb: any UWBManaging
    private let handshake: any HandshakeChannel
    /// Set once the peer's token reached `uwb`; cleared whenever the handshake starts over.
    private var hasPeer = false
    private var ownError: String?
    private var isWired = false

    nonisolated init(uwb: any UWBManaging = UWBManager(), handshake: any HandshakeChannel = BLEHandshake()) {
        self.uwb = uwb
        self.handshake = handshake
    }

    var isSupported: Bool { uwb.isSupported }
    var handshakeState: HandshakeState { handshake.state }

    var rangingState: RangingState {
        guard isRunning else { return .idle }
        if let errorMessage { return .failed(errorMessage) }
        return hasPeer ? .ranging : .waitingForPeer
    }

    var errorMessage: String? {
        if let ownError { return ownError }
        if case .failed(let message) = handshake.state { return message }
        return uwb.errorMessage
    }

    var distance: Float? { uwb.distance }
    var azimuth: Float? { uwb.azimuth }
    var elevation: Float? { uwb.elevation }
    var isSignalLost: Bool { uwb.isSignalLost }
    var supportsDirectionMeasurement: Bool { uwb.supportsDirectionMeasurement }
    var isCameraAssisted: Bool { uwb.isCameraAssisted }

    // MARK: - Lifecycle

    func start() {
        wireCallbacksIfNeeded()
        ownError = nil
        isRunning = true
        beginHandshake()
    }

    func stop() {
        isRunning = false
        hasPeer = false
        ownError = nil
        handshake.stop()
        uwb.stop()
    }

    func pause() {
        guard isRunning else { return }
        if hasPeer {
            uwb.pause()
        } else {
            handshake.stop()
        }
    }

    func resume() {
        guard isRunning else { return }
        if hasPeer {
            uwb.resume()
        } else {
            beginHandshake()
        }
    }

    // MARK: - Flow

    private func wireCallbacksIfNeeded() {
        guard !isWired else { return }
        isWired = true
        handshake.onStateChange = { [weak self] state in
            self?.log("Handshake: \(state)")
        }
        handshake.onPeerToken = { [weak self] token in
            self?.peerTokenReceived(token)
        }
        uwb.onPeerEnded = { [weak self] in
            self?.peerEnded()
        }
        uwb.onLog = { [weak self] message in
            self?.log(message)
        }
    }

    private func beginHandshake() {
        hasPeer = false
        do {
            let token = try uwb.prepareSession()
            handshake.start(localToken: token)
        } catch {
            log("Could not start: \(error.localizedDescription)")
            ownError = error.localizedDescription
            isRunning = false
        }
    }

    private func peerTokenReceived(_ token: Data) {
        guard isRunning else { return }
        log("Received peer token (\(token.count) bytes).")
        do {
            try uwb.startSession(withPeerToken: token)
            hasPeer = true
        } catch {
            log("Could not start ranging: \(error.localizedDescription)")
            ownError = error.localizedDescription
        }
    }

    private func peerEnded() {
        guard isRunning else { return }
        log("Looking for the peer again.")
        beginHandshake()
    }

    private func log(_ message: String) {
        Self.logger.info("\(message, privacy: .public)")
        logs.append(TrackingLogEntry(message: message))
        if logs.count > Self.maxLogs {
            logs.removeFirst(logs.count - Self.maxLogs)
        }
    }
}
