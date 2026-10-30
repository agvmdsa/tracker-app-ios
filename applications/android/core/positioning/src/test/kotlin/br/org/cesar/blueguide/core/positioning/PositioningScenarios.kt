package br.org.cesar.blueguide.core.positioning

import br.org.cesar.blueguide.core.model.Anchor
import br.org.cesar.blueguide.core.model.DeviceId
import br.org.cesar.blueguide.core.model.Point2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What any [PositioningEngine] has to do, whatever is inside it: the acceptance criteria of a
 * rewrite. A new engine gets a subclass that only says how to build it, like
 * [PathLossPositioningScenariosTest]. Everything here goes through the contract, on the 2.5 m
 * bench square with the app's default calibration (-70 dBm in the middle, -30 dBm touching); how
 * close is close enough is part of each scenario.
 *
 * How one engine gets there is tested next to it (`PathLossPositioningEngineTest` and the tests of
 * each step), and goes with it when it is replaced.
 */
abstract class PositioningScenarios {

    /** A new engine, positioning with [setup]. */
    protected abstract fun engine(setup: PositioningSetup): PositioningEngine

    private val corners = listOf(Point2D(0.0, 0.0), Point2D(2.5, 0.0), Point2D(2.5, 2.5), Point2D(0.0, 2.5))
    private val middle = Point2D(1.25, 1.25)
    private val ids = listOf("9F3C1A02", "3F8A1C04", "1A2B3C4D", "B7E2F910").map(::DeviceId)
    private val anchors = corners.mapIndexed { index, corner -> Anchor(ids[index], "ABCD"[index].toString(), corner) }

    /** Readings that place the phone somewhere with no symmetry to lean on. */
    private val uneven = listOf(-55, -68, -74, -66)

    private fun bench(maxSpeedKmh: Double? = null, anchors: List<Anchor> = this.anchors) =
        engine(PositioningSetup(anchors, maxSpeedKmh = maxSpeedKmh))

    /** Five advertisements from each anchor, A first, at [rssis]. */
    private fun PositioningEngine.hear(rssis: List<Int>, atMs: Long) =
        rssis.forEachIndexed { index, rssi -> repeat(5) { record(ids[index], rssi, atMs) } }

    private fun PositioningEngine.positionAfterHearing(rssis: List<Int>): Point2D {
        hear(rssis, NOW)
        return requireNotNull(refresh(NOW).fix) { "no position for $rssis" }.position
    }

    private fun assertNear(message: String, expected: Point2D, actual: Point2D, within: Double) =
        assertTrue("$message: $actual is ${actual.distanceTo(expected)} m from $expected", actual.distanceTo(expected) <= within)

    @Test
    fun `fewer than three anchors heard is no position`() {
        val engine = bench()
        engine.hear(listOf(-60, -60), NOW)

        val update = engine.refresh(NOW)

        assertNull(update.fix)
        assertEquals(ids.take(2).toSet(), update.ranges.map { it.anchor.id }.toSet())
    }

    @Test
    fun `the same RSSI from every anchor is the middle`() {
        for (rssi in listOf(-45, -60, -70, -80)) {
            assertNear("$rssi dBm", middle, bench().positionAfterHearing(List(4) { rssi }), CENTIMETRE)
        }
    }

    /** "RSSI encostado" is what the phone reads touching a beacon. */
    @Test
    fun `reading the touching RSSI from a beacon is being on it`() {
        for (others in listOf(listOf(-70, -70, -70), listOf(-72, -76, -72))) {
            assertNear("others $others", corners[0], bench().positionAfterHearing(listOf(-30) + others), TOUCHING)
        }
    }

    @Test
    fun `a stronger beacon pulls the phone towards it`() {
        val position = bench().positionAfterHearing(listOf(-62, -70, -70, -70))

        assertTrue("$position", position.distanceTo(corners[0]) < middle.distanceTo(corners[0]) - 0.1)
        assertEquals("on the diagonal, by symmetry", position.x, position.y, CENTIMETRE)
    }

    /** Up to a centimetre: an iterative solver stops a little apart from different starts (the
     *  app's ends about 1 mm away, its linear start depending on which anchor comes first). */
    @Test
    fun `the order the anchors are listed in doesn't matter`() {
        val listed = bench().positionAfterHearing(uneven)
        val reversed = bench(anchors = anchors.reversed()).positionAfterHearing(uneven)

        assertNear("reversed", listed, reversed, CENTIMETRE)
    }

