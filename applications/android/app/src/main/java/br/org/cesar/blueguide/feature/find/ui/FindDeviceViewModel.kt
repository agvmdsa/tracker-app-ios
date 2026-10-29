package br.org.cesar.blueguide.feature.find.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.org.cesar.blueguide.feature.find.data.AndroidUwbSessionController
import br.org.cesar.blueguide.feature.find.data.BleUwbHandshakeChannel
import br.org.cesar.blueguide.feature.find.domain.UwbProximityTracker
import br.org.cesar.blueguide.feature.find.domain.UwbTrackerState
import kotlinx.coroutines.flow.StateFlow

/**
 * Owns the [UwbProximityTracker] at ViewModel scope — same rotation-survival reasoning as
 * [br.org.cesar.blueguide.feature.fingerprint.ui.FingerprintViewModel]. Counterpart of the
 * iOS app's `ProximityTracker` usage in `TrackingView`.
 */
class FindDeviceViewModel(appContext: Context) : ViewModel() {

    private val tracker = UwbProximityTracker(
        uwb = AndroidUwbSessionController(appContext, viewModelScope),
        handshake = BleUwbHandshakeChannel(appContext),
        scope = viewModelScope
    )

    val state: StateFlow<UwbTrackerState> = tracker.state
    val isSupported: Boolean get() = tracker.isSupported

    fun start() = tracker.start()
    fun stop() = tracker.stop()

    override fun onCleared() {
        tracker.stop()
    }
}
