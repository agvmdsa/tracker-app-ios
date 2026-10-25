package br.org.cesar.blueguide.presentation.devicelist

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.org.cesar.blueguide.domain.ble.BleAdvertiser
import br.org.cesar.blueguide.domain.ble.BleScanner
import br.org.cesar.blueguide.domain.ble.BroadcastStatus
import br.org.cesar.blueguide.domain.ble.LocalDeviceIdProvider
import br.org.cesar.blueguide.domain.entity.DeviceId
import br.org.cesar.blueguide.domain.network.ObserverFixReport
import br.org.cesar.blueguide.domain.network.PositioningReport
import br.org.cesar.blueguide.domain.network.PositioningReporting
import br.org.cesar.blueguide.domain.position.AnchorStore
import br.org.cesar.blueguide.domain.position.PathLossModel
import br.org.cesar.blueguide.domain.position.Point2D
import java.time.Instant
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

class DeviceListViewModel(
    private val scanner: BleScanner,
    private val advertiser: BleAdvertiser,
    private val localDeviceId: LocalDeviceIdProvider,
    private val anchorStore: AnchorStore,
    /** Uploads sightings/fixes to the positioning API while observing. Optional, same as the
     *  iOS app's `PositioningEngine` reporter — null just means "don't upload". */
    private val reporter: PositioningReporting? = null
): ViewModel() {

    /*
    * Point here is that MutableStateFlow can be written to and StateFlow
    * can only be read.
    *
    * I assume that the thing between parenthesis below is *what* the
    * MutableStateFlow is, as in "I have a MutableStateFlow of
    * DeviceListState", and then "I have a StateFlow of this map
    * (DeviceListState) to be seen from outside". I assume the StateFlow
    * "only" exists due to encapsulation.
    * */

    // My internal state
    private val _state = MutableStateFlow(DeviceListState())

    // Public state
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
            // scan callbacks doesn't turn into a flood of HTTP requests: this samples
            // whatever the state looks like once a second instead, same cadence as the
            // iOS app's PositioningEngine.
            state
                .map { it.mode to it.isModePermitted }
                .distinctUntilChanged()
                .collectLatest { (mode, permitted) ->
                    if (!permitted || mode != AppMode.OBSERVER || reporter == null) return@collectLatest
                    reportPeriodically(reporter)
                }
        }
    }

    private suspend fun reportPeriodically(reporter: PositioningReporting) {
        while (true) {
            delay(REPORT_INTERVAL_MS)
            val snapshot = state.value
            val observerID = snapshot.localDeviceId?.value ?: continue

            val reports = snapshot.anchors.mapNotNull { anchor ->
                val sighting = snapshot.devicesByKey.values.firstOrNull { it.device.id == anchor.id } ?: return@mapNotNull null
                // Raw rssi in the report (what was actually measured just now), but the
                // smoothed value for distanceMeters — same split as the iOS app's
                // PositioningEngine.refresh().
                val smoothed = snapshot.smoothedRssiByKey[sighting.device.key] ?: sighting.rssi.toDouble()
                PositioningReport(
                    observerID = observerID,
                    anchorID = anchor.id.value,
                    rssi = sighting.rssi,
                    distanceMeters = PathLossModel.distanceMeters(smoothed),
                    seenAt = Instant.now()
                )
            }
            if (reports.isNotEmpty()) {
                runCatching { reporter.send(reports) }
                    .onFailure { Log.w(TAG, "failed to report sightings", it) }
            }

            snapshot.observerFix?.let { fix ->
                val fixReport = ObserverFixReport(
                    observerID = observerID,
                    x = fix.position.x,
                    y = fix.position.y,
                    uncertaintyMeters = fix.uncertaintyMeters,
                    recordedAt = Instant.now()
                )
                runCatching { reporter.send(fixReport) }
                    .onFailure { Log.w(TAG, "failed to report fix", it) }
            }
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

    // In MVI the only way in is/should be through a dispatcher/onIntent
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

    fun removeAnchor(id: DeviceId) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { anchorStore.removeAnchor(id) }
            dispatch(DeviceListIntent.AnchorsChanged(anchorStore.anchors))
        }
    }

    private companion object {
        const val TAG = "DeviceListViewModel"
        const val REPORT_INTERVAL_MS = 1000L
    }
}
