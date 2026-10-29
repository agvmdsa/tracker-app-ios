package br.org.cesar.blueguide.feature.positioning.ui

import androidx.lifecycle.viewModelScope
import br.org.cesar.blueguide.core.ble.BleAdvertiser
import br.org.cesar.blueguide.core.ble.BleScanner
import br.org.cesar.blueguide.core.ble.BroadcastStatus
import br.org.cesar.blueguide.core.ble.LocalDeviceIdProvider
import br.org.cesar.blueguide.core.data.AnchorRepository
import br.org.cesar.blueguide.core.data.AnchorStore
import br.org.cesar.blueguide.core.data.CalibrationRepository
import br.org.cesar.blueguide.core.data.CalibrationSettings
import br.org.cesar.blueguide.core.data.CalibrationStore
import br.org.cesar.blueguide.core.model.Anchor
import br.org.cesar.blueguide.core.model.BleDevice
import br.org.cesar.blueguide.core.model.DeviceId
import br.org.cesar.blueguide.core.model.DeviceSighting
import br.org.cesar.blueguide.core.model.Point2D
import br.org.cesar.blueguide.core.network.ObserverFixReport
import br.org.cesar.blueguide.core.network.PositioningReport
import br.org.cesar.blueguide.core.network.PositioningReporting
import br.org.cesar.blueguide.core.positioning.Multilateration
import br.org.cesar.blueguide.core.positioning.PathLossModel
import br.org.cesar.blueguide.core.positioning.PositioningReplay
import br.org.cesar.blueguide.core.positioning.RecordedSighting
import br.org.cesar.blueguide.core.system.DeviceInfo
import br.org.cesar.blueguide.core.system.MonotonicClock
import br.org.cesar.blueguide.feature.positioning.domain.AddAnchorUseCase
import br.org.cesar.blueguide.feature.positioning.domain.SavedLocation
import br.org.cesar.blueguide.feature.positioning.domain.SavedLocationRepository
import br.org.cesar.blueguide.feature.positioning.domain.SavedLocationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

private class ChannelScanner : BleScanner {
    private val channel = Channel<DeviceSighting>(Channel.UNLIMITED)
    fun deliver(id: DeviceId, rssi: Int, atMs: Long) {
        channel.trySend(DeviceSighting(BleDevice(address = "AA:BB:CC:DD:EE:FF", name = null, id = id), rssi, atMs))
    }
    override fun scan(): Flow<DeviceSighting> = channel.receiveAsFlow()
}

private object SilentAdvertiser : BleAdvertiser {
    override fun advertise(): Flow<BroadcastStatus> = emptyFlow()
}

private class InMemoryAnchors(var anchors: List<Anchor>) : AnchorRepository {
    override suspend fun load() = anchors
    override suspend fun save(anchors: List<Anchor>) {
        this.anchors = anchors
    }
}

private class InMemoryCalibration(var settings: CalibrationSettings = CalibrationSettings()) : CalibrationRepository {
    override suspend fun load() = settings
    override suspend fun save(settings: CalibrationSettings) {
        this.settings = settings
    }
}

private object NoSavedLocations : SavedLocationRepository {
    override suspend fun load(): List<SavedLocation> = emptyList()
    override suspend fun save(locations: List<SavedLocation>) = Unit
}

private class RecordingReporter : PositioningReporting {
    val sightings = mutableListOf<List<PositioningReport>>()
    val fixes = mutableListOf<ObserverFixReport>()
    override suspend fun send(reports: List<PositioningReport>) {
        sightings += reports
    }
    override suspend fun send(fix: ObserverFixReport) {
        fixes += fix
    }
}

