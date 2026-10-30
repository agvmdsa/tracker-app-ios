package br.org.cesar.blueguide.feature.positioning.ui

import br.org.cesar.blueguide.core.data.CalibrationSettings
import br.org.cesar.blueguide.core.model.Anchor
import br.org.cesar.blueguide.core.model.BleDevice
import br.org.cesar.blueguide.core.model.DeviceId
import br.org.cesar.blueguide.core.model.DeviceSighting
import br.org.cesar.blueguide.core.model.Point2D
import br.org.cesar.blueguide.core.positioning.AnchorRange
import br.org.cesar.blueguide.core.positioning.ObserverFix
import br.org.cesar.blueguide.core.positioning.PathLossModel
import br.org.cesar.blueguide.core.positioning.PositioningSetup
import br.org.cesar.blueguide.core.positioning.PositioningUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the Positioning screen keeps of each refresh. Where the phone is comes from the
 * `PositioningEngine` (tested in :core:positioning); this is what the screen does with it.
 */
class DeviceListReducerTest {

    private val square = listOf(Point2D(0.0, 0.0), Point2D(2.5, 0.0), Point2D(2.5, 2.5), Point2D(0.0, 2.5))
    private val ids = listOf("9F3C1A02", "3F8A1C04", "1A2B3C4D", "B7E2F910").map(::DeviceId)
    private val anchors = square.mapIndexed { index, position -> Anchor(ids[index], "ABCD"[index].toString(), position) }

    private fun observing(anchors: List<Anchor> = this.anchors): DeviceListState =
        DeviceListReducer.reduce(DeviceListState(scanGranted = true), DeviceListIntent.AnchorsChanged(anchors))

    private fun DeviceListState.hear(index: Int, rssi: Int, atMs: Long, id: DeviceId = ids[index]): DeviceListState =
        DeviceListReducer.reduce(
            this,
            DeviceListIntent.DeviceObserved(DeviceSighting(BleDevice("AA:BB:CC:DD:EE:0$index", id.value, id), rssi, atMs))
        )

    /** A refresh at [atMs] that ranged the anchors at [rssis] (one each, in order). */
    private fun DeviceListState.updated(atMs: Long, vararg rssis: Double, fix: ObserverFix? = null): DeviceListState {
        val ranges = rssis.mapIndexed { index, rssi -> AnchorRange(anchors[index], PathLossModel.distanceMeters(rssi), rssi) }
        return DeviceListReducer.reduce(this, DeviceListIntent.PositioningUpdated(PositioningUpdate(atMs, ranges, fix, emptyMap())))
    }

    @Test
    fun `the screen shows the ranges and the fix of the last refresh`() {
        val fix = ObserverFix(emptyList(), Point2D(1.0, 2.0), uncertaintyMeters = 0.4)

        val state = observing().updated(NOW, -60.0, -70.0, -80.0, fix = fix)

        assertEquals(ids.take(3), state.anchorRanges.map { it.anchor.id })
        assertEquals(fix, state.observerFix)
    }

    /** What the engine is handed: the anchors and the calibration on screen, the speed limit
     *  only when it is on. */
    @Test
    fun `the engine ranges with the anchors and the calibration on screen`() {
        var state = observing()
        assertEquals(PositioningSetup(anchors), state.positioningSetup)

        state = DeviceListReducer.reduce(
            state,
            DeviceListIntent.CalibrationChanged(CalibrationSettings(centerRssi = -76.0, touchRssi = -38.0, isSpeedLimited = false))
        )

        assertEquals(PositioningSetup(anchors, centerRssi = -76.0, touchRssi = -38.0, maxSpeedKmh = null), state.positioningSetup)
        assertEquals(state.positioningSetup.calibration, state.calibration)
    }

    /** Touching one beacon and taking what the phone hears now: the strongest anchor's median. */
    @Test
    fun `current touch RSSI is the strongest ranged anchor's median`() {
        val state = observing()
        assertNull("nothing heard yet", state.currentTouchRssi)

        assertEquals(-37.0, requireNotNull(state.updated(NOW, -80.0, -37.0, -75.0).currentTouchRssi), 1e-9)
    }

    /** Standing in the middle and taking what the phone hears now: the mean of the anchors' medians. */
    @Test
    fun `current center RSSI is the mean of the ranged anchors medians`() {
        val state = observing()
        assertNull("nothing heard yet", state.currentCenterRssi)

        assertEquals(-73.0, requireNotNull(state.updated(NOW, -70.0, -72.0, -74.0, -76.0).currentCenterRssi), 1e-9)
    }

    @Test
    fun `only devices heard in the last five seconds are listed`() {
        val stranger = DeviceId("CAFE0001")
        val state = observing().hear(1, -60, NOW - 6_000, id = stranger).hear(0, -60, NOW).updated(NOW)

        assertEquals(listOf(ids[0]), state.visibleDevices.mapNotNull { it.device.id })
    }

    @Test
    fun `the nearby list shows only devices that are not anchors yet, strongest first`() {
        val near = DeviceId("CAFE0001")
        val far = DeviceId("CAFE0002")
        val state = observing(anchors.take(1))
            .hear(1, -80, NOW, id = far)
            .hear(2, -55, NOW, id = near)
            .hear(0, -50, NOW)

        assertEquals(listOf(near, far), state.unconfiguredSightings.map { it.device.id })
    }

    @Test
    fun `switching mode drops the ranges and the fix`() {
        val state = observing().updated(NOW, -70.0, -70.0, -70.0, fix = ObserverFix(emptyList(), Point2D(1.0, 1.0), 0.0))

        val broadcasting = DeviceListReducer.reduce(state, DeviceListIntent.ModeSelected(AppMode.BROADCAST))

        assertTrue(broadcasting.anchorRanges.isEmpty())
        assertNull(broadcasting.observerFix)
    }

    /** The engine can still be finishing a refresh when the mode switches. */
    @Test
    fun `a refresh that arrives after leaving observer mode is ignored`() {
        val broadcasting = DeviceListReducer.reduce(observing(), DeviceListIntent.ModeSelected(AppMode.BROADCAST))

        val state = broadcasting.updated(NOW, -70.0, -70.0, -70.0, fix = ObserverFix(emptyList(), Point2D(1.0, 1.0), 0.0))

        assertTrue(state.anchorRanges.isEmpty())
        assertNull(state.observerFix)
    }

    private companion object {
        const val NOW = 100_000L
    }
}
