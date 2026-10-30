package br.org.cesar.blueguide.core.positioning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Each beacon's recent RSSIs, and their median. */
class RssiWindowTest {

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

    private companion object {
        const val TOLERANCE = 1e-6
    }
}