/** The once-a-second loop: ranges and fix worked out from the windows, and reported. */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceListViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val observer = DeviceId("0B5E7E01")
    private val anchors = listOf(
        Anchor(DeviceId("AAAA0001"), "A", Point2D(0.0, 0.0)),
        Anchor(DeviceId("AAAA0002"), "B", Point2D(2.0, 0.0)),
        Anchor(DeviceId("AAAA0003"), "C", Point2D(0.0, 2.0))
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        scanner: BleScanner = ChannelScanner(),
        anchorStore: AnchorStore = AnchorStore(InMemoryAnchors(anchors)),
        calibration: CalibrationStore = CalibrationStore(InMemoryCalibration()),
        reporter: PositioningReporting = RecordingReporter(),
        clock: MonotonicClock = MonotonicClock { 0L }
    ) = DeviceListViewModel(
        scanner = scanner,
        advertiser = SilentAdvertiser,
        localDeviceId = object : LocalDeviceIdProvider {
            override fun current() = observer
        },
        anchorStore = anchorStore,
        calibrationStore = calibration,
        addAnchorUseCase = AddAnchorUseCase(anchorStore),
        savedLocationStore = SavedLocationStore(NoSavedLocations),
        deviceInfo = DeviceInfo(model = "Pixel 9", systemVersion = "15"),
        clock = clock,
        reporter = reporter,
        estimator = Multilateration,
        io = dispatcher
    )

    /** Observing, with every anchor heard at [rssi] and one refresh done. */
    private fun TestScope.observingOneSecond(vm: DeviceListViewModel, scanner: ChannelScanner, rssi: Int, nowMs: Long) {
        vm.dispatch(DeviceListIntent.PermissionsChanged(scanGranted = true, advertiseGranted = false))
        runCurrent()
        anchors.forEach { scanner.deliver(it.id, rssi, atMs = nowMs) }
        advanceTimeBy(1_000)
        runCurrent()
    }

    @Test
    fun `observing, each second it reports every anchor heard and the fix, with this phone's id and model`() = runTest(dispatcher) {
        val scanner = ChannelScanner()
        val reporter = RecordingReporter()
        var nowMs = 10_000L
        val vm = viewModel(scanner = scanner, reporter = reporter, clock = { nowMs })
        vm.dispatch(DeviceListIntent.PermissionsChanged(scanGranted = true, advertiseGranted = false))
        runCurrent() // loads the id and the anchors, and starts the scan and the 1 s loop

        anchors.forEach { scanner.deliver(it.id, rssi = -60, atMs = nowMs) }
        runCurrent()
        assertTrue("nothing is reported before the first second", reporter.sightings.isEmpty())

        nowMs += 1_000
        advanceTimeBy(1_000)
        runCurrent()

        val reports = reporter.sightings.single()
        assertEquals(anchors.map { it.id.value }.toSet(), reports.map { it.anchorID }.toSet())
        assertTrue(reports.all { it.observerID == observer.value && it.deviceModel == "Pixel 9" && it.rssi == -60 })
        val fix = reporter.fixes.single()
        assertEquals(observer.value, fix.observerID)
        assertEquals(3, fix.anchorCount)

        vm.viewModelScope.cancel() // the scan and the 1 s loop never end on their own
    }

    @Test
    fun `the screen shows what the stores hold, the saved calibration and anchors added anywhere`() = runTest(dispatcher) {
        val anchorStore = AnchorStore(InMemoryAnchors(emptyList()))
        val calibration = CalibrationStore(InMemoryCalibration(CalibrationSettings(centerRssi = -75.0, isSpeedLimited = false)))
        val vm = viewModel(anchorStore = anchorStore, calibration = calibration)
        runCurrent()

        assertEquals(-75.0, vm.state.value.centerRssi, 0.0)
        assertFalse(vm.state.value.isSpeedLimited)

        vm.addAnchor(anchors[0].id, label = null, position = Point2D(1.0, 2.0), height = null)
        runCurrent()
        assertEquals("the shared store, which the Fingerprint tab reads", "A", anchorStore.anchor(anchors[0].id)?.label)
        assertEquals(listOf(anchors[0].id), vm.state.value.anchors.map { it.id })

        vm.setCenterRssi(-68.0)
        runCurrent()
        assertEquals(-68.0, calibration.settings.value.centerRssi)
        assertEquals(-68.0, vm.state.value.centerRssi, 0.0)

        vm.viewModelScope.cancel()
    }

    /** "RSSI no centro" is what the phone reads in the middle of the anchors: taking the RSSI it
     *  hears now ranges every anchor at their mean distance to the middle, without waiting for
     *  the next refresh. */
    @Test
    fun `a new calibration ranges again at once, from the readings already heard`() = runTest(dispatcher) {
        val scanner = ChannelScanner()
        val vm = viewModel(scanner = scanner, clock = { 10_000L })
        observingOneSecond(vm, scanner, rssi = -76, nowMs = 10_000L)
        val middle = requireNotNull(PathLossModel.centerDistanceMeters(anchors.map { it.position }))
        assertTrue(vm.state.value.anchorRanges.none { abs(it.distanceMeters - middle) < 1e-6 })

        vm.setCenterRssi(-76.0)
        runCurrent()

        assertEquals(3, vm.state.value.anchorRanges.size)
        vm.state.value.anchorRanges.forEach { assertEquals(middle, it.distanceMeters, 1e-9) }

        vm.viewModelScope.cancel()
    }

    @Test
    fun `leaving observer mode forgets what was heard`() = runTest(dispatcher) {
        val scanner = ChannelScanner()
        val vm = viewModel(scanner = scanner, clock = { 10_000L })
        observingOneSecond(vm, scanner, rssi = -60, nowMs = 10_000L)
        assertTrue(vm.state.value.observerFix != null)

        vm.dispatch(DeviceListIntent.ModeSelected(AppMode.BROADCAST))
        runCurrent()
        vm.dispatch(DeviceListIntent.ModeSelected(AppMode.OBSERVER))
        advanceTimeBy(1_000)
        runCurrent()

        assertTrue("the readings were still within the window", vm.state.value.anchorRanges.isEmpty())
        assertNull(vm.state.value.observerFix)

        vm.viewModelScope.cancel()
    }

    /** The offline replay is only worth anything if it is what the app showed: the same
     *  advertisements, heard live by the screen and replayed from a recording, give the same
     *  ranges and the same positions every second — speed limit included. */
    @Test
    fun `replaying what was heard gives what the screen showed`() = runTest(dispatcher) {
        val scanner = ChannelScanner()
        val reporter = RecordingReporter()
        val startMs = 50_000L
        var nowMs = startMs
        val vm = viewModel(scanner = scanner, reporter = reporter, clock = { nowMs })
        vm.dispatch(DeviceListIntent.PermissionsChanged(scanGranted = true, advertiseGranted = false))
        runCurrent() // observing from startMs: the first refresh is a second later

        // A walk past the anchors for 8 s: each one heard at its own pace, louder then quieter.
        val heard = (0 until 8_000 step 70).flatMap { t ->
            anchors.mapIndexedNotNull { index, anchor ->
                if ((t / 70) % (index + 2) == 0) return@mapIndexedNotNull null
                RecordedSighting(anchor.id, rssi = -45 - (t / 100 + 17 * index) % 35, atMs = startMs + t + index * 13L)
            }
        }.sortedBy { it.atMs }
        for (second in 1..8) {
            val refreshMs = startMs + second * 1_000L
            heard.filter { it.atMs in (refreshMs - 1_000 + 1)..refreshMs }.forEach { scanner.deliver(it.beacon, it.rssi, it.atMs) }
            runCurrent()
            nowMs = refreshMs
            advanceTimeBy(1_000)
            runCurrent()
        }

        val replayed = PositioningReplay.replay(heard, vm.state.value.positioningSetup, startMs = startMs, endMs = startMs + 8_000)

        assertEquals(8, replayed.size)
        assertEquals(
            replayed.map { update -> update.ranges.map { it.distanceMeters } },
            reporter.sightings.map { reports -> reports.map { it.distanceMeters } }
        )
        val fixes = replayed.mapNotNull { it.fix }
        assertTrue("the walk is placed most seconds", fixes.size >= 6)
        val unlimited = PositioningReplay.replay(heard, vm.state.value.positioningSetup.copy(maxSpeedKmh = null), startMs = startMs)
        assertNotEquals("the speed limit holds the phone back on this walk", unlimited.mapNotNull { it.fix }, fixes)
        assertEquals(fixes.map { it.position.x to it.position.y }, reporter.fixes.map { it.x to it.y })

        vm.viewModelScope.cancel()
    }
}
