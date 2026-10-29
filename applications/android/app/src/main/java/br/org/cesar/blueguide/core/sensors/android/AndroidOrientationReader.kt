package br.org.cesar.blueguide.core.sensors.android

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import br.org.cesar.blueguide.core.sensors.DeviceMotionReading
import br.org.cesar.blueguide.core.sensors.DeviceMotionSample

/**
 * [DeviceMotionReading] over `SensorManager`'s rotation-vector sensor — the counterpart of
 * the iOS app's `DeviceMotionReader` (`CMMotionManager`'s device-motion attitude). Uses
 * sensor fusion (accelerometer + gyroscope, and the magnetometer where available) rather
 * than a single raw accelerometer sample, same reasoning as the iOS reader.
 *
 * `getOrientation()` returns `[azimuth, pitch, roll]` (its own, fixed order) — mapped here
 * to this app's `yaw/pitch/roll` names, but Android's axis convention is not the same as
 * `CMDeviceMotion`'s `xArbitraryZVertical` attitude the iOS reader uses. Both are "radians,
 * yaw not tied to true/magnetic north", but a model trained on one platform's values should
 * not assume the other's agree on sign or zero point — the fingerprint recording's metadata
 * documents which convention a given file used.
 */
class AndroidOrientationReader(context: Context) : DeviceMotionReading, SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    override var latest: DeviceMotionSample? = null
        private set

    override fun start() {
        val sensor = rotationVectorSensor ?: return
        sensorManager.registerListener(this, sensor, SENSOR_DELAY_MICROS)
    }

    override fun stop() {
        sensorManager.unregisterListener(this)
        latest = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        val orientation = FloatArray(3)
        SensorManager.getOrientation(rotationMatrix, orientation)
        // getOrientation()'s own order: [0]=azimuth around Z, [1]=pitch around X, [2]=roll around Y.
        latest = DeviceMotionSample(
            roll = orientation[2].toDouble(),
            pitch = orientation[1].toDouble(),
            yaw = orientation[0].toDouble()
        )
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit

    private companion object {
        // Comfortably above the beacons' 10 advertisements/second (see DeviceMotionReader.updateInterval
        // on iOS, which this mirrors): a request, not a guarantee — the OS may deliver slower.
        const val SENSOR_DELAY_MICROS = 20_000 // 50 Hz
    }
}
