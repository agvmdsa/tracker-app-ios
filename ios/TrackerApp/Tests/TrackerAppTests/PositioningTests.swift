import SwiftUI
import XCTest
@testable import TrackerApp
// SwiftUI has its own `Anchor`; the scoped import makes the app's one win in this file.
@testable import struct TrackerApp.Anchor

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

    func test_exactRanges_recoverThePositionTheyWereGeneratedFrom() throws {
        let anchors = makeTriangle()
        let truth = Point2D(x: 0.4, y: 1.2)
        let readings = anchors.map { AnchorRange(anchor: $0, distanceMeters: $0.position.distance(to: truth)) }

        let fix = try XCTUnwrap(Multilateration.solve(readings: readings))

        XCTAssertEqual(fix.position.x, truth.x, accuracy: tolerance)
        XCTAssertEqual(fix.position.y, truth.y, accuracy: tolerance)
        XCTAssertEqual(fix.uncertaintyMeters, 0.0, accuracy: tolerance)
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

    func test_moreThanThreeAnchors_recoversThePosition() throws {
        let anchors = (0..<5).map { i -> Anchor in
            let angle = Double(i) * (2 * .pi / 5)
            return Anchor(id: BeaconID.random(), label: "\(i)", position: Point2D(x: 3 * cos(angle), y: 3 * sin(angle)))
        }
        let truth = Point2D(x: 0.3, y: -0.5)
        let readings = anchors.map { AnchorRange(anchor: $0, distanceMeters: $0.position.distance(to: truth)) }

        let fix = try XCTUnwrap(Multilateration.solve(readings: readings))

        XCTAssertEqual(fix.position.x, truth.x, accuracy: tolerance)
        XCTAssertEqual(fix.position.y, truth.y, accuracy: tolerance)
    }

    // MARK: Decimal input

    func test_decimalInput_acceptsADotOrACommaAsTheSeparator() {
        // A Portuguese keyboard types "2,5"; Swift's own `Double("2,5")` is nil.
        XCTAssertEqual(DecimalInput.parse("2.5"), 2.5)
        XCTAssertEqual(DecimalInput.parse("2,5"), 2.5)
        XCTAssertEqual(DecimalInput.parse("-1,25"), -1.25)
        XCTAssertEqual(DecimalInput.parse("+3"), 3)
        XCTAssertEqual(DecimalInput.parse("4"), 4)
        XCTAssertEqual(DecimalInput.parse(",5"), 0.5)
        XCTAssertEqual(DecimalInput.parse(".5"), 0.5)
        XCTAssertEqual(DecimalInput.parse("2."), 2)
        XCTAssertEqual(DecimalInput.parse("0"), 0)
    }

    func test_decimalInput_ignoresSurroundingSpaces() {
        XCTAssertEqual(DecimalInput.parse("  2,5 "), 2.5)
    }

    func test_decimalInput_refusesWhatIsNotAPlainNumber() {
        for text in ["", " ", ",", ".", "-", "+", "abc", "2,5m", "1,2,3", "1.2.3", "1.234,5", "1,234.5", "1e3", "nan", "inf", "-inf", "--1", "2 5", "٢"] {
            XCTAssertNil(DecimalInput.parse(text), "\"\(text)\" is not a number to accept")
        }
    }

    func test_decimalInput_textForAFieldIsPlainDecimalWithoutExponentOrTrailingZeros() {
        XCTAssertEqual(DecimalInput.text(for: 2.5), "2.5")
        XCTAssertEqual(DecimalInput.text(for: 2), "2")
        XCTAssertEqual(DecimalInput.text(for: -1.25), "-1.25")
        XCTAssertEqual(DecimalInput.text(for: 0.125), "0.125")
        XCTAssertEqual(DecimalInput.text(for: 0.00001), "0", "no exponent notation that the field could not read back")
        XCTAssertEqual(DecimalInput.text(for: -0.0001), "0", "no negative zero")
        XCTAssertEqual(DecimalInput.text(for: .nan), "")
    }

    func test_decimalInput_whatAFieldShowsCanBeReadBack() {
        for value in [0.0, 2.5, -1.25, 3.0, 12.375, -0.5] {
            XCTAssertEqual(DecimalInput.parse(DecimalInput.text(for: value)), value)
        }
    }

    // MARK: Map projection

    private let mapSize = CGSize(width: 360, height: 300)

    func test_projection_keepsEveryPointInsideTheMargin() {
        let points = [Point2D(x: 0.4, y: 0.4), Point2D(x: 5.6, y: 0.4), Point2D(x: 3.0, y: 4.6), Point2D(x: 7.0, y: -1.0)]

        let projection = MapProjection(fitting: points, in: mapSize, margin: 28)

        for point in points {
            let v = projection.viewPoint(for: point)
            XCTAssertGreaterThanOrEqual(v.x, 28 - 1e-9)
            XCTAssertLessThanOrEqual(v.x, mapSize.width - 28 + 1e-9)
            XCTAssertGreaterThanOrEqual(v.y, 28 - 1e-9)
            XCTAssertLessThanOrEqual(v.y, mapSize.height - 28 + 1e-9)
        }
    }

    func test_projection_usesTheSameScaleOnBothAxes() {
        let projection = MapProjection(fitting: [Point2D(x: 0, y: 0), Point2D(x: 6, y: 5)], in: mapSize)

        let origin = projection.viewPoint(for: Point2D(x: 0, y: 0))
        let oneRight = projection.viewPoint(for: Point2D(x: 1, y: 0))
        let oneUp = projection.viewPoint(for: Point2D(x: 0, y: 1))

        XCTAssertEqual(oneRight.x - origin.x, origin.y - oneUp.y, accuracy: 1e-9, "1 m to the right is as long as 1 m up")
        XCTAssertEqual(oneRight.x - origin.x, projection.scale, accuracy: 1e-9)
    }

    func test_projection_putsLargerYHigherOnTheScreen() {
        let projection = MapProjection(fitting: [Point2D(x: 0, y: 0), Point2D(x: 6, y: 5)], in: mapSize)

        XCTAssertLessThan(projection.viewPoint(for: Point2D(x: 1, y: 4)).y, projection.viewPoint(for: Point2D(x: 1, y: 1)).y)
    }

    func test_projection_aSinglePointIsCenteredWithoutDividingByZero() {
        let projection = MapProjection(fitting: [Point2D(x: 3, y: 2)], in: mapSize)

        let v = projection.viewPoint(for: Point2D(x: 3, y: 2))
        XCTAssertEqual(v.x, mapSize.width / 2, accuracy: 1e-9)
        XCTAssertEqual(v.y, mapSize.height / 2, accuracy: 1e-9)
        XCTAssertTrue(projection.scale.isFinite && projection.scale > 0)
    }

    func test_projection_withoutPointsStillWorks() {
        let projection = MapProjection(fitting: [], in: mapSize)

        XCTAssertTrue(projection.scale.isFinite && projection.scale > 0)
        XCTAssertTrue(projection.viewPoint(for: Point2D(x: 0, y: 0)).x.isFinite)
    }

    func test_projection_theVisibleRangeCoversWhatTheViewShows() {
        let projection = MapProjection(fitting: [Point2D(x: 0, y: 0), Point2D(x: 6, y: 5)], in: mapSize)

        let left = projection.viewPoint(for: Point2D(x: projection.visibleX.lowerBound, y: 0)).x
        let right = projection.viewPoint(for: Point2D(x: projection.visibleX.upperBound, y: 0)).x
        XCTAssertEqual(left, 0, accuracy: 1e-6)
        XCTAssertEqual(right, mapSize.width, accuracy: 1e-6)
    }

    func test_gridStep_growsAsTheMapShrinks() {
        XCTAssertEqual(MapProjection.gridStep(forScale: 100), 0.5, accuracy: 1e-9)
        XCTAssertEqual(MapProjection.gridStep(forScale: 40), 1, accuracy: 1e-9)
        XCTAssertEqual(MapProjection.gridStep(forScale: 20), 2, accuracy: 1e-9)
        XCTAssertEqual(MapProjection.gridStep(forScale: 5), 10, accuracy: 1e-9)
    }

    // MARK: RSSI window

    /// A window filled with `readings`, 100 ms apart starting at 0.
    private func window(_ readings: [Int], capacity: Int = 100, durationMs: Int64 = 3_000) -> RSSIWindow {
        var window = RSSIWindow(durationMs: durationMs, capacity: capacity)
        for (index, rssi) in readings.enumerated() {
            window.add(rssi: rssi, atMs: Int64(index) * 100)
        }
        return window
    }

    func test_window_medianOfAnOddCount() throws {
        XCTAssertEqual(try XCTUnwrap(window([-70, -60, -65]).median(atMs: 300)), -65, accuracy: tolerance)
    }

    func test_window_medianOfAnEvenCount_isTheMeanOfTheTwoMiddleReadings() throws {
        XCTAssertEqual(try XCTUnwrap(window([-70, -60, -64, -66]).median(atMs: 400)), -65, accuracy: tolerance)
    }

    func test_window_aSingleOutlier_doesNotMoveTheMedian() throws {
        XCTAssertEqual(try XCTUnwrap(window([-60, -61, -60, -92, -59]).median(atMs: 500)), -60, accuracy: tolerance)
    }

    func test_window_dropsReadingsOlderThanItsDuration() throws {
        var window = RSSIWindow(durationMs: 3_000, capacity: 100)
        window.add(rssi: -90, atMs: 0)
        window.add(rssi: -60, atMs: 2_900)

        XCTAssertEqual(try XCTUnwrap(window.median(atMs: 3_500)), -60, accuracy: tolerance, "the reading from 3.5 s ago is gone")
        XCTAssertNil(window.median(atMs: 6_000), "nothing heard in the last 3 s")
    }

    func test_window_withoutReadings_hasNoMedian() {
        XCTAssertNil(RSSIWindow(durationMs: 3_000, capacity: 10).median(atMs: 0))
    }

    func test_window_whenFull_keepsTheNewestReadings() throws {
        let full = window([-90, -90, -60, -61, -62], capacity: 3)

        XCTAssertEqual(full.count, 3)
        XCTAssertEqual(try XCTUnwrap(full.median(atMs: 400)), -61, accuracy: tolerance)
    }

    // MARK: Weighted multilateration

    /// Four anchors at the corners of an 8 x 6 m room, a phone near one corner, and every reading off
    /// by a fixed fraction of its distance, as RSSI noise makes it: the far anchors err by metres, the
    /// near one by centimetres. `order` picks which corner comes first in the list.
    private func roomReadings(order: [Int] = [0, 1, 2, 3]) -> (readings: [AnchorRange], truth: Point2D) {
        let corners = [Point2D(x: 0, y: 0), Point2D(x: 8, y: 0), Point2D(x: 8, y: 6), Point2D(x: 0, y: 6)]
        let factors = [1.05, 1.30, 0.80, 1.10]
        let truth = Point2D(x: 1, y: 1)
        let readings = order.map { index -> AnchorRange in
            let anchor = Anchor(id: BeaconID.random(), label: "\(index)", position: corners[index])
            return AnchorRange(anchor: anchor, distanceMeters: corners[index].distance(to: truth) * factors[index])
        }
        return (readings, truth)
    }

    func test_proportionalRangeErrors_theNearAnchorCountsMoreThanTheFarOnes() throws {
        let (readings, truth) = roomReadings()

        let fix = try XCTUnwrap(Multilateration.solve(readings: readings))

        // Counting every reading the same puts the fix about 1.6 m away from the phone.
        XCTAssertLessThan(fix.position.distance(to: truth), 0.3)
    }

    func test_solve_doesNotDependOnTheOrderOfTheReadings() throws {
        let forward = try XCTUnwrap(Multilateration.solve(readings: roomReadings().readings))
        let reversed = try XCTUnwrap(Multilateration.solve(readings: roomReadings(order: [3, 2, 1, 0]).readings))

        XCTAssertEqual(forward.position.x, reversed.position.x, accuracy: 1e-4)
        XCTAssertEqual(forward.position.y, reversed.position.y, accuracy: 1e-4)
    }

    func test_weight_fallsWithTheSquareOfTheDistance_andStopsGrowingNearTheAnchor() {
        // 1 / error², with the error proportional to the distance: 4x the distance, 16x less weight.
        let ratio = Multilateration.weight(forDistance: 2) / Multilateration.weight(forDistance: 8)
        XCTAssertEqual(ratio, 16, accuracy: 0.01)

        XCTAssertEqual(
            Multilateration.weight(forDistance: 0.05),
            Multilateration.weight(forDistance: 0.2),
            accuracy: tolerance,
            "below the minimum error the weight stops growing"
        )
        XCTAssertTrue(Multilateration.weight(forDistance: 0).isFinite)
    }

    /// Touching a beacon is the surest reading there is: indoors the far beacons read stronger than
    /// the model expects (here all three as the center), and with a 0.5 m floor on the error they
    /// pulled the fix 0.6 m towards the middle.
    func test_aBeaconAFewCentimetresAway_decidesTheFix_evenWhenTheFarOnesReadShort() throws {
        let truth = Point2D(x: 0.2, y: 0.2)
        let readings = squareCorners.enumerated().map { index, corner in
            AnchorRange(
                anchor: Anchor(id: BeaconID.random(), label: "", position: corner),
                distanceMeters: index == 0 ? corner.distance(to: truth) : 2.5 / 2.0.squareRoot()
            )
        }

        let fix = try XCTUnwrap(Multilateration.solve(readings: readings))

        XCTAssertLessThan(fix.position.distance(to: truth), 0.1, "fix at \(fix.position)")
    }

    private func squareReadings(_ distances: [Double]) -> [AnchorRange] {
        zip(squareCorners, distances).map { corner, distance in
            AnchorRange(anchor: Anchor(id: BeaconID.random(), label: "", position: corner), distanceMeters: distance)
        }
    }

    /// Reading as touching a beacon ("RSSI encostado", the touching distance) puts the phone on it,
    /// however wrong the others are: here they claim 0.6 m, which no point near A allows.
    func test_aBeaconReadAsTouching_putsThePhoneOnIt_whateverTheOthersSay() throws {
        for others in [[0.6, 0.6, 0.6], [4.0, 6.0, 6.0], [1.77, 1.77, 1.77], [2.5, 0.6, 6.0]] {
            for touching in [PathLossModel.touchDistanceMeters, 0.05] {
                let fix = try XCTUnwrap(Multilateration.solve(readings: squareReadings([touching] + others)))

                XCTAssertEqual(fix.position.distance(to: squareCorners[0]), 0, accuracy: 1e-9, "others \(others): fix at \(fix.position)")
            }
        }
    }

    /// A little weaker than touching, the phone stays within the extra distance of the beacon,
    /// counted from touching it: 0.3 m by the model is at most 0.2 m from A.
    func test_aBeaconReadNearTouching_keepsThePhoneWithinTheExtraDistance() throws {
        for others in [[1.0, 1.0, 1.0], [1.77, 1.77, 1.77]] {
            let fix = try XCTUnwrap(Multilateration.solve(readings: squareReadings([0.3] + others)))

            XCTAssertLessThanOrEqual(fix.position.distance(to: squareCorners[0]), 0.3 - PathLossModel.touchDistanceMeters + 1e-9, "others \(others)")
        }
    }

    /// A first step that made things worse used to end the search where it started: here 1.6 m from
    /// A, which reads 0.8 m. Halving the step instead goes on to the minimum.
    func test_aBadStart_doesNotStrandTheFix() throws {
        let fix = try XCTUnwrap(Multilateration.solve(readings: squareReadings([0.8, 1.77, 2.5, 6])))

        XCTAssertEqual(fix.position.distance(to: squareCorners[0]), 0.8, accuracy: 0.15, "fix at \(fix.position)")
    }

    // MARK: Speed limit

    func test_speedLimit_movesTowardsTheNewFix_byAtMostTheSpeedTimesTheTime() {
        // 3.6 km/h is 1 m/s: 2 s allow 2 m of the 5 m to (3, 4).
        let limited = MotionLimit.limited(from: Point2D(x: 0, y: 0), to: Point2D(x: 3, y: 4), maxSpeedKmh: 3.6, elapsedSeconds: 2)

        XCTAssertEqual(limited.x, 1.2, accuracy: 1e-9)
        XCTAssertEqual(limited.y, 1.6, accuracy: 1e-9)
    }

    func test_speedLimit_withinReach_isTheNewFix() {
        let limited = MotionLimit.limited(from: Point2D(x: 0, y: 0), to: Point2D(x: 0.3, y: 0.4), maxSpeedKmh: 3.6, elapsedSeconds: 1)

        XCTAssertEqual(limited, Point2D(x: 0.3, y: 0.4))
    }

    func test_speedLimit_withoutAPreviousFixOrALimit_isTheNewFix() {
        let target = Point2D(x: 3, y: 4)
        XCTAssertEqual(MotionLimit.limited(from: nil, to: target, maxSpeedKmh: 2, elapsedSeconds: 1), target)
        XCTAssertEqual(MotionLimit.limited(from: Point2D(x: 0, y: 0), to: target, maxSpeedKmh: nil, elapsedSeconds: 1), target)
    }

    func test_speedLimit_withNoTimeElapsed_staysPut() {
        let previous = Point2D(x: 1, y: 1)
        XCTAssertEqual(MotionLimit.limited(from: previous, to: Point2D(x: 3, y: 4), maxSpeedKmh: 2, elapsedSeconds: 0), previous)
        XCTAssertEqual(MotionLimit.limited(from: previous, to: Point2D(x: 3, y: 4), maxSpeedKmh: 2, elapsedSeconds: -1), previous)
    }

    /// The corners of a 2.5 m square, the layout of the bench test.
    private let squareCorners = [Point2D(x: 0, y: 0), Point2D(x: 2.5, y: 0), Point2D(x: 2.5, y: 2.5), Point2D(x: 0, y: 2.5)]
    private let squareCenter = Point2D(x: 1.25, y: 1.25)

    /// The same RSSI from every corner means the same distance to each, so the fix is the center
    /// whatever that distance is: shorter than the real 1.77 m, equal to it, or much longer, as an
    /// uncalibrated model gives.
    func test_equalRSSIFromTheCornersOfASquare_isTheCenter() throws {
        for rssi in [-30.0, -40.0, -50.0, -60.0, -64.0, -70.0, -80.0] {
            let readings = squareCorners.map { corner in
                AnchorRange(
                    anchor: Anchor(id: BeaconID.random(), label: "", position: corner),
                    distanceMeters: PathLossModel.distanceMeters(rssi: rssi)
                )
            }
            let fix = try XCTUnwrap(Multilateration.solve(readings: readings))
            XCTAssertLessThan(fix.position.distance(to: squareCenter), 1e-6, "\(rssi) dBm")
        }
    }

    /// Measured on the bench: in the middle of the 2.5 m square of ESP32 beacons the iPhone reads
    /// about -70 dBm from each, and the middle is 1.77 m from every corner.
    func test_seventyDbm_isTheDistanceFromTheCornerToTheCenterOfTheBenchSquare() {
        XCTAssertEqual(PathLossModel.distanceMeters(rssi: -70), 2.5 / 2.0.squareRoot(), accuracy: 0.02)
    }

    func test_centerDistance_isTheMeanDistanceFromTheAnchorsToTheirMiddle() throws {
        let square = [Point2D(x: 0, y: 0), Point2D(x: 2.5, y: 0), Point2D(x: 2.5, y: 2.5), Point2D(x: 0, y: 2.5)]
        XCTAssertEqual(try XCTUnwrap(PathLossModel.centerDistanceMeters(of: square)), 2.5 / 2.0.squareRoot(), accuracy: 1e-9)
        XCTAssertNil(PathLossModel.centerDistanceMeters(of: [Point2D(x: 1, y: 1)]), "one anchor has no middle to be away from")
        XCTAssertNil(PathLossModel.centerDistanceMeters(of: []))
    }

    /// The default touching RSSI, -30 dBm, reads as the touching distance.
    func test_thirtyDbm_isTheTouchingDistance() {
        XCTAssertEqual(PathLossModel.distanceMeters(rssi: -30), PathLossModel.touchDistanceMeters, accuracy: 1e-9)
    }

    /// Two readings, two unknowns: the center RSSI is the center distance and the touching RSSI is
    /// the touching distance, which fixes both the RSSI at 1 m and the path loss exponent.
    func test_calibration_readsTheCenterAndTheTouchingRSSIAsTheirDistances() {
        let calibration = PathLossModel.calibration(centerRSSI: -76, centerDistanceMeters: 3, touchRSSI: -38)

        XCTAssertTrue(calibration.usesTouchRSSI)
        XCTAssertEqual(calibration.distanceMeters(rssi: -76), 3, accuracy: 1e-9)
        XCTAssertEqual(calibration.distanceMeters(rssi: -38), PathLossModel.touchDistanceMeters, accuracy: 1e-9)
        // 38 dB over a 30x change in distance.
        XCTAssertEqual(calibration.exponent, 38 / (10 * log10(30.0)), accuracy: 1e-9)
    }

    func test_calibration_rssiAtADistance_isTheInverseOfTheDistanceForAnRSSI() {
        let calibration = PathLossModel.calibration(centerRSSI: -70, centerDistanceMeters: 2, touchRSSI: -36)

        XCTAssertEqual(calibration.distanceMeters(rssi: calibration.rssi(atDistance: 3.3)), 3.3, accuracy: 1e-9)
    }

    /// A touching RSSI no stronger than the center's would make the signal grow with the distance:
    /// it is ignored, and the center alone calibrates with the nominal exponent, as before.
    func test_calibration_withATouchingRSSINoStrongerThanTheCenter_usesTheCenterAlone() {
        for touch in [-76.0, -80.0] {
            let calibration = PathLossModel.calibration(centerRSSI: -76, centerDistanceMeters: 3, touchRSSI: touch)

            XCTAssertFalse(calibration.usesTouchRSSI, "\(touch) dBm")
            XCTAssertEqual(calibration.exponent, PathLossModel.pathLossExponent, accuracy: 1e-9)
            XCTAssertEqual(calibration.distanceMeters(rssi: -76), 3, accuracy: 1e-9)
        }
    }

    /// Readings so far apart that the exponent comes out beyond what any room gives are a mistake
    /// (the phone was not touching, or not in the middle): the center alone calibrates.
    func test_calibration_withAnImplausibleExponent_usesTheCenterAlone() {
        let calibration = PathLossModel.calibration(centerRSSI: -95, centerDistanceMeters: 1.77, touchRSSI: -15)

        XCTAssertFalse(calibration.usesTouchRSSI)
        XCTAssertEqual(calibration.distanceMeters(rssi: -95), 1.77, accuracy: 1e-9)
    }

    func test_calibration_withAnchorsCloserThanTheTouchingDistance_usesTheCenterAlone() {
        let calibration = PathLossModel.calibration(centerRSSI: -40, centerDistanceMeters: 0.05, touchRSSI: -30)

        XCTAssertFalse(calibration.usesTouchRSSI)
        XCTAssertEqual(calibration.distanceMeters(rssi: -40), 0.05, accuracy: 1e-9)
    }

    func test_calibration_withoutATouchingRSSI_usesTheCenterAlone() {
        let calibration = PathLossModel.calibration(centerRSSI: -76, centerDistanceMeters: 3, touchRSSI: nil)

        XCTAssertFalse(calibration.usesTouchRSSI)
        XCTAssertEqual(calibration.distanceMeters(rssi: -76), 3, accuracy: 1e-9)
    }

    func test_referenceRSSI_meansOneMetre() {
        XCTAssertEqual(PathLossModel.distanceMeters(rssi: PathLossModel.referenceRSSI), 1.0, accuracy: tolerance)
    }

    func test_weakerSignal_readsAsFurtherAway() {
        let near = PathLossModel.distanceMeters(rssi: -60.0)
        let far = PathLossModel.distanceMeters(rssi: -80.0)

        XCTAssertGreaterThan(far, near)
    }
}

