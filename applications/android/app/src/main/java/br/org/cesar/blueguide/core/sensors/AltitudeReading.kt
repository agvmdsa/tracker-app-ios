package br.org.cesar.blueguide.core.sensors

/**
 * A barometric reading — the counterpart of the iOS app's `AltitudeSample`/`CMAltimeter`.
 * [relativeAltitudeM] is metres **relative to wherever altitude updates started** for this
 * run of the app (not altitude above sea level and not a floor number), so it only tells two
 * readings apart — enough to separate floors in a multi-floor building as long as every
 * recording in a session starts from the same reference.
 */
data class AltitudeSample(val relativeAltitudeM: Double, val pressureKPa: Double)

interface AltitudeReading {
    /** The most recent reading; null before the first one arrives or on hardware without a
     *  barometer. */
    val latest: AltitudeSample?

    fun start()
    fun stop()
}
