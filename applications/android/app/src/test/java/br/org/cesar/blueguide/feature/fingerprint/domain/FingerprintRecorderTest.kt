package br.org.cesar.blueguide.feature.fingerprint.domain

import br.org.cesar.blueguide.core.ble.BleScanner
import br.org.cesar.blueguide.core.dataset.FingerprintLabel
import br.org.cesar.blueguide.core.dataset.FingerprintRecord
import br.org.cesar.blueguide.core.dataset.FingerprintRecordFormat
import br.org.cesar.blueguide.core.model.Anchor
import br.org.cesar.blueguide.core.model.BleDevice
import br.org.cesar.blueguide.core.model.DeviceId
import br.org.cesar.blueguide.core.model.DeviceSighting
import br.org.cesar.blueguide.core.model.Point2D
import br.org.cesar.blueguide.core.network.FingerprintUploading
import br.org.cesar.blueguide.core.sensors.AltitudeReading
import br.org.cesar.blueguide.core.sensors.AltitudeSample
import br.org.cesar.blueguide.core.sensors.DeviceMotionReading
import br.org.cesar.blueguide.core.sensors.DeviceMotionSample
import br.org.cesar.blueguide.core.sensors.MotionActivityReading
import br.org.cesar.blueguide.core.sensors.MotionActivitySample
import br.org.cesar.blueguide.core.sensors.MotionActivityState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Instant

private class FakeBleScanner : BleScanner {
    private val channel = Channel<DeviceSighting>(Channel.UNLIMITED)
    fun deliver(id: DeviceId, rssi: Int, atMs: Long = 0L) {
        channel.trySend(DeviceSighting(BleDevice(address = "AA:BB:CC:DD:EE:FF", name = null, id = id), rssi, atMs))
    }
    override fun scan(): Flow<DeviceSighting> = channel.receiveAsFlow()
}

private class FakeDeviceMotionReading : DeviceMotionReading {
    override var latest: DeviceMotionSample? = DeviceMotionSample(roll = 0.1, pitch = 0.2, yaw = 0.3)
    var running = false
    override fun start() {
        running = true
    }
    override fun stop() {
        running = false
    }
}

private class FakeAltitudeReading : AltitudeReading {
    override var latest: AltitudeSample? = AltitudeSample(relativeAltitudeM = 1.5, pressureKPa = 101.3)
    var running = false
    override fun start() {
        running = true
    }
    override fun stop() {
        running = false
    }
}

private class FakeMotionActivityReading : MotionActivityReading {
    override var latest: MotionActivitySample? = MotionActivitySample(MotionActivityState.STATIONARY, confidence = 90)
    var running = false
    override fun start() {
        running = true
    }
    override fun stop() {
        running = false
    }
}

