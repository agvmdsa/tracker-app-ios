package br.org.cesar.blueguide.core.sensors

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

interface DeviceMotionReading {
    /** The most recent reading; null before the first one arrives or on hardware without a
     *  rotation-vector sensor. */
    val latest: DeviceMotionSample?

    fun start()
    fun stop()
}
