package br.org.cesar.blueguide.core.positioning

import br.org.cesar.blueguide.core.model.Anchor
import br.org.cesar.blueguide.core.model.DeviceId
import br.org.cesar.blueguide.core.model.Point2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The phone is ranged from the median of every advertisement heard in the last few seconds, once
 * per refresh, like the iOS app's `PositioningEngine` — not from the last packet it happened to read.
 */
class PositioningEngineTest {

    private val square = listOf(Point2D(0.0, 0.0), Point2D(2.5, 0.0), Point2D(2.5, 2.5), Point2D(0.0, 2.5))
    private val ids = listOf("9F3C1A02", "3F8A1C04", "1A2B3C4D", "B7E2F910").map(::DeviceId)

    private fun anchorsAt(positions: List<Point2D>) =
        positions.mapIndexed { index, position -> Anchor(ids[index], "ABCD"[index].toString(), position) }

    private fun engine(
        positions: List<Point2D>,
        estimator: PositionEstimator = Multilateration,
        setup: (PositioningSetup) -> PositioningSetup = { it }
    ) = PositioningEngine(estimator, setup(PositioningSetup(anchorsAt(positions))))

    private fun PositioningEngine.hear(index: Int, rssi: Int, atMs: Long, times: Int = 1) =
        repeat(times) { record(ids[index], rssi, atMs) }

    @Test
    fun `refresh ranges from the median of the recent advertisements`() {
        val engine = engine(square.take(1))
        for (rssi in listOf(-60, -61, -60, -92, -59)) engine.hear(0, rssi, NOW)

        val range = engine.refresh(NOW).ranges.single()

        assertEquals(PathLossModel.distanceMeters(-60.0), range.distanceMeters, 1e-9)
        assertEquals("the range keeps the median RSSI it was computed from", -60.0, requireNotNull(range.rssi), 1e-9)
    }

    @Test
    fun `refresh ignores advertisements older than the window`() {
        val engine = engine(square.take(1))
        engine.hear(0, -70, NOW - 10_000)

        assertTrue("an anchor that has not been heard lately has no range", engine.refresh(NOW).ranges.isEmpty())
    }

    @Test
    fun `refresh locates the phone from three anchors`() {
        val anchors = listOf(Point2D(0.0, 0.0), Point2D(6.0, 0.0), Point2D(3.0, 5.0))
        val engine = engine(anchors)
        val calibration = PathLossModel.calibration(
            PathLossModel.DEFAULT_CENTER_RSSI,
            requireNotNull(PathLossModel.centerDistanceMeters(anchors)),
            PathLossModel.DEFAULT_TOUCH_RSSI
        )
        val phone = Point2D(2.0, 2.0)
        anchors.forEachIndexed { index, anchor ->
            engine.hear(index, calibration.rssi(atDistance = anchor.distanceTo(phone)).roundToInt(), NOW, times = 5)
        }

        val fix = requireNotNull(engine.refresh(NOW).fix)

        assertTrue(fix.position.distanceTo(phone) < 0.1)
    }

    /** Beacons advertise at their own pace and the phone misses some packets: each range is that
     *  beacon's own median, so the count does not weigh in. */
    @Test
    fun `beacons heard at different rates with the same RSSI is the center`() {
        val engine = engine(square)
        listOf(30, 3, 10, 1).forEachIndexed { index, count ->
            for (packet in 0 until count) engine.hear(index, -66, NOW - packet * 2_900L / count)
        }

        val update = engine.refresh(NOW)

        assertEquals(4, update.ranges.size)
        assertTrue(requireNotNull(update.fix).position.distanceTo(Point2D(1.25, 1.25)) < 1e-6)
    }

    @Test
    fun `the center RSSI is read as the distance to the middle of the anchors`() {
        val engine = engine(square) { it.copy(centerRssi = -76.0) }
        square.indices.forEach { index -> engine.hear(index, -76, NOW, times = 5) }

        val update = engine.refresh(NOW)

        update.ranges.forEach { assertEquals(2.5 / sqrt(2.0), it.distanceMeters, 1e-9) }
        val fix = requireNotNull(update.fix)
        assertTrue(fix.position.distanceTo(Point2D(1.25, 1.25)) < 1e-6)
        assertTrue(fix.uncertaintyMeters < 1e-6)
    }