/// The engine ranges from the median of every advertisement heard in the last few seconds, not from
/// the last one it happened to read.
@MainActor
final class PositioningEngineTests: XCTestCase {
    /// `AnchorStore`'s key in `UserDefaults`: it persists on every change, so a test starts and ends clean.
    private let anchorsKey = "trackerapp.positioning.anchors"

    override func setUp() {
        UserDefaults.standard.removeObject(forKey: anchorsKey)
    }

    override func tearDown() {
        UserDefaults.standard.removeObject(forKey: anchorsKey)
    }

    private struct Rig {
        let engine: PositioningEngine
        let scanner: FakeBeaconScanning
        let ids: [BeaconID]
    }

    private func makeRig(anchorsAt positions: [Point2D]) -> Rig {
        let scanner = FakeBeaconScanning()
        let store = AnchorStore()
        let ids = positions.map { _ in BeaconID.random() }
        for (id, position) in zip(ids, positions) {
            store.addAnchor(id: id, position: position)
        }
        let engine = PositioningEngine(scanner: scanner, anchorStore: store, observerID: BeaconID.random())
        engine.start()
        return Rig(engine: engine, scanner: scanner, ids: ids)
    }

    /// One advertisement, `ageMs` old by the time the engine looks at it.
    private func hear(_ rig: Rig, _ index: Int, rssi: Int, ageMs: Int64 = 0) {
        rig.scanner.onSighting?(
            BeaconSighting(beaconID: rig.ids[index], rssi: rssi, seenAt: Date(), monotonicMs: MonotonicClock.nowMs() - ageMs)
        )
    }

