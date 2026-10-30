package br.org.cesar.blueguide.core.positioning

import br.org.cesar.blueguide.core.model.Point2D
import org.junit.Assert.assertEquals
import org.junit.Test

/** How far the position shown can move towards a new fix. */
class MotionLimitTest {

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
}
