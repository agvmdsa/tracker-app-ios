package br.org.cesar.blueguide.core.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import br.org.cesar.blueguide.core.data.CalibrationRepository
import br.org.cesar.blueguide.core.data.CalibrationSettings
import br.org.cesar.blueguide.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** The calibration settings in SharedPreferences, next to the anchors (and, like them, on [io]).
 *  An unset field has no key, so it reads back as unset rather than as a default. */
class SharedPrefsCalibrationRepository @Inject constructor(
    @ApplicationContext context: Context,
    @IoDispatcher private val io: CoroutineDispatcher
) : CalibrationRepository {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun load(): CalibrationSettings = withContext(io) {
        CalibrationSettings(
            centerRssi = prefs.double(KEY_CENTER_RSSI),
            touchRssi = prefs.double(KEY_TOUCH_RSSI),
            isSpeedLimited = if (prefs.contains(KEY_SPEED_LIMITED)) prefs.getBoolean(KEY_SPEED_LIMITED, true) else null,
            maxSpeedKmh = prefs.double(KEY_MAX_SPEED_KMH)
        )
    }

    override suspend fun save(settings: CalibrationSettings) = withContext(io) {
        prefs.edit {
            putOrRemove(KEY_CENTER_RSSI, settings.centerRssi)
            putOrRemove(KEY_TOUCH_RSSI, settings.touchRssi)
            settings.isSpeedLimited?.let { putBoolean(KEY_SPEED_LIMITED, it) } ?: remove(KEY_SPEED_LIMITED)
            putOrRemove(KEY_MAX_SPEED_KMH, settings.maxSpeedKmh)
        }
    }

    private fun SharedPreferences.double(key: String): Double? = if (contains(key)) getFloat(key, 0f).toDouble() else null

    private fun SharedPreferences.Editor.putOrRemove(key: String, value: Double?) {
        if (value != null) putFloat(key, value.toFloat()) else remove(key)
    }

    private companion object {
        const val PREFS_NAME = "blueguide_anchors"
        const val KEY_CENTER_RSSI = "center_rssi"
        const val KEY_TOUCH_RSSI = "touch_rssi"
        const val KEY_SPEED_LIMITED = "speed_limited"
        const val KEY_MAX_SPEED_KMH = "max_speed_kmh"
    }
}
