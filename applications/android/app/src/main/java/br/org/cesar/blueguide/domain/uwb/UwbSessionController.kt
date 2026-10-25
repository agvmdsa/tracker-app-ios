package br.org.cesar.blueguide.domain.uwb

/**
 * Wraps one `androidx.core.uwb` ranging session — the counterpart of the iOS app's
 * `UWBManaging` (there, a `NISession`). See `AndroidUwbSessionController` for the real
 * implementation and its caveats.
 */
interface UwbSessionController {
    val isSupported: Boolean
    val distance: Float?
    val azimuth: Float?
    val elevation: Float?
    /** True once a session was ranging but the signal is temporarily blocked/lost — same
     *  "Searching for signal..." case the iOS app's `isSignalLost` covers. */
    val isSignalLost: Boolean
    val errorMessage: String?

    /** Called when the peer ended its session, so the owner can look for it again. */
    var onPeerEnded: (() -> Unit)?
    /** Called whenever [distance]/[azimuth]/[elevation]/[isSignalLost] change, so the owner
     *  doesn't have to poll — the counterpart of the iOS app's `apply(distance:direction:)`
     *  being driven by `NISessionDelegate` callbacks. */
    var onRangingUpdate: (() -> Unit)?
    var onLog: ((String) -> Unit)?

    /** Opens a session scope for [role] and returns this device's payload for the peer. */
    suspend fun prepareSession(role: UwbRole): ByteArray

    /** Starts ranging once the peer's payload has arrived. */
    suspend fun startSession(peerPayload: ByteArray)

    fun stop()
}
