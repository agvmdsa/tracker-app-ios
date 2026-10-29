package br.org.cesar.blueguide.core.positioning

import br.org.cesar.blueguide.core.model.Anchor
import br.org.cesar.blueguide.core.model.DeviceId
import br.org.cesar.blueguide.core.model.Point2D
import br.org.cesar.blueguide.core.ui.DecimalInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * The weighted multilateration, the RSSI window and the "RSSI no centro" calibration, with the
 * same cases as the iOS app's `PositioningTests`, so both apps put a phone in the same place.
 */
class CalibratedPositioningTest {

    private val squareCorners = listOf(Point2D(0.0, 0.0), Point2D(2.5, 0.0), Point2D(2.5, 2.5), Point2D(0.0, 2.5))
    private val squareCenter = Point2D(1.25, 1.25)

    private fun anchorAt(position: Point2D, label: String = "") =
        Anchor(DeviceId("%08X".format(position.hashCode())), label, position)

    // Weighted multilateration

    /** Four anchors at the corners of an 8 x 6 m room, a phone near one corner, and every reading
     *  off by a fixed fraction of its distance, as RSSI noise makes it. */
    private fun roomReadings(order: List<Int> = listOf(0, 1, 2, 3)): Pair<List<AnchorRange>, Point2D> {
        val corners = listOf(Point2D(0.0, 0.0), Point2D(8.0, 0.0), Point2D(8.0, 6.0), Point2D(0.0, 6.0))
        val factors = listOf(1.05, 1.30, 0.80, 1.10)
        val truth = Point2D(1.0, 1.0)
        return order.map { AnchorRange(anchorAt(corners[it], "$it"), corners[it].distanceTo(truth) * factors[it]) } to truth
    }

    @Test
    fun `proportional range errors - the near anchor counts more than the far ones`() {
        val (readings, truth) = roomReadings()

        val fix = requireNotNull(Multilateration.solve(readings))

        // Counting every reading the same puts the fix about 1.6 m away from the phone.
        assertTrue(fix.position.distanceTo(truth) < 0.3)
    }

    @Test
    fun `solve does not depend on the order of the readings`() {
        val forward = requireNotNull(Multilateration.solve(roomReadings().first))
        val reversed = requireNotNull(Multilateration.solve(roomReadings(listOf(3, 2, 1, 0)).first))

        assertEquals(forward.position.x, reversed.position.x, 1e-4)
        assertEquals(forward.position.y, reversed.position.y, 1e-4)
    }

    @Test
    fun `the weight falls with the square of the distance and stops growing near the anchor`() {
        assertEquals(16.0, Multilateration.weight(2.0) / Multilateration.weight(8.0), 0.01)
        assertEquals(Multilateration.weight(0.05), Multilateration.weight(0.2), TOLERANCE)
        assertTrue(Multilateration.weight(0.0).isFinite())
    }

    /** The same RSSI from every corner is the same distance to each, so the fix is the center
     *  whatever that distance is. */
    @Test
    fun `equal RSSI from the corners of a square is the center`() {
        for (rssi in listOf(-30.0, -40.0, -50.0, -60.0, -64.0, -70.0, -80.0)) {
            val readings = squareCorners.map { AnchorRange(anchorAt(it), PathLossModel.distanceMeters(rssi)) }

            val fix = requireNotNull(Multilateration.solve(readings))

            assertTrue("$rssi dBm", fix.position.distanceTo(squareCenter) < 1e-6)
        }
    }

    /** Touching a beacon is the surest reading there is: indoors the far beacons read stronger
     *  than the model expects (here all three as the center), and with a 0.5 m floor on the error
     *  they pulled the fix 0.6 m towards the middle. */
    @Test
    fun `a beacon a few centimetres away decides the fix even when the far ones read short`() {
        val truth = Point2D(0.2, 0.2)
        val readings = squareCorners.mapIndexed { index, corner ->
            AnchorRange(anchorAt(corner), if (index == 0) corner.distanceTo(truth) else 2.5 / sqrt(2.0))
        }

        val fix = requireNotNull(Multilateration.solve(readings))

        assertTrue("fix at ${fix.position}", fix.position.distanceTo(truth) < 0.1)
    }

