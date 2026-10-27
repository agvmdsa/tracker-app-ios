package br.org.cesar.blueguide.domain.position

import br.org.cesar.blueguide.domain.entity.DeviceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PositioningTest {

    /** An equilateral triangle, 3 m sides, centred on the origin — the shape the old,
     *  fixed `AnchorLayout` always used. [Multilateration] makes no such assumption any
     *  more; this is just a convenient fixture shared by the triangle-specific tests. */
    private val triangle = listOf(
        Anchor(DeviceId("1A2B3C4D"), "A", Point2D(0.0, CIRCUMRADIUS)),
        Anchor(DeviceId("3F8A1C04"), "B", Point2D(SIDE / 2, -CIRCUMRADIUS / 2)),
        Anchor(DeviceId("9B20E7D1"), "C", Point2D(-SIDE / 2, -CIRCUMRADIUS / 2))
    )

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
    fun `reference rssi means one metre`() {
        assertEquals(1.0, PathLossModel.distanceMeters(PathLossModel.REFERENCE_RSSI), TOLERANCE)
    }

    @Test
    fun `weaker signal reads as further away`() {
        val near = PathLossModel.distanceMeters(-60.0)
        val far = PathLossModel.distanceMeters(-80.0)

        assertTrue("$far should exceed $near", far > near)
    }

    private companion object {
        const val TOLERANCE = 1e-6
        const val SIDE = 3.0
        val CIRCUMRADIUS = SIDE / kotlin.math.sqrt(3.0)
    }
}
