package br.org.cesar.blueguide.presentation.ar

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.org.cesar.blueguide.data.ar.AndroidArPositionTracker
import br.org.cesar.blueguide.domain.ar.ArPositionTracking
import br.org.cesar.blueguide.domain.ar.ArTrackingState
import br.org.cesar.blueguide.domain.position.Point2D
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ArPositionSnapshot(
    val isTracking: Boolean = false,
    val currentPosition: Point2D? = null,
    val trackingState: ArTrackingState = ArTrackingState.UNAVAILABLE
)

/**
 * Owns the [ArPositionTracking] instance at ViewModel scope — same rotation-survival
 * reasoning as [br.org.cesar.blueguide.presentation.fingerprint.FingerprintViewModel].
 * [ArPositionTracking.onUpdate] is wired into a `StateFlow` here, since the tracker itself
 * is a plain polled object (see its doc comment) with nothing Compose can observe directly.
 */
class ArPositionViewModel(appContext: Context) : ViewModel() {

    private val tracker: ArPositionTracking = AndroidArPositionTracker(appContext, viewModelScope)

    private val _state = MutableStateFlow(ArPositionSnapshot())
    val state: StateFlow<ArPositionSnapshot> = _state.asStateFlow()

    val isSupported: Boolean get() = tracker.isSupported

    init {
        tracker.onUpdate = {
            _state.value = ArPositionSnapshot(tracker.isTracking, tracker.currentPosition, tracker.trackingState)
        }
    }

    fun start() {
        tracker.start()
        _state.value = ArPositionSnapshot(tracker.isTracking, tracker.currentPosition, tracker.trackingState)
    }

    fun stop() {
        tracker.stop()
        _state.value = ArPositionSnapshot(tracker.isTracking, tracker.currentPosition, tracker.trackingState)
    }

    override fun onCleared() {
        tracker.stop()
    }
}
