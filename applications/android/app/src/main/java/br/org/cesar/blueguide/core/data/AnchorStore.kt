package br.org.cesar.blueguide.core.data

import br.org.cesar.blueguide.core.model.Anchor
import br.org.cesar.blueguide.core.model.DeviceId
import kotlinx.coroutines.flow.StateFlow

/**
 * The anchors placed by hand on the floor, kept across launches via [AnchorRepository] —
 * the counterpart of the iOS app's `AnchorStore` (`UserDefaults`-backed there, same shape
 * here). One for the whole app: every screen observes [anchors], and every change goes
 * through [update]. Any anchor count works, and placement is explicit (see
 * `AnchorEditorDialog`; the labels it leaves blank come from `AddAnchorUseCase`).
 */
class AnchorStore(repository: AnchorRepository) {
    private val persisted = PersistedValue(emptyList(), repository::load, repository::save)

    /** Empty until [load] has read the disk. */
    val anchors: StateFlow<List<Anchor>> = persisted.value

    suspend fun load() = persisted.load()

    /** Changes the list in one step, from the latest one, and persists it. */
    suspend fun update(transform: (List<Anchor>) -> List<Anchor>) = persisted.update(transform)

    suspend fun remove(id: DeviceId) {
        update { anchors -> anchors.filterNot { it.id == id } }
    }

    fun anchor(id: DeviceId): Anchor? = anchors.value.firstOrNull { it.id == id }
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