    func test_refresh_rangesFromTheMedianOfTheRecentAdvertisements() throws {
        let rig = makeRig(anchorsAt: [Point2D(x: 0, y: 0)])
        defer { rig.engine.stop() }
        for rssi in [-60, -61, -60, -92, -59] {
            hear(rig, 0, rssi: rssi)  // the last one heard is -59, the median is -60
        }

        rig.engine.refresh()

        let range = try XCTUnwrap(rig.engine.anchorRanges.first)
        XCTAssertEqual(range.distanceMeters, PathLossModel.distanceMeters(rssi: -60), accuracy: 1e-9)
        XCTAssertEqual(try XCTUnwrap(range.rssi), -60, accuracy: 1e-9, "the range keeps the median RSSI it was computed from")
    }

    func test_refresh_ignoresAdvertisementsOlderThanTheWindow() {
        let rig = makeRig(anchorsAt: [Point2D(x: 0, y: 0)])
        defer { rig.engine.stop() }
        hear(rig, 0, rssi: -70, ageMs: 10_000)

        rig.engine.refresh()

        XCTAssertTrue(rig.engine.anchorRanges.isEmpty, "an anchor that has not been heard lately has no range")
    }

    func test_refresh_locatesThePhoneFromThreeAnchors() throws {
        let anchors = [Point2D(x: 0, y: 0), Point2D(x: 6, y: 0), Point2D(x: 3, y: 5)]
        let rig = makeRig(anchorsAt: anchors)
        defer { rig.engine.stop() }
        // What a phone at (2, 2) would hear by the engine's model, rounded to whole dBm.
        let calibration = PathLossModel.calibration(
            centerRSSI: rig.engine.centerRSSI,
            centerDistanceMeters: try XCTUnwrap(PathLossModel.centerDistanceMeters(of: anchors)),
            touchRSSI: rig.engine.touchRSSI
        )
        let phone = Point2D(x: 2, y: 2)
        for (index, anchor) in anchors.enumerated() {
            let rssi = calibration.rssi(atDistance: anchor.distance(to: phone))
            for _ in 0..<5 { hear(rig, index, rssi: Int(rssi.rounded())) }
        }

        rig.engine.refresh()

        let fix = try XCTUnwrap(rig.engine.fix)
        XCTAssertLessThan(fix.position.distance(to: Point2D(x: 2, y: 2)), 0.1)
    }

