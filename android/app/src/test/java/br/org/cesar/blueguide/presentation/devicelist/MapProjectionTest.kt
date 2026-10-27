package br.org.cesar.blueguide.presentation.devicelist

import br.org.cesar.blueguide.domain.position.Point2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The same cases as the iOS app's `MapProjection` tests. */
class MapProjectionTest {

    private val width = 360.0
    private val height = 300.0

    @Test
    fun `keeps every point inside the margin`() {
        val points = listOf(Point2D(0.4, 0.4), Point2D(5.6, 0.4), Point2D(3.0, 4.6), Point2D(7.0, -1.0))

        val projection = MapProjection.fitting(points, width, height, margin = 28.0)

        for (point in points) {
            val (x, y) = projection.viewPoint(point)
            assertTrue(x >= 28 - 1e-9 && x <= width - 28 + 1e-9)
            assertTrue(y >= 28 - 1e-9 && y <= height - 28 + 1e-9)
        }
    }

    @Test
    fun `uses the same scale on both axes`() {
        val projection = MapProjection.fitting(listOf(Point2D(0.0, 0.0), Point2D(6.0, 5.0)), width, height)

        val origin = projection.viewPoint(Point2D(0.0, 0.0))
        val oneRight = projection.viewPoint(Point2D(1.0, 0.0))
        val oneUp = projection.viewPoint(Point2D(0.0, 1.0))

        assertEquals("1 m to the right is as long as 1 m up", oneRight.first - origin.first, origin.second - oneUp.second, 1e-9)
        assertEquals(projection.scale, oneRight.first - origin.first, 1e-9)
    }

    @Test
    fun `puts larger y higher on the screen`() {
        val projection = MapProjection.fitting(listOf(Point2D(0.0, 0.0), Point2D(6.0, 5.0)), width, height)

        assertTrue(projection.viewPoint(Point2D(1.0, 4.0)).second < projection.viewPoint(Point2D(1.0, 1.0)).second)
    }

    @Test
    fun `a single point is centered without dividing by zero`() {
        val projection = MapProjection.fitting(listOf(Point2D(3.0, 2.0)), width, height)

        val (x, y) = projection.viewPoint(Point2D(3.0, 2.0))
        assertEquals(width / 2, x, 1e-9)
        assertEquals(height / 2, y, 1e-9)
        assertTrue(projection.scale.isFinite() && projection.scale > 0)
    }

    @Test
    fun `without points it still works`() {
        val projection = MapProjection.fitting(emptyList(), width, height)

        assertTrue(projection.scale.isFinite() && projection.scale > 0)
        assertTrue(projection.viewPoint(Point2D(0.0, 0.0)).first.isFinite())
    }

    @Test
    fun `the visible range covers what the view shows`() {
        val projection = MapProjection.fitting(listOf(Point2D(0.0, 0.0), Point2D(6.0, 5.0)), width, height)

        assertEquals(0.0, projection.viewPoint(Point2D(projection.visibleX.start, 0.0)).first, 1e-6)
        assertEquals(width, projection.viewPoint(Point2D(projection.visibleX.endInclusive, 0.0)).first, 1e-6)
    }

    @Test
    fun `the grid step grows as the map shrinks`() {
        assertEquals(0.5, MapProjection.gridStep(scale = 100.0, minimumGap = 40.0), 1e-9)
        assertEquals(1.0, MapProjection.gridStep(scale = 40.0, minimumGap = 40.0), 1e-9)
        assertEquals(2.0, MapProjection.gridStep(scale = 20.0, minimumGap = 40.0), 1e-9)
        assertEquals(10.0, MapProjection.gridStep(scale = 5.0, minimumGap = 40.0), 1e-9)
    }

    @Test
    fun `ticks are the multiples of the step inside the range, capped`() {
        assertEquals(listOf(-1.0, 0.0, 1.0, 2.0), MapProjection.ticks(-1.2..2.7, 1.0))
        assertTrue("absurd coordinates make no grid", MapProjection.ticks(-1e12..1e12, 0.5).isEmpty())
    }
}
