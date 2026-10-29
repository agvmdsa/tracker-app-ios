package br.org.cesar.blueguide.feature.find.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UwbTrackerState(
    val isRunning: Boolean = false,
    val handshakeState: HandshakeState = HandshakeState.Idle,
    val rangingState: RangingState = RangingState.Idle,
    val distance: Float? = null,
    val azimuth: Float? = null,
    val elevation: Float? = null,
    val errorMessage: String? = null,
    /** Diagnostic messages (capabilities, role decisions, handshake progress), newest last. */
    val logs: List<String> = emptyList()
)

/**
 * Glues a [HandshakeChannel] to a [UwbSessionController]: finds a nearby phone running this
 * app, decides the UWB role by comparing ids, swaps session payloads with it over the
 * handshake, then ranges it. When the peer ends its session the handshake starts over, so
 * the pairing recovers on its own — the counterpart of the iOS app's `ProximityTracker`.
 */
class UwbProximityTracker(
    private val uwb: UwbSessionController,
    private val handshake: HandshakeChannel,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(UwbTrackerState())
    val state: StateFlow<UwbTrackerState> = _state.asStateFlow()

    val isSupported: Boolean get() = uwb.isSupported

    private var hasPeer = false
    private var isWired = false

    fun start() {
        wireCallbacksIfNeeded()
        hasPeer = false
        _state.update { UwbTrackerState(isRunning = true) }
        handshake.start()
    }

    fun stop() {
        hasPeer = false
        handshake.stop()
        uwb.stop()
        _state.update { UwbTrackerState(isRunning = false) }
    }

    private fun wireCallbacksIfNeeded() {
        if (isWired) return
        isWired = true

        handshake.onStateChange = { handshakeState ->
            log("Handshake: $handshakeState")
            _state.update { it.copy(handshakeState = handshakeState) }
        }

        handshake.onPeerDiscovered = { peerId ->
            val role = if (handshake.localId <= peerId) UwbRole.CONTROLLER else UwbRole.CONTROLEE
            log("Peer found ($peerId) — taking role $role.")
            scope.launch {
                try {
                    val payload = uwb.prepareSession(role)
                    handshake.provideLocalPayload(payload)
                } catch (e: Exception) {
                    fail("Could not prepare UWB session: ${e.message}")
                }
            }
        }

        handshake.onPeerPayload = { peerPayload ->
            log("Received peer payload (${peerPayload.size} bytes).")
            hasPeer = true
            _state.update { it.copy(rangingState = RangingState.Ranging) }
            scope.launch {
                try {
                    uwb.startSession(peerPayload)
                } catch (e: Exception) {
                    fail("Could not start ranging: ${e.message}")
                }
            }
        }

        uwb.onRangingUpdate = {
            _state.update {
                it.copy(
                    distance = uwb.distance,
                    azimuth = uwb.azimuth,
                    elevation = uwb.elevation,
                    rangingState = when {
                        !hasPeer -> it.rangingState
                        uwb.isSignalLost -> RangingState.WaitingForPeer
                        else -> RangingState.Ranging
                    }
                )
            }
        }

        uwb.onPeerEnded = {
            log("Looking for the peer again.")
            hasPeer = false
            _state.update {
                it.copy(rangingState = RangingState.WaitingForPeer, distance = null, azimuth = null, elevation = null)
            }
            handshake.start()
        }

        uwb.onLog = { message -> log(message) }
    }

    private fun fail(message: String) {
        log(message)
        _state.update { it.copy(errorMessage = message, rangingState = RangingState.Failed(message)) }
    }

    private fun log(message: String) {
        _state.update { it.copy(logs = (it.logs + message).takeLast(MAX_LOGS)) }
    }

    private companion object {
        const val MAX_LOGS = 50
    }
}
