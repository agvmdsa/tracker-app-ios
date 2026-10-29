package br.org.cesar.blueguide.feature.find.domain

/** Who ends up running the UWB session for a pairing — decided by comparing [HandshakeChannel.localId]
 *  to the peer's id, the same "smaller id wins" rule the iOS app's `BLEHandshake.shouldInitiate`
 *  uses for its own, simpler (symmetric-token) collision avoidance. */
enum class UwbRole { CONTROLLER, CONTROLEE }

/**
 * Out-of-band transport that finds a nearby peer running this app and swaps one
 * [UwbHandshakePayload] with it (BLE today) — the counterpart of the iOS app's
 * `HandshakeChannel`, adapted for UWB's asymmetric controller/controlee roles.
 *
 * NearbyInteraction's discovery tokens don't depend on a role, so the iOS channel can be
 * handed a finished local token before it even starts. A UWB payload's shape depends on
 * [UwbRole], which can't be decided until a peer's id is known — so this channel starts
 * without one, and [onPeerDiscovered] fires as soon as the peer's id arrives (which happens
 * before the full exchange completes: both sides advertise and scan at once, so each sees
 * the other's id independently of who ends up GATT-central for the actual connection). The
 * owner compares ids there, asks a [br.org.cesar.blueguide.feature.find.domain.UwbSessionController]
 * to prepare a session for the decided role, and calls [provideLocalPayload] once that
 * resolves.
 */
interface HandshakeChannel {
    val state: HandshakeState

    /** This device's own randomly generated id for the current session. Valid once [start]
     *  has been called. */
    val localId: String

    var onPeerDiscovered: ((peerId: String) -> Unit)?

    /** Fired once, when the peer's full payload has arrived. */
    var onPeerPayload: ((ByteArray) -> Unit)?
    var onStateChange: ((HandshakeState) -> Unit)?

    /** Starts looking for a peer. */
    fun start()

    /** Supplies this device's payload once [UwbSessionController.prepareSession] resolves,
     *  so a peer reading it gets a real value instead of an empty one. */
    fun provideLocalPayload(payload: ByteArray)

    fun stop()
}
