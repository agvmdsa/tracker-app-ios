package br.org.cesar.blueguide.core.ble

import br.org.cesar.blueguide.core.model.DeviceSighting
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn

/** What the shared scan is doing, so a screen can say why no sightings arrive. */
sealed interface ScanStatus {
    /** Nobody is listening, so the radio isn't scanning. */
    data object Idle : ScanStatus

    data object Scanning : ScanStatus

    /** The radio's scan stopped — [cause] is null when it just ended — and starts again in
     *  [retryInMs]: no permission yet, Bluetooth off, or a scan failure. */
    data class Interrupted(val cause: Throwable?, val retryInMs: Long) : ScanStatus
}

/**
 * One radio scan for everyone who listens: the Positioning screen and the fingerprint recorder
 * used to start one each. Starts with the first collector, stops [stopTimeoutMs] after the last
 * one leaves (so a quick mode switch doesn't restart it: Android blocks apps that start scans
 * too often), and never fails its collectors: when the radio's scan stops it starts again by
 * itself, waiting a little longer each time ([retryDelaysMs], back to the first once sightings
 * arrive again), which is also what picks it up once the permission is granted.
 */
class SharedBleScanner(
    private val radio: BleScanner,
    scope: CoroutineScope,
    private val retryDelaysMs: List<Long> = DEFAULT_RETRY_DELAYS_MS,
    stopTimeoutMs: Long = DEFAULT_STOP_TIMEOUT_MS
) : BleScanner {

    init {
        require(retryDelaysMs.isNotEmpty()) { "retryDelaysMs needs at least one delay" }
    }

    private val _status = MutableStateFlow<ScanStatus>(ScanStatus.Idle)
    val status: StateFlow<ScanStatus> = _status.asStateFlow()

    private val sightings: SharedFlow<DeviceSighting> = restartingScan()
        .onCompletion { _status.value = ScanStatus.Idle }
        .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMs))

    override fun scan(): Flow<DeviceSighting> = sightings

    private fun restartingScan(): Flow<DeviceSighting> = flow {
        var failures = 0
        while (true) {
            var cause: Throwable? = null
            _status.value = ScanStatus.Scanning
            radio.scan()
                .onEach { failures = 0 }
                .catch {
                    if (it is CancellationException) throw it
                    cause = it
                }
                .collect { emit(it) }
            val wait = retryDelaysMs[minOf(failures, retryDelaysMs.lastIndex)]
            _status.value = ScanStatus.Interrupted(cause, wait)
            failures++
            delay(wait)
        }
    }

    companion object {
        val DEFAULT_RETRY_DELAYS_MS = listOf(1_000L, 2_000L, 5_000L, 10_000L, 30_000L)
        const val DEFAULT_STOP_TIMEOUT_MS = 5_000L
    }
}
