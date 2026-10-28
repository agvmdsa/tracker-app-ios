import Foundation

/// What `TrackingView` needs from the pairing + ranging flow, so it can be driven by a fake in
/// previews and tests.
@MainActor
protocol ProximityTracking: AnyObject {
    var isRunning: Bool { get }
    var isSupported: Bool { get }
    var handshakeState: HandshakeState { get }
    var rangingState: RangingState { get }
    var errorMessage: String? { get }
    var logs: [TrackingLogEntry] { get }

    var distance: Float? { get }
    /// Horizontal angle to the peer in radians: 0 = straight ahead, positive = to the right.
    var azimuth: Float? { get }
    /// Vertical angle to the peer in radians, if available.
    var elevation: Float? { get }
    var isSignalLost: Bool { get }
    var supportsDirectionMeasurement: Bool { get }
    var isCameraAssisted: Bool { get }

    /// Looks for a nearby phone running this app, swaps UWB tokens with it and ranges it.
    func start()
    func stop()
    /// App went to the background: UWB can't range there, and BLE discovery is throttled.
    func pause()
    func resume()
}

extension ProximityTracker: ProximityTracking {}
