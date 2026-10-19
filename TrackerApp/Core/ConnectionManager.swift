import Foundation
import MultipeerConnectivity
import NearbyInteraction
import Observation
import os

/// MultipeerConnectivity wrapper: discovers nearby TrackerApp peers, exchanges NearbyInteraction
/// discovery tokens (P2P Contract 1), and sends/receives custom data payloads (P2P Contract 2).
/// See specs/001-ios-tracker-app/contracts/p2p-messages.md and research.md.
@Observable
@MainActor
final class ConnectionManager: NSObject {
    static let serviceType = "tracker-app"
    private static let logger = Logger(subsystem: "com.airtagclone.TrackerApp", category: "ConnectionManager")

    private(set) var discoveredDevices: [TrackerDevice] = []
    var transferError: String?

    /// Called on the main thread when a peer's NearbyInteraction discovery token arrives.
    var onDiscoveryTokenReceived: ((DiscoveryTokenWrapper) -> Void)?
    /// Called on the main thread when a custom data payload arrives.
    var onPayloadReceived: ((PayloadMessage) -> Void)?
    /// Discovery tokens that arrived before anything was listening via `onDiscoveryTokenReceived`,
    /// keyed by sender displayName. See `consumePendingDiscoveryToken`.
    private var pendingDiscoveryTokens: [String: DiscoveryTokenWrapper] = [:]

    private let myPeerID: MCPeerID
    private let session: MCSession
    private var advertiser: MCNearbyServiceAdvertiser?
    private var browser: MCNearbyServiceBrowser?

    private var peerIDByDeviceID: [UUID: MCPeerID] = [:]
    private var deviceIDByPeerID: [MCPeerID: UUID] = [:]
    /// Device IDs with a send in flight; used to detect interrupted transfers (Edge Case: Data Transfer Interruption).
    private var pendingSendDeviceIDs: Set<UUID> = []
    /// Random per-launch identifier advertised alongside our peer, used to break the mutual-invite
    /// race: since every peer both advertises and browses, two devices can discover each other at
    /// the same instant and both call `invitePeer` simultaneously, which can leave the session stuck.
    /// Only the side with the lexicographically smaller `sessionID` invites; the other just accepts.
    private let sessionID = UUID().uuidString
    /// Pending "still not connected" diagnostics, keyed by device ID. See `scheduleConnectionTimeoutCheck`.
    private var connectionTimeoutTasks: [UUID: DispatchWorkItem] = [:]
    /// The peer's advertised sessionID, remembered so we can re-run the tie-break check on retry
    /// without needing a fresh `foundPeer` callback.
    private var theirSessionIDByPeerID: [MCPeerID: String] = [:]
    /// Pending re-invite attempts after a failed connection, keyed by device ID. AWDL/ICE
    /// negotiation is known to be flaky — a single failed attempt doesn't mean the peer is
    /// unreachable, so we retry instead of waiting passively for the next `foundPeer` callback.
    private var retryWorkItems: [UUID: DispatchWorkItem] = [:]
    private var reconciliationTimer: Timer?

    nonisolated init(displayName: String = DeviceIdentity.displayName) {
        myPeerID = MCPeerID(displayName: displayName)
        // DIAGNOSTIC: `.required` and `.optional` both failed identically on real hardware
        // (session reaches `.connecting` then drops to `.notConnected`, "Not in connected
        // state, so giving up..." on every channel). Testing `.none` next to conclusively rule
        // encryption in or out as the cause before looking elsewhere (Wi-Fi/AWDL, hotspot, etc).
        session = MCSession(peer: myPeerID, securityIdentity: nil, encryptionPreference: .none)
        super.init()
        session.delegate = self
    }

    // MARK: - Lifecycle (T024a: paused on background, resumed on foreground)

