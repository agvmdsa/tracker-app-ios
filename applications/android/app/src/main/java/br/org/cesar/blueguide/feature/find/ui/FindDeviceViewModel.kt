package br.org.cesar.blueguide.feature.find.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.org.cesar.blueguide.feature.find.domain.HandshakeChannel
import br.org.cesar.blueguide.feature.find.domain.UwbProximityTracker
import br.org.cesar.blueguide.feature.find.domain.UwbSessionControllerFactory
import br.org.cesar.blueguide.feature.find.domain.UwbTrackerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Owns the [UwbProximityTracker] at ViewModel scope — same rotation-survival reasoning as
 * [br.org.cesar.blueguide.feature.fingerprint.ui.FingerprintViewModel]. Counterpart of the
 * iOS app's `ProximityTracker` usage in `TrackingView`.
 */
@HiltViewModel
class FindDeviceViewModel @Inject constructor(
    uwbSessionControllerFactory: UwbSessionControllerFactory,
    handshake: HandshakeChannel
) : ViewModel() {

    private val tracker = UwbProximityTracker(
        uwb = uwbSessionControllerFactory.create(viewModelScope),
        handshake = handshake,
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