    private fun squareReadings(distances: List<Double>): List<AnchorRange> =
        squareCorners.zip(distances).map { (corner, distance) -> AnchorRange(anchorAt(corner), distance) }

    /** Reading as touching a beacon ("RSSI encostado", the touching distance) puts the phone on it,
     *  however wrong the others are: here they claim 0.6 m, which no point near A allows. */
    @Test
    fun `a beacon read as touching puts the phone on it whatever the others say`() {
        for (others in listOf(listOf(0.6, 0.6, 0.6), listOf(4.0, 6.0, 6.0), listOf(1.77, 1.77, 1.77), listOf(2.5, 0.6, 6.0))) {
            for (touching in listOf(PathLossModel.TOUCH_DISTANCE_METERS, 0.05)) {
                val fix = requireNotNull(Multilateration.solve(squareReadings(listOf(touching) + others)))

                assertEquals("others $others: fix at ${fix.position}", 0.0, fix.position.distanceTo(squareCorners[0]), 1e-9)
            }
        }
    }

    /** A little weaker than touching, the phone stays within the extra distance of the beacon,
     *  counted from touching it: 0.3 m by the model is at most 0.2 m from A. */
    @Test
    fun `a beacon read near touching keeps the phone within the extra distance`() {
        for (others in listOf(listOf(1.0, 1.0, 1.0), listOf(1.77, 1.77, 1.77))) {
            val fix = requireNotNull(Multilateration.solve(squareReadings(listOf(0.3) + others)))

            assertTrue("others $others", fix.position.distanceTo(squareCorners[0]) <= 0.3 - PathLossModel.TOUCH_DISTANCE_METERS + 1e-9)
        }
    }

    /** A first step that made things worse used to end the search where it started: here 1.6 m
     *  from A, which reads 0.8 m. Halving the step instead goes on to the minimum. */
    @Test
    fun `a bad start does not strand the fix`() {
        val fix = requireNotNull(Multilateration.solve(squareReadings(listOf(0.8, 1.77, 2.5, 6.0))))

        assertEquals("fix at ${fix.position}", 0.8, fix.position.distanceTo(squareCorners[0]), 0.15)
    }

    // Speed limit

    @Test
    fun `the speed limit moves towards the new fix by at most the speed times the time`() {
        // 3.6 km/h is 1 m/s: 2 s allow 2 m of the 5 m to (3, 4).
        val limited = MotionLimit.limited(Point2D(0.0, 0.0), Point2D(3.0, 4.0), maxSpeedKmh = 3.6, elapsedSeconds = 2.0)

        assertEquals(1.2, limited.x, 1e-9)
        assertEquals(1.6, limited.y, 1e-9)
    }

    @Test
    fun `within reach the speed limit is the new fix`() {
        assertEquals(Point2D(0.3, 0.4), MotionLimit.limited(Point2D(0.0, 0.0), Point2D(0.3, 0.4), 3.6, 1.0))
    }

    @Test
    fun `without a previous fix or a limit it is the new fix`() {
        val target = Point2D(3.0, 4.0)
        assertEquals(target, MotionLimit.limited(null, target, 2.0, 1.0))
        assertEquals(target, MotionLimit.limited(Point2D(0.0, 0.0), target, null, 1.0))
    }

    @Test
    fun `with no time elapsed it stays put`() {
        val previous = Point2D(1.0, 1.0)
        assertEquals(previous, MotionLimit.limited(previous, Point2D(3.0, 4.0), 2.0, 0.0))
        assertEquals(previous, MotionLimit.limited(previous, Point2D(3.0, 4.0), 2.0, -1.0))
    }

    // Path loss model and calibration

    @Test
    fun `seventy dBm is the distance from a corner to the center of the bench square`() {
        assertEquals(2.5 / sqrt(2.0), PathLossModel.distanceMeters(-70.0), 0.02)
    }