    @Test
    fun `a new calibration ranges again at once, from the same readings`() {
        val engine = engine(square)
        square.indices.forEach { index -> engine.hear(index, -76, NOW) }
        engine.refresh(NOW)

        engine.setup = engine.setup.copy(centerRssi = -76.0)
        val update = requireNotNull(engine.rangeAgain())

        assertEquals(NOW, update.atMs)
        update.ranges.forEach { assertEquals(2.5 / sqrt(2.0), it.distanceMeters, 1e-9) }
    }

    @Test
    fun `there is nothing to range again before the first refresh`() {
        val engine = engine(square)
        square.indices.forEach { index -> engine.hear(index, -76, NOW) }

        assertNull(engine.rangeAgain())
    }

    /** The touching RSSI is read as the touching distance, with the center RSSI still the center's. */
    @Test
    fun `the touching RSSI is read as the touching distance`() {
        val engine = engine(square) { it.copy(centerRssi = -72.0, touchRssi = -38.0) }
        engine.hear(0, -38, NOW, times = 5)
        (1..3).forEach { index -> engine.hear(index, -72, NOW, times = 5) }

        val ranges = engine.refresh(NOW).ranges.associate { it.anchor.position to it.distanceMeters }

        assertEquals(PathLossModel.TOUCH_DISTANCE_METERS, requireNotNull(ranges[square[0]]), 1e-9)
        assertEquals(2.5 / sqrt(2.0), requireNotNull(ranges[square[1]]), 1e-9)
        assertTrue(engine.setup.calibration.usesTouchRssi)
    }

    /** The bench, end to end: a 2.5 m square with the default calibration (-70 dBm in the middle,
     *  -30 dBm touching). Hearing -30 from the beacon at (0, 0) puts the phone at (0, 0), whether
     *  the others read what the model says for that corner or, as indoors, closer to the middle's. */
    @Test
    fun `on the bench touching the beacon at the origin is the origin`() {
        val calibration = PathLossModel.DEFAULT_CALIBRATION
        val byTheModel = listOf(2.5, 2.5 * sqrt(2.0), 2.5).map { calibration.rssi(atDistance = it).roundToInt() }
        for (others in listOf(byTheModel, listOf(-70, -70, -70), listOf(-72, -76, -72))) {
            val engine = engine(square)
            (listOf(-30) + others).forEachIndexed { index, rssi -> engine.hear(index, rssi, NOW, times = 5) }

            val fix = requireNotNull(engine.refresh(NOW).fix)

            assertEquals("others $others", 0.0, fix.position.x, 1e-9)
            assertEquals("others $others", 0.0, fix.position.y, 1e-9)
        }
    }

    /** The bench, end to end: the same RSSI from all four corners is the middle, (1.25, 1.25),
     *  at any level, touching included. */
    @Test
    fun `on the bench the same RSSI from every beacon is the middle`() {
        for (rssi in listOf(-30, -45, -60, -70, -80, -90)) {
            val engine = engine(square)
            square.indices.forEach { index -> engine.hear(index, rssi, NOW, times = 5) }

            val fix = requireNotNull(engine.refresh(NOW).fix)

            assertEquals("$rssi dBm", 1.25, fix.position.x, 1e-6)
            assertEquals("$rssi dBm", 1.25, fix.position.y, 1e-6)
        }
    }

    /** From the middle to touching the beacon at (0, 0), 1.77 m away, 3.1 s later: at 1 km/h the
     *  phone shows 0.86 m along the way, towards (0, 0); without a limit, on it. */
    @Test
    fun `the speed limit moves the phone towards the new fix`() {
        val middle = Point2D(1.25, 1.25)
        for (limit in listOf(1.0, null)) {
            val engine = engine(square) { it.copy(maxSpeedKmh = limit) }
            square.indices.forEach { index -> engine.hear(index, -70, NOW, times = 5) }
            assertTrue(requireNotNull(engine.refresh(NOW).fix).position.distanceTo(middle) < 1e-6)

            // The first readings leave the 3 s window by then.
            listOf(-30, -70, -70, -70).forEachIndexed { index, rssi -> engine.hear(index, rssi, NOW + 3_050, times = 5) }
            val position = requireNotNull(engine.refresh(NOW + 3_100).fix).position

            if (limit != null) {
                assertEquals(limit / 3.6 * 3.1, position.distanceTo(middle), 1e-6)
                assertEquals("along the diagonal, towards (0, 0)", position.x, position.y, 1e-9)
            } else {
                assertEquals(0.0, position.distanceTo(square[0]), 1e-9)
            }
        }
    }

