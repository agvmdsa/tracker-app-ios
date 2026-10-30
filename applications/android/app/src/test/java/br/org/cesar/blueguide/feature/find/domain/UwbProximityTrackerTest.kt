package br.org.cesar.blueguide.feature.find.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeHandshakeChannel : HandshakeChannel {
    override var state: HandshakeState = HandshakeState.Idle
    override val localId: String = "AAAAAAAA"
    override var onPeerDiscovered: ((String) -> Unit)? = null
    override var onPeerPayload: ((ByteArray) -> Unit)? = null
    override var onStateChange: ((HandshakeState) -> Unit)? = null

    var startCount = 0
    var stopCount = 0
    var lastProvidedPayload: ByteArray? = null

    override fun start() {
        startCount++
        state = HandshakeState.Searching
        onStateChange?.invoke(state)
    }

    override fun provideLocalPayload(payload: ByteArray) {
        lastProvidedPayload = payload
    }

    override fun stop() {
        stopCount++
        state = HandshakeState.Idle
    }

    fun discoverPeer(peerId: String) = onPeerDiscovered?.invoke(peerId)
    fun deliverPeerPayload(payload: ByteArray) = onPeerPayload?.invoke(payload)
}

private class FakeUwbSessionController : UwbSessionController {
    override val isSupported = true
    override var distance: Float? = null
    override var azimuth: Float? = null
    override var elevation: Float? = null
    override var isSignalLost: Boolean = false
    override var errorMessage: String? = null
    override var onPeerEnded: (() -> Unit)? = null
    override var onRangingUpdate: (() -> Unit)? = null
    override var onLog: ((String) -> Unit)? = null

    var preparedRole: UwbRole? = null
    var startedWithPayload: ByteArray? = null
    var stopCount = 0
    var prepareError: Exception? = null
    var prepareHangs = false

    override suspend fun prepareSession(role: UwbRole): ByteArray {
        prepareError?.let { throw it }
        if (prepareHangs) awaitCancellation()
        preparedRole = role
        return byteArrayOf(0x42)
    }

    override suspend fun startSession(peerPayload: ByteArray) {
        startedWithPayload = peerPayload
    }

    override fun stop() {
        stopCount++
    }

    fun deliverPosition(distance: Float, azimuth: Float?, elevation: Float?) {
        this.distance = distance
        this.azimuth = azimuth
        this.elevation = elevation
        isSignalLost = false
        onRangingUpdate?.invoke()
    }

    fun deliverPeerEnded() = onPeerEnded?.invoke()
}

@OptIn(ExperimentalCoroutinesApi::class)
class UwbProximityTrackerTest {

    @Test
    fun `starting begins the handshake`() = runTest {
        val handshake = FakeHandshakeChannel()
        val uwb = FakeUwbSessionController()
        val sut = UwbProximityTracker(uwb, handshake, this)

        sut.start()

        assertEquals(1, handshake.startCount)
        assertTrue(sut.state.value.isRunning)
    }

    @Test
    fun `a peer with the larger id makes the local device the controller`() = runTest {
        val handshake = FakeHandshakeChannel() // localId = "AAAAAAAA"
        val uwb = FakeUwbSessionController()
        val sut = UwbProximityTracker(uwb, handshake, this)
        sut.start()

        handshake.discoverPeer("ZZZZZZZZ") // local <= peer -> local is CONTROLLER
        runCurrent()

        assertEquals(UwbRole.CONTROLLER, uwb.preparedRole)
        assertEquals(1, handshake.lastProvidedPayload?.size)
    }

    @Test
    fun `a peer with the smaller id makes the local device the controlee`() = runTest {
        val handshake = FakeHandshakeChannel() // localId = "AAAAAAAA"
        val uwb = FakeUwbSessionController()
        val sut = UwbProximityTracker(uwb, handshake, this)
        sut.start()

        handshake.discoverPeer("0000AAAA") // peer < local -> local is CONTROLEE
        runCurrent()

        assertEquals(UwbRole.CONTROLEE, uwb.preparedRole)
    }

    @Test
    fun `receiving the peer payload starts ranging`() = runTest {
        val handshake = FakeHandshakeChannel()
        val uwb = FakeUwbSessionController()
        val sut = UwbProximityTracker(uwb, handshake, this)
        sut.start()

        val peerPayload = byteArrayOf(9, 9)
        handshake.deliverPeerPayload(peerPayload)
        runCurrent()

        assertArrayEquals(peerPayload, uwb.startedWithPayload)
        assertEquals(RangingState.Ranging, sut.state.value.rangingState)
    }

    @Test
    fun `a position update is reflected in state`() = runTest {
        val handshake = FakeHandshakeChannel()
        val uwb = FakeUwbSessionController()
        val sut = UwbProximityTracker(uwb, handshake, this)
        sut.start()
        handshake.deliverPeerPayload(byteArrayOf(1))
        runCurrent()

        uwb.deliverPosition(distance = 1.5f, azimuth = 0.2f, elevation = null)

        assertEquals(1.5f, sut.state.value.distance)
        assertEquals(0.2f, sut.state.value.azimuth)
    }

    @Test
    fun `when the peer ends the session, the handshake restarts automatically`() = runTest {
        val handshake = FakeHandshakeChannel()
        val uwb = FakeUwbSessionController()
        val sut = UwbProximityTracker(uwb, handshake, this)
        sut.start()
        handshake.deliverPeerPayload(byteArrayOf(1))
        runCurrent()

        uwb.deliverPeerEnded()

        assertEquals(2, handshake.startCount) // once from sut.start(), once from the retry
        assertEquals(RangingState.WaitingForPeer, sut.state.value.rangingState)
    }

    @Test
    fun `a failure preparing the session is surfaced as an error`() = runTest {
        val handshake = FakeHandshakeChannel()
        val uwb = FakeUwbSessionController().apply { prepareError = RuntimeException("no UWB chip") }
        val sut = UwbProximityTracker(uwb, handshake, this)
        sut.start()

        handshake.discoverPeer("ZZZZZZZZ")
        runCurrent()

        assertEquals(RangingState.Failed("Could not prepare UWB session: no UWB chip"), sut.state.value.rangingState)
    }

    /** Closing the screen cancels its scope, the session being prepared with it: that is not a
     *  UWB failure to show. */
    @Test
    fun `the screen going away while a session is prepared is not an error`() = runTest {
        val uwb = FakeUwbSessionController().apply { prepareHangs = true }
        val handshake = FakeHandshakeChannel()
        val screen = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val sut = UwbProximityTracker(uwb, handshake, screen)
        sut.start()
        handshake.discoverPeer("BBBBBBBB")
        runCurrent()

        screen.cancel()
        runCurrent()

        assertNull(sut.state.value.errorMessage)
        assertTrue(sut.state.value.rangingState !is RangingState.Failed)
    }

    @Test
    fun `stop tears down both the handshake and the uwb session`() = runTest {
        val handshake = FakeHandshakeChannel()
        val uwb = FakeUwbSessionController()
        val sut = UwbProximityTracker(uwb, handshake, this)
        sut.start()

        sut.stop()

        assertEquals(1, handshake.stopCount)
        assertEquals(1, uwb.stopCount)
        assertTrue(!sut.state.value.isRunning)
    }
}
