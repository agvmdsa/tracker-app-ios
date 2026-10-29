package br.org.cesar.blueguide.feature.positioning.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.org.cesar.blueguide.core.ble.BleAdvertiser
import br.org.cesar.blueguide.core.ble.BleScanner
import br.org.cesar.blueguide.core.ble.BroadcastStatus
import br.org.cesar.blueguide.core.ble.LocalDeviceIdProvider
import br.org.cesar.blueguide.core.data.AnchorStore
import br.org.cesar.blueguide.core.data.CalibrationStore
import br.org.cesar.blueguide.core.model.DeviceId
import br.org.cesar.blueguide.core.model.Point2D
import br.org.cesar.blueguide.core.network.ObserverFixReport
import br.org.cesar.blueguide.core.network.PositioningReport
import br.org.cesar.blueguide.core.network.PositioningReporting
import br.org.cesar.blueguide.core.system.DeviceInfo
import br.org.cesar.blueguide.core.system.MonotonicClock
import br.org.cesar.blueguide.di.IoDispatcher
import br.org.cesar.blueguide.feature.positioning.domain.AddAnchorUseCase
import br.org.cesar.blueguide.feature.positioning.domain.SavedLocationStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class DeviceListViewModel @Inject constructor(
    private val scanner: BleScanner,
    private val advertiser: BleAdvertiser,
    private val localDeviceId: LocalDeviceIdProvider,
    private val anchorStore: AnchorStore,
    private val calibrationStore: CalibrationStore,
    private val addAnchorUseCase: AddAnchorUseCase,
    private val savedLocationStore: SavedLocationStore,
    /** Its model is sent with every report, as the iOS app's `deviceModel`. */
    private val deviceInfo: DeviceInfo,
    /** The sightings' clock, injected so tests can drive it. */
    private val clock: MonotonicClock,
    /** Uploads sightings/fixes to the positioning API while observing. */
    private val reporter: PositioningReporting,
    @IoDispatcher private val io: CoroutineDispatcher
) : ViewModel() {

    private val _state = MutableStateFlow(DeviceListState())
    val state: StateFlow<DeviceListState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // First read hits SharedPreferences, so keep it off the main thread. The
            // provider caches, which also warms the lookup the advertiser makes later.
            val id = withContext(io) { localDeviceId.current() }
            dispatch(DeviceListIntent.LocalDeviceIdLoaded(id))
        }

        // The stores are the source of truth: the screen shows whatever they hold, from the
        // first read of the disk on, and every edit below goes through them.
        viewModelScope.launch { anchorStore.anchors.collect { dispatch(DeviceListIntent.AnchorsChanged(it)) } }
        viewModelScope.launch { savedLocationStore.locations.collect { dispatch(DeviceListIntent.SavedLocationsChanged(it)) } }
        viewModelScope.launch { calibrationStore.settings.collect { dispatch(DeviceListIntent.CalibrationChanged(it)) } }
        viewModelScope.launch {
            anchorStore.load()
            savedLocationStore.load()
            calibrationStore.load()
        }

        viewModelScope.launch {
            // The radio does one job at a time, and which job is itself part of the
            // state. collectLatest cancels the running mode before starting the next,
            // so a mode switch tears the old flow down (which stops the scan or the
            // advertisement) before the new one starts.
            //
            // Permission is part of the key too: it arrives asynchronously, and a mode
            // that started out blocked has to start for real once the user says yes.
            state
                .map { it.mode to it.isModePermitted }
                .distinctUntilChanged()
                .collectLatest { (mode, permitted) ->
                    if (!permitted) return@collectLatest
                    when (mode) {
                        AppMode.OBSERVER -> observe()
                        AppMode.BROADCAST -> broadcast()
                    }
                }
        }

        viewModelScope.launch {
            // Separate from observe()'s collectLatest above so a flood of individual BLE
            // scan callbacks doesn't turn into a flood of fixes and HTTP requests: once a
            // second the ranges and the fix are worked out again from the windows' medians,
            // and reported — same cadence as the iOS app's PositioningEngine.
            state
                .map { it.mode to it.isModePermitted }
                .distinctUntilChanged()
                .collectLatest { (mode, permitted) ->
                    if (!permitted || mode != AppMode.OBSERVER) return@collectLatest
                    refreshPeriodically()
                }
        }
    }

    private suspend fun refreshPeriodically() {
        while (true) {
            delay(DeviceListReducer.REFRESH_INTERVAL_MS)
            dispatch(DeviceListIntent.Refreshed(clock.nowMs()))
            report(reporter, state.value)
        }
    }

    private suspend fun report(reporter: PositioningReporting, snapshot: DeviceListState) {
        val observerID = snapshot.localDeviceId?.value ?: return
        if (snapshot.anchorRanges.isEmpty()) return

        val reports = snapshot.anchorRanges.map { range ->
            val stats = snapshot.sampleStats[range.anchor.id]
            PositioningReport(
                observerID = observerID,
                anchorID = range.anchor.id.value,
                rssi = (range.rssi ?: 0.0).roundToInt(),
                distanceMeters = range.distanceMeters,
                seenAt = Instant.now(),
                sampleCount = stats?.count ?: 0,
                rssiStdDev = stats?.stdDev ?: 0.0,
                deviceModel = deviceInfo.model
            )
        }
        runCatching { reporter.send(reports) }
            .onFailure { Log.w(TAG, "failed to report sightings", it) }

        snapshot.observerFix?.let { fix ->
            val fixReport = ObserverFixReport(
                observerID = observerID,
                x = fix.position.x,
                y = fix.position.y,
                uncertaintyMeters = fix.uncertaintyMeters,
                recordedAt = Instant.now(),
                anchorCount = snapshot.anchorRanges.size
            )
            runCatching { reporter.send(fixReport) }
                .onFailure { Log.w(TAG, "failed to report fix", it) }
        }
    }

    private suspend fun observe() {
        scanner.scan()
            // TODO(3c): show SharedBleScanner.status in DeviceListState instead of only logging.
            // Without a catch, a closed-with-exception flow (Bluetooth off, scan
            // failure) propagates out of collect and crashes the app.
            .catch { cause ->
                // Switching modes cancels this flow; that is not a failure to report.
                if (cause is CancellationException) throw cause
                Log.w(TAG, "Scan stopped", cause)
            }
            .collect { sighting ->
                dispatch(DeviceListIntent.DeviceObserved(sighting))
            }
    }

    private suspend fun broadcast() {
        advertiser.advertise()
            .catch { cause ->
                if (cause is CancellationException) throw cause
                Log.w(TAG, "Broadcast stopped", cause)
                emit(BroadcastStatus.Failed(cause))
            }
            .collect { status ->
                dispatch(DeviceListIntent.BroadcastStatusChanged(status))
            }
    }

    /** The only way the state changes: every change goes through [DeviceListReducer]. */
    fun dispatch(intent: DeviceListIntent) {
        _state.update { current -> DeviceListReducer.reduce(current, intent) }
    }

    /** Adds or edits an anchor — used by the anchor editor dialog. */
    fun addAnchor(id: DeviceId, label: String?, position: Point2D, height: Double?) {
        viewModelScope.launch { addAnchorUseCase(id, label, position, height) }
    }

    fun removeAnchor(id: DeviceId) {
        viewModelScope.launch { anchorStore.remove(id) }
    }

    /** The "RSSI no centro" setting: ranges again at once, and is kept for the next launch. */
    fun setCenterRssi(rssi: Double) {
        viewModelScope.launch { calibrationStore.update { it.copy(centerRssi = rssi) } }
    }

    /** The "RSSI encostado" setting: ranges again at once, and is kept for the next launch. */
    fun setTouchRssi(rssi: Double) {
        viewModelScope.launch { calibrationStore.update { it.copy(touchRssi = rssi) } }
    }

    /** "Limitar pela velocidade" and its speed, kept for the next launch. */
    fun setSpeedLimit(isLimited: Boolean, kmh: Double) {
        viewModelScope.launch { calibrationStore.update { it.copy(isSpeedLimited = isLimited, maxSpeedKmh = kmh) } }
    }

    /** Saves where the fix puts the phone now under [label] ("Salvar posição atual"). */
    fun saveCurrentLocation(label: String) {
        val position = state.value.observerFix?.position ?: return
        viewModelScope.launch { savedLocationStore.save(label.trim().ifEmpty { "Local salvo" }, position) }
    }

    fun removeSavedLocation(id: String) {
        viewModelScope.launch { savedLocationStore.remove(id) }
    }

    private companion object {
        const val TAG = "DeviceListViewModel"
    }
}