    func start() {
        guard advertiser == nil, browser == nil else { return }
        Self.logger.info("start() — myPeerID=\(self.myPeerID.displayName, privacy: .public) sessionID=\(self.sessionID, privacy: .public)")

        let advertiser = MCNearbyServiceAdvertiser(peer: myPeerID, discoveryInfo: ["sessionID": sessionID], serviceType: Self.serviceType)
        advertiser.delegate = self
        advertiser.startAdvertisingPeer()
        self.advertiser = advertiser

        let browser = MCNearbyServiceBrowser(peer: myPeerID, serviceType: Self.serviceType)
        browser.delegate = self
        browser.startBrowsingForPeers()
        self.browser = browser

        // Safety net: `MCSession`'s delegate callbacks have proven unreliable in practice for
        // keeping our own `discoveredDevices` list in sync — the live session repeatedly reaches
        // and stays in `.connected` (proven by stable heartbeats and successful payload sends)
        // while our event-driven bookkeeping sometimes shows a stale "Discovered"/"Connecting"
        // state. Reconcile against `session.connectedPeers` — Apple's own ground truth — every
        // second so the UI can never drift for more than that long.
        reconciliationTimer?.invalidate()
        reconciliationTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
            self?.reconcileConnectionStates()
        }
    }

    func isConnected(deviceID: UUID) -> Bool {
        guard let peerID = peerIDByDeviceID[deviceID] else { return false }
        return session.connectedPeers.contains(peerID)
    }

    func stop() {
        advertiser?.stopAdvertisingPeer()
        advertiser = nil
        browser?.stopBrowsingForPeers()
        browser = nil
        session.disconnect()
        reconciliationTimer?.invalidate()
        reconciliationTimer = nil
        pendingSendDeviceIDs.removeAll()
        connectionTimeoutTasks.values.forEach { $0.cancel() }
        connectionTimeoutTasks.removeAll()
        retryWorkItems.values.forEach { $0.cancel() }
        retryWorkItems.removeAll()
    }

    func peerID(forDeviceID deviceID: UUID) -> MCPeerID? {
        peerIDByDeviceID[deviceID]
    }

    // MARK: - Token exchange (P2P Contract 1)

    func sendDiscoveryToken(_ token: NIDiscoveryToken, toDeviceWithID deviceID: UUID) {
        guard let peerID = peerIDByDeviceID[deviceID], session.connectedPeers.contains(peerID) else {
            transferError = "Cannot start tracking: device is not connected yet."
            return
        }
        do {
            let tokenData = try NSKeyedArchiver.archivedData(withRootObject: token, requiringSecureCoding: true)
            let message = TokenWireMessage(type: "NIDiscoveryToken", payload: tokenData.base64EncodedString())
            try send(message, to: peerID, deviceID: deviceID)
        } catch {
            transferError = "Failed to send discovery token: \(error.localizedDescription)"
        }
    }

    // MARK: - Payload transfer (P2P Contract 2)

    func sendPayload(_ payload: PayloadMessage, toDeviceWithID deviceID: UUID) {
        guard let peerID = peerIDByDeviceID[deviceID], session.connectedPeers.contains(peerID) else {
            transferError = "Cannot send payload: device is no longer connected."
            return
        }
        do {
            let actionPayload = ActionPayload(actionType: payload.actionType, data: payload.data.base64EncodedString())
            let message = ActionWireMessage(type: "CustomAction", payload: actionPayload)
            try send(message, to: peerID, deviceID: deviceID)
        } catch {
            transferError = "Payload transfer interrupted: \(error.localizedDescription)"
        }
    }

    private func send(_ message: some Encodable, to peerID: MCPeerID, deviceID: UUID) throws {
        pendingSendDeviceIDs.insert(deviceID)
        defer { pendingSendDeviceIDs.remove(deviceID) }
        let data = try JSONEncoder().encode(message)
        try session.send(data, toPeers: [peerID], with: .reliable)
    }

    // MARK: - Incoming data

    private func handleReceivedData(_ data: Data, from peerID: MCPeerID) {
        guard let probe = try? JSONDecoder().decode(TypeProbe.self, from: data) else {
            Self.logger.info("handleReceivedData: could not decode TypeProbe from \(peerID.displayName, privacy: .public)")
            return
        }

        switch probe.type {
        case "NIDiscoveryToken":
            guard let message = try? JSONDecoder().decode(TokenWireMessage.self, from: data),
                  let tokenData = Data(base64Encoded: message.payload) else {
                Self.logger.info("handleReceivedData: failed to decode NIDiscoveryToken payload from \(peerID.displayName, privacy: .public)")
                return
            }
            let wrapper = DiscoveryTokenWrapper(tokenData: tokenData, peerId: peerID.displayName)
            Self.logger.info("handleReceivedData: got NIDiscoveryToken from \(peerID.displayName, privacy: .public)")
            DispatchQueue.main.async { [weak self] in
                guard let self else { return }
                // `TrackingView` only wires up `onDiscoveryTokenReceived` once its screen is open.
                // If the peer's token arrives before that (e.g. they opened their tracking screen
                // first and sent immediately), it must not be silently dropped — MultipeerConnectivity
                // doesn't redeliver unhandled data. Buffer it so `consumePendingDiscoveryToken` can
                // pick it up the moment the view registers, in addition to live delivery.
                self.pendingDiscoveryTokens[peerID.displayName] = wrapper
                self.onDiscoveryTokenReceived?(wrapper)
            }

        case "CustomAction":
            guard let message = try? JSONDecoder().decode(ActionWireMessage.self, from: data),
                  let decodedData = Data(base64Encoded: message.payload.data) else {
                Self.logger.info("handleReceivedData: failed to decode CustomAction payload from \(peerID.displayName, privacy: .public)")
                return
            }
            let payload = PayloadMessage(
                messageId: UUID(),
                timestamp: Date(),
                actionType: message.payload.actionType,
                data: decodedData
            )
            DispatchQueue.main.async { [weak self] in
                self?.onPayloadReceived?(payload)
            }

        default:
            break
        }
    }

    /// Returns and clears any discovery token that arrived from this peer before something was
    /// listening for it via `onDiscoveryTokenReceived`. Call this right after registering that
    /// closure so an already-arrived token isn't lost.
    func consumePendingDiscoveryToken(fromPeerNamed peerName: String) -> DiscoveryTokenWrapper? {
        pendingDiscoveryTokens.removeValue(forKey: peerName)
    }

    // MARK: - Device list bookkeeping

    /// MultipeerConnectivity can hand us a *different* `MCPeerID` instance for the same remote
    /// peer depending on the callback (e.g. one reconstructed from raw bytes during the session
    /// handshake vs. the one obtained via Bonjour in `foundPeer`). If `MCPeerID`'s `hash` isn't
    /// perfectly aligned with its `==` across that reconstruction, a plain `[MCPeerID: UUID]`
    /// dictionary lookup can silently miss and mint a second, never-updated entry — which is
    /// exactly what caused the device list to get stuck on "Discovered" even though the real
    /// `MCSession` had connected (`session.connectedPeers.contains(peerID)` still worked fine,
    /// since `Array.contains` only relies on `==`, not hashing). Falling back to matching by
    /// `displayName` — stable and unique per install via `DeviceIdentity` — avoids depending on
    /// `MCPeerID` hashing being consistent across instances.
    private func deviceID(for peerID: MCPeerID) -> UUID {
        if let existing = deviceIDByPeerID[peerID] {
            peerIDByDeviceID[existing] = peerID
            return existing
        }
        if let matchedID = deviceIDByPeerID.first(where: { $0.key.displayName == peerID.displayName })?.value {
            deviceIDByPeerID[peerID] = matchedID
            peerIDByDeviceID[matchedID] = peerID
            return matchedID
        }
        let newID = UUID()
        deviceIDByPeerID[peerID] = newID
        peerIDByDeviceID[newID] = peerID
        return newID
    }

    private func upsertDevice(id: UUID, name: String, state: ConnectionState) {
        if let index = discoveredDevices.firstIndex(where: { $0.id == id }) {
            discoveredDevices[index].displayName = name
            discoveredDevices[index].state = state
        } else {
            discoveredDevices.append(TrackerDevice(id: id, displayName: name, distance: nil, direction: nil, state: state))
        }

        switch state {
        case .discovered, .connecting:
            scheduleConnectionTimeoutCheck(deviceID: id, peerName: name)
        case .connected, .tracking, .disconnected:
            connectionTimeoutTasks[id]?.cancel()
            connectionTimeoutTasks[id] = nil
        }
        if state == .connected || state == .tracking {
            retryWorkItems[id]?.cancel()
            retryWorkItems[id] = nil
        }
    }

    /// Safety net run every second from `start()`. Corrects any device whose displayed state
    /// disagrees with `session.connectedPeers` — Apple's own live truth — regardless of whatever
    /// delegate-callback ordering issue caused the drift.
    private func reconcileConnectionStates() {
        let connected = session.connectedPeers
        Self.logger.info("reconcile tick: connectedPeers=[\(connected.map(\.displayName).joined(separator: ","), privacy: .public)] list=[\(self.discoveredDevices.map { "\($0.displayName):\($0.state)" }.joined(separator: ","), privacy: .public)]")
        for peerID in connected {
            let id = deviceID(for: peerID)
            guard let index = discoveredDevices.firstIndex(where: { $0.id == id }) else {
                // No entry matches this connected peer at all (e.g. it raced with a `lostPeer`
                // removal) — insert it as connected instead of silently doing nothing.
                Self.logger.info("reconcile: no entry for connected peer \(peerID.displayName, privacy: .public) — inserting")
                upsertDevice(id: id, name: peerID.displayName, state: .connected)
                continue
            }
            let state = discoveredDevices[index].state
            guard state != .connected, state != .tracking else { continue }
            Self.logger.info("reconcile: forcing \(peerID.displayName, privacy: .public) from \(String(describing: state), privacy: .public) to .connected")
            upsertDevice(id: id, name: peerID.displayName, state: .connected)
        }
    }

    /// Only the side with the lexicographically smaller `sessionID` invites — avoids a mutual
    /// simultaneous invite that can otherwise leave both sides stuck un-connected.
    private func shouldInvite(theirSessionID: String?) -> Bool {
        guard let theirSessionID else { return true }
        return theirSessionID > sessionID
    }

    /// AWDL/ICE negotiation is known to be flaky on real hardware — a single failed invite
    /// doesn't mean the peer is unreachable. Retry rather than waiting passively for the next
    /// `foundPeer` callback, which can take a while since Bonjour re-announcements aren't frequent.
    private func scheduleRetryInvite(peerID: MCPeerID, deviceID: UUID) {
        retryWorkItems[deviceID]?.cancel()
        let workItem = DispatchWorkItem { [weak self] in
            guard let self, let browser = self.browser else { return }
            guard self.discoveredDevices.contains(where: { $0.id == deviceID }) else { return }
            guard !self.session.connectedPeers.contains(peerID) else { return }
            Self.logger.info("Retrying invite to \(peerID.displayName, privacy: .public) after disconnect")
            browser.invitePeer(peerID, to: self.session, withContext: nil, timeout: 10)
        }
        retryWorkItems[deviceID] = workItem
        DispatchQueue.main.asyncAfter(deadline: .now() + 2, execute: workItem)
    }

    /// MCSession gives no error callback for a stalled invite — it either reaches `.connected` or
    /// silently stays put. If a peer hasn't connected ~12s after being found/invited, surface an
    /// actionable hint instead of leaving the user staring at "Discovered"/"Connecting" forever.
    private func scheduleConnectionTimeoutCheck(deviceID: UUID, peerName: String) {
        connectionTimeoutTasks[deviceID]?.cancel()
        let workItem = DispatchWorkItem { [weak self] in
            guard let self else { return }
            guard let device = self.discoveredDevices.first(where: { $0.id == deviceID }),
                  device.state == .discovered || device.state == .connecting else { return }
            self.transferError = "Couldn't finish connecting to \(peerName). On both iPhones, check Settings > Privacy & Security > Local Network is ON for TrackerApp, Wi-Fi is turned on, and the app is open in the foreground."
        }
        connectionTimeoutTasks[deviceID] = workItem
        DispatchQueue.main.asyncAfter(deadline: .now() + 12, execute: workItem)
    }
}

