package br.org.cesar.blueguide.feature.positioning.ui

import androidx.lifecycle.viewModelScope
import br.org.cesar.blueguide.core.ble.BleAdvertiser
import br.org.cesar.blueguide.core.ble.BleScanner
import br.org.cesar.blueguide.core.ble.BroadcastStatus
import br.org.cesar.blueguide.core.ble.LocalDeviceIdProvider
import br.org.cesar.blueguide.core.data.AnchorRepository
import br.org.cesar.blueguide.core.data.AnchorStore
import br.org.cesar.blueguide.core.data.CalibrationRepository
import br.org.cesar.blueguide.core.model.Anchor
import br.org.cesar.blueguide.core.model.BleDevice
import br.org.cesar.blueguide.core.model.DeviceId
import br.org.cesar.blueguide.core.model.DeviceSighting
import br.org.cesar.blueguide.core.model.Point2D
import br.org.cesar.blueguide.core.network.ObserverFixReport
import br.org.cesar.blueguide.core.network.PositioningReport
import br.org.cesar.blueguide.core.network.PositioningReporting
import br.org.cesar.blueguide.core.system.DeviceInfo
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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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

private class InMemoryAnchors(private var anchors: List<Anchor>) : AnchorRepository {
    override fun load() = anchors
    override fun save(anchors: List<Anchor>) {
        this.anchors = anchors
    }
}

private object NoCalibration : CalibrationRepository {
    override fun loadCenterRssi(): Double? = null
    override fun saveCenterRssi(rssi: Double) = Unit
    override fun loadTouchRssi(): Double? = null
    override fun saveTouchRssi(rssi: Double) = Unit
    override fun loadIsSpeedLimited(): Boolean? = null
    override fun loadMaxSpeedKmh(): Double? = null
    override fun saveSpeedLimit(isLimited: Boolean, kmh: Double) = Unit
}

private object NoSavedLocations : SavedLocationRepository {
    override fun load(): List<SavedLocation> = emptyList()
    override fun save(locations: List<SavedLocation>) = Unit
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

    @Test
    fun `observing, each second it reports every anchor heard and the fix, with this phone's id and model`() = runTest(dispatcher) {
        val scanner = ChannelScanner()
        val reporter = RecordingReporter()
        var nowMs = 10_000L
        val vm = DeviceListViewModel(
            scanner = scanner,
            advertiser = SilentAdvertiser,
            localDeviceId = object : LocalDeviceIdProvider {
                override fun current() = observer
            },
            anchorStore = AnchorStore(InMemoryAnchors(anchors)),
            calibrationRepository = NoCalibration,
            savedLocationStore = SavedLocationStore(NoSavedLocations),
            deviceInfo = DeviceInfo(model = "Pixel 9", systemVersion = "15"),
            clock = { nowMs },
            reporter = reporter,
            io = dispatcher
        )
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
}
