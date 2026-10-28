package br.org.cesar.blueguide.domain.uwb

/** Progress of the out-of-band exchange of UWB session payloads — counterpart of the iOS
 *  app's `HandshakeState` (there, NearbyInteraction discovery tokens). */
sealed interface HandshakeState {
    data object Idle : HandshakeState
    data object Searching : HandshakeState
    data object Exchanging : HandshakeState
    data object Completed : HandshakeState
    data class Failed(val message: String) : HandshakeState
}

/** What the UWB side of a tracking session is doing, derived from the handshake and the
 *  session controller rather than stored, so it can't drift out of sync with them —
 *  counterpart of the iOS app's `RangingState`. */
sealed interface RangingState {
    data object Idle : RangingState
    data object WaitingForPeer : RangingState
    data object Ranging : RangingState
    data class Failed(val message: String) : RangingState
}
