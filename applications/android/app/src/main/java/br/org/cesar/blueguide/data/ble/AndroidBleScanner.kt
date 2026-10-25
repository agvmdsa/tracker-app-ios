package br.org.cesar.blueguide.data.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.os.SystemClock
import androidx.core.content.ContextCompat
import br.org.cesar.blueguide.domain.ble.BleScanException
import br.org.cesar.blueguide.domain.ble.BleScanner
import br.org.cesar.blueguide.domain.ble.BlueGuideProtocol
import br.org.cesar.blueguide.domain.entity.BleDevice
import br.org.cesar.blueguide.domain.entity.DeviceSighting
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class AndroidBleScanner(private val context: Context) : BleScanner {
    private fun hasScanPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    override fun scan(): Flow<DeviceSighting> = callbackFlow {
        if (!hasScanPermission()) {
            close(BleScanException.PermissionDenied())
            return@callbackFlow
        }

        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        val leScanner = adapter?.bluetoothLeScanner
        if (adapter == null || !adapter.isEnabled || leScanner == null) {
            close(BleScanException.BluetoothUnavailable())
            return@callbackFlow
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                trySend(result.toDeviceSighting())
            }

            override fun onScanFailed(errorCode: Int) {
                close(BleScanException.ScanFailed(errorCode))
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        // Only surface other people running BlueGuide. Matching on the service UUID is
        // done by the Bluetooth controller rather than in our callback, so unrelated
        // radios never wake the app up at all.
        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(BlueGuideProtocol.SERVICE_UUID))
                .build()
        )

        leScanner.startScan(filters, settings, callback)

        awaitClose { leScanner.stopScan(callback) }
    }
}

/**
 * [ScanRecord.getDeviceName] is the name carried in the advertisement, which needs
 * only BLUETOOTH_SCAN. Reading `device.name` instead would require BLUETOOTH_CONNECT.
 */
private fun ScanResult.toDeviceSighting(): DeviceSighting = DeviceSighting(
    device = BleDevice(
        address = this.device.address,
        name = scanRecord?.deviceName,
        // The address above is a rotating private address, so it is the id that decides
        // which row this sighting belongs to.
        id = BlueGuideProtocol.idFromBytes(
            scanRecord?.getManufacturerSpecificData(BlueGuideProtocol.MANUFACTURER_ID)
        )
    ),
    rssi = rssi,
    lastSeenAtMillis = SystemClock.elapsedRealtime()
)