    @Test
    fun `the center distance is the mean distance from the anchors to their middle`() {
        assertEquals(2.5 / sqrt(2.0), requireNotNull(PathLossModel.centerDistanceMeters(squareCorners)), 1e-9)
        assertNull("one anchor has no middle to be away from", PathLossModel.centerDistanceMeters(listOf(Point2D(1.0, 1.0))))
        assertNull(PathLossModel.centerDistanceMeters(emptyList()))
    }

    @Test
    fun `thirty dBm is the touching distance`() {
        assertEquals(PathLossModel.TOUCH_DISTANCE_METERS, PathLossModel.distanceMeters(-30.0), 1e-9)
    }

    /** Two readings, two unknowns: the center RSSI is the center distance and the touching RSSI is
     *  the touching distance, which fixes both the RSSI at 1 m and the path loss exponent. */
    @Test
    fun `the calibration reads the center and the touching RSSI as their distances`() {
        val calibration = PathLossModel.calibration(centerRssi = -76.0, centerDistanceMeters = 3.0, touchRssi = -38.0)

        assertTrue(calibration.usesTouchRssi)
        assertEquals(3.0, calibration.distanceMeters(-76.0), 1e-9)
        assertEquals(PathLossModel.TOUCH_DISTANCE_METERS, calibration.distanceMeters(-38.0), 1e-9)
        // 38 dB over a 30x change in distance.
        assertEquals(38 / (10 * log10(30.0)), calibration.exponent, 1e-9)
    }

    /** A touching RSSI no stronger than the center's would make the signal grow with the distance:
     *  it is ignored, and the center alone calibrates with the nominal exponent. */
    @Test
    fun `a touching RSSI no stronger than the center is ignored`() {
        for (touch in listOf(-76.0, -80.0)) {
            val calibration = PathLossModel.calibration(-76.0, 3.0, touch)

            assertFalse("$touch dBm", calibration.usesTouchRssi)
            assertEquals(PathLossModel.PATH_LOSS_EXPONENT, calibration.exponent, 1e-9)
            assertEquals(3.0, calibration.distanceMeters(-76.0), 1e-9)
        }
    }

    @Test
    fun `an implausible exponent ignores the touching RSSI`() {
        val calibration = PathLossModel.calibration(-95.0, 1.77, -15.0)

        assertFalse(calibration.usesTouchRssi)
        assertEquals(1.77, calibration.distanceMeters(-95.0), 1e-9)
    }

    @Test
    fun `anchors closer than the touching distance ignore the touching RSSI`() {
        val calibration = PathLossModel.calibration(-40.0, 0.05, -30.0)

        assertFalse(calibration.usesTouchRssi)
        assertEquals(0.05, calibration.distanceMeters(-40.0), 1e-9)
    }

    @Test
    fun `without a touching RSSI the center alone calibrates`() {
        val calibration = PathLossModel.calibration(-76.0, 3.0, null)

        assertFalse(calibration.usesTouchRssi)
        assertEquals(3.0, calibration.distanceMeters(-76.0), 1e-9)
    }

    @Test
    fun `reference RSSI means one metre`() {
        assertEquals(1.0, PathLossModel.distanceMeters(PathLossModel.REFERENCE_RSSI), TOLERANCE)
    }

    @Test
    fun `a weaker signal reads as further away`() {
        assertTrue(PathLossModel.distanceMeters(-80.0) > PathLossModel.distanceMeters(-60.0))
    }

    @Test
    fun `rssi for a distance is the inverse of the distance for an rssi`() {
        val calibration = PathLossModel.calibration(-70.0, 2.0, -36.0)

        assertEquals(3.3, calibration.distanceMeters(calibration.rssi(atDistance = 3.3)), 1e-9)
    }

    // RSSI window

    private fun window(readings: List<Int>, capacity: Int = 100, durationMs: Long = 3_000): RssiWindow =
        readings.foldIndexed(RssiWindow(durationMs, capacity)) { index, window, rssi -> window.plus(rssi, index.toLong()) }

