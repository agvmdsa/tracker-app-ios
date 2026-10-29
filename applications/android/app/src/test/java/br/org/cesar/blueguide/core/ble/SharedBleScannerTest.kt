package br.org.cesar.blueguide.core.ble

import br.org.cesar.blueguide.core.model.BleDevice
import br.org.cesar.blueguide.core.model.DeviceId
import br.org.cesar.blueguide.core.model.DeviceSighting
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Stands in for the phone's radio: counts the scans started and running, and lets a test
 *  deliver sightings into the current one or make it fail. */
private class FakeRadio : BleScanner {
    var starts = 0
    var running = 0
    private var current: Channel<DeviceSighting>? = null

    fun deliver(rssi: Int) {
        requireNotNull(current) { "no scan running" }
            .trySend(DeviceSighting(BleDevice("AA:BB:CC:DD:EE:FF", null, DeviceId("BEAC0001")), rssi, 0L))
    }

    fun fail(message: String) {
        requireNotNull(current) { "no scan running" }.close(IllegalStateException(message))
    }

    override fun scan(): Flow<DeviceSighting> = flow {
        starts++
        running++
        val channel = Channel<DeviceSighting>(Channel.UNLIMITED).also { current = it }
        try {
            for (sighting in channel) emit(sighting)
        } finally {
            running--
            current = null
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SharedBleScannerTest {

    private fun TestScope.listen(scanner: BleScanner, into: MutableList<Int>) =
        backgroundScope.launch { scanner.scan().collect { into += it.rssi } }

    @Test
    fun `two listeners share one radio scan`() = runTest {
        val radio = FakeRadio()
        val shared = SharedBleScanner(radio, backgroundScope)
        val first = mutableListOf<Int>()
        val second = mutableListOf<Int>()
        listen(shared, first)
        listen(shared, second)
        runCurrent()

        radio.deliver(-60)
        runCurrent()

        assertEquals(1, radio.starts)
        assertEquals(listOf(-60), first)
        assertEquals(listOf(-60), second)
        assertEquals(ScanStatus.Scanning, shared.status.value)
    }

    @Test
    fun `a failed scan is reported and started again, waiting longer each time until sightings arrive`() = runTest {
        val radio = FakeRadio()
        val shared = SharedBleScanner(radio, backgroundScope, retryDelaysMs = listOf(1_000, 5_000))
        val heard = mutableListOf<Int>()
        val listener = listen(shared, heard)
        runCurrent()

        radio.fail("no permission yet")
        runCurrent()
        val interrupted = shared.status.value as ScanStatus.Interrupted
        assertEquals("no permission yet", interrupted.cause?.message)
        assertEquals(1_000, interrupted.retryInMs)

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(2, radio.starts)
        assertEquals(ScanStatus.Scanning, shared.status.value)

        radio.fail("still nothing")
        runCurrent()
        assertEquals(5_000, (shared.status.value as ScanStatus.Interrupted).retryInMs)

        advanceTimeBy(5_000)
        runCurrent()
        radio.deliver(-61)
        runCurrent()
        radio.fail("Bluetooth off")
        runCurrent()

        assertEquals("back to the first delay after a sighting", 1_000, (shared.status.value as ScanStatus.Interrupted).retryInMs)
        assertEquals(3, radio.starts)
        assertEquals(listOf(-61), heard)
        assertTrue("the listener never sees the failures", listener.isActive)
    }

    @Test
    fun `the radio stops a while after the last listener leaves, and not before`() = runTest {
        val radio = FakeRadio()
        val shared = SharedBleScanner(radio, backgroundScope, stopTimeoutMs = 5_000)
        val listener = listen(shared, mutableListOf())
        runCurrent()

        listener.cancel()
        runCurrent()
        advanceTimeBy(2_000)
        val again = listen(shared, mutableListOf()) // back within the timeout: same scan
        runCurrent()
        assertEquals(1, radio.starts)

        again.cancel()
        runCurrent()
        advanceTimeBy(4_999)
        runCurrent()
        assertEquals(1, radio.running)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(0, radio.running)
        assertEquals(ScanStatus.Idle, shared.status.value)
    }
}
