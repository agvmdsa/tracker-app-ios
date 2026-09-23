import ARKit

@MainActor
protocol ARPositionTracking: AnyObject {
    var session: ARSession { get }
    var isTracking: Bool { get }
    var currentPosition: Point2D? { get }
    var trackingState: ARCamera.TrackingState { get }

    func start()
    func stop()
}