// MARK: - Wire envelope (see contracts/p2p-messages.md)

private struct TypeProbe: Codable {
    let type: String
}

private struct TokenWireMessage: Codable {
    let type: String
    let payload: String
}

private struct ActionPayload: Codable {
    let actionType: String
    let data: String
}

private struct ActionWireMessage: Codable {
    let type: String
    let payload: ActionPayload
}

// MARK: - MCSessionDelegate

extension ConnectionManager: MCSessionDelegate {
    func session(_ session: MCSession, peer peerID: MCPeerID, didChange state: MCSessionState) {
        Self.logger.info("session didChange: peer=\(peerID.displayName, privacy: .public) newState=\(String(describing: state), privacy: .public)")
        // `deviceID(for:)` mutates shared dictionaries and MultipeerConnectivity delegate methods
        // are not guaranteed to run on the main thread (or even the same thread as each other) —
        // computing it here, outside the dispatch, was a data race with `browser(_:foundPeer:)`
        // doing the same thing concurrently. Do it inside the `main.async` block instead so all
        // bookkeeping is confined to one thread.
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            let id = self.deviceID(for: peerID)
            switch state {
            case .connecting:
                self.upsertDevice(id: id, name: peerID.displayName, state: .connecting)
            case .connected:
                self.upsertDevice(id: id, name: peerID.displayName, state: .connected)
            case .notConnected:
                self.upsertDevice(id: id, name: peerID.displayName, state: .disconnected)
                if self.pendingSendDeviceIDs.remove(id) != nil {
                    self.transferError = "Transfer interrupted — the device moved out of range. Move closer and try again."
                }
                if self.shouldInvite(theirSessionID: self.theirSessionIDByPeerID[peerID]) {
                    self.scheduleRetryInvite(peerID: peerID, deviceID: id)
                }
            @unknown default:
                break
            }
        }
    }

    func session(_ session: MCSession, didReceive data: Data, fromPeer peerID: MCPeerID) {
        handleReceivedData(data, from: peerID)
    }

    func session(_ session: MCSession, didReceive stream: InputStream, withName streamName: String, fromPeer peerID: MCPeerID) {
        // Not used: TrackerApp exchanges only discrete messages, no continuous streams.
    }

    func session(_ session: MCSession, didStartReceivingResourceWithName resourceName: String, fromPeer peerID: MCPeerID, with progress: Progress) {
        // Not used: payloads are sent as discrete messages, not resources.
    }

    func session(_ session: MCSession, didFinishReceivingResourceWithName resourceName: String, fromPeer peerID: MCPeerID, at localURL: URL?, withError error: Error?) {
        // Not used: payloads are sent as discrete messages, not resources.
    }
}

