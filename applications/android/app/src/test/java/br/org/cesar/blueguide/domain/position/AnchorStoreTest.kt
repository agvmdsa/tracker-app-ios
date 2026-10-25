package br.org.cesar.blueguide.domain.position

import br.org.cesar.blueguide.domain.entity.DeviceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeAnchorRepository(seed: List<Anchor> = emptyList()) : AnchorRepository {
    var saved: List<Anchor> = seed
    override fun load(): List<Anchor> = saved
    override fun save(anchors: List<Anchor>) {
        saved = anchors
    }
}

class AnchorStoreTest {

    @Test
    fun `load reads whatever the repository has`() {
        val repo = FakeAnchorRepository(listOf(Anchor(DeviceId("1A2B3C4D"), "A", Point2D(1.0, 1.0))))
        val store = AnchorStore(repo)

        assertTrue("anchors must stay empty until load() runs", store.anchors.isEmpty())
        store.load()
        assertEquals(1, store.anchors.size)
    }

    @Test
    fun `adding an anchor without a label assigns the next free letter`() {
        val store = AnchorStore(FakeAnchorRepository()).apply { load() }

        val first = store.addAnchor(DeviceId("1A2B3C4D"), label = null, position = Point2D(0.0, 0.0))
        val second = store.addAnchor(DeviceId("3F8A1C04"), label = null, position = Point2D(1.0, 0.0))

        assertEquals("A", first.label)
        assertEquals("B", second.label)
    }

    @Test
    fun `adding the same id again replaces it instead of duplicating`() {
        val store = AnchorStore(FakeAnchorRepository()).apply { load() }
        val id = DeviceId("1A2B3C4D")

        store.addAnchor(id, label = "A", position = Point2D(0.0, 0.0))
        store.addAnchor(id, label = "A", position = Point2D(5.0, 5.0))

        assertEquals(1, store.anchors.size)
        assertEquals(Point2D(5.0, 5.0), store.anchor(id)?.position)
    }

    @Test
    fun `removing an anchor drops it and persists`() {
        val repo = FakeAnchorRepository()
        val store = AnchorStore(repo).apply { load() }
        val id = DeviceId("1A2B3C4D")
        store.addAnchor(id, label = "A", position = Point2D(0.0, 0.0))

        store.removeAnchor(id)

        assertNull(store.anchor(id))
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