    /// Beacons advertise at their own pace and the phone misses some packets, so one may be heard 30
    /// times in the window and another 3: each range is that beacon's own median, so the count does
    /// not weigh in, and the same RSSI still puts the phone in the center.
    func test_refresh_beaconsHeardAtDifferentRates_withTheSameRSSI_isTheCenter() throws {
        let corners = [Point2D(x: 0, y: 0), Point2D(x: 2.5, y: 0), Point2D(x: 2.5, y: 2.5), Point2D(x: 0, y: 2.5)]
        let rig = makeRig(anchorsAt: corners)
        defer { rig.engine.stop() }
        for (index, count) in [30, 3, 10, 1].enumerated() {
            for packet in 0..<count {
                hear(rig, index, rssi: -66, ageMs: Int64(packet * 2_900 / count))
            }
        }

        rig.engine.refresh()

        XCTAssertEqual(rig.engine.anchorRanges.count, 4)
        let fix = try XCTUnwrap(rig.engine.fix)
        XCTAssertLessThan(fix.position.distance(to: Point2D(x: 1.25, y: 1.25)), 1e-6)
    }

    /// The RSSI set as the center's is read as the distance from the anchors to their middle, so
    /// hearing it from every corner puts the phone there with the ranges agreeing.
    func test_refresh_theCenterRSSIIsReadAsTheDistanceToTheMiddleOfTheAnchors() throws {
        let corners = [Point2D(x: 0, y: 0), Point2D(x: 2.5, y: 0), Point2D(x: 2.5, y: 2.5), Point2D(x: 0, y: 2.5)]
        let rig = makeRig(anchorsAt: corners)
        defer { rig.engine.stop() }
        rig.engine.centerRSSI = -76
        for index in corners.indices {
            for _ in 0..<5 { hear(rig, index, rssi: -76) }
        }

        rig.engine.refresh()

        for range in rig.engine.anchorRanges {
            XCTAssertEqual(range.distanceMeters, 2.5 / 2.0.squareRoot(), accuracy: 1e-9)
        }
        let fix = try XCTUnwrap(rig.engine.fix)
        XCTAssertLessThan(fix.position.distance(to: Point2D(x: 1.25, y: 1.25)), 1e-6)
        XCTAssertLessThan(fix.uncertaintyMeters, 1e-6)
    }

