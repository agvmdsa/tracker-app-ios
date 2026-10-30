package br.org.cesar.blueguide.feature.fingerprint.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.org.cesar.blueguide.core.ble.BleScanner
import br.org.cesar.blueguide.core.data.AnchorStore
import br.org.cesar.blueguide.core.data.CalibrationStore
import br.org.cesar.blueguide.core.dataset.FingerprintLabel
import br.org.cesar.blueguide.core.model.DeviceId
import br.org.cesar.blueguide.core.network.FingerprintUploading
import br.org.cesar.blueguide.core.sensors.AltitudeReading
import br.org.cesar.blueguide.core.sensors.DeviceMotionReading
import br.org.cesar.blueguide.core.sensors.MotionActivityReading
import br.org.cesar.blueguide.core.system.DeviceInfo
import br.org.cesar.blueguide.core.system.MonotonicClock
import br.org.cesar.blueguide.di.IoDispatcher
import br.org.cesar.blueguide.feature.fingerprint.domain.FingerprintRecorder
import br.org.cesar.blueguide.feature.fingerprint.domain.FingerprintRecorderState
import br.org.cesar.blueguide.feature.fingerprint.domain.FingerprintRecordingStore
import br.org.cesar.blueguide.feature.fingerprint.domain.StoredRecording
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Owns the [FingerprintRecorder] at ViewModel scope — the fix for a recording in progress
 * being silently dropped on a screen rotation. It used to live directly in a `@Composable`
 * (built with `remember`, started/stopped by a `DisposableEffect`), which is torn down and
 * rebuilt on every configuration change; a `ViewModel` is retained by the framework across
 * those, same as `DeviceListViewModel` already relies on for the positioning screen.
 */
@HiltViewModel(assistedFactory = FingerprintViewModel.Factory::class)
class FingerprintViewModel @AssistedInject constructor(
    /** This install's id ([DeviceId.value]), the `node` of every row. A plain string: the Java
     *  code Dagger generates doesn't handle Kotlin value classes as parameters. */
    @Assisted nodeId: String,
    scanner: BleScanner,
    deviceMotion: DeviceMotionReading,
    altimeter: AltitudeReading,
    motionActivity: MotionActivityReading,
    uploader: FingerprintUploading,
    private val anchorStore: AnchorStore,
    private val calibrationStore: CalibrationStore,
    store: FingerprintRecordingStore,
    deviceInfo: DeviceInfo,
    clock: MonotonicClock,
    @IoDispatcher private val io: CoroutineDispatcher
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(nodeId: String): FingerprintViewModel
    }

    private val recorder = FingerprintRecorder(
        scanner = scanner,
        deviceMotion = deviceMotion,
        altimeter = altimeter,
        motionActivity = motionActivity,
        uploader = uploader,
        positioningSetup = { calibrationStore.settings.value.positioningSetup(anchorStore.anchors.value) },
        store = store,
        node = DeviceId(nodeId),
        model = deviceInfo.model,
        systemVersion = deviceInfo.systemVersion,
        // Its own thread rather than viewModelScope: the rows are written off the main thread,
        // and stop() in onCleared — when viewModelScope is already cancelled — can still
        // discard a recording left half-way.
        dispatcher = io.limitedParallelism(1),
        monotonicMs = clock::nowMs
    )

    val state: StateFlow<FingerprintRecorderState> = recorder.state

    init {
        recorder.start()
        // Usually already read by the Positioning tab; they are the same stores, so anchors and
        // calibration edited there show up here at once.
        viewModelScope.launch {
            anchorStore.load()
            calibrationStore.load()
        }
    }

    fun startRecording(label: FingerprintLabel, durationSeconds: Double) = recorder.startRecording(label, durationSeconds)

    fun cancelRecording() = recorder.cancelRecording()

    fun deleteRecording(recording: StoredRecording) = recorder.deleteRecording(recording)

    override fun onCleared() {
        recorder.stop()
    }
}
