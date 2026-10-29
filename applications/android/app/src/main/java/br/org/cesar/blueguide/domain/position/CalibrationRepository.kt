package br.org.cesar.blueguide.domain.position

/** Keeps the "RSSI no centro", "RSSI encostado" and speed limit settings across launches, like the iOS app's
 *  `@AppStorage`. Null until the user sets one, which means [PathLossModel]'s default. */
interface CalibrationRepository {
    fun loadCenterRssi(): Double?
    fun saveCenterRssi(rssi: Double)
    fun loadTouchRssi(): Double?
    fun saveTouchRssi(rssi: Double)

    /** The speed limit in km/h, null when turned off; [loadMaxSpeedKmh] alone is what it was set
     *  to, kept while off. Unset means on, at [MotionLimit.DEFAULT_MAX_SPEED_KMH]. */
    fun loadIsSpeedLimited(): Boolean?
    fun loadMaxSpeedKmh(): Double?
    fun saveSpeedLimit(isLimited: Boolean, kmh: Double)
}