    /// The touching RSSI is read as the touching distance, with the center RSSI still the center's.
    func test_refresh_theTouchingRSSIIsReadAsTheTouchingDistance() throws {
        let corners = [Point2D(x: 0, y: 0), Point2D(x: 2.5, y: 0), Point2D(x: 2.5, y: 2.5), Point2D(x: 0, y: 2.5)]
        let rig = makeRig(anchorsAt: corners)
        defer { rig.engine.stop() }
        rig.engine.centerRSSI = -72
        rig.engine.touchRSSI = -38
        for _ in 0..<5 { hear(rig, 0, rssi: -38) }
        for index in 1..<4 {
            for _ in 0..<5 { hear(rig, index, rssi: -72) }
        }

        rig.engine.refresh()

        let ranges = Dictionary(uniqueKeysWithValues: rig.engine.anchorRanges.map { ($0.anchor.position, $0.distanceMeters) })
        XCTAssertEqual(try XCTUnwrap(ranges[corners[0]]), PathLossModel.touchDistanceMeters, accuracy: 1e-9)
        XCTAssertEqual(try XCTUnwrap(ranges[corners[1]]), 2.5 / 2.0.squareRoot(), accuracy: 1e-9)
        XCTAssertTrue(rig.engine.calibration.usesTouchRSSI)
    }

