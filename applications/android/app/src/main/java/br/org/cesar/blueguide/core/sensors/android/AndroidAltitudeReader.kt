package br.org.cesar.blueguide.core.sensors.android

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import br.org.cesar.blueguide.core.sensors.AltitudeReading
import br.org.cesar.blueguide.core.sensors.AltitudeSample
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * [AltitudeReading] over `SensorManager`'s barometer — the counterpart of the iOS app's
 * `AltitudeReader` (`CMAltimeter`). `CMAltimeter.relativeAltitude` is documented as metres
 * relative to the pressure recorded when updates started; `SensorManager.getAltitude(p0, p)`
 * computes the same international barometric formula given a reference pressure, so passing
 * the first reading of this session as `p0` reproduces that behaviour here.
 */
class AndroidAltitudeReader @Inject constructor(@ApplicationContext context: Context) : AltitudeReading, SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

    /** hPa, same unit `TYPE_PRESSURE` delivers in. Set by the first reading of a session. */
    private var referencePressureHPa: Float? = null

    override var latest: AltitudeSample? = null
        private set

    override fun start() {
        val sensor = pressureSensor ?: return
        referencePressureHPa = null
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun stop() {
        sensorManager.unregisterListener(this)
        referencePressureHPa = null
        latest = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_PRESSURE) return
        val pressureHPa = event.values[0]
        val reference = referencePressureHPa ?: pressureHPa.also { referencePressureHPa = it }
        latest = AltitudeSample(
            relativeAltitudeM = SensorManager.getAltitude(reference, pressureHPa).toDouble(),
            pressureKPa = pressureHPa / HPA_PER_KPA
        )
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit

    private companion object {
        const val HPA_PER_KPA = 10.0
    }
}
