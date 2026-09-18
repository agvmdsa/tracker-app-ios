import SwiftUI
import NearbyInteraction
import simd

struct TrackingView: View {
    let device: TrackerDevice

    @EnvironmentObject private var connectionManager: ConnectionManager
    @EnvironmentObject private var uwbManager: UWBManager
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
            if uwbManager.isSignalLost {
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
            } else {
                Image(systemName: "location.slash")
                    .font(.system(size: 64))
                    .foregroundStyle(.secondary)
            }
        }
    }

    private func registerCallbacks() {
        connectionManager.onDiscoveryTokenReceived = { wrapper in
            guard wrapper.peerId == device.displayName,
                  let peerToken = try? NSKeyedUnarchiver.unarchivedObject(ofClass: NIDiscoveryToken.self, from: wrapper.tokenData)
            else { return }
            uwbManager.startSession(withPeerToken: peerToken)
        }

        connectionManager.onPayloadReceived = { payload in
            if payload.actionType == "ping" {
                receivedPingAlert = true
            }
        }

        // The MCSession may already be connected by the time this view appears
        // (e.g. re-entering a previously tracked device).
        if isConnected {
            beginTokenExchangeIfNeeded()
        }
    }

    /// Only exchanges the NearbyInteraction discovery token once MultipeerConnectivity has
    /// actually finished connecting — sending it earlier throws "peer not connected".
    private func beginTokenExchangeIfNeeded() {
        guard !didStartTokenExchange else { return }
        guard let myToken = uwbManager.prepareSession() else { return }
        didStartTokenExchange = true
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
