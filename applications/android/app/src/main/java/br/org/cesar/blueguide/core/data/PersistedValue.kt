package br.org.cesar.blueguide.core.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A value kept on disk, with the in-memory copy every screen observes: the one source of truth
 * the anchors, the calibration and the saved locations are each built on. [value] is [initial]
 * until [load] has read the disk; every [update] sees the latest value, one at a time, and is
 * written before anyone observes it.
 */
class PersistedValue<T>(
    initial: T,
    private val read: suspend () -> T,
    private val write: suspend (T) -> Unit
) {
    private val mutex = Mutex()
    private var loaded = false
    private val _value = MutableStateFlow(initial)
    val value: StateFlow<T> = _value.asStateFlow()

    /** Reads the disk the first time; after that, does nothing. */
    suspend fun load() {
        mutex.withLock { loadLocked() }
    }

    /** Applies [transform] to the latest value (loading it first if needed), writes the result
     *  and publishes it. */
    suspend fun update(transform: (T) -> T): T = mutex.withLock {
        loadLocked()
        transform(_value.value).also {
            write(it)
            _value.value = it
        }
    }

    private suspend fun loadLocked() {
        if (loaded) return
        _value.value = read()
        loaded = true
    }
}