    /// The bench, end to end: a 2.5 m square with the default calibration (-70 dBm in the middle,
    /// -30 dBm touching). Hearing -30 from the beacon at (0, 0) puts the phone at (0, 0), whether
    /// the others read what the model says for that corner or, as indoors, closer to the middle's.
    func test_refresh_onTheBench_touchingTheBeaconAtTheOrigin_isTheOrigin() throws {
        let corners = [Point2D(x: 0, y: 0), Point2D(x: 2.5, y: 0), Point2D(x: 2.5, y: 2.5), Point2D(x: 0, y: 2.5)]
        let calibration = PathLossModel.defaultCalibration
        let byTheModel = [2.5, 2.5 * 2.0.squareRoot(), 2.5].map { Int(calibration.rssi(atDistance: $0).rounded()) }
        for others in [byTheModel, [-70, -70, -70], [-72, -76, -72]] {
            let rig = makeRig(anchorsAt: corners)
            defer { rig.engine.stop() }
            for (index, rssi) in zip(0..., [-30] + others) {
                for _ in 0..<5 { hear(rig, index, rssi: rssi) }
            }

            rig.engine.refresh()

            let fix = try XCTUnwrap(rig.engine.fix)
            XCTAssertEqual(fix.position.x, 0, accuracy: 1e-9, "others \(others)")
            XCTAssertEqual(fix.position.y, 0, accuracy: 1e-9, "others \(others)")
        }
    }

