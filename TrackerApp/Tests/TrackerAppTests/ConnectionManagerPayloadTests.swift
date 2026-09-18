import XCTest
import MultipeerConnectivity
@testable import TrackerApp

/// Tests for the WiFi/high-bandwidth payload logic (P2P Contract 2) — encode/decode round trips
/// and the send/receive path of `ConnectionManager`, exercised through its public API rather than
/// the private wire-envelope types (see contracts/p2p-messages.md for the wire format).
final class ConnectionManagerPayloadTests: XCTestCase {
    private var sut: ConnectionManager!
    private var fakeSession: MCSession!

    override func setUp() {
        super.setUp()
        sut = ConnectionManager(displayName: "Test Device")
        fakeSession = MCSession(peer: MCPeerID(displayName: "unused"))
    }

    override func tearDown() {
        sut = nil
        fakeSession = nil
        super.tearDown()
    }

    func test_payloadMessage_encodeDecode_roundTrips() throws {
        let original = PayloadMessage(messageId: UUID(), timestamp: Date(), actionType: "ping", data: Data([0x01, 0x02, 0x03]))

        let encoded = try JSONEncoder().encode(original)
        let decoded = try JSONDecoder().decode(PayloadMessage.self, from: encoded)

        XCTAssertEqual(decoded.messageId, original.messageId)
        XCTAssertEqual(decoded.actionType, original.actionType)
        XCTAssertEqual(decoded.data, original.data)
        XCTAssertEqual(decoded.timestamp.timeIntervalSince1970, original.timestamp.timeIntervalSince1970, accuracy: 0.001)
    }

    func test_didReceiveData_withValidCustomActionMessage_invokesOnPayloadReceived() {
        let expectation = expectation(description: "payload received")
        var receivedPayload: PayloadMessage?
        sut.onPayloadReceived = { payload in
            receivedPayload = payload
            expectation.fulfill()
        }

        let wireJSON = """
        {"type":"CustomAction","payload":{"actionType":"ping","data":"\(Data("hello".utf8).base64EncodedString())"}}
        """
        sut.session(fakeSession, didReceive: Data(wireJSON.utf8), fromPeer: MCPeerID(displayName: "iPhone A"))

        wait(for: [expectation], timeout: 1)
        XCTAssertEqual(receivedPayload?.actionType, "ping")
        XCTAssertEqual(receivedPayload?.data, Data("hello".utf8))
    }

    func test_didReceiveData_withValidDiscoveryTokenMessage_invokesOnDiscoveryTokenReceived() {
        let expectation = expectation(description: "token received")
        var receivedWrapper: DiscoveryTokenWrapper?
        sut.onDiscoveryTokenReceived = { wrapper in
            receivedWrapper = wrapper
            expectation.fulfill()
        }

        let wireJSON = """
        {"type":"NIDiscoveryToken","payload":"\(Data("faketoken".utf8).base64EncodedString())"}
        """
        sut.session(fakeSession, didReceive: Data(wireJSON.utf8), fromPeer: MCPeerID(displayName: "iPhone A"))

        wait(for: [expectation], timeout: 1)
        XCTAssertEqual(receivedWrapper?.peerId, "iPhone A", "peerId must come from fromPeer, not the wire payload")
        XCTAssertEqual(receivedWrapper?.tokenData, Data("faketoken".utf8))
    }

    func test_didReceiveData_withUnknownMessageType_invokesNoCallback() {
        var callbackInvoked = false
        sut.onPayloadReceived = { _ in callbackInvoked = true }
        sut.onDiscoveryTokenReceived = { _ in callbackInvoked = true }

        sut.session(fakeSession, didReceive: Data(#"{"type":"Unknown","payload":"x"}"#.utf8), fromPeer: MCPeerID(displayName: "iPhone A"))

        let settle = expectation(description: "let main queue drain")
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) { settle.fulfill() }
        wait(for: [settle], timeout: 1)

        XCTAssertFalse(callbackInvoked)
    }

    func test_didReceiveData_withMalformedJSON_doesNotCrash() {
        sut.session(fakeSession, didReceive: Data("not json".utf8), fromPeer: MCPeerID(displayName: "iPhone A"))
        // Reaching this line without a crash is the assertion.
    }
}
