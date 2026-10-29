package br.org.cesar.blueguide.feature.find.domain

import kotlinx.coroutines.CoroutineScope

/** Makes a [UwbSessionController] that works in [scope] and stops with it — the scope of the
 *  ViewModel that owns it. */
fun interface UwbSessionControllerFactory {
    fun create(scope: CoroutineScope): UwbSessionController
}