    /** A new calibration ranges again at the same moment: it starts from where the phone was
     *  shown before that refresh, not from the refresh's own result, so it can still move. */
    @Test
    fun `ranging again after a calibration change is limited from before the refresh`() {
        val engine = engine(square) { it.copy(maxSpeedKmh = 1.0) }
        square.indices.forEach { index -> engine.hear(index, -70, NOW, times = 5) }
        engine.refresh(NOW)
        listOf(-30, -70, -70, -70).forEachIndexed { index, rssi -> engine.hear(index, rssi, NOW + 3_050, times = 5) }
        val before = requireNotNull(engine.refresh(NOW + 3_100).fix).position

        engine.setup = engine.setup.copy(centerRssi = -71.0)
        val again = requireNotNull(requireNotNull(engine.rangeAgain()).fix).position

        assertEquals(before.distanceTo(Point2D(1.25, 1.25)), again.distanceTo(Point2D(1.25, 1.25)), 1e-6)
    }

    @Test
    fun `each range reports how many advertisements arrived since the last refresh and their spread`() {
        val engine = engine(square.take(1))
        engine.hear(0, -60, NOW - 1_500)
        for (rssi in listOf(-60, -62, -64)) engine.hear(0, rssi, NOW - 200)

        val stats = requireNotNull(engine.refresh(NOW).sampleStats[ids[0]])

        assertEquals(3, stats.count)
        assertEquals(2.0, stats.stdDev, 1e-9)
    }

    /** Where a trained model plugs in: the engine ranges, the estimator places, and the speed limit
     *  still applies to what it says. */
    @Test
    fun `the estimator places the phone from the ranges, and the speed limit follows it`() {
        val seen = mutableListOf<List<AnchorRange>>()
        val estimator = PositionEstimator { ranges ->
            seen += ranges
            ObserverFix(ranges, Point2D(2.0, 0.0), uncertaintyMeters = 0.3)
        }
        val engine = engine(square, estimator) { it.copy(maxSpeedKmh = null) }
        square.indices.forEach { index -> engine.hear(index, -70, NOW) }

        val fix = requireNotNull(engine.refresh(NOW).fix)

        assertEquals(Point2D(2.0, 0.0), fix.position)
        assertEquals(0.3, fix.uncertaintyMeters, 0.0)
        assertEquals(ids, seen.single().map { it.anchor.id })
    }

    @Test
    fun `reset forgets the readings and the position shown`() {
        val engine = engine(square)
        square.indices.forEach { index -> engine.hear(index, -70, NOW) }
        engine.refresh(NOW)

        engine.reset()

        assertNull("nothing to range again", engine.rangeAgain())
        val update = engine.refresh(NOW)
        assertTrue(update.ranges.isEmpty())
        assertNull(update.fix)
    }

    /** Only anchors are listened to, so readings heard before a beacon became one don't count,
     *  and those of a beacon that stops being one are dropped. */
    @Test
    fun `only the readings of the setup's anchors are kept`() {
        val engine = engine(square.take(3))
        square.indices.forEach { index -> engine.hear(index, -60, NOW) }
        engine.setup = engine.setup.copy(anchors = anchorsAt(square))
        assertEquals("heard before it was an anchor", ids.take(3), engine.refresh(NOW).ranges.map { it.anchor.id })

        engine.hear(3, -60, NOW)
        engine.setup = engine.setup.copy(anchors = anchorsAt(square.take(3)))
        engine.setup = engine.setup.copy(anchors = anchorsAt(square))

        assertEquals("dropped when it stopped being one", ids.take(3), engine.refresh(NOW).ranges.map { it.anchor.id })
    }

    private companion object {
        const val NOW = 100_000L
    }
}
