package br.org.cesar.blueguide.domain.position

/** Keeps the "RSSI no centro" and "RSSI encostado" settings across launches, like the iOS app's
 *  `@AppStorage`. Null until the user sets one, which means [PathLossModel]'s default. */
interface CalibrationRepository {
    fun loadCenterRssi(): Double?
    fun saveCenterRssi(rssi: Double)
    fun loadTouchRssi(): Double?
    fun saveTouchRssi(rssi: Double)
}
