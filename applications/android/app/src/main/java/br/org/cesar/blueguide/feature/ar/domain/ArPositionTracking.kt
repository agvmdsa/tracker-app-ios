package br.org.cesar.blueguide.feature.ar.domain

import br.org.cesar.blueguide.core.model.Point2D

/** Counterpart of the iOS app's `ARCamera.TrackingState` (ARKit) — ARCore's own
 *  `TrackingState` enum has the same three cases, `UNAVAILABLE` added here for "not even
 *  started" / "hardware unsupported", which ARKit's `.notAvailable` already covers but
 *  ARCore's enum has no equivalent of. */
enum class ArTrackingState { TRACKING, PAUSED, STOPPED, UNAVAILABLE }

/**
 * Wraps one AR session tracking this device's position — the counterpart of the iOS app's
 * `ARPositionTracking` (there, `ARSession`/ARKit). See `AndroidArPositionTracker` for the
 * real implementation (ARCore) and its caveats.
 */
interface ArPositionTracking {
    val isTracking: Boolean
    val currentPosition: Point2D?
    val trackingState: ArTrackingState
    val isSupported: Boolean

    /** Called after each poll cycle (whether or not anything changed) — ARCore is a pull
     *  model with no update callback of its own, so without this the owner would have no
     *  way to know a new frame (and so a Compose recomposition) is due. */
    var onUpdate: (() -> Unit)?

    fun start()
    fun stop()
}
