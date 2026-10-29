package br.org.cesar.blueguide.feature.find.data

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import br.org.cesar.blueguide.feature.find.domain.HandshakeChannel
import br.org.cesar.blueguide.feature.find.domain.HandshakeState
import br.org.cesar.blueguide.feature.find.domain.UwbHandshakeProtocol
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * [HandshakeChannel] over BLE GATT — the counterpart of the iOS app's `BLEHandshake`, adapted
 * so the local payload can arrive *after* scanning/advertising starts (see
 * [HandshakeChannel]'s doc for why). Every device runs both roles at once, same as the iOS
 * side: a `BluetoothGattServer` (this device's own payload, once ready, answers reads) and a
 * scanner that both discovers peers and decides, by comparing ids, which side connects to
 * the other (the smaller id does, exactly like `BLEHandshake.shouldInitiate` — and that
 * device also becomes the UWB controller, see [br.org.cesar.blueguide.feature.find.domain.UwbProximityTracker]).
 *
 * **Compiles, but unverified against real hardware** (no two BLE-capable devices available
 * to pair in this environment) beyond the pure id-comparison and payload logic covered by
 * `UwbHandshakeProtocolTest`/`UwbHandshakePayloadTest` — see `list.md`.
 */
class BleUwbHandshakeChannel @Inject constructor(@ApplicationContext private val context: Context) : HandshakeChannel {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val handler = Handler(Looper.getMainLooper())

    override var state: HandshakeState = HandshakeState.Idle
        private set
    override var localId: String = ""
        private set

    override var onPeerDiscovered: ((String) -> Unit)? = null
    override var onPeerPayload: ((ByteArray) -> Unit)? = null
    override var onStateChange: ((HandshakeState) -> Unit)? = null

    private var isActive = false
    private var didComplete = false
    private var localPayload: ByteArray? = null
    private var discoveredPeerId: String? = null
    private var discoveredPeerDevice: BluetoothDevice? = null
    private var isInitiator = false
    private var readRetryCount = 0

    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null
    private var gattServer: BluetoothGattServer? = null
    private var clientGatt: BluetoothGatt? = null
    private var characteristic: BluetoothGattCharacteristic? = null

    @SuppressLint("MissingPermission")
    override fun start() {
        stop()
        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN) ||
            !hasPermission(Manifest.permission.BLUETOOTH_ADVERTISE) ||
            !hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
        ) {
            setState(HandshakeState.Failed("Bluetooth permission is required to find a nearby phone."))
            return
        }

        val adapter = bluetoothManager.adapter
        if (adapter == null || !adapter.isEnabled) {
            setState(HandshakeState.Failed("Turn Bluetooth on to find a nearby phone."))
            return
        }

        localId = UwbHandshakeProtocol.randomId()
        isActive = true
        didComplete = false
        localPayload = null
        discoveredPeerId = null
        discoveredPeerDevice = null
        isInitiator = false
        readRetryCount = 0

        startGattServer()
        startAdvertising(adapter.bluetoothLeAdvertiser)
        startScanning(adapter.bluetoothLeScanner)
        setState(HandshakeState.Searching)
    }

    @SuppressLint("MissingPermission")
    override fun stop() {
        isActive = false
        scanner?.stopScan(scanCallback)
        advertiser?.stopAdvertising(advertiseCallback)
        clientGatt?.close()
        gattServer?.close()
        scanner = null
        advertiser = null
        clientGatt = null
        gattServer = null
        characteristic = null
        setState(HandshakeState.Idle)
    }

    override fun provideLocalPayload(payload: ByteArray) {
        localPayload = payload
        connectIfReady()
    }

    // MARK: - Advertising + GATT server (every device answers reads with its own payload)

    @SuppressLint("MissingPermission")
    private fun startGattServer() {
        val service = BluetoothGattService(UwbHandshakeProtocol.SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val payloadCharacteristic = BluetoothGattCharacteristic(
            UwbHandshakeProtocol.PAYLOAD_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        characteristic = payloadCharacteristic
        val server = bluetoothManager.openGattServer(context, gattServerCallback)
        server?.addService(service.apply { addCharacteristic(payloadCharacteristic) })
        gattServer = server
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertising(leAdvertiser: BluetoothLeAdvertiser?) {
        if (leAdvertiser == null) {
            setState(HandshakeState.Failed("This device cannot advertise over Bluetooth LE."))
            return
        }
        advertiser = leAdvertiser
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true) // unlike BlueGuideProtocol's beacon advertisement: a peer must connect to read/write.
            .build()
        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(UwbHandshakeProtocol.SERVICE_UUID))
            .addManufacturerData(UwbHandshakeProtocol.MANUFACTURER_ID, UwbHandshakeProtocol.idToBytes(localId))
            .build()
        leAdvertiser.startAdvertising(settings, data, advertiseCallback)
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
            setState(HandshakeState.Failed("Could not advertise for UWB handshake (code $errorCode)."))
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        @SuppressLint("MissingPermission")
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            val value = localPayload ?: ByteArray(0)
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
            }
            complete(value)
        }
    }

    // MARK: - Scanning (discovers the peer's id, decides the role, and — if initiator — connects)

    @SuppressLint("MissingPermission")
    private fun startScanning(leScanner: BluetoothLeScanner?) {
        if (leScanner == null) {
            setState(HandshakeState.Failed("This device cannot scan over Bluetooth LE."))
            return
        }
        scanner = leScanner
        val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(UwbHandshakeProtocol.SERVICE_UUID)).build())
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        leScanner.startScan(filters, settings, scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (!isActive || discoveredPeerId != null) return
            val peerId = UwbHandshakeProtocol.idFromBytes(
                result.scanRecord?.getManufacturerSpecificData(UwbHandshakeProtocol.MANUFACTURER_ID)
            ) ?: return

            discoveredPeerId = peerId
            discoveredPeerDevice = result.device
            isInitiator = localId <= peerId
            setState(HandshakeState.Exchanging)
            onPeerDiscovered?.invoke(peerId)
            connectIfReady()
        }

        override fun onScanFailed(errorCode: Int) {
            setState(HandshakeState.Failed("Scan failed (code $errorCode)."))
        }
    }

    // MARK: - GATT client (initiator only): connect, read the peer's payload, write ours

    @SuppressLint("MissingPermission")
    private fun connectIfReady() {
        if (!isInitiator || clientGatt != null) return
        val payload = localPayload ?: return
        val device = discoveredPeerDevice ?: return
        clientGatt = device.connectGatt(context, false, gattClientCallback)
    }

    private val gattClientCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> gatt.discoverServices()
                BluetoothProfile.STATE_DISCONNECTED -> {
                    gatt.close()
                    if (clientGatt === gatt) clientGatt = null
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val characteristic = gatt.getService(UwbHandshakeProtocol.SERVICE_UUID)
                ?.getCharacteristic(UwbHandshakeProtocol.PAYLOAD_CHARACTERISTIC_UUID)
            if (characteristic == null) {
                setState(HandshakeState.Failed("Peer is missing the handshake characteristic."))
                return
            }
            readRetryCount = 0
            gatt.readCharacteristic(characteristic)
        }

        @Suppress("DEPRECATION") // characteristic.value: the pre-API-33 read callback, which the
        // framework still invokes for compatibility when this (not the API-33 3-arg) overload
        // is the one implemented — needed since minSdk is 32.
        @SuppressLint("MissingPermission")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                setState(HandshakeState.Failed("Could not read the peer's payload (status $status)."))
                return
            }
            val value = characteristic.value ?: ByteArray(0)
            if (value.isEmpty() && readRetryCount < MAX_READ_RETRIES) {
                // The peer hasn't called provideLocalPayload() yet (its own UWB session is
                // still being prepared) — same "empty means not ready, retry" convention
                // HandshakeChannel's own docs call out.
                readRetryCount++
                handler.postDelayed({ gatt.readCharacteristic(characteristic) }, READ_RETRY_DELAY_MS)
                return
            }
            if (value.isEmpty()) {
                setState(HandshakeState.Failed("Peer never provided a payload."))
                return
            }

            val ownPayload = localPayload
            if (ownPayload != null) {
                writeOwnPayload(gatt, characteristic, ownPayload)
            }
            complete(value)
        }

        @Suppress("DEPRECATION")
        @SuppressLint("MissingPermission")
        private fun writeOwnPayload(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, payload: ByteArray) {
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            characteristic.value = payload
            gatt.writeCharacteristic(characteristic)
        }
    }

    private fun complete(peerPayload: ByteArray) {
        if (didComplete) return
        didComplete = true
        setState(HandshakeState.Completed)
        onPeerPayload?.invoke(peerPayload)
    }

    private fun setState(newState: HandshakeState) {
        state = newState
        onStateChange?.invoke(newState)
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val MAX_READ_RETRIES = 10
        const val READ_RETRY_DELAY_MS = 300L
    }
}
