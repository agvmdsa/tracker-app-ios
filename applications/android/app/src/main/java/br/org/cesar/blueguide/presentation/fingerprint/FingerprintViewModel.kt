package br.org.cesar.blueguide.presentation.fingerprint

import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.org.cesar.blueguide.data.ble.AndroidBleScanner
import br.org.cesar.blueguide.data.network.PositioningApiClient
import br.org.cesar.blueguide.data.positioning.SharedPrefsAnchorRepository
import br.org.cesar.blueguide.data.sensors.AndroidAltitudeReader
import br.org.cesar.blueguide.data.sensors.AndroidMotionActivityReader
import br.org.cesar.blueguide.data.sensors.AndroidOrientationReader
import br.org.cesar.blueguide.domain.entity.DeviceId
import br.org.cesar.blueguide.domain.position.AnchorStore
import br.org.cesar.blueguide.domain.position.FingerprintLabel
import br.org.cesar.blueguide.domain.position.FingerprintRecorder
import br.org.cesar.blueguide.domain.position.FingerprintRecorderState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Owns the [FingerprintRecorder] at ViewModel scope — the fix for a recording in progress
 * being silently dropped on a screen rotation. It used to live directly in a `@Composable`
 * (built with `remember`, started/stopped by a `DisposableEffect`), which is torn down and
 * rebuilt on every configuration change; a `ViewModel` is retained by the framework across
 * those, same as `DeviceListViewModel` already relies on for the positioning screen.
 */
class FingerprintViewModel(
    appContext: Context,
    node: DeviceId
) : ViewModel() {

    private val anchorStore = AnchorStore(SharedPrefsAnchorRepository(appContext))

    private val recorder = FingerprintRecorder(
        scanner = AndroidBleScanner(appContext),
        deviceMotion = AndroidOrientationReader(appContext),
        altimeter = AndroidAltitudeReader(appContext),
        motionActivity = AndroidMotionActivityReader(appContext),
        uploader = PositioningApiClient(),
        anchors = { anchorStore.anchors },
        directory = File(appContext.filesDir, "Fingerprints"),
        node = node,
        model = Build.MODEL,
        systemVersion = Build.VERSION.RELEASE,
        scope = viewModelScope,
        monotonicMs = { SystemClock.elapsedRealtime() }
    )

    val state: StateFlow<FingerprintRecorderState> = recorder.state

    init {
        recorder.start()
        refreshAnchors()
    }

    /**
     * Re-reads [AnchorStore] from disk. This `ViewModel` keeps its own instance rather than
     * sharing the positioning screen's (crossing `ViewModel`s would mean one holding a
     * reference into the other's lifecycle, which is backwards), so an anchor added on the
     * Positioning tab isn't picked up here until this runs — call it whenever the Fingerprint
     * tab becomes visible.
     */
    fun refreshAnchors() {
        viewModelScope.launch(Dispatchers.IO) { anchorStore.load() }
    }

    fun startRecording(label: FingerprintLabel, durationSeconds: Double) =
        recorder.startRecording(label, durationSeconds)

    fun cancelRecording() = recorder.cancelRecording()

    fun deleteRecording(file: File) = recorder.deleteRecording(file)

    override fun onCleared() {
        recorder.stop()
    }
}