// MARK: - MCNearbyServiceBrowserDelegate

extension ConnectionManager: MCNearbyServiceBrowserDelegate {
    func browser(_ browser: MCNearbyServiceBrowser, foundPeer peerID: MCPeerID, withDiscoveryInfo info: [String: String]?) {
        Self.logger.info("foundPeer: \(peerID.displayName, privacy: .public) theirSessionID=\(info?["sessionID"] ?? "nil", privacy: .public) mySessionID=\(self.sessionID, privacy: .public)")

        DispatchQueue.main.async { [weak self] in
            guard let self else { return }

            let id = self.deviceID(for: peerID)
            self.theirSessionIDByPeerID[peerID] = info?["sessionID"]

            // MCNearbyServiceBrowser keeps re-announcing already-visible peers even after they've
            // connected. Only (re)set to `.discovered` for genuinely new or dropped peers — never
            // downgrade a peer that has already progressed to connecting/connected/tracking, or
            // this races with `session(_:peer:didChange:)` and can permanently clobber it back to
            // "Discovered" even though the MCSession actually connected.
            let currentState = self.discoveredDevices.first(where: { $0.id == id })?.state
            if currentState == nil || currentState == .disconnected {
                self.upsertDevice(id: id, name: peerID.displayName, state: .discovered)
            }

            // Don't re-invite a peer that's already connecting/connected/tracking — repeatedly
            // calling invitePeer on a live session is what produced the "Not in connected state,
            // so giving up... on channel [N]" warnings after a peer had already connected.
            guard currentState == nil || currentState == .discovered || currentState == .disconnected,
                  !self.session.connectedPeers.contains(peerID) else {
                return
            }

            guard self.shouldInvite(theirSessionID: info?["sessionID"]) else {
                Self.logger.info("foundPeer: skipping invite to \(peerID.displayName, privacy: .public) — waiting for their invite instead")
                return
            }
            Self.logger.info("foundPeer: inviting \(peerID.displayName, privacy: .public)")
            browser.invitePeer(peerID, to: self.session, withContext: nil, timeout: 10)
        }
    }

