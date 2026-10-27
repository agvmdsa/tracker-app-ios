package br.org.cesar.blueguide.domain.position

/** Keeps the "RSSI no centro" setting across launches, like the iOS app's `@AppStorage`. */
interface CenterRssiRepository {
    /** Null until the user sets one, which means [PathLossModel.DEFAULT_CENTER_RSSI]. */
    fun load(): Double?
    fun save(rssi: Double)
}
