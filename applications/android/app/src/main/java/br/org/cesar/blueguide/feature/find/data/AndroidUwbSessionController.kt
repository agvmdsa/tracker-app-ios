package br.org.cesar.blueguide.feature.find.data

import android.content.Context
import androidx.core.uwb.RangingParameters
import androidx.core.uwb.RangingResult
import androidx.core.uwb.UwbAddress
import androidx.core.uwb.UwbClientSessionScope
import androidx.core.uwb.UwbComplexChannel
import androidx.core.uwb.UwbControleeSessionScope
import androidx.core.uwb.UwbControllerSessionScope
import androidx.core.uwb.UwbDevice
import androidx.core.uwb.UwbManager
import br.org.cesar.blueguide.feature.find.domain.UwbHandshakePayload
import br.org.cesar.blueguide.feature.find.domain.UwbRole
import br.org.cesar.blueguide.feature.find.domain.UwbSessionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.security.SecureRandom

/**
 * [UwbSessionController] over `androidx.core.uwb` (Jetpack UWB) 1.0.0 — the counterpart of
 * the iOS app's `UWBManager` (there, `NearbyInteraction`). The real API was inspected
 * directly from the downloaded `uwb-1.0.0.aar` (`javap` on its classes), the same way this
 * session's ESP32 work inspected `espMqttClient`'s real headers instead of guessing.
 *
 * **Unverified beyond compiling and the pure logic
 * (`UwbHandshakePayload`/`UwbProximityTracker`) being tested**: ranging two real phones
 * needs UWB-chip-equipped hardware (Pixel 6 Pro+, Galaxy S21 Ultra+ or equivalent) this
 * environment has no way to test against — see `list.md`.
 */
class AndroidUwbSessionController(
    context: Context,
    private val scope: CoroutineScope
) : UwbSessionController {

    private val manager = UwbManager.createInstance(context)
    private val random = SecureRandom()

    private var sessionScope: UwbClientSessionScope? = null
    private var rangingJob: Job? = null
    private var pendingControllerSessionId: Int? = null
    private var pendingControllerSessionKeyInfo: ByteArray? = null

    override var distance: Float? = null
        private set
    override var azimuth: Float? = null
        private set
    override var elevation: Float? = null
        private set
    override var isSignalLost: Boolean = false
        private set
    override var errorMessage: String? = null
        private set

    override var onPeerEnded: (() -> Unit)? = null
    override var onRangingUpdate: (() -> Unit)? = null
    override var onLog: ((String) -> Unit)? = null

    override val isSupported: Boolean
        // androidx.core.uwb has no synchronous capability check — UwbManager.isAvailable()
        // is a suspend call, and this property is read synchronously by UwbProximityTracker.
        // Reporting true here and letting prepareSession() surface a real failure
        // (UwbHardwareNotAvailableException, caught as a generic Exception by the caller)
        // is the tradeoff; restructuring isSupported into a suspend call would ripple into
        // every caller for a check that would otherwise block the UI briefly on every visit
        // to the tracking screen.
        get() = true

    override suspend fun prepareSession(role: UwbRole): ByteArray = when (role) {
        UwbRole.CONTROLLER -> {
            val controller = manager.controllerSessionScope()
            sessionScope = controller
            val sessionId = random.nextInt()
            val sessionKeyInfo = ByteArray(SESSION_KEY_INFO_SIZE).also(random::nextBytes)
            pendingControllerSessionId = sessionId
            pendingControllerSessionKeyInfo = sessionKeyInfo
            UwbHandshakePayload.encode(
                address = controller.localAddress.address,
                controllerInfo = UwbHandshakePayload.ControllerInfo(
                    channel = controller.uwbComplexChannel.channel,
                    preambleIndex = controller.uwbComplexChannel.preambleIndex,
                    sessionId = sessionId,
                    sessionKeyInfo = sessionKeyInfo
                )
            )
        }
        UwbRole.CONTROLEE -> {
            val controlee = manager.controleeSessionScope()
            sessionScope = controlee
            UwbHandshakePayload.encode(address = controlee.localAddress.address, controllerInfo = null)
        }
    }

    override suspend fun startSession(peerPayload: ByteArray) {
        val decoded = UwbHandshakePayload.decode(peerPayload)
        if (decoded == null) {
            errorMessage = "Invalid peer payload"
            return
        }
        val scopeToUse = sessionScope
        if (scopeToUse == null) {
            errorMessage = "Session was not prepared before startSession()"
            return
        }

        val peerDevice = UwbDevice.createForAddress(decoded.address)
        val parameters = when (scopeToUse) {
            is UwbControllerSessionScope -> RangingParameters(
                uwbConfigType = RangingParameters.CONFIG_UNICAST_DS_TWR,
                sessionId = pendingControllerSessionId ?: 0,
                subSessionId = 0,
                sessionKeyInfo = pendingControllerSessionKeyInfo ?: ByteArray(0),
                subSessionKeyInfo = null,
                complexChannel = scopeToUse.uwbComplexChannel,
                peerDevices = listOf(peerDevice),
                updateRateType = RangingParameters.RANGING_UPDATE_RATE_FREQUENT
            )
            is UwbControleeSessionScope -> {
                val info = decoded.controllerInfo
                if (info == null) {
                    errorMessage = "Controlee needs the controller's session info, missing from the peer's payload"
                    return
                }
                RangingParameters(
                    uwbConfigType = RangingParameters.CONFIG_UNICAST_DS_TWR,
                    sessionId = info.sessionId,
                    subSessionId = 0,
                    sessionKeyInfo = info.sessionKeyInfo,
                    subSessionKeyInfo = null,
                    complexChannel = UwbComplexChannel(info.channel, info.preambleIndex),
                    peerDevices = listOf(peerDevice),
                    updateRateType = RangingParameters.RANGING_UPDATE_RATE_FREQUENT
                )
            }
            else -> {
                errorMessage = "Unknown UWB session scope type: ${scopeToUse::class.simpleName}"
                return
            }
        }

        rangingJob?.cancel()
        rangingJob = scope.launch {
            try {
                scopeToUse.prepareSession(parameters).collect { result -> handle(result) }
            } catch (e: Exception) {
                errorMessage = e.message
                onLog?.invoke("Ranging stopped: ${e.message}")
            }
        }
    }

    private fun handle(result: RangingResult) {
        when (result) {
            is RangingResult.RangingResultPosition -> {
                val position = result.position
                distance = position.distance?.value
                azimuth = position.azimuth?.value
                elevation = position.elevation?.value
                isSignalLost = position.distance == null
                onRangingUpdate?.invoke()
            }
            is RangingResult.RangingResultPeerDisconnected -> {
                distance = null
                azimuth = null
                elevation = null
                isSignalLost = true
                onRangingUpdate?.invoke()
                onPeerEnded?.invoke()
            }
            is RangingResult.RangingResultFailure -> {
                errorMessage = "Ranging failed (reason ${result.reason})"
                onLog?.invoke(errorMessage ?: "")
            }
            is RangingResult.RangingResultInitialized -> onLog?.invoke("Session initialized.")
        }
    }

    override fun stop() {
        rangingJob?.cancel()
        rangingJob = null
        sessionScope = null
        pendingControllerSessionId = null
        pendingControllerSessionKeyInfo = null
        distance = null
        azimuth = null
        elevation = null
        isSignalLost = false
        errorMessage = null
    }

    private companion object {
        const val SESSION_KEY_INFO_SIZE = 8
    }
}
