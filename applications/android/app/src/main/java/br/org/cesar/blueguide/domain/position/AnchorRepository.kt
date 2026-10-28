package br.org.cesar.blueguide.domain.position

/** Where [AnchorStore] persists the anchor list. Same split as [br.org.cesar.blueguide.domain.ble.LocalDeviceIdProvider]: a pure domain interface, an Android-backed implementation in `data`. */
interface AnchorRepository {
    fun load(): List<Anchor>
    fun save(anchors: List<Anchor>)
}
