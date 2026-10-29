package br.org.cesar.blueguide.core.data

import br.org.cesar.blueguide.core.model.Anchor

/** Where the anchors are kept. Main-safe: implementations do their own I/O off the main thread. */
interface AnchorRepository {
    suspend fun load(): List<Anchor>
    suspend fun save(anchors: List<Anchor>)
}
