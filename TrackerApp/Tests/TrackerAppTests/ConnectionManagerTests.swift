import XCTest
import MultipeerConnectivity
import NearbyInteraction
@testable import TrackerApp

final class ConnectionManagerTests: XCTestCase {
    private var sut: ConnectionManager!
    private var fakeSession: MCSession!
    private var fakeBrowser: MCNearbyServiceBrowser!

    override func setUp() {
        super.setUp()
        sut = ConnectionManager(displayName: "Test Device")
        fakeSession = MCSession(peer: MCPeerID(displayName: "unused"))
        fakeBrowser = MCNearbyServiceBrowser(peer: MCPeerID(displayName: "unused-browser"), serviceType: ConnectionManager.serviceType)
    }

    override func tearDown() {
        sut = nil
        fakeSession = nil
        fakeBrowser = nil
        super.tearDown()
    }

    /// Delegate callbacks update `@Published` state via `DispatchQueue.main.async` (they can be
    /// invoked off the main thread by MultipeerConnectivity), so tests must let the main run loop
    /// turn once before asserting on the result.
    private func flushMainQueue() {
        let expectation = expectation(description: "main queue flush")
        DispatchQueue.main.async { expectation.fulfill() }
        wait(for: [expectation], timeout: 1)
    }

    func test_initialState_hasNoDiscoveredDevices() {
        XCTAssertTrue(sut.discoveredDevices.isEmpty)
    }

    func test_foundPeer_addsDeviceAsDiscovered() {
        let peerID = MCPeerID(displayName: "iPhone A")

        sut.browser(fakeBrowser, foundPeer: peerID, withDiscoveryInfo: nil)
        flushMainQueue()

        XCTAssertEqual(sut.discoveredDevices.count, 1)
        XCTAssertEqual(sut.discoveredDevices.first?.displayName, "iPhone A")
        XCTAssertEqual(sut.discoveredDevices.first?.state, .discovered)
    }

    func test_lostPeer_removesDeviceFromDiscoveredList() {
        let peerID = MCPeerID(displayName: "iPhone A")
        sut.browser(fakeBrowser, foundPeer: peerID, withDiscoveryInfo: nil)
        flushMainQueue()

        sut.browser(fakeBrowser, lostPeer: peerID)
        flushMainQueue()

        XCTAssertTrue(sut.discoveredDevices.isEmpty)
    }

    func test_sessionDidChange_toConnecting_updatesDeviceState() {
        let peerID = MCPeerID(displayName: "iPhone A")
        sut.browser(fakeBrowser, foundPeer: peerID, withDiscoveryInfo: nil)
        flushMainQueue()

        sut.session(fakeSession, peer: peerID, didChange: .connecting)
        flushMainQueue()

        XCTAssertEqual(sut.discoveredDevices.first?.state, .connecting)
    }

    func test_sessionDidChange_toConnected_marksDeviceConnected_notDiscovered() {
        // Regression test: earlier this incorrectly mapped to `.discovered`, which made
        // TrackingView unable to tell "found via scan" apart from "MCSession actually connected",
        // causing it to send the NearbyInteraction discovery token before the handshake finished.
        let peerID = MCPeerID(displayName: "iPhone A")
        sut.browser(fakeBrowser, foundPeer: peerID, withDiscoveryInfo: nil)
        flushMainQueue()

        sut.session(fakeSession, peer: peerID, didChange: .connected)
        flushMainQueue()

        XCTAssertEqual(sut.discoveredDevices.first?.state, .connected)
    }

    func test_sessionDidChange_toNotConnected_marksDeviceDisconnected() {
        let peerID = MCPeerID(displayName: "iPhone A")
        sut.browser(fakeBrowser, foundPeer: peerID, withDiscoveryInfo: nil)
        flushMainQueue()

        sut.session(fakeSession, peer: peerID, didChange: .notConnected)
        flushMainQueue()

        XCTAssertEqual(sut.discoveredDevices.first?.state, .disconnected)
    }

    func test_sendDiscoveryToken_beforeSessionConnected_setsTransferError() throws {
        // Regression test: sending the token to a peer that was only *found*, not yet connected,
        // must fail loudly instead of silently no-op'ing — this is exactly the "Peers not
        // connected" failure observed when TrackingView fired the exchange too early.
        guard let token = NISession().discoveryToken else {
            throw XCTSkip("NearbyInteraction discovery token unavailable in this environment (needs U1 hardware)")
        }
        let peerID = MCPeerID(displayName: "iPhone A")
        sut.browser(fakeBrowser, foundPeer: peerID, withDiscoveryInfo: nil)
        flushMainQueue()
        guard let deviceID = sut.discoveredDevices.first?.id else {
            return XCTFail("Expected a discovered device")
        }

        sut.sendDiscoveryToken(token, toDeviceWithID: deviceID)

        XCTAssertNotNil(sut.transferError)
    }

    func test_sendPayload_toUnconnectedDevice_setsTransferError() {
        let peerID = MCPeerID(displayName: "iPhone A")
        sut.browser(fakeBrowser, foundPeer: peerID, withDiscoveryInfo: nil)
        flushMainQueue()
        guard let deviceID = sut.discoveredDevices.first?.id else {
            return XCTFail("Expected a discovered device")
        }

        sut.sendPayload(PayloadMessage(messageId: UUID(), timestamp: Date(), actionType: "ping", data: Data()), toDeviceWithID: deviceID)

        XCTAssertNotNil(sut.transferError, "Sending to a peer with no live MCSession connection must surface a transfer error (Edge Case: Data Transfer Interruption)")
    }

    func test_sendPayload_toUnknownDevice_setsTransferError() {
        sut.sendPayload(PayloadMessage(messageId: UUID(), timestamp: Date(), actionType: "ping", data: Data()), toDeviceWithID: UUID())

        XCTAssertNotNil(sut.transferError)
    }
}