    func browser(_ browser: MCNearbyServiceBrowser, lostPeer peerID: MCPeerID) {
        Self.logger.info("lostPeer: \(peerID.displayName, privacy: .public)")
        DispatchQueue.main.async { [weak self] in
            guard let self, let id = self.deviceIDByPeerID[peerID] else { return }
            self.discoveredDevices.removeAll { $0.id == id }
            self.retryWorkItems[id]?.cancel()
            self.retryWorkItems[id] = nil
        }
    }

    func browser(_ browser: MCNearbyServiceBrowser, didNotStartBrowsingForPeers error: Error) {
        DispatchQueue.main.async { [weak self] in
            self?.transferError = "Discovery failed to start: \(error.localizedDescription)"
        }
    }
}

// MARK: - MCNearbyServiceAdvertiserDelegate

extension ConnectionManager: MCNearbyServiceAdvertiserDelegate {
    func advertiser(_ advertiser: MCNearbyServiceAdvertiser, didReceiveInvitationFromPeer peerID: MCPeerID, withContext context: Data?, invitationHandler: @escaping (Bool, MCSession?) -> Void) {
        Self.logger.info("didReceiveInvitationFromPeer: \(peerID.displayName, privacy: .public) — accepting")
        invitationHandler(true, session)
    }

    func advertiser(_ advertiser: MCNearbyServiceAdvertiser, didNotStartAdvertisingPeer error: Error) {
        DispatchQueue.main.async { [weak self] in
            self?.transferError = "Advertising failed to start: \(error.localizedDescription)"
        }
    }
}
