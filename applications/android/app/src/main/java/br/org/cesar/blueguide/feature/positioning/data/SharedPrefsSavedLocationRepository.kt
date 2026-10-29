package br.org.cesar.blueguide.feature.positioning.data

import android.content.Context
import androidx.core.content.edit
import br.org.cesar.blueguide.core.data.prefs.SharedPrefsAnchorRepository
import br.org.cesar.blueguide.core.model.Point2D
import br.org.cesar.blueguide.feature.positioning.domain.SavedLocation
import br.org.cesar.blueguide.feature.positioning.domain.SavedLocationRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** [SavedLocation]s as JSON in SharedPreferences, the same way [SharedPrefsAnchorRepository] keeps anchors. */
class SharedPrefsSavedLocationRepository(context: Context) : SavedLocationRepository {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): List<SavedLocation> {
        val raw = prefs.getString(KEY_LOCATIONS, null) ?: return emptyList()
        return runCatching { Json.decodeFromString<List<SavedLocationDto>>(raw) }
            .getOrDefault(emptyList())
            .map { it.toSavedLocation() }
    }

    override fun save(locations: List<SavedLocation>) {
        prefs.edit { putString(KEY_LOCATIONS, Json.encodeToString(locations.map(SavedLocationDto::of))) }
    }

    @Serializable
    private data class SavedLocationDto(
        val id: String,
        val label: String,
        val x: Double,
        val y: Double,
        val savedAtMillis: Long
    ) {
        fun toSavedLocation() = SavedLocation(id, label, Point2D(x, y), savedAtMillis)

        companion object {
            fun of(location: SavedLocation) =
                SavedLocationDto(location.id, location.label, location.position.x, location.position.y, location.savedAtMillis)
        }
    }

    private companion object {
        const val PREFS_NAME = "blueguide_anchors"
        const val KEY_LOCATIONS = "saved_locations"
    }
}
