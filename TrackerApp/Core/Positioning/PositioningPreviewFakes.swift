#if DEBUG
import ARKit
import Foundation

@MainActor
final class FakeBeaconAdvertising: BeaconAdvertising {
    let localID: BeaconID
    var isAdvertising: Bool
    var isBluetoothUnavailable: Bool

    init(
        localID: BeaconID = BeaconID(hex: "9F3C1A02")!,
        isAdvertising: Bool = true,
        isBluetoothUnavailable: Bool = false
    ) {
        self.localID = localID
        self.isAdvertising = isAdvertising
        self.isBluetoothUnavailable = isBluetoothUnavailable
    }

    func startAdvertising() {}
    func stopAdvertising() {}
}

@MainActor
final class FakeBeaconScanning: BeaconScanning {
    var sightings: [BeaconID: BeaconSighting]
    var isBluetoothUnavailable: Bool

    init(sightings: [BeaconID: BeaconSighting] = [:], isBluetoothUnavailable: Bool = false) {
        self.sightings = sightings
        self.isBluetoothUnavailable = isBluetoothUnavailable
    }

    func startScanning() {}
    func stopScanning() {}
}

@MainActor
final class FakeARPositionTracking: ARPositionTracking {
    let session = ARSession()
    var isTracking: Bool
    var currentPosition: Point2D?
    var trackingState: ARCamera.TrackingState

    init(
        isTracking: Bool = true,
        currentPosition: Point2D? = Point2D(x: 1.2, y: 0.4),
        trackingState: ARCamera.TrackingState = .normal
    ) {
        self.isTracking = isTracking
        self.currentPosition = currentPosition
        self.trackingState = trackingState
    }

    func start() {}
    func stop() {}
}
#endif
