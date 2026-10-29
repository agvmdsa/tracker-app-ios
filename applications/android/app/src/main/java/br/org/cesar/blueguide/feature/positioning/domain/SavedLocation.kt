package br.org.cesar.blueguide.feature.positioning.domain

import br.org.cesar.blueguide.core.model.Point2D
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

interface SavedLocationRepository {
    fun load(): List<SavedLocation>
    fun save(locations: List<SavedLocation>)
}

/** The saved locations, kept across launches — the counterpart of the iOS app's `SavedLocationStore`. */
class SavedLocationStore(private val repository: SavedLocationRepository) {

    /** Empty until [load] runs, which reads off disk, so call it off the main thread. */
    var locations: List<SavedLocation> = emptyList()
        private set

    fun load() {
        locations = repository.load()
    }

    fun save(label: String, position: Point2D, savedAtMillis: Long = System.currentTimeMillis()): SavedLocation {
        val location = SavedLocation(UUID.randomUUID().toString(), label, position, savedAtMillis)
        locations = locations + location
        repository.save(locations)
        return location
    }

    fun remove(id: String) {
        locations = locations.filterNot { it.id == id }
        repository.save(locations)
    }
}
