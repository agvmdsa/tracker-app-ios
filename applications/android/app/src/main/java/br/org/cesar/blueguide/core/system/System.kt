package br.org.cesar.blueguide.core.system

/** Milliseconds on a clock that never jumps (`SystemClock.elapsedRealtime()` on Android): the
 *  clock the sightings' `lastSeenAtMillis` and the recordings' `t_ms` are on. */
fun interface MonotonicClock {
    fun nowMs(): Long
}

/** The phone the app runs on, as reported to the API and written into every recording. */
data class DeviceInfo(val model: String, val systemVersion: String)
