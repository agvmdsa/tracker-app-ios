package br.org.cesar.blueguide.core.sensors

import br.org.cesar.blueguide.core.model.DeviceMotionSample

interface DeviceMotionReading {
    /** The most recent reading; null before the first one arrives or on hardware without a
     *  rotation-vector sensor. */
    val latest: DeviceMotionSample?

    fun start()
    fun stop()
}
