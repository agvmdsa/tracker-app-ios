package br.org.cesar.blueguide.core.positioning

import br.org.cesar.blueguide.core.model.Anchor
import br.org.cesar.blueguide.core.model.DeviceId
import br.org.cesar.blueguide.core.model.Point2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/** The weighted multilateration, with the same cases as the iOS app's `PositioningTests`, so both apps put a phone in the same place. */
class MultilaterationTest {

    /** An equilateral triangle, 3 m sides, centred on the origin — the shape the old,
     *  fixed `AnchorLayout` always used. [Multilateration] makes no such assumption any
     *  more; this is just a convenient fixture shared by the triangle-specific tests. */
    private val triangle = listOf(
        Anchor(DeviceId("1A2B3C4D"), "A", Point2D(0.0, CIRCUMRADIUS)),
        Anchor(DeviceId("3F8A1C04"), "B", Point2D(SIDE / 2, -CIRCUMRADIUS / 2)),
        Anchor(DeviceId("9B20E7D1"), "C", Point2D(-SIDE / 2, -CIRCUMRADIUS / 2))
    )

    private val squareCorners = listOf(Point2D(0.0, 0.0), Point2D(2.5, 0.0), Point2D(2.5, 2.5), Point2D(0.0, 2.5))
    private val squareCenter = Point2D(1.25, 1.25)

    private fun anchorAt(position: Point2D, label: String = "") =
        Anchor(DeviceId("%08X".format(position.hashCode())), label, position)

    /** Four anchors at the corners of an 8 x 6 m room, a phone near one corner, and every reading
     *  off by a fixed fraction of its distance, as RSSI noise makes it. */
    private fun roomReadings(order: List<Int> = listOf(0, 1, 2, 3)): Pair<List<AnchorRange>, Point2D> {
        val corners = listOf(Point2D(0.0, 0.0), Point2D(8.0, 0.0), Point2D(8.0, 6.0), Point2D(0.0, 6.0))
        val factors = listOf(1.05, 1.30, 0.80, 1.10)
        val truth = Point2D(1.0, 1.0)
        return order.map { AnchorRange(anchorAt(corners[it], "$it"), corners[it].distanceTo(truth) * factors[it]) } to truth
    }

    private fun squareReadings(distances: List<Double>): List<AnchorRange> =
        squareCorners.zip(distances).map { (corner, distance) -> AnchorRange(anchorAt(corner), distance) }

    @Test
    fun `exact ranges recover the position they were generated from`() {
        // Inside the triangle, which is where the observer is assumed to walk.
        val truth = Point2D(0.4, 1.2)
        val readings = triangle.map { anchor -> AnchorRange(anchor, anchor.position.distanceTo(truth)) }

        val fix = requireNotNull(Multilateration.solve(readings))

        assertEquals(truth.x, fix.position.x, TOLERANCE)
        assertEquals(truth.y, fix.position.y, TOLERANCE)
        // Circles that genuinely meet leave nothing to disagree about.
        assertEquals(0.0, fix.uncertaintyMeters, TOLERANCE)
    }

    @Test
    fun `inconsistent ranges still produce a fix, with the disagreement reported`() {
        val truth = Point2D(0.0, 0.6)
        val readings = triangle.mapIndexed { index, anchor ->
            // Stretch one range by half a metre: no common intersection any more.
            val error = if (index == 0) 0.5 else 0.0
            AnchorRange(anchor, anchor.position.distanceTo(truth) + error)
        }

        val fix = requireNotNull(Multilateration.solve(readings))

        assertTrue("expected a non-zero residual", fix.uncertaintyMeters > 0.05)
        assertNotEquals(truth.y, fix.position.y, TOLERANCE)
    }

    @Test
    fun `fewer than three anchors is not a fix`() {
        val readings = triangle.take(2).map { AnchorRange(it, 1.0) }

        assertNull(Multilateration.solve(readings))
    }

    @Test
    fun `more than three anchors still recovers the position, overdetermined`() {
        // A fourth anchor outside the triangle: Multilateration (unlike the old,
        // exactly-three Trilateration it replaces) takes any count of three or more.
        val truth = Point2D(0.3, 0.2)
        val anchors = triangle + Anchor(DeviceId("C0FFEE01"), "D", Point2D(2.5, 2.5))
        val readings = anchors.map { anchor -> AnchorRange(anchor, anchor.position.distanceTo(truth)) }

        val fix = requireNotNull(Multilateration.solve(readings))

        assertEquals(truth.x, fix.position.x, TOLERANCE)
        assertEquals(truth.y, fix.position.y, TOLERANCE)
        assertEquals(0.0, fix.uncertaintyMeters, TOLERANCE)
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

    private companion object {
        const val TOLERANCE = 1e-6
        const val SIDE = 3.0
        val CIRCUMRADIUS = SIDE / kotlin.math.sqrt(3.0)
    }
}
