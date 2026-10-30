package br.org.cesar.blueguide.core.data

import br.org.cesar.blueguide.core.model.Anchor
import br.org.cesar.blueguide.core.positioning.MotionLimit
import br.org.cesar.blueguide.core.positioning.PathLossModel
import br.org.cesar.blueguide.core.positioning.PositioningSetup
import kotlinx.coroutines.flow.StateFlow

/**
 * The "RSSI no centro", "RSSI encostado" and speed limit settings, kept across launches like the
 * iOS app's `@AppStorage`. A null field was never set, which means [PathLossModel]'s or
 * [MotionLimit]'s default.
 */
data class CalibrationSettings(
    val centerRssi: Double? = null,
    val touchRssi: Double? = null,
    /** Unset means on. */
    val isSpeedLimited: Boolean? = null,
    /** What the limit is set to, kept while it is off; unset means [MotionLimit.DEFAULT_MAX_SPEED_KMH]. */
    val maxSpeedKmh: Double? = null
) {
    /** "RSSI no centro": heard from the anchors standing in their middle, and read as their mean
     *  distance to it. */
    val centerRssiOrDefault: Double get() = centerRssi ?: PathLossModel.DEFAULT_CENTER_RSSI

    /** "RSSI encostado": heard touching a beacon, and read as [PathLossModel.TOUCH_DISTANCE_METERS]. */
    val touchRssiOrDefault: Double get() = touchRssi ?: PathLossModel.DEFAULT_TOUCH_RSSI

    /** "Limitar pela velocidade". */
    val isSpeedLimitedOrDefault: Boolean get() = isSpeedLimited ?: true

    val maxSpeedKmhOrDefault: Double get() = maxSpeedKmh ?: MotionLimit.DEFAULT_MAX_SPEED_KMH

    /** What the positioning engine ranges [anchors] with under these settings — on the
     *  Positioning screen and in each fingerprint recording alike. */
    fun positioningSetup(anchors: List<Anchor>) = PositioningSetup(
        anchors,
        centerRssi = centerRssiOrDefault,
        touchRssi = touchRssiOrDefault,
        maxSpeedKmh = if (isSpeedLimitedOrDefault) maxSpeedKmhOrDefault else null
    )
}

/** Where [CalibrationSettings] are kept. Main-safe: implementations do their own I/O off the main thread. */
interface CalibrationRepository {
    suspend fun load(): CalibrationSettings
    suspend fun save(settings: CalibrationSettings)
}

/** The calibration for the whole app: the Positioning screen edits it, and each fingerprint
 *  recording keeps a copy in its metadata. */
class CalibrationStore(repository: CalibrationRepository) {
    private val persisted = PersistedValue(CalibrationSettings(), repository::load, repository::save)

    /** All unset until [load] has read the disk. */
    val settings: StateFlow<CalibrationSettings> = persisted.value

    suspend fun load() = persisted.load()

    suspend fun update(transform: (CalibrationSettings) -> CalibrationSettings) = persisted.update(transform)
}
