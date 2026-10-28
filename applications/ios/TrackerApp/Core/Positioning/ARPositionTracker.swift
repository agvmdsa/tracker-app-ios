import ARKit
import Observation

@Observable
@MainActor
final class ARPositionTracker: NSObject {
    let session = ARSession()

    private(set) var isTracking = false
    private(set) var currentPosition: Point2D?
    private(set) var trackingState: ARCamera.TrackingState = .notAvailable

    static var isSupported: Bool {
        ARWorldTrackingConfiguration.isSupported
    }

    nonisolated override init() {
        super.init()
        session.delegate = self
    }

    func start() {
        let configuration = ARWorldTrackingConfiguration()
        configuration.worldAlignment = .gravity
        session.run(configuration, options: [.resetTracking, .removeExistingAnchors])
        isTracking = true
    }

    func stop() {
        session.pause()
        isTracking = false
        currentPosition = nil
        trackingState = .notAvailable
    }
}

extension ARPositionTracker: ARPositionTracking {}

extension ARPositionTracker: ARSessionDelegate {
    func session(_ session: ARSession, didUpdate frame: ARFrame) {
        let translation = frame.camera.transform.columns.3
        currentPosition = Point2D(x: Double(translation.x), y: Double(-translation.z))
        trackingState = frame.camera.trackingState
    }
}
