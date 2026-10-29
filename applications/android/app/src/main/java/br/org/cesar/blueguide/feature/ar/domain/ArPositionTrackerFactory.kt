package br.org.cesar.blueguide.feature.ar.domain

import kotlinx.coroutines.CoroutineScope

/** Makes an [ArPositionTracking] that works in [scope] and stops with it — the scope of the
 *  ViewModel that owns it. */
fun interface ArPositionTrackerFactory {
    fun create(scope: CoroutineScope): ArPositionTracking
}
