import XCTest
@testable import TrackerApp

final class PositioningTests: XCTestCase {
    private let tolerance = 1e-6

    private func makeTriangle() -> [Anchor] {
        let sideMeters = 3.0
        let circumradius = sideMeters / sqrt(3.0)
        let corners = [
            Point2D(x: 0.0, y: circumradius),
            Point2D(x: sideMeters / 2, y: -circumradius / 2),
            Point2D(x: -sideMeters / 2, y: -circumradius / 2),
        ]
        return zip(["A", "B", "C"], corners).map { label, position in
            Anchor(id: BeaconID.random(), label: label, position: position)
        }
    }

    func test_exactRanges_recoverThePositionTheyWereGeneratedFrom() {
        let anchors = makeTriangle()
        let truth = Point2D(x: 0.4, y: 1.2)
        let readings = anchors.map { AnchorRange(anchor: $0, distanceMeters: $0.position.distance(to: truth)) }

        let fix = Multilateration.solve(readings: readings)

        XCTAssertEqual(fix?.position.x, truth.x, accuracy: tolerance)
        XCTAssertEqual(fix?.position.y, truth.y, accuracy: tolerance)
        XCTAssertEqual(fix?.uncertaintyMeters, 0.0, accuracy: tolerance)
    }

    func test_inconsistentRanges_stillProducesAFix_withDisagreementReported() {
        let anchors = makeTriangle()
        let truth = Point2D(x: 0.0, y: 0.6)
        let readings = anchors.enumerated().map { index, anchor -> AnchorRange in
            let error = index == 0 ? 0.5 : 0.0
            return AnchorRange(anchor: anchor, distanceMeters: anchor.position.distance(to: truth) + error)
        }

        let fix = Multilateration.solve(readings: readings)

        XCTAssertNotNil(fix)
        XCTAssertGreaterThan(fix?.uncertaintyMeters ?? 0, 0.05, "expected a non-zero residual")
        XCTAssertNotEqual(fix?.position.y, truth.y)
    }

    func test_fewerThanThreeAnchors_isNotAFix() {
        let readings = makeTriangle().prefix(2).map { AnchorRange(anchor: $0, distanceMeters: 1.0) }

        XCTAssertNil(Multilateration.solve(readings: Array(readings)))
    }

    func test_collinearAnchors_isNotAFix() {
        let anchors = [
            Anchor(id: BeaconID.random(), label: "A", position: Point2D(x: 0, y: 0)),
            Anchor(id: BeaconID.random(), label: "B", position: Point2D(x: 1, y: 0)),
            Anchor(id: BeaconID.random(), label: "C", position: Point2D(x: 2, y: 0)),
        ]
        let observed = Point2D(x: 1, y: 5)
        let readings = anchors.map { AnchorRange(anchor: $0, distanceMeters: $0.position.distance(to: observed)) }

        XCTAssertNil(Multilateration.solve(readings: readings))
    }

    func test_moreThanThreeAnchors_recoversThePosition() {
        let anchors = (0..<5).map { i -> Anchor in
            let angle = Double(i) * (2 * .pi / 5)
            return Anchor(id: BeaconID.random(), label: "\(i)", position: Point2D(x: 3 * cos(angle), y: 3 * sin(angle)))
        }
        let truth = Point2D(x: 0.3, y: -0.5)
        let readings = anchors.map { AnchorRange(anchor: $0, distanceMeters: $0.position.distance(to: truth)) }

        let fix = Multilateration.solve(readings: readings)

        XCTAssertEqual(fix?.position.x, truth.x, accuracy: tolerance)
        XCTAssertEqual(fix?.position.y, truth.y, accuracy: tolerance)
    }

    func test_referenceRSSI_meansOneMetre() {
        XCTAssertEqual(PathLossModel.distanceMeters(rssi: PathLossModel.referenceRSSI), 1.0, accuracy: tolerance)
    }

    func test_weakerSignal_readsAsFurtherAway() {
        let near = PathLossModel.distanceMeters(rssi: -60.0)
        let far = PathLossModel.distanceMeters(rssi: -80.0)

        XCTAssertGreaterThan(far, near)
    }

    func test_smoothing_startsAtTheFirstReading_thenLags() {
        let first = PathLossModel.smooth(previous: nil, reading: -70)
        XCTAssertEqual(first, -70.0, accuracy: tolerance)

        let second = PathLossModel.smooth(previous: first, reading: -90)
        XCTAssertEqual(second, -70.0 + PathLossModel.smoothing * -20.0, accuracy: tolerance)
        XCTAssertGreaterThan(second, -75.0, "outlier must not be adopted wholesale")
    }
}