    @Test
    fun `beacons that aren't anchors don't move the phone`() {
        val alone = bench().positionAfterHearing(uneven)
        val engine = bench()
        repeat(5) { engine.record(DeviceId("CAFE0001"), -40, NOW) }

        assertNear("with a stranger nearby", alone, engine.positionAfterHearing(uneven), MILLIMETRE)
    }

    @Test
    fun `a beacon not heard for ten seconds is not used`() {
        val engine = bench()
        engine.hear(List(4) { -70 }, NOW)
        engine.hear(List(3) { -70 }, NOW + 10_000)

        assertEquals(ids.take(3).toSet(), engine.refresh(NOW + 10_000).ranges.map { it.anchor.id }.toSet())
    }

    @Test
    fun `an anchor taken out of the setup is not used`() {
        val engine = bench()
        engine.hear(List(4) { -70 }, NOW)

        engine.setup = engine.setup.copy(anchors = anchors.take(3))

        assertEquals(ids.take(3).toSet(), engine.refresh(NOW).ranges.map { it.anchor.id }.toSet())
    }

    /** From the middle to touching the beacon at (0, 0), 1.77 m away, 10 s later: at 0.5 km/h
     *  that is out of reach (1.39 m), so the phone shows on its way; without a limit, there. */
    @Test
    fun `the speed limit holds the phone back, and without it the phone follows`() {
        for (limit in listOf(0.5, null)) {
            val engine = bench(maxSpeedKmh = limit)
            val before = engine.positionAfterHearing(List(4) { -70 })

            engine.hear(listOf(-30, -70, -70, -70), NOW + 10_000)
            val after = requireNotNull(engine.refresh(NOW + 10_000).fix).position

            if (limit != null) {
                assertTrue("moved ${after.distanceTo(before)} m", after.distanceTo(before) <= limit / 3.6 * 10 + MILLIMETRE)
                assertTrue("towards (0, 0)", after.distanceTo(corners[0]) < before.distanceTo(corners[0]) - 0.1)
            } else {
                assertNear("no limit", corners[0], after, TOUCHING)
            }
        }
    }

    @Test
    fun `a new setup ranges again at once, at the moment of the last refresh`() {
        val engine = bench()
        assertNull("nothing to range again before the first refresh", engine.rangeAgain())
        engine.hear(List(4) { -76 }, NOW)
        val before = engine.refresh(NOW)

        engine.setup = engine.setup.copy(centerRssi = -76.0)
        val again = requireNotNull(engine.rangeAgain())

        assertEquals(NOW, again.atMs)
        assertNotEquals(
            "a new center RSSI changes the ranges",
            before.ranges.map {
                it.distanceMeters
            },
            again.ranges.map { it.distanceMeters }
        )
    }

    @Test
    fun `reset forgets what was heard`() {
        val engine = bench()
        engine.positionAfterHearing(List(4) { -70 })

        engine.reset()

        assertNull(engine.rangeAgain())
        val update = engine.refresh(NOW)
        assertTrue(update.ranges.isEmpty())
        assertNull(update.fix)
    }

    /** The API gets each range, and "Usar a leitura de agora" takes the RSSI from them. */
    @Test
    fun `each anchor heard is ranged once, with the RSSI it was heard at`() {
        val engine = bench()
        engine.hear(uneven, NOW)

        val ranges = engine.refresh(NOW).ranges

        assertEquals(ids, ranges.map { it.anchor.id }.sortedBy { ids.indexOf(it) })
        assertEquals(uneven.map(Int::toDouble), ids.map { id -> ranges.single { it.anchor.id == id }.rssi })
        assertTrue(ranges.all { it.distanceMeters > 0 })
    }

    /** What the API's `sampleCount` reports. */
    @Test
    fun `the stats count the advertisements since the last refresh`() {
        val engine = bench()
        engine.hear(List(4) { -70 }, NOW - 500)
        val first = engine.refresh(NOW)
        engine.hear(List(4) { -70 }, NOW + 500)
        val second = engine.refresh(NOW + 1_000)

        ids.forEach { id ->
            assertEquals(5, first.sampleStats.getValue(id).count)
            assertEquals(5, second.sampleStats.getValue(id).count)
        }
    }

    private companion object {
        const val NOW = 100_000L
        const val MILLIMETRE = 0.001
        const val CENTIMETRE = 0.01

        /** The 10 cm between the antennas with the phone touching a beacon. */
        const val TOUCHING = 0.1
    }
}

/** The app's engine against the scenarios. */
class PathLossPositioningScenariosTest : PositioningScenarios() {
    override fun engine(setup: PositioningSetup): PositioningEngine = PathLossPositioningEngine(setup = setup)
}
