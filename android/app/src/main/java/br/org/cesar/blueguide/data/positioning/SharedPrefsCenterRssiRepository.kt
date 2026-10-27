package br.org.cesar.blueguide.data.positioning

import android.content.Context
import androidx.core.content.edit
import br.org.cesar.blueguide.domain.position.CenterRssiRepository

/** The "RSSI no centro" setting in SharedPreferences, next to the anchors. */
class SharedPrefsCenterRssiRepository(context: Context) : CenterRssiRepository {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): Double? =
        if (prefs.contains(KEY_CENTER_RSSI)) prefs.getFloat(KEY_CENTER_RSSI, 0f).toDouble() else null

    override fun save(rssi: Double) {
        prefs.edit { putFloat(KEY_CENTER_RSSI, rssi.toFloat()) }
    }

    private companion object {
        const val PREFS_NAME = "blueguide_anchors"
        const val KEY_CENTER_RSSI = "center_rssi"
    }
}
