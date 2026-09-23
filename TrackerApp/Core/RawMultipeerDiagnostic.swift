import Foundation
import MultipeerConnectivity
import Observation
import os

/// Deliberately minimal, fully isolated from `ConnectionManager` — no tie-break, no custom
/// discoveryInfo, no encryption customization, no timeout scheduling, both sides always invite
/// (matching Apple's own simplest sample pattern). Used to determine whether the "Not in
/// connected state, so giving up..." failures we've been chasing are caused by something in
/// ConnectionManager's extra logic, or are present even in the bare-minimum MultipeerConnectivity
/// setup — which would mean the cause is outside our code entirely (device/OS/network).
///
/// Temporary debugging tool — remove once the root cause of the connection failures is found.
@Observable
@MainActor
final class RawMultipeerDiagnostic: NSObject {
    private static let logger = Logger(subsystem: "com.airtagclone.TrackerApp", category: "RawMCTest")
    private static let serviceType = "mc-raw-test"

    nonisolated override init() {
        super.init()
    }

    private(set) var log: [String] = []

    private let peerID = MCPeerID(displayName: DeviceIdentity.displayName + "-raw")
    private lazy var session: MCSession = {
        let session = MCSession(peer: peerID)
        session.delegate = self
        return session
    }()
    private var advertiser: MCNearbyServiceAdvertiser?
    private var browser: MCNearbyServiceBrowser?

    private func append(_ message: String) {
        Self.logger.info("\(message, privacy: .public)")
        DispatchQueue.main.async { [weak self] in
            self?.log.append(message)
        }
    }

    func start() {
        append("RAW start() peerID=\(peerID.displayName)")

        let advertiser = MCNearbyServiceAdvertiser(peer: peerID, discoveryInfo: nil, serviceType: Self.serviceType)
        advertiser.delegate = self
        advertiser.startAdvertisingPeer()
        self.advertiser = advertiser

        let browser = MCNearbyServiceBrowser(peer: peerID, serviceType: Self.serviceType)
        browser.delegate = self
        browser.startBrowsingForPeers()
        self.browser = browser
    }

    func stop() {
        advertiser?.stopAdvertisingPeer()
        advertiser = nil
        browser?.stopBrowsingForPeers()
        browser = nil
        session.disconnect()
    }
}

extension RawMultipeerDiagnostic: MCSessionDelegate {
    func session(_ session: MCSession, peer peerID: MCPeerID, didChange state: MCSessionState) {
        append("RAW session didChange: \(peerID.displayName) -> rawValue=\(state.rawValue)")
    }

    func session(_ session: MCSession, didReceive data: Data, fromPeer peerID: MCPeerID) {}
    func session(_ session: MCSession, didReceive stream: InputStream, withName streamName: String, fromPeer peerID: MCPeerID) {}
    func session(_ session: MCSession, didStartReceivingResourceWithName resourceName: String, fromPeer peerID: MCPeerID, with progress: Progress) {}
    func session(_ session: MCSession, didFinishReceivingResourceWithName resourceName: String, fromPeer peerID: MCPeerID, at localURL: URL?, withError error: Error?) {}
}

extension RawMultipeerDiagnostic: MCNearbyServiceBrowserDelegate {
    func browser(_ browser: MCNearbyServiceBrowser, foundPeer peerID: MCPeerID, withDiscoveryInfo info: [String: String]?) {
        append("RAW foundPeer: \(peerID.displayName) — inviting")
        browser.invitePeer(peerID, to: session, withContext: nil, timeout: 30)
    }

    func browser(_ browser: MCNearbyServiceBrowser, lostPeer peerID: MCPeerID) {
        append("RAW lostPeer: \(peerID.displayName)")
    }

    func browser(_ browser: MCNearbyServiceBrowser, didNotStartBrowsingForPeers error: Error) {
        append("RAW didNotStartBrowsingForPeers: \(error.localizedDescription)")
    }
}

extension RawMultipeerDiagnostic: MCNearbyServiceAdvertiserDelegate {
    func advertiser(_ advertiser: MCNearbyServiceAdvertiser, didReceiveInvitationFromPeer peerID: MCPeerID, withContext context: Data?, invitationHandler: @escaping (Bool, MCSession?) -> Void) {
        append("RAW didReceiveInvitationFromPeer: \(peerID.displayName) — accepting")
        invitationHandler(true, session)
    }

    func advertiser(_ advertiser: MCNearbyServiceAdvertiser, didNotStartAdvertisingPeer error: Error) {
        append("RAW didNotStartAdvertisingPeer: \(error.localizedDescription)")
    }
}
