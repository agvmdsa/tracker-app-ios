import SwiftUI
import NearbyInteraction
import simd
import os

struct TrackingView: View {
    private static let logger = Logger(subsystem: "com.airtagclone.TrackerApp", category: "TrackingView")

    let device: TrackerDevice

    @Environment(\.connectionManager) private var connectionManager: any ConnectionManaging
    @Environment(\.uwbManager) private var uwbManager: any UWBManaging
    @State private var receivedPingAlert = false
    @State private var didStartTokenExchange = false

    /// The device's live connection state, kept in sync with `ConnectionManager.discoveredDevices`
    /// rather than the static snapshot passed in from `MainView`'s list.
    private var liveState: ConnectionState {
        connectionManager.discoveredDevices.first { $0.id == device.id }?.state ?? .disconnected
    }

    private var isConnected: Bool {
        liveState == .connected || liveState == .tracking
    }

    var body: some View {
        VStack(spacing: 32) {
            if isConnected {
                distanceLabel
                directionIndicator
                Button("Send Ping") {
                    sendPing()
                }
                .buttonStyle(.borderedProminent)
            } else {
                ProgressView("Connecting to \(device.displayName)…")
                    .padding(.top, 40)
            }
        }
        .padding()
        .navigationTitle(device.displayName)
        .onAppear(perform: registerCallbacks)
        .onDisappear(perform: stopTracking)
        .onChange(of: isConnected) { connected in
            Self.logger.info("isConnected changed to \(connected, privacy: .public)")
            if connected { beginTokenExchangeIfNeeded() }
        }
        .alert("Ping received", isPresented: $receivedPingAlert) {
            Button("OK", role: .cancel) {}
        }
    }

    private var distanceLabel: some View {
        Group {
            if let distance = uwbManager.distance {
                Text(String(format: "%.2f m", distance))
                    .font(.system(size: 48, weight: .bold, design: .rounded))
            } else {
                Text("Searching for signal…")
                    .font(.title3)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var directionIndicator: some View {
        Group {
            if !uwbManager.supportsDirectionMeasurement {
                VStack(spacing: 8) {
                    Image(systemName: "location.viewfinder")
                        .font(.system(size: 64))
                        .foregroundStyle(.secondary)
                    Text("This iPhone can only measure distance, not direction")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                }
            } else if uwbManager.isSignalLost {
                VStack(spacing: 8) {
                    Image(systemName: "questionmark.circle")
                        .font(.system(size: 64))
                        .foregroundStyle(.secondary)
                    Text("Searching for signal…")
                        .foregroundStyle(.secondary)
                }
            } else if let direction = uwbManager.direction {
                Image(systemName: "location.north.fill")
                    .font(.system(size: 64))
                    .rotationEffect(.radians(Double(atan2(direction.x, direction.z))))
                    .animation(.easeInOut, value: direction)
            } else if let horizontalAngle = uwbManager.horizontalAngle {
                // Camera-assisted-only devices (iPhone 14 Pro and later) never populate
                // `direction`; the angle instead arrives as this single radian value.
                Image(systemName: "location.north.fill")
                    .font(.system(size: 64))
                    .rotationEffect(.radians(Double(horizontalAngle)))
                    .animation(.easeInOut, value: horizontalAngle)
            } else {
                Image(systemName: "location.slash")
                    .font(.system(size: 64))
                    .foregroundStyle(.secondary)
            }
        }
    }

    private func registerCallbacks() {
        Self.logger.info("registerCallbacks() for \(device.displayName, privacy: .public), isConnected=\(isConnected, privacy: .public)")
        connectionManager.onDiscoveryTokenReceived = { wrapper in
            handleIncomingToken(wrapper)
        }

        connectionManager.onPayloadReceived = { payload in
            if payload.actionType == "ping" {
                receivedPingAlert = true
            }
        }

        // Create our own local NISession (via beginTokenExchangeIfNeeded) *before* processing any
        // pending token — `UWBManager.startSession(withPeerToken:)` silently no-ops if our own
        // session doesn't exist yet, which was quietly dropping the peer's buffered token.
        // The MCSession may already be connected by the time this view appears (e.g. re-entering
        // a previously tracked device).
        if isConnected {
            beginTokenExchangeIfNeeded()
        }

        // The peer's token may have arrived before this screen registered a listener for it
        // (e.g. they opened their tracking screen first and sent immediately) — pick it up now
        // instead of leaving it stuck in `ConnectionManager`'s buffer forever.
        if let pending = connectionManager.consumePendingDiscoveryToken(fromPeerNamed: device.displayName) {
            Self.logger.info("registerCallbacks: found a pending discovery token from \(device.displayName, privacy: .public)")
            handleIncomingToken(pending)
        }
    }

    private func handleIncomingToken(_ wrapper: DiscoveryTokenWrapper) {
        Self.logger.info("handleIncomingToken from wrapper.peerId=\(wrapper.peerId, privacy: .public) (expecting \(device.displayName, privacy: .public))")
        guard wrapper.peerId == device.displayName else {
            Self.logger.info("handleIncomingToken: peerId mismatch, ignoring")
            return
        }
        guard let peerToken = try? NSKeyedUnarchiver.unarchivedObject(ofClass: NIDiscoveryToken.self, from: wrapper.tokenData) else {
            Self.logger.info("handleIncomingToken: failed to unarchive NIDiscoveryToken")
            return
        }
        Self.logger.info("handleIncomingToken: starting UWB session with peer token")
        uwbManager.startSession(withPeerToken: peerToken)
    }

    /// Only exchanges the NearbyInteraction discovery token once MultipeerConnectivity has
    /// actually finished connecting — sending it earlier throws "peer not connected".
    private func beginTokenExchangeIfNeeded() {
        guard !didStartTokenExchange else {
            Self.logger.info("beginTokenExchangeIfNeeded: already started, skipping")
            return
        }
        guard let myToken = uwbManager.prepareSession() else {
            Self.logger.info("beginTokenExchangeIfNeeded: prepareSession() returned nil token — NearbyInteraction unavailable (no U1 chip, or permission denied)")
            return
        }
        didStartTokenExchange = true
        Self.logger.info("beginTokenExchangeIfNeeded: sending our discovery token to \(device.displayName, privacy: .public)")
        connectionManager.sendDiscoveryToken(myToken, toDeviceWithID: device.id)
    }

    private func stopTracking() {
        uwbManager.stop()
        didStartTokenExchange = false
        connectionManager.onDiscoveryTokenReceived = nil
        connectionManager.onPayloadReceived = nil
    }

    private func sendPing() {
        let payload = PayloadMessage(messageId: UUID(), timestamp: Date(), actionType: "ping", data: Data())
        connectionManager.sendPayload(payload, toDeviceWithID: device.id)
    }
}
