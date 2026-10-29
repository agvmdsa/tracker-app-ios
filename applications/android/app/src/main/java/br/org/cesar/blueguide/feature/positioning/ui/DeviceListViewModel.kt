package br.org.cesar.blueguide.feature.positioning.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.org.cesar.blueguide.core.ble.BleAdvertiser
import br.org.cesar.blueguide.core.ble.BleScanner
import br.org.cesar.blueguide.core.ble.BroadcastStatus
import br.org.cesar.blueguide.core.ble.LocalDeviceIdProvider
import br.org.cesar.blueguide.core.data.AnchorStore
import br.org.cesar.blueguide.core.data.CalibrationRepository
import br.org.cesar.blueguide.core.model.DeviceId
import br.org.cesar.blueguide.core.model.Point2D
import br.org.cesar.blueguide.core.network.ObserverFixReport
import br.org.cesar.blueguide.core.network.PositioningReport
import br.org.cesar.blueguide.core.network.PositioningReporting
import br.org.cesar.blueguide.feature.positioning.domain.SavedLocationStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
import kotlin.math.roundToInt

class DeviceListViewModel(
    private val scanner: BleScanner,
    private val advertiser: BleAdvertiser,
    private val localDeviceId: LocalDeviceIdProvider,
    private val anchorStore: AnchorStore,
    private val calibrationRepository: CalibrationRepository,
    private val savedLocationStore: SavedLocationStore,
    /** The phone model sent with every report, as the iOS app's `deviceModel`. */
    private val deviceModel: String,
    /** The sightings' clock (`SystemClock.elapsedRealtime`), injected so tests can drive it. */
    private val nowMs: () -> Long,
    /** Uploads sightings/fixes to the positioning API while observing. Optional, same as the
     *  iOS app's `PositioningEngine` reporter — null just means "don't upload". */
    private val reporter: PositioningReporting? = null
) : ViewModel() {

    private val _state = MutableStateFlow(DeviceListState())
    val state: StateFlow<DeviceListState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // First read hits SharedPreferences, so keep it off the main thread. The
            // provider caches, which also warms the lookup the advertiser makes later.
            val id = withContext(Dispatchers.IO) { localDeviceId.current() }
            dispatch(DeviceListIntent.LocalDeviceIdLoaded(id))
        }

        viewModelScope.launch {
            withContext(Dispatchers.IO) { anchorStore.load() }
            dispatch(DeviceListIntent.AnchorsChanged(anchorStore.anchors))
        }

        viewModelScope.launch {
            withContext(Dispatchers.IO) { savedLocationStore.load() }
            dispatch(DeviceListIntent.SavedLocationsChanged(savedLocationStore.locations))
        }

        viewModelScope.launch {
            val (center, touch) = withContext(Dispatchers.IO) {
                calibrationRepository.loadCenterRssi() to calibrationRepository.loadTouchRssi()
            }
            center?.let { dispatch(DeviceListIntent.CenterRssiChanged(it)) }
            touch?.let { dispatch(DeviceListIntent.TouchRssiChanged(it)) }
            val (limited, kmh) = withContext(Dispatchers.IO) {
                calibrationRepository.loadIsSpeedLimited() to calibrationRepository.loadMaxSpeedKmh()
            }
            kmh?.let { dispatch(DeviceListIntent.MaxSpeedChanged(it)) }
            if (limited == false) dispatch(DeviceListIntent.MaxSpeedChanged(null))
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
            dispatch(DeviceListIntent.Refreshed(nowMs()))
            reporter?.let { report(it, state.value) }
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
                deviceModel = deviceModel
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
            // TODO(3c): surface this in DeviceListState instead of only logging.
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

    /** Adds or edits an anchor, then republishes the list — used by the anchor editor dialog. */
    fun addAnchor(id: DeviceId, label: String?, position: Point2D, height: Double?) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { anchorStore.addAnchor(id, label, position, height) }
            dispatch(DeviceListIntent.AnchorsChanged(anchorStore.anchors))
        }
    }

    /** The "RSSI no centro" setting: ranges again at once, and is kept for the next launch. */
    fun setCenterRssi(rssi: Double) {
        dispatch(DeviceListIntent.CenterRssiChanged(rssi))
        viewModelScope.launch(Dispatchers.IO) { calibrationRepository.saveCenterRssi(rssi) }
    }

    /** The "RSSI encostado" setting: ranges again at once, and is kept for the next launch. */
    fun setTouchRssi(rssi: Double) {
        dispatch(DeviceListIntent.TouchRssiChanged(rssi))
        viewModelScope.launch(Dispatchers.IO) { calibrationRepository.saveTouchRssi(rssi) }
    }

    /** Saves where the fix puts the phone now under [label] ("Salvar posição atual"). */
    fun saveCurrentLocation(label: String) {
        val position = state.value.observerFix?.position ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { savedLocationStore.save(label.trim().ifEmpty { "Local salvo" }, position) }
            dispatch(DeviceListIntent.SavedLocationsChanged(savedLocationStore.locations))
        }
    }

    fun removeSavedLocation(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { savedLocationStore.remove(id) }
            dispatch(DeviceListIntent.SavedLocationsChanged(savedLocationStore.locations))
        }
    }

    /** "Limitar pela velocidade" and its speed, kept for the next launch. */
    fun setSpeedLimit(isLimited: Boolean, kmh: Double) {
        dispatch(DeviceListIntent.MaxSpeedChanged(kmh))
        if (!isLimited) dispatch(DeviceListIntent.MaxSpeedChanged(null))
        viewModelScope.launch(Dispatchers.IO) { calibrationRepository.saveSpeedLimit(isLimited, kmh) }
    }

    fun removeAnchor(id: DeviceId) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { anchorStore.removeAnchor(id) }
            dispatch(DeviceListIntent.AnchorsChanged(anchorStore.anchors))
        }
    }

    private companion object {
        const val TAG = "DeviceListViewModel"
    }
}
