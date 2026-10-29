package br.org.cesar.blueguide.core.positioning

import br.org.cesar.blueguide.core.model.Anchor
import br.org.cesar.blueguide.core.model.DeviceId
import br.org.cesar.blueguide.core.model.Point2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A recording replayed second by second, as the app's loop would have refreshed it. */
class PositioningReplayTest {

    private val ids = listOf("9F3C1A02", "3F8A1C04", "1A2B3C4D").map(::DeviceId)
    private val setup = PositioningSetup(
        listOf(Point2D(0.0, 0.0), Point2D(2.5, 0.0), Point2D(0.0, 2.5)).mapIndexed { index, position ->
            Anchor(ids[index], "ABC"[index].toString(), position)
        }
    )

    private fun heard(atMs: Long, rssi: Int = -60) = ids.map { RecordedSighting(it, rssi, atMs) }

    @Test
    fun `a refresh every second from the start, each with what was heard up to it`() {
        val sightings = heard(START + 500, rssi = -60) + heard(START + 1_000, rssi = -60) + heard(START + 1_500, rssi = -70)

        val updates = PositioningReplay.replay(sightings, setup, startMs = START, endMs = START + 2_000)

        assertEquals(listOf(START + 1_000, START + 2_000), updates.map { it.atMs })
        assertEquals("a sighting at the refresh's moment is in it", 2, updates[0].sampleStats.getValue(ids[0]).count)
        updates[0].ranges.forEach { assertEquals(-60.0, requireNotNull(it.rssi), 0.0) }
        assertEquals("one second of samples", 2, updates[1].sampleStats.getValue(ids[0]).count)
        updates[1].ranges.forEach { assertEquals("the median of -60, -60, -70", -60.0, requireNotNull(it.rssi), 0.0) }
    }

    @Test
    fun `by default it runs from the first sighting to the last`() {
        val updates = PositioningReplay.replay(heard(START) + heard(START + 3_200), setup)

        assertEquals(listOf(START + 1_000, START + 2_000, START + 3_000), updates.map { it.atMs })
    }

    @Test
    fun `a sighting dated before a refresh but heard after one past it counts from the next refresh`() {
        val sightings = heard(START + 900) + RecordedSighting(ids[0], -40, START + 1_100) + RecordedSighting(ids[0], -40, START + 950)

        val updates = PositioningReplay.replay(sightings, setup, startMs = START, endMs = START + 2_000)

        fun medianOfFirst(update: PositioningUpdate) = requireNotNull(update.ranges.first { it.anchor.id == ids[0] }.rssi)
        assertEquals("only the -60 at 900 ms", -60.0, medianOfFirst(updates[0]), 0.0)
        assertEquals("-60, -40 and the late -40", -40.0, medianOfFirst(updates[1]), 0.0)
    }

    /** Past the last sighting the refreshes go on, and the anchors drop out as their readings
     *  leave the window — what the screen shows when the beacons go quiet. */
    @Test
    fun `refreshes go on to the end, with nothing heard the ranges run out`() {
        val updates = PositioningReplay.replay(heard(START), setup, startMs = START, endMs = START + 4_000)

        assertEquals(4, updates.size)
        assertEquals(3, updates[2].ranges.size)
        assertTrue(updates[3].ranges.isEmpty())
    }

    @Test
    fun `nothing heard is nothing to replay`() {
        assertTrue(PositioningReplay.replay(emptyList(), setup).isEmpty())
    }

    private companion object {
        const val START = 20_000L
    }
}