private class FakeFingerprintUploader : FingerprintUploading {
    val uploadedBatches = mutableListOf<List<FingerprintRecord>>()
    var errorToThrow: Exception? = null
    override suspend fun upload(records: List<FingerprintRecord>) {
        errorToThrow?.let { throw it }
        uploadedBatches.add(records)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FingerprintRecorderTest {

    private val node = DeviceId("9F3C1A02")
    private val beaconA = DeviceId("1A2B3C4D")
    private val beaconB = DeviceId("B7E2F910")

    private lateinit var directory: File
    private lateinit var scanner: FakeBleScanner
    private lateinit var deviceMotion: FakeDeviceMotionReading
    private lateinit var altimeter: FakeAltitudeReading
    private lateinit var motionActivity: FakeMotionActivityReading
    private lateinit var uploader: FakeFingerprintUploader
    private var anchors: List<Anchor> = emptyList()
    private var fakeClockMs = 0L

    @Before
    fun setUp() {
        directory = File.createTempFile("fingerprints", "").apply {
            delete()
            mkdirs()
        }
        scanner = FakeBleScanner()
        deviceMotion = FakeDeviceMotionReading()
        altimeter = FakeAltitudeReading()
        motionActivity = FakeMotionActivityReading()
        uploader = FakeFingerprintUploader()
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    /** The recorder's single thread is a test dispatcher on the test's scheduler, so its
     *  commands run when the test advances it (`runCurrent`/`advanceUntilIdle`). */
    private fun recorder(
        testScope: TestScope,
        uploadBatchSize: Int = FingerprintRecorder.DEFAULT_UPLOAD_BATCH_SIZE
    ) = FingerprintRecorder(
        scanner = scanner,
        deviceMotion = deviceMotion,
        altimeter = altimeter,
        motionActivity = motionActivity,
        uploader = uploader,
        anchors = { anchors },
        directory = directory,
        node = node,
        model = "Pixel 9",
        systemVersion = "15",
        dispatcher = StandardTestDispatcher(testScope.testScheduler),
        monotonicMs = { fakeClockMs },
        now = { Instant.ofEpochMilli(fakeClockMs) },
        uploadBatchSize = uploadBatchSize
    )

    @Test
    fun `a finished recording writes a csv with the header and one row per sighting`() = runTest {
        val sut = recorder(this)
        sut.start()
        advanceUntilIdle()

        val label = requireNotNull(FingerprintLabel.of("S1", "P1", x = 1.0, y = 2.0))
        sut.startRecording(label, durationSeconds = 2.0)
        runCurrent()

        // runCurrent(), not advanceUntilIdle(), for both deliveries: advancing virtual time
        // here would run the 2s end-of-recording timer to completion before the second row
        // ever arrives.
        scanner.deliver(beaconA, rssi = -60)
        runCurrent()
        fakeClockMs += 500
        scanner.deliver(beaconA, rssi = -62)
        runCurrent()

        advanceUntilIdle() // now let the 2s end-of-recording timer fire
        val state = sut.state.value

        assertFalse(state.isRecording)
        // recordsPerBeacon is reset to empty once a recording finishes — the tally lives on
        // in lastRecording instead (same split as the iOS app's recorder).
        assertEquals(2, state.lastRecording?.recordsPerBeacon?.get(beaconA))
        assertNotNull(state.lastRecording)

        val csv = state.recordings.first()
        val lines = csv.readLines()
        assertEquals(FingerprintRecordFormat.CSV_HEADER, lines.first())
        assertEquals(3, lines.size) // header + 2 rows
        assertTrue(lines[1].contains("\"Pixel 9\""))
        assertTrue(lines[1].endsWith("S1,P1,1.00,2.00"))
        sut.stop() // cancels the scan collector job, so runTest sees no leftover work
    }

    @Test
    fun `t_ms is when the scan saw the advertisement, not when the recorder got to it`() = runTest {
        val sut = recorder(this)
        sut.start()
        advanceUntilIdle()
        sut.startRecording(requireNotNull(FingerprintLabel.of("S1", "P1", 0.0, 0.0)), durationSeconds = 1.0)
        runCurrent()

        fakeClockMs = 5_000 // the recorder runs later than the radio saw it
        scanner.deliver(beaconA, rssi = -60, atMs = 4_321)
        advanceUntilIdle()

        val row = sut.state.value.recordings.first().readLines()[1]
        assertTrue(row, row.startsWith("4321,"))
        sut.stop()
    }

    @Test
    fun `finishing a recording writes a metadata json next to the csv`() = runTest {
        val sut = recorder(this)
        anchors = listOf(Anchor(beaconA, "A", Point2D(0.0, 0.0)))
        sut.start()
        advanceUntilIdle()

        sut.startRecording(requireNotNull(FingerprintLabel.of("S1", "P1", 0.0, 0.0)), durationSeconds = 1.0)
        scanner.deliver(beaconA, rssi = -60)
        advanceUntilIdle()

        val csv = sut.state.value.recordings.first()
        val metadata = FingerprintRecorder.metadataFile(csv)
        assertTrue(metadata.exists())
        assertTrue(metadata.readText().contains("\"platform\": \"Android\""))
        sut.stop()
    }

    @Test
    fun `a finished recording is uploaded`() = runTest {
        val sut = recorder(this)
        sut.start()
        advanceUntilIdle()

        sut.startRecording(requireNotNull(FingerprintLabel.of("S1", "P1", 0.0, 0.0)), durationSeconds = 1.0)
        scanner.deliver(beaconA, rssi = -60)
        scanner.deliver(beaconB, rssi = -70)
        advanceUntilIdle()

        assertEquals(1, uploader.uploadedBatches.size)
        assertEquals(2, uploader.uploadedBatches.first().size)
        sut.stop()
    }

    @Test
    fun `a long recording is uploaded in batches, in order`() = runTest {
        val sut = recorder(this, uploadBatchSize = 2)
        sut.start()
        advanceUntilIdle()

        sut.startRecording(requireNotNull(FingerprintLabel.of("S1", "P1", 0.0, 0.0)), durationSeconds = 1.0)
        runCurrent()
        (1..5).forEach { scanner.deliver(beaconA, rssi = -60 - it) }
        advanceUntilIdle()

        assertEquals(listOf(2, 2, 1), uploader.uploadedBatches.map { it.size })
        assertEquals((1..5).map { -60 - it }, uploader.uploadedBatches.flatten().map { it.rssi })
        sut.stop()
    }

    @Test
    fun `cancelling a recording deletes its file and leaves no trace in state`() = runTest {
        val sut = recorder(this)
        sut.start()
        advanceUntilIdle()

        sut.startRecording(requireNotNull(FingerprintLabel.of("S1", "P1", 0.0, 0.0)), durationSeconds = 10.0)
        scanner.deliver(beaconA, rssi = -60)
        // runCurrent(), not advanceUntilIdle(): the point of this test is to cancel before
        // the 10s end-of-recording timer would otherwise finish it on its own.
        runCurrent()

        sut.cancelRecording()
        runCurrent()

        assertFalse(sut.state.value.isRecording)
        assertTrue(sut.state.value.recordings.isEmpty())
        assertTrue(directory.listFiles()?.isEmpty() != false)
        sut.stop()
    }

    @Test
    fun `starting a new recording while one is active discards the old one`() = runTest {
        val sut = recorder(this)
        sut.start()
        advanceUntilIdle()

        // runCurrent(), not advanceUntilIdle(), after each one: advancing time here would run
        // the 10s end-of-recording timer to completion before the second recording even
        // starts, defeating the point of this test.
        sut.startRecording(requireNotNull(FingerprintLabel.of("S1", "P1", 0.0, 0.0)), durationSeconds = 10.0)
        runCurrent()
        assertNotNull(sut.state.value.activeRecording)

        sut.startRecording(requireNotNull(FingerprintLabel.of("S1", "P2", 1.0, 1.0)), durationSeconds = 10.0)
        runCurrent()

        assertEquals("P2", sut.state.value.activeRecording?.label?.point)
        assertTrue(sut.state.value.recordings.isEmpty()) // the discarded one never "finished"
        sut.stop()
    }

    @Test
    fun `an upload failure keeps the csv and surfaces an error message`() = runTest {
        uploader.errorToThrow = RuntimeException("network down")
        val sut = recorder(this)
        sut.start()
        advanceUntilIdle()

        sut.startRecording(requireNotNull(FingerprintLabel.of("S1", "P1", 0.0, 0.0)), durationSeconds = 1.0)
        scanner.deliver(beaconA, rssi = -60)
        advanceUntilIdle()

        assertEquals(1, sut.state.value.recordings.size)
        assertNotNull(sut.state.value.errorMessage)
        sut.stop()
    }

    @Test
    fun `layout check reports configured-but-unheard and heard-but-unconfigured beacons`() = runTest {
        anchors = listOf(Anchor(beaconA, "A", Point2D(0.0, 0.0)))
        val sut = recorder(this)
        sut.start()
        advanceUntilIdle()
        scanner.deliver(beaconB, rssi = -60)
        advanceUntilIdle()

        val check = sut.layoutCheck
        assertEquals(listOf(beaconB), check.withoutPosition)
        assertEquals(listOf(beaconA), check.notHeard)
        assertNull(sut.state.value.activeRecording)
        sut.stop()
    }
}
