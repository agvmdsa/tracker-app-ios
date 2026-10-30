package br.org.cesar.blueguide.core.sensors

import br.org.cesar.blueguide.core.model.MotionActivitySample

interface MotionActivityReading {
    /** The most recent reading; null before the first one arrives, before authorization is
     *  granted, or on hardware without Google Play Services. */
    val latest: MotionActivitySample?

    fun start()
    fun stop()
}
