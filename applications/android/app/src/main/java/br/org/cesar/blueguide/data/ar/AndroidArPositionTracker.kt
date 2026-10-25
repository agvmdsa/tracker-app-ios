package br.org.cesar.blueguide.data.ar

import android.content.Context
import br.org.cesar.blueguide.domain.ar.ArPositionTracking
import br.org.cesar.blueguide.domain.ar.ArTrackingState
import br.org.cesar.blueguide.domain.position.Point2D
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * [ArPositionTracking] over ARCore — the counterpart of the iOS app's `ARPositionTracker`
 * (ARKit). The real API was inspected directly from the downloaded `core-1.57.0.aar`
 * (`javap` on its classes), the same way this session's ESP32 and UWB work inspected their
 * real library APIs instead of guessing.
 *
 * ARCore is a **pull** model — the app calls [Session.update] itself to get each frame —
 * unlike ARKit's `ARSessionDelegate` push callbacks, so this polls on a coroutine loop
 * instead of receiving updates; see [start]. ARCore's camera space follows the same
 * OpenGL-style convention ARKit's does (X right, Y up, -Z forward), so the iOS tracker's
 * `x = translation.x, y = -translation.z` mapping applies unchanged — see [poll].
 *
 * **Unverified beyond compiling**: this environment has no camera-equipped,
 * ARCore-supported device to run it on — see `list.md`.
 */
class AndroidArPositionTracker(
    private val context: Context,
    private val scope: CoroutineScope
) : ArPositionTracking {

    private var session: Session? = null
    private var pollJob: Job? = null

    override var currentPosition: Point2D? = null
        private set
    override var trackingState: ArTrackingState = ArTrackingState.UNAVAILABLE
        private set
    override var isTracking: Boolean = false
        private set
    override var onUpdate: (() -> Unit)? = null

    override val isSupported: Boolean
        get() = ArCoreApk.getInstance().checkAvailability(context).isSupported

    override fun start() {
        stop()
        if (!isSupported) {
            trackingState = ArTrackingState.UNAVAILABLE
            return
        }

        val newSession = try {
            Session(context).apply {
                configure(
                    Config(this).apply {
                        // Position tracking only — no plane/light/depth features, same
                        // scope as the iOS tracker's plain ARWorldTrackingConfiguration.
                        planeFindingMode = Config.PlaneFindingMode.DISABLED
                        lightEstimationMode = Config.LightEstimationMode.DISABLED
                    }
                )
            }
        } catch (e: Exception) {
            trackingState = ArTrackingState.UNAVAILABLE
            return
        }

        try {
            newSession.resume()
        } catch (e: Exception) {
            // Most likely CameraNotAvailableException (permission missing or camera in use
            // by another app) — same "can't track" outcome either way.
            newSession.close()
            trackingState = ArTrackingState.UNAVAILABLE
            return
        }

        session = newSession
        isTracking = true
        pollJob = scope.launch {
            while (isTracking) {
                poll()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    override fun stop() {
        pollJob?.cancel()
        pollJob = null
        isTracking = false
        currentPosition = null
        trackingState = ArTrackingState.STOPPED
        session?.close()
        session = null
    }

    private fun poll() {
        val currentSession = session ?: return
        try {
            val frame = currentSession.update()
            val pose = frame.camera.pose
            currentPosition = Point2D(x = pose.tx().toDouble(), y = -pose.tz().toDouble())
            trackingState = when (frame.camera.trackingState) {
                TrackingState.TRACKING -> ArTrackingState.TRACKING
                TrackingState.PAUSED -> ArTrackingState.PAUSED
                TrackingState.STOPPED -> ArTrackingState.STOPPED
            }
        } catch (e: Exception) {
            // CameraNotAvailableException mid-session (camera taken by another app, etc.) —
            // leave the last known position/state rather than clearing it, since this is
            // expected to be transient.
            trackingState = ArTrackingState.PAUSED
        }
        onUpdate?.invoke()
    }

    private companion object {
        // Well above what a 2D position display needs; ARCore itself drives the camera at
        // its own (typically 30/60 Hz) rate regardless — this just bounds how often update()
        // is polled for a new frame.
        const val POLL_INTERVAL_MS = 33L
    }
}
