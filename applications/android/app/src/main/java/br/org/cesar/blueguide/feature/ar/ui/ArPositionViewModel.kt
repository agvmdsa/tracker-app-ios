package br.org.cesar.blueguide.feature.ar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.org.cesar.blueguide.core.model.Point2D
import br.org.cesar.blueguide.feature.ar.domain.ArPositionTrackerFactory
import br.org.cesar.blueguide.feature.ar.domain.ArPositionTracking
import br.org.cesar.blueguide.feature.ar.domain.ArTrackingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class ArPositionSnapshot(
    val isTracking: Boolean = false,
    val currentPosition: Point2D? = null,
    val trackingState: ArTrackingState = ArTrackingState.UNAVAILABLE
)

/**
 * Owns the [ArPositionTracking] instance at ViewModel scope — same rotation-survival
 * reasoning as [br.org.cesar.blueguide.feature.fingerprint.ui.FingerprintViewModel].
 * [ArPositionTracking.onUpdate] is wired into a `StateFlow` here, since the tracker itself
 * is a plain polled object (see its doc comment) with nothing Compose can observe directly.
 */
@HiltViewModel
class ArPositionViewModel @Inject constructor(trackerFactory: ArPositionTrackerFactory) : ViewModel() {

    private val tracker: ArPositionTracking = trackerFactory.create(viewModelScope)

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
