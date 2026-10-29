package br.org.cesar.blueguide.feature.positioning.domain

import br.org.cesar.blueguide.core.data.AnchorStore
import br.org.cesar.blueguide.core.model.Anchor
import br.org.cesar.blueguide.core.model.DeviceId
import br.org.cesar.blueguide.core.model.Point2D

/**
 * Places an anchor, or moves one already placed (same [DeviceId]). Left without a label, it gets
 * the first letter A to Z no anchor uses yet — the one it replaces included — then `#n`. The
 * letter is picked inside the store's update, so two anchors added at once can't get the same.
 */
class AddAnchorUseCase(private val store: AnchorStore) {

    suspend operator fun invoke(id: DeviceId, label: String?, position: Point2D, height: Double? = null): Anchor {
        lateinit var added: Anchor
        store.update { anchors ->
            added = Anchor(
                id = id,
                label = label?.takeIf { it.isNotEmpty() } ?: nextLabel(anchors),
                position = position,
                height = height
            )
            anchors.filterNot { it.id == id } + added
        }
        return added
    }

    private fun nextLabel(anchors: List<Anchor>): String {
        val used = anchors.map { it.label }.toSet()
        return LABELS.firstOrNull { it !in used } ?: "#${anchors.size + 1}"
    }

    private companion object {
        val LABELS = ('A'..'Z').map { it.toString() }
    }
}