    /// The bench, end to end: the same RSSI from all four corners is the middle, (1.25, 1.25),
    /// at any level, touching included.
    func test_refresh_onTheBench_theSameRSSIFromEveryBeacon_isTheMiddle() throws {
        let corners = [Point2D(x: 0, y: 0), Point2D(x: 2.5, y: 0), Point2D(x: 2.5, y: 2.5), Point2D(x: 0, y: 2.5)]
        for rssi in [-30, -45, -60, -70, -80, -90] {
            let rig = makeRig(anchorsAt: corners)
            defer { rig.engine.stop() }
            for index in corners.indices {
                for _ in 0..<5 { hear(rig, index, rssi: rssi) }
            }

            rig.engine.refresh()

            let fix = try XCTUnwrap(rig.engine.fix)
            XCTAssertEqual(fix.position.x, 1.25, accuracy: 1e-6, "\(rssi) dBm")
            XCTAssertEqual(fix.position.y, 1.25, accuracy: 1e-6, "\(rssi) dBm")
        }
    }

    /// From the middle to touching the beacon at (0, 0), 1.77 m away, 3.1 s later: at 1 km/h the
    /// phone shows 0.86 m along the way, towards (0, 0); without a limit, on it.
    func test_refresh_theSpeedLimitMovesThePhoneTowardsTheNewFix() throws {
        let corners = [Point2D(x: 0, y: 0), Point2D(x: 2.5, y: 0), Point2D(x: 2.5, y: 2.5), Point2D(x: 0, y: 2.5)]
        let middle = Point2D(x: 1.25, y: 1.25)
        for limit in [1.0, nil] {
            let rig = makeRig(anchorsAt: corners)
            defer { rig.engine.stop() }
            rig.engine.maxSpeedKmh = limit
            let start = MonotonicClock.nowMs()
            for index in corners.indices {
                for _ in 0..<5 { hear(rig, index, rssi: -70, ageMs: MonotonicClock.nowMs() - start) }
            }
            rig.engine.refresh(nowMs: start)
            XCTAssertLessThan(try XCTUnwrap(rig.engine.fix).position.distance(to: middle), 1e-6)

            // The first readings leave the 3 s window by then.
            for (index, rssi) in zip(0..., [-30, -70, -70, -70]) {
                for _ in 0..<5 { hear(rig, index, rssi: rssi, ageMs: MonotonicClock.nowMs() - (start + 3_050)) }
            }
            rig.engine.refresh(nowMs: start + 3_100)

            let position = try XCTUnwrap(rig.engine.fix).position
            if let limit {
                XCTAssertEqual(position.distance(to: middle), limit / 3.6 * 3.1, accuracy: 1e-6)
                XCTAssertEqual(position.x, position.y, accuracy: 1e-9, "along the diagonal, towards (0, 0)")
            } else {
                XCTAssertEqual(position.distance(to: corners[0]), 0, accuracy: 1e-9)
            }
        }
    }

