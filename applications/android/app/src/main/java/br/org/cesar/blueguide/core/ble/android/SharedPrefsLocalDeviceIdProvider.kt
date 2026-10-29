package br.org.cesar.blueguide.core.ble.android

import android.content.Context
import androidx.core.content.edit
import br.org.cesar.blueguide.core.ble.BlueGuideProtocol
import br.org.cesar.blueguide.core.ble.LocalDeviceIdProvider
import br.org.cesar.blueguide.core.model.DeviceId

/**
 * Keeps this install's [DeviceId] in SharedPreferences, generating it the first time it
 * is asked for.
 *
 * Uninstalling or clearing app data starts a new identity, which is the right
 * granularity: what the advertisement claims is "this install", not "this hardware".
 */
class SharedPrefsLocalDeviceIdProvider(context: Context) : LocalDeviceIdProvider {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // `lazy` is synchronized by default, so two modes starting at once cannot race and
    // persist two different ids.
    private val id: DeviceId by lazy { loadOrCreate() }

    override fun current(): DeviceId = id

    private fun loadOrCreate(): DeviceId {
        prefs.getString(KEY_DEVICE_ID, null)?.let { return DeviceId(it) }

        return BlueGuideProtocol.randomId().also { generated ->
            prefs.edit { putString(KEY_DEVICE_ID, generated.value) }
        }
    }

    private companion object {
        const val PREFS_NAME = "blueguide_identity"
        const val KEY_DEVICE_ID = "device_id"
    }
}
