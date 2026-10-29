package br.org.cesar.blueguide.feature.positioning.domain

import br.org.cesar.blueguide.core.data.PersistedValue
import br.org.cesar.blueguide.core.model.Point2D
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID
import kotlin.math.atan2

/** A named point on the map to walk back to — the counterpart of the iOS app's `SavedLocation`. */
data class SavedLocation(
    val id: String,
    val label: String,
    val position: Point2D,
    val savedAtMillis: Long
) {
    companion object {
        /** The direction from [from] to [to] on the map, in radians from its y axis, clockwise.
         *  Relative to the anchors, not to where the phone is facing: there is no compass
         *  calibrated to the map. */
        fun bearingRadians(from: Point2D, to: Point2D): Double = atan2(to.x - from.x, to.y - from.y)
    }
}

/** Where the saved locations are kept. Main-safe: implementations do their own I/O off the
 *  main thread. */
interface SavedLocationRepository {
    suspend fun load(): List<SavedLocation>
    suspend fun save(locations: List<SavedLocation>)
}

/** The saved locations, kept across launches — the counterpart of the iOS app's `SavedLocationStore`. */
class SavedLocationStore(repository: SavedLocationRepository) {
    private val persisted = PersistedValue(emptyList(), repository::load, repository::save)

    /** Empty until [load] has read the disk; in the order they were saved. */
    val locations: StateFlow<List<SavedLocation>> = persisted.value

    suspend fun load() = persisted.load()

    suspend fun save(label: String, position: Point2D, savedAtMillis: Long = System.currentTimeMillis()): SavedLocation {
        val location = SavedLocation(UUID.randomUUID().toString(), label, position, savedAtMillis)
        persisted.update { it + location }
        return location
    }

    suspend fun remove(id: String) {
        persisted.update { locations -> locations.filterNot { it.id == id } }
    }
}
