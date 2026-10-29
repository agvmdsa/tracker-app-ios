package br.org.cesar.blueguide.core.ble.android

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import br.org.cesar.blueguide.core.ble.BleAdvertiseException
import br.org.cesar.blueguide.core.ble.BleAdvertiser
import br.org.cesar.blueguide.core.ble.BlueGuideProtocol
import br.org.cesar.blueguide.core.ble.BroadcastStatus
import br.org.cesar.blueguide.core.ble.LocalDeviceIdProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class AndroidBleAdvertiser(
    private val context: Context,
    private val localDeviceId: LocalDeviceIdProvider
) : BleAdvertiser {
    private fun hasAdvertisePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_ADVERTISE
        ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    override fun advertise(): Flow<BroadcastStatus> = callbackFlow {
        if (!hasAdvertisePermission()) {
            close(BleAdvertiseException.PermissionDenied())
            return@callbackFlow
        }

        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            close(BleAdvertiseException.BluetoothUnavailable())
            return@callbackFlow
        }

        // Null on chipsets without peripheral mode, which is a real and common case.
        val leAdvertiser = adapter.bluetoothLeAdvertiser
        if (leAdvertiser == null) {
            close(BleAdvertiseException.AdvertisingUnsupported())
            return@callbackFlow
        }

        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                trySend(BroadcastStatus.Advertising)
            }

            override fun onStartFailure(errorCode: Int) {
                close(BleAdvertiseException.AdvertiseFailed(errorCode))
            }
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            // We only want to be seen, never connected to.
            .setConnectable(false)
            .build()

        // An advertisement is 31 bytes. Flags (3), the 128-bit service UUID (18) and the
        // id's manufacturer data (8) come to 29, so the device name goes in the scan
        // response instead: a long name in the main packet would fail with
        // ADVERTISE_FAILED_DATA_TOO_LARGE.
        //
        // The UUID has to be in the main packet, not the scan response, so that an
        // observer's hardware ScanFilter can match on it.
        val advertiseData = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(BlueGuideProtocol.SERVICE_UUID))
            // The id shares the main packet for a different reason: it is what the
            // observer's list keys on, so every sighting has to carry it rather than
            // wait on a scan-response round trip that may never land.
            .addManufacturerData(
                BlueGuideProtocol.MANUFACTURER_ID,
                BlueGuideProtocol.idToBytes(localDeviceId.current())
            )
            .build()

        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()

        send(BroadcastStatus.Starting)
        leAdvertiser.startAdvertising(settings, advertiseData, scanResponse, callback)

        awaitClose { leAdvertiser.stopAdvertising(callback) }
    }
}
