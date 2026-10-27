package br.org.cesar.blueguide.presentation.devicelist

import br.org.cesar.blueguide.domain.entity.BleDevice
import br.org.cesar.blueguide.domain.entity.DeviceId
import br.org.cesar.blueguide.domain.entity.DeviceSighting
import br.org.cesar.blueguide.domain.position.Anchor
import br.org.cesar.blueguide.domain.position.PathLossModel
import br.org.cesar.blueguide.domain.position.Point2D
import kotlin.math.roundToInt
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The screen ranges from the median of every advertisement heard in the last few seconds, once
 * per refresh, like the iOS app's `PositioningEngine` — not from the last packet it happened to read.
 */
class DeviceListReducerTest {

    private val square = listOf(Point2D(0.0, 0.0), Point2D(2.5, 0.0), Point2D(2.5, 2.5), Point2D(0.0, 2.5))
    private val ids = listOf("9F3C1A02", "3F8A1C04", "1A2B3C4D", "B7E2F910").map(::DeviceId)

    private fun observing(positions: List<Point2D>): DeviceListState {
        val anchors = positions.mapIndexed { index, position -> Anchor(ids[index], "ABCD"[index].toString(), position) }
        return DeviceListReducer.reduce(DeviceListState(scanGranted = true), DeviceListIntent.AnchorsChanged(anchors))
    }

    private fun DeviceListState.hear(index: Int, rssi: Int, atMs: Long, id: DeviceId = ids[index]): DeviceListState =
        DeviceListReducer.reduce(this, DeviceListIntent.DeviceObserved(DeviceSighting(BleDevice("AA:BB:CC:DD:EE:0$index", id.value, id), rssi, atMs)))

    private fun DeviceListState.refresh(nowMs: Long): DeviceListState =
        DeviceListReducer.reduce(this, DeviceListIntent.Refreshed(nowMs))

    @Test
    fun `refresh ranges from the median of the recent advertisements`() {
        var state = observing(square.take(1))
        for (rssi in listOf(-60, -61, -60, -92, -59)) state = state.hear(0, rssi, NOW)

        state = state.refresh(NOW)

        val range = state.anchorRanges.single()
        assertEquals(PathLossModel.distanceMeters(-60.0), range.distanceMeters, 1e-9)
        assertEquals("the range keeps the median RSSI it was computed from", -60.0, requireNotNull(range.rssi), 1e-9)
    }

    @Test
    fun `refresh ignores advertisements older than the window`() {
        val state = observing(square.take(1)).hear(0, -70, NOW - 10_000).refresh(NOW)

        assertTrue("an anchor that has not been heard lately has no range", state.anchorRanges.isEmpty())
    }

    @Test
    fun `refresh locates the phone from three anchors`() {
        val anchors = listOf(Point2D(0.0, 0.0), Point2D(6.0, 0.0), Point2D(3.0, 5.0))
        var state = observing(anchors)
        val calibration = PathLossModel.calibration(state.centerRssi, requireNotNull(PathLossModel.centerDistanceMeters(anchors)), state.touchRssi)
        val phone = Point2D(2.0, 2.0)
        anchors.forEachIndexed { index, anchor ->
            val rssi = calibration.rssi(atDistance = anchor.distanceTo(phone))
            repeat(5) { state = state.hear(index, rssi.roundToInt(), NOW) }
        }

        state = state.refresh(NOW)

        assertTrue(requireNotNull(state.observerFix).position.distanceTo(phone) < 0.1)
    }

    /** Beacons advertise at their own pace and the phone misses some packets: each range is that
     *  beacon's own median, so the count does not weigh in. */
    @Test
    fun `beacons heard at different rates with the same RSSI is the center`() {
        var state = observing(square)
        listOf(30, 3, 10, 1).forEachIndexed { index, count ->
            for (packet in 0 until count) state = state.hear(index, -66, NOW - packet * 2_900L / count)
        }

        state = state.refresh(NOW)

        assertEquals(4, state.anchorRanges.size)
        assertTrue(requireNotNull(state.observerFix).position.distanceTo(Point2D(1.25, 1.25)) < 1e-6)
    }

    @Test
    fun `the center RSSI is read as the distance to the middle of the anchors`() {
        var state = DeviceListReducer.reduce(observing(square), DeviceListIntent.CenterRssiChanged(-76.0))
        square.indices.forEach { index -> repeat(5) { state = state.hear(index, -76, NOW) } }

        state = state.refresh(NOW)

        state.anchorRanges.forEach { assertEquals(2.5 / sqrt(2.0), it.distanceMeters, 1e-9) }
        val fix = requireNotNull(state.observerFix)
        assertTrue(fix.position.distanceTo(Point2D(1.25, 1.25)) < 1e-6)
        assertTrue(fix.uncertaintyMeters < 1e-6)
    }

