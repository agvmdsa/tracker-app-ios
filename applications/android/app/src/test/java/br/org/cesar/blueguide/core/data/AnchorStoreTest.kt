package br.org.cesar.blueguide.core.data

import br.org.cesar.blueguide.core.model.Anchor
import br.org.cesar.blueguide.core.model.DeviceId
import br.org.cesar.blueguide.core.model.Point2D
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeAnchorRepository(seed: List<Anchor> = emptyList()) : AnchorRepository {
    var saved: List<Anchor> = seed
    var loads = 0
    override suspend fun load(): List<Anchor> {
        loads++
        return saved
    }
    override suspend fun save(anchors: List<Anchor>) {
        saved = anchors
    }
}

class AnchorStoreTest {

    private val a = Anchor(DeviceId("1A2B3C4D"), "A", Point2D(1.0, 1.0))

    @Test
    fun `the list is empty until load reads the disk, which it does once`() = runTest {
        val repo = FakeAnchorRepository(listOf(a))
        val store = AnchorStore(repo)

        assertTrue(store.anchors.value.isEmpty())
        store.load()
        store.load()

        assertEquals(listOf(a), store.anchors.value)
        assertEquals(1, repo.loads)
    }

    @Test
    fun `an update starts from the persisted list even before load, and is persisted`() = runTest {
        val repo = FakeAnchorRepository(listOf(a))
        val store = AnchorStore(repo)
        val b = Anchor(DeviceId("3F8A1C04"), "B", Point2D(0.0, 0.0))

        store.update { it + b }

        assertEquals(listOf(a, b), store.anchors.value)
        assertEquals(listOf(a, b), repo.saved)
    }

    @Test
    fun `removing an anchor drops it and persists`() = runTest {
        val repo = FakeAnchorRepository(listOf(a))
        val store = AnchorStore(repo).apply { load() }

        store.remove(a.id)

        assertNull(store.anchor(a.id))
        assertTrue("the repository must see the removal too", repo.saved.isEmpty())
    }

    @Test
    fun `layout check reports both unpositioned and unheard anchors`() {
        val positioned = DeviceId("1A2B3C4D")
        val unheard = DeviceId("3F8A1C04")
        val heardButUnpositioned = DeviceId("9B20E7D1")

        val check = AnchorLayoutCheck.of(
            anchors = listOf(
                Anchor(positioned, "A", Point2D(0.0, 0.0)),
                Anchor(unheard, "B", Point2D(1.0, 0.0))
            ),
            heard = listOf(positioned, heardButUnpositioned)
        )

        assertEquals(listOf(heardButUnpositioned), check.withoutPosition)
        assertEquals(listOf(unheard), check.notHeard)
        assertTrue(!check.isComplete)
    }
}
