package br.org.cesar.blueguide.feature.positioning.domain

import br.org.cesar.blueguide.core.data.AnchorRepository
import br.org.cesar.blueguide.core.data.AnchorStore
import br.org.cesar.blueguide.core.model.Anchor
import br.org.cesar.blueguide.core.model.DeviceId
import br.org.cesar.blueguide.core.model.Point2D
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private class MemoryAnchors : AnchorRepository {
    var saved: List<Anchor> = emptyList()
    override suspend fun load() = saved
    override suspend fun save(anchors: List<Anchor>) {
        saved = anchors
    }
}

class AddAnchorUseCaseTest {

    private val store = AnchorStore(MemoryAnchors())
    private val addAnchor = AddAnchorUseCase(store)

    @Test
    fun `an anchor without a label gets the next free letter`() = runTest {
        val first = addAnchor(DeviceId("1A2B3C4D"), label = null, position = Point2D(0.0, 0.0))
        val second = addAnchor(DeviceId("3F8A1C04"), label = "", position = Point2D(1.0, 0.0))

        assertEquals("A", first.label)
        assertEquals("B", second.label)
    }

    @Test
    fun `a label that is given is kept`() = runTest {
        assertEquals("Porta", addAnchor(DeviceId("1A2B3C4D"), label = "Porta", position = Point2D(0.0, 0.0)).label)
    }

    @Test
    fun `adding the same id again moves it instead of duplicating it`() = runTest {
        val id = DeviceId("1A2B3C4D")

        addAnchor(id, label = "A", position = Point2D(0.0, 0.0))
        addAnchor(id, label = "A", position = Point2D(5.0, 5.0))

        assertEquals(1, store.anchors.value.size)
        assertEquals(Point2D(5.0, 5.0), store.anchor(id)?.position)
    }

    @Test
    fun `past Z the labels are numbers`() = runTest {
        repeat(26) { addAnchor(DeviceId("AAAA%04X".format(it)), label = null, position = Point2D(0.0, 0.0)) }

        assertEquals("#27", addAnchor(DeviceId("BBBB0000"), label = null, position = Point2D(0.0, 0.0)).label)
    }
}