    @Test
    fun `the median of an odd count`() {
        assertEquals(-61.0, requireNotNull(window(listOf(-60, -62, -61)).median(500)), TOLERANCE)
    }

    @Test
    fun `the median of an even count is the mean of the two middle readings`() {
        assertEquals(-61.5, requireNotNull(window(listOf(-60, -63, -61, -62)).median(500)), TOLERANCE)
    }

    @Test
    fun `a single outlier does not move the median`() {
        assertEquals(-60.0, requireNotNull(window(listOf(-60, -61, -60, -92, -59)).median(500)), TOLERANCE)
    }

    @Test
    fun `the window drops readings older than its duration`() {
        val window = RssiWindow(1_000, 100).plus(-90, 0).plus(-60, 900)

        assertEquals(-75.0, requireNotNull(window.median(950)), TOLERANCE)
        assertEquals("only the reading from 900 ms is recent at 1500 ms", -60.0, requireNotNull(window.median(1_500)), TOLERANCE)
        assertNull("nothing heard in the last second", window.median(2_000))
    }

    @Test
    fun `a window without readings has no median`() {
        assertNull(RssiWindow(3_000, 10).median(0))
    }

    @Test
    fun `a full window keeps the newest readings`() {
        val full = window(listOf(-90, -90, -60, -61, -62), capacity = 3)

        assertEquals(3, full.count)
        assertEquals(-61.0, requireNotNull(full.median(400)), TOLERANCE)
    }

    @Test
    fun `samples since a time are the readings heard from then on`() {
        val window = RssiWindow(3_000, 100).plus(-60, 0).plus(-61, 500).plus(-62, 1_200)

        assertEquals(listOf(-61, -62), window.samples(sinceMs = 400))
    }

    // Decimal input

    @Test
    fun `decimal input accepts a dot or a comma as the separator`() {
        assertEquals(2.5, requireNotNull(DecimalInput.parse("2,5")), TOLERANCE)
        assertEquals(2.5, requireNotNull(DecimalInput.parse("2.5")), TOLERANCE)
        assertEquals(-1.25, requireNotNull(DecimalInput.parse("-1,25")), TOLERANCE)
        assertEquals(3.0, requireNotNull(DecimalInput.parse("+3")), TOLERANCE)
        assertEquals(0.5, requireNotNull(DecimalInput.parse(",5")), TOLERANCE)
        assertEquals(2.0, requireNotNull(DecimalInput.parse("2.")), TOLERANCE)
        assertEquals(4.0, requireNotNull(DecimalInput.parse("4")), TOLERANCE)
    }

    @Test
    fun `decimal input ignores surrounding spaces`() {
        assertEquals(1.5, requireNotNull(DecimalInput.parse("  1,5 ")), TOLERANCE)
    }

    @Test
    fun `decimal input refuses what is not a plain number`() {
        for (text in listOf("", " ", "-", ",", "abc", "1e3", "nan", "NaN", "inf", "Infinity", "1,2,3", "1.234,5", "1 5", "--1", "0x10")) {
            assertNull("'$text'", DecimalInput.parse(text))
        }
    }

    @Test
    fun `the text for a field is a plain decimal without exponent or trailing zeros`() {
        assertEquals("2.5", DecimalInput.text(2.5))
        assertEquals("-1", DecimalInput.text(-1.0))
        assertEquals("0.125", DecimalInput.text(0.125))
        assertEquals("0", DecimalInput.text(-0.0001))
        assertEquals("0.00001 has no exponent", "0", DecimalInput.text(0.00001))
        assertEquals("1234567", DecimalInput.text(1234567.0))
        assertEquals("", DecimalInput.text(Double.NaN))
    }

    @Test
    fun `what a field shows can be read back`() {
        for (value in listOf(2.5, -1.0, 0.125, 1234567.0, 0.001)) {
            assertEquals(value, requireNotNull(DecimalInput.parse(DecimalInput.text(value))), TOLERANCE)
        }
    }

    private companion object {
        const val TOLERANCE = 1e-6
    }
}