    @Test
    fun `changing the center RSSI ranges again at once`() {
        var state = observing(square)
        square.indices.forEach { index -> state = state.hear(index, -76, NOW) }
        state = state.refresh(NOW)

        state = DeviceListReducer.reduce(state, DeviceListIntent.CenterRssiChanged(-76.0))

        state.anchorRanges.forEach { assertEquals(2.5 / sqrt(2.0), it.distanceMeters, 1e-9) }
    }

    /** The touching RSSI is read as the touching distance, with the center RSSI still the center's. */
    @Test
    fun `the touching RSSI is read as the touching distance`() {
        var state = DeviceListReducer.reduce(observing(square), DeviceListIntent.CenterRssiChanged(-72.0))
        state = DeviceListReducer.reduce(state, DeviceListIntent.TouchRssiChanged(-38.0))
        repeat(5) { state = state.hear(0, -38, NOW) }
        (1..3).forEach { index -> repeat(5) { state = state.hear(index, -72, NOW) } }

        state = state.refresh(NOW)

        val ranges = state.anchorRanges.associate { it.anchor.position to it.distanceMeters }
        assertEquals(PathLossModel.TOUCH_DISTANCE_METERS, requireNotNull(ranges[square[0]]), 1e-9)
        assertEquals(2.5 / sqrt(2.0), requireNotNull(ranges[square[1]]), 1e-9)
        assertTrue(state.calibration.usesTouchRssi)
    }

    /** Touching one beacon and taking what the phone hears now: the strongest anchor's median. */
    @Test
    fun `current touch RSSI is the strongest ranged anchor's median`() {
        var state = observing(square.take(3))
        assertNull("nothing heard yet", state.currentTouchRssi)
        listOf(-80, -37, -75).forEachIndexed { index, rssi -> repeat(3) { state = state.hear(index, rssi, NOW) } }

        state = state.refresh(NOW)

        assertEquals(-37.0, requireNotNull(state.currentTouchRssi), 1e-9)
    }

    /** Standing in the middle and taking what the phone hears now: the mean of the anchors' medians. */
    @Test
    fun `current center RSSI is the mean of the ranged anchors medians`() {
        var state = observing(square)
        assertNull("nothing heard yet", state.currentCenterRssi)
        listOf(-70, -72, -74, -76).forEachIndexed { index, rssi -> repeat(3) { state = state.hear(index, rssi, NOW) } }

        state = state.refresh(NOW)

        assertEquals(-73.0, requireNotNull(state.currentCenterRssi), 1e-9)
    }

    @Test
    fun `each range reports how many advertisements arrived since the last refresh and their spread`() {
        var state = observing(square.take(1))
        state = state.hear(0, -60, NOW - 1_500)
        for (rssi in listOf(-60, -62, -64)) state = state.hear(0, rssi, NOW - 200)

        state = state.refresh(NOW)

        val stats = requireNotNull(state.sampleStats[ids[0]])
        assertEquals(3, stats.count)
        assertEquals(2.0, stats.stdDev, 1e-9)
    }

    @Test
    fun `only devices heard in the last five seconds are listed`() {
        val stranger = DeviceId("CAFE0001")
        var state = observing(square.take(1)).hear(1, -60, NOW - 6_000, id = stranger).hear(0, -60, NOW)

        state = state.refresh(NOW)

        assertEquals(listOf(ids[0]), state.visibleDevices.mapNotNull { it.device.id })
    }

    @Test
    fun `the nearby list shows only devices that are not anchors yet, strongest first`() {
        val near = DeviceId("CAFE0001")
        val far = DeviceId("CAFE0002")
        val state = observing(square.take(1))
            .hear(1, -80, NOW, id = far)
            .hear(2, -55, NOW, id = near)
            .hear(0, -50, NOW)

        assertEquals(listOf(near, far), state.unconfiguredSightings.map { it.device.id })
    }

    @Test
    fun `switching mode drops the windows and the fix`() {
        var state = observing(square)
        square.indices.forEach { index -> state = state.hear(index, -70, NOW) }
        state = state.refresh(NOW)

        state = DeviceListReducer.reduce(state, DeviceListIntent.ModeSelected(AppMode.BROADCAST))
        state = DeviceListReducer.reduce(state, DeviceListIntent.ModeSelected(AppMode.OBSERVER)).refresh(NOW)

        assertTrue(state.anchorRanges.isEmpty())
        assertNull(state.observerFix)
    }

    private companion object {
        const val NOW = 100_000L
    }
}
