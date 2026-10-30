package br.org.cesar.blueguide.core.sensors

import br.org.cesar.blueguide.core.model.AltitudeSample

interface AltitudeReading {
    /** The most recent reading; null before the first one arrives or on hardware without a
     *  barometer. */
    val latest: AltitudeSample?

    fun start()
    fun stop()
}
