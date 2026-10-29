package br.org.cesar.blueguide.core.data

import br.org.cesar.blueguide.core.model.Anchor
import br.org.cesar.blueguide.core.model.DeviceId
import br.org.cesar.blueguide.core.model.Point2D

/**
 * The anchors placed by hand on the floor, kept across launches via [AnchorRepository] —
 * the counterpart of the iOS app's `AnchorStore` (`UserDefaults`-backed there, same shape
 * here). Replaces the old `AnchorLayout`, which assumed exactly three anchors arranged in a
 * fixed equilateral triangle and assigned automatically by strongest signal; any anchor
 * count now works, and placement is explicit (see `AnchorEditorDialog`).
 */
class AnchorStore(private val repository: AnchorRepository) {

    private val labels = ('A'..'Z').map { it.toString() }

    /** Empty until [load] runs. Construction itself must stay cheap — callers read off disk
     *  explicitly (and off the main thread), same discipline as `LocalDeviceIdProvider`. */
    var anchors: List<Anchor> = emptyList()
        private set

    /** Reads the persisted list. Call once, off the main thread, before relying on [anchors]. */
    fun load() {
        anchors = repository.load()
    }

    fun addAnchor(id: DeviceId, label: String? = null, position: Point2D, height: Double? = null): Anchor {
        val anchor = Anchor(
            id = id,
            label = label?.takeIf { it.isNotEmpty() } ?: nextLabel(),
            position = position,
            height = height
        )
        anchors = anchors.filterNot { it.id == id } + anchor
        persist()
        return anchor
    }

    fun removeAnchor(id: DeviceId) {
        anchors = anchors.filterNot { it.id == id }
        persist()
    }

    fun anchor(id: DeviceId): Anchor? = anchors.firstOrNull { it.id == id }

    private fun nextLabel(): String {
        val used = anchors.map { it.label }.toSet()
        return labels.firstOrNull { it !in used } ?: "#${anchors.size + 1}"
    }

    private fun persist() = repository.save(anchors)
}

/**
 * Configured anchors that aren't heard right now, and heard devices without a configured
 * position — the counterpart of the iOS app's `BeaconLayoutCheck`. Either list being
 * non-empty means a fix (or a fingerprint recording) would go out with an incomplete
 * layout.
 */
data class AnchorLayoutCheck(
    val withoutPosition: List<DeviceId>,
    val notHeard: List<DeviceId>
) {
    val isComplete: Boolean get() = withoutPosition.isEmpty() && notHeard.isEmpty()

    companion object {
        fun of(anchors: List<Anchor>, heard: Collection<DeviceId>): AnchorLayoutCheck {
            val positioned = anchors.map { it.id }.toSet()
            val heardSet = heard.toSet()
            return AnchorLayoutCheck(
                withoutPosition = (heardSet - positioned).sortedBy { it.value },
                notHeard = (positioned - heardSet).sortedBy { it.value }
            )
        }
    }
}
