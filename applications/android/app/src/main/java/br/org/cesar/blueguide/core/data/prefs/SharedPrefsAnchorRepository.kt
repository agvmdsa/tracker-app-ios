package br.org.cesar.blueguide.core.data.prefs

import android.content.Context
import androidx.core.content.edit
import br.org.cesar.blueguide.core.data.AnchorRepository
import br.org.cesar.blueguide.core.model.Anchor
import br.org.cesar.blueguide.core.model.DeviceId
import br.org.cesar.blueguide.core.model.Point2D
import br.org.cesar.blueguide.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Persists [Anchor]s as JSON in SharedPreferences — same storage class and granularity as
 * [br.org.cesar.blueguide.core.ble.android.SharedPrefsLocalDeviceIdProvider], same file format
 * choice as the iOS app's `AnchorStore` (`UserDefaults` + `JSONEncoder`).
 *
 * The DTO below is kept separate from the domain [Anchor] so the domain layer never needs
 * a `kotlinx.serialization` annotation, matching how [br.org.cesar.blueguide.core.ble]
 * never mentions Android Bluetooth types. Reads and writes on [io], so it is safe to call from
 * the main thread.
 */
class SharedPrefsAnchorRepository @Inject constructor(
    @ApplicationContext context: Context,
    @IoDispatcher private val io: CoroutineDispatcher
) : AnchorRepository {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun load(): List<Anchor> = withContext(io) {
        val raw = prefs.getString(KEY_ANCHORS, null) ?: return@withContext emptyList()
        runCatching { Json.decodeFromString<List<AnchorDto>>(raw) }
            .getOrDefault(emptyList())
            .map { it.toAnchor() }
    }

    override suspend fun save(anchors: List<Anchor>) = withContext(io) {
        val raw = Json.encodeToString(anchors.map(AnchorDto::of))
        prefs.edit { putString(KEY_ANCHORS, raw) }
    }

    @Serializable
    private data class AnchorDto(
        val id: String,
        val label: String,
        val x: Double,
        val y: Double,
        val height: Double? = null
    ) {
        fun toAnchor() = Anchor(DeviceId(id), label, Point2D(x, y), height)

        companion object {
            fun of(anchor: Anchor) = AnchorDto(
                id = anchor.id.value,
                label = anchor.label,
                x = anchor.position.x,
                y = anchor.position.y,
                height = anchor.height
            )
        }
    }

    private companion object {
        const val PREFS_NAME = "blueguide_anchors"
        const val KEY_ANCHORS = "anchors"
    }
}
