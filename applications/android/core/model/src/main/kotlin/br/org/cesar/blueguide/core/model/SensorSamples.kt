package br.org.cesar.blueguide.core.model

/**
 * The phone's attitude (orientation), in radians — the counterpart of the iOS app's
 * `DeviceMotionSample`/`CMDeviceMotion` reading. Tells how the phone is being held (and so
 * how the body might be blocking the antenna), as opposed to a single raw accelerometer
 * sample, which mixes gravity and actual movement into the same three numbers.
 *
 * Axis convention differs from iOS by construction (`AndroidOrientationReader` derives
 * these from `SensorManager`'s `getOrientation()`, a different sensor-fusion API than
 * `CMDeviceMotion`'s `xArbitraryZVertical` attitude) — both are "roll/pitch/yaw in radians,
 * yaw not tied to true/magnetic north", but not guaranteed to agree on sign or zero point.
 * The fingerprint recording's metadata documents which convention a given file used.
 */
data class DeviceMotionSample(val roll: Double, val pitch: Double, val yaw: Double)

/**
 * A barometric reading — the counterpart of the iOS app's `AltitudeSample`/`CMAltimeter`.
 * [relativeAltitudeM] is metres **relative to wherever altitude updates started** for this
 * run of the app (not altitude above sea level and not a floor number), so it only tells two
 * readings apart — enough to separate floors in a multi-floor building as long as every
 * recording in a session starts from the same reference.
 */
data class AltitudeSample(val relativeAltitudeM: Double, val pressureKPa: Double)

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
    UNKNOWN("unknown");

    companion object {
        /** The state a recording's `motion_state` column holds; null for a value no app writes. */
        fun ofWireValue(value: String): MotionActivityState? = entries.firstOrNull { it.wireValue == value }
    }
}

/** [confidence] is 0-100 (the platform's own `DetectedActivity.confidence` range) — a
 *  different scale from the iOS app's 0/1/2, documented in the recording's metadata rather
 *  than remapped, so nothing is lost or invented converting between them. */
data class MotionActivitySample(val state: MotionActivityState, val confidence: Int)
