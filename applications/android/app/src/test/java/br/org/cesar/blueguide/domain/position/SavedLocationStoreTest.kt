package br.org.cesar.blueguide.domain.position

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class SavedLocationStoreTest {

    private class MemoryRepository : SavedLocationRepository {
        var saved: List<SavedLocation> = emptyList()
        override fun load() = saved
        override fun save(locations: List<SavedLocation>) { saved = locations }
    }

    @Test
    fun `saving a location keeps it across launches`() {
        val repository = MemoryRepository()
        val store = SavedLocationStore(repository)

        val location = store.save("Meu carro", Point2D(1.5, 2.0), savedAtMillis = 1_000)

        val reloaded = SavedLocationStore(repository).also { it.load() }
        assertEquals(listOf(location), reloaded.locations)
        assertEquals("Meu carro", location.label)
    }

    @Test
    fun `locations are kept in the order they were saved`() {
        val store = SavedLocationStore(MemoryRepository())

        store.save("A", Point2D(0.0, 0.0), savedAtMillis = 1)
        store.save("B", Point2D(1.0, 0.0), savedAtMillis = 2)

        assertEquals(listOf("A", "B"), store.locations.map { it.label })
    }

    @Test
    fun `removing a location forgets it`() {
        val repository = MemoryRepository()
        val store = SavedLocationStore(repository)
        val first = store.save("A", Point2D(0.0, 0.0), savedAtMillis = 1)
        store.save("B", Point2D(1.0, 0.0), savedAtMillis = 2)

        store.remove(first.id)

        assertEquals(listOf("B"), store.locations.map { it.label })
        assertEquals(listOf("B"), repository.saved.map { it.label })
    }

    @Test
    fun `each location gets its own id`() {
        val store = SavedLocationStore(MemoryRepository())

        val a = store.save("A", Point2D(0.0, 0.0), savedAtMillis = 1)
        val b = store.save("A", Point2D(0.0, 0.0), savedAtMillis = 1)

        assertTrue(a.id != b.id)
        UUID.fromString(a.id)
    }

    /** As the iOS app's `NavigateToLocationView`: 0 is towards +y on the map, clockwise. */
    @Test
    fun `the bearing is measured from the map's y axis, clockwise`() {
        val here = Point2D(1.0, 1.0)

        assertEquals(0.0, SavedLocation.bearingRadians(here, Point2D(1.0, 5.0)), 1e-9)
        assertEquals(PI / 2, SavedLocation.bearingRadians(here, Point2D(4.0, 1.0)), 1e-9)
        assertEquals(PI, SavedLocation.bearingRadians(here, Point2D(1.0, -2.0)), 1e-9)
        assertEquals(-PI / 2, SavedLocation.bearingRadians(here, Point2D(-3.0, 1.0)), 1e-9)
    }
}