    /// Touching one beacon and taking what the phone hears now: the strongest anchor's median.
    func test_currentTouchRSSI_isTheStrongestRangedAnchorsMedian() throws {
        let corners = [Point2D(x: 0, y: 0), Point2D(x: 2.5, y: 0), Point2D(x: 2.5, y: 2.5)]
        let rig = makeRig(anchorsAt: corners)
        defer { rig.engine.stop() }
        XCTAssertNil(rig.engine.currentTouchRSSI, "nothing heard yet")
        for (index, rssi) in [-80, -37, -75].enumerated() {
            for _ in 0..<3 { hear(rig, index, rssi: rssi) }
        }

        rig.engine.refresh()

        XCTAssertEqual(try XCTUnwrap(rig.engine.currentTouchRSSI), -37, accuracy: 1e-9)
    }

    /// Standing in the middle and taking what the phone hears now: the mean of the anchors' medians.
    func test_currentCenterRSSI_isTheMeanOfTheRangedAnchorsMedians() throws {
        let corners = [Point2D(x: 0, y: 0), Point2D(x: 2.5, y: 0), Point2D(x: 2.5, y: 2.5), Point2D(x: 0, y: 2.5)]
        let rig = makeRig(anchorsAt: corners)
        defer { rig.engine.stop() }
        XCTAssertNil(rig.engine.currentCenterRSSI, "nothing heard yet")
        for (index, rssi) in [-70, -72, -74, -76].enumerated() {
            for _ in 0..<3 { hear(rig, index, rssi: rssi) }
        }

        rig.engine.refresh()

        XCTAssertEqual(try XCTUnwrap(rig.engine.currentCenterRSSI), -73, accuracy: 1e-9)
    }

    func test_stop_detachesFromTheScanner() {
        let rig = makeRig(anchorsAt: [Point2D(x: 0, y: 0)])

        rig.engine.stop()

        XCTAssertNil(rig.scanner.onSighting)
    }
}

/// The map draws without a device: it is rendered to an image. With TRACKERAPP_MAP_PNG set (run with
/// TEST_RUNNER_TRACKERAPP_MAP_PNG=/tmp/map) the images also go to <path>-light.png and <path>-dark.png.
@MainActor
final class PositionMapViewTests: XCTestCase {
    private func sample() -> (anchors: [Anchor], ranges: [AnchorRange], fix: ObserverFix) {
        let anchors = [("A", 0.4, 0.4), ("B", 5.6, 0.4), ("C", 3.0, 4.6)].map {
            Anchor(id: BeaconID.random(), label: $0.0, position: Point2D(x: $0.1, y: $0.2))
        }
        let truth = Point2D(x: 2.6, y: 2.0)
        let ranges = anchors.map { anchor -> AnchorRange in
            let distance = anchor.position.distance(to: truth) * 1.08
            return AnchorRange(anchor: anchor, distanceMeters: distance, rssi: PathLossModel.defaultCalibration.rssi(atDistance: distance))
        }
        return (anchors, ranges, Multilateration.solve(readings: ranges)!)
    }

    private func render(_ scheme: ColorScheme, anchors: [Anchor], ranges: [AnchorRange], fix: ObserverFix?) -> UIImage? {
        let view = PositionMapView(anchors: anchors, ranges: ranges, fix: fix)
            .frame(width: 360, height: 300)
            .background(Color(.systemBackground))
            .environment(\.colorScheme, scheme)
        let renderer = ImageRenderer(content: view)
        renderer.scale = 2
        return renderer.uiImage
    }

    /// How many different pixel values the image has: a blank image has one.
    private func distinctPixels(_ image: UIImage) -> Int {
        guard let data = image.cgImage?.dataProvider?.data, let bytes = CFDataGetBytePtr(data) else { return 0 }
        var seen = Set<UInt32>()
        let count = CFDataGetLength(data) / 4
        for i in stride(from: 0, to: count, by: 7) {
            seen.insert(UInt32(bytes[i * 4]) << 16 | UInt32(bytes[i * 4 + 1]) << 8 | UInt32(bytes[i * 4 + 2]))
        }
        return seen.count
    }

    func test_theMapRendersBeaconsAndTheiPhone_inLightAndDark() throws {
        let s = sample()
        for (name, scheme) in [("light", ColorScheme.light), ("dark", ColorScheme.dark)] {
            let image = try XCTUnwrap(render(scheme, anchors: s.anchors, ranges: s.ranges, fix: s.fix), name)
            XCTAssertEqual(image.size.width, 360, accuracy: 0.5)
            XCTAssertEqual(image.size.height, 300, accuracy: 0.5)
            XCTAssertGreaterThan(distinctPixels(image), 8, "\(name): the map is not blank")
            if let base = ProcessInfo.processInfo.environment["TRACKERAPP_MAP_PNG"], let png = image.pngData() {
                try png.write(to: URL(fileURLWithPath: "\(base)-\(name).png"))
            }
        }
    }

    func test_theMapRendersWithoutAFix_andWithoutAnchors() throws {
        let s = sample()
        XCTAssertNotNil(render(.light, anchors: s.anchors, ranges: [], fix: nil), "beacons only, before there is a position")
        let empty = try XCTUnwrap(render(.light, anchors: [], ranges: [], fix: nil))
        XCTAssertGreaterThan(distinctPixels(empty), 2, "the empty map still shows its hint")
    }
}
