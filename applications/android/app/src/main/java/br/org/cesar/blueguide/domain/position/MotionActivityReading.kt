package br.org.cesar.blueguide.domain.position

/**
 * A classified state from Google Play Services' activity recognition — the counterpart of
 * the iOS app's `MotionActivityState`/`CMMotionActivityManager`. Separates "stopped on
 * purpose at a point" from "walking past it" in a fingerprint recording, and marks rows that
 * happened while the phone wasn't actually stationary even though the recording was
 * labelled as a static point.
 */
enum class MotionActivityState(val wireValue: String) {
    STATIONARY("stationary"),
    WALKING("walking"),
    RUNNING("running"),
    AUTOMOTIVE("automotive"),
    CYCLING("cycling"),
    UNKNOWN("unknown")
}

/** [confidence] is 0-100 (the platform's own `DetectedActivity.confidence` range) — a
 *  different scale from the iOS app's 0/1/2, documented in the recording's metadata rather
 *  than remapped, so nothing is lost or invented converting between them. */
data class MotionActivitySample(val state: MotionActivityState, val confidence: Int)

interface MotionActivityReading {
    /** The most recent reading; null before the first one arrives, before authorization is
     *  granted, or on hardware without Google Play Services. */
    val latest: MotionActivitySample?

    fun start()
    fun stop()
}
