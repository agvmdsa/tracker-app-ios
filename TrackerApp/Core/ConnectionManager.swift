import Foundation
import MultipeerConnectivity
import NearbyInteraction
import UIKit

/// MultipeerConnectivity wrapper: discovers nearby TrackerApp peers, exchanges NearbyInteraction
/// discovery tokens (P2P Contract 1), and sends/receives custom data payloads (P2P Contract 2).
/// See specs/001-ios-tracker-app/contracts/p2p-messages.md and research.md.
final class ConnectionManager: NSObject, ObservableObject {
    static let serviceType = "tracker-app"

    @Published private(set) var discoveredDevices: [TrackerDevice] = []
    @Published var transferError: String?

    /// Called on the main thread when a peer's NearbyInteraction discovery token arrives.
    var onDiscoveryTokenReceived: ((DiscoveryTokenWrapper) -> Void)?
    /// Called on the main thread when a custom data payload arrives.
    var onPayloadReceived: ((PayloadMessage) -> Void)?

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

    init(displayName: String = UIDevice.current.name) {
        myPeerID = MCPeerID(displayName: displayName)
        session = MCSession(peer: myPeerID, securityIdentity: nil, encryptionPreference: .required)
        super.init()
        session.delegate = self
    }

    // MARK: - Lifecycle (T024a: paused on background, resumed on foreground)

    func start() {
        guard advertiser == nil, browser == nil else { return }

        let advertiser = MCNearbyServiceAdvertiser(peer: myPeerID, discoveryInfo: ["sessionID": sessionID], serviceType: Self.serviceType)
        advertiser.delegate = self
        advertiser.startAdvertisingPeer()
        self.advertiser = advertiser

        let browser = MCNearbyServiceBrowser(peer: myPeerID, serviceType: Self.serviceType)
        browser.delegate = self
        browser.startBrowsingForPeers()
        self.browser = browser
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
        pendingSendDeviceIDs.removeAll()
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
        guard let probe = try? JSONDecoder().decode(TypeProbe.self, from: data) else { return }

        switch probe.type {
        case "NIDiscoveryToken":
            guard let message = try? JSONDecoder().decode(TokenWireMessage.self, from: data),
                  let tokenData = Data(base64Encoded: message.payload) else { return }
            let wrapper = DiscoveryTokenWrapper(tokenData: tokenData, peerId: peerID.displayName)
            DispatchQueue.main.async { [weak self] in
                guard let self else { return }
                self.onDiscoveryTokenReceived?(wrapper)
            }

        case "CustomAction":
            guard let message = try? JSONDecoder().decode(ActionWireMessage.self, from: data),
                  let decodedData = Data(base64Encoded: message.payload.data) else { return }
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

    // MARK: - Device list bookkeeping

    private func deviceID(for peerID: MCPeerID) -> UUID {
        if let existing = deviceIDByPeerID[peerID] { return existing }
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
        let id = deviceID(for: peerID)
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
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
        let id = deviceID(for: peerID)
        DispatchQueue.main.async { [weak self] in
            self?.upsertDevice(id: id, name: peerID.displayName, state: .discovered)
        }

        // Tie-break: only the side with the smaller sessionID invites, avoiding a mutual
        // simultaneous invite that can otherwise leave both sides stuck un-connected.
        if let theirSessionID = info?["sessionID"], theirSessionID <= sessionID {
            return
        }
        browser.invitePeer(peerID, to: session, withContext: nil, timeout: 10)
    }

    func browser(_ browser: MCNearbyServiceBrowser, lostPeer peerID: MCPeerID) {
        guard let id = deviceIDByPeerID[peerID] else { return }
        DispatchQueue.main.async { [weak self] in
            self?.discoveredDevices.removeAll { $0.id == id }
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
        invitationHandler(true, session)
    }

    func advertiser(_ advertiser: MCNearbyServiceAdvertiser, didNotStartAdvertisingPeer error: Error) {
        DispatchQueue.main.async { [weak self] in
            self?.transferError = "Advertising failed to start: \(error.localizedDescription)"
        }
    }
}
