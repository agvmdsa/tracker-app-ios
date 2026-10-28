package br.org.cesar.blueguide.data.positioning

import android.content.Context
import androidx.core.content.edit
import br.org.cesar.blueguide.domain.position.CalibrationRepository

/** The calibration settings in SharedPreferences, next to the anchors. */
class SharedPrefsCalibrationRepository(context: Context) : CalibrationRepository {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun loadCenterRssi(): Double? = load(KEY_CENTER_RSSI)

    override fun saveCenterRssi(rssi: Double) = save(KEY_CENTER_RSSI, rssi)

    override fun loadTouchRssi(): Double? = load(KEY_TOUCH_RSSI)

    override fun saveTouchRssi(rssi: Double) = save(KEY_TOUCH_RSSI, rssi)

    override fun loadIsSpeedLimited(): Boolean? =
        if (prefs.contains(KEY_SPEED_LIMITED)) prefs.getBoolean(KEY_SPEED_LIMITED, true) else null

    override fun loadMaxSpeedKmh(): Double? = load(KEY_MAX_SPEED_KMH)

    override fun saveSpeedLimit(isLimited: Boolean, kmh: Double) {
        prefs.edit {
            putBoolean(KEY_SPEED_LIMITED, isLimited)
            putFloat(KEY_MAX_SPEED_KMH, kmh.toFloat())
        }
    }

    private fun load(key: String): Double? =
        if (prefs.contains(key)) prefs.getFloat(key, 0f).toDouble() else null

    private fun save(key: String, rssi: Double) {
        prefs.edit { putFloat(key, rssi.toFloat()) }
    }

    private companion object {
        const val PREFS_NAME = "blueguide_anchors"
        const val KEY_CENTER_RSSI = "center_rssi"
        const val KEY_TOUCH_RSSI = "touch_rssi"
        const val KEY_SPEED_LIMITED = "speed_limited"
        const val KEY_MAX_SPEED_KMH = "max_speed_kmh"
    }
}
