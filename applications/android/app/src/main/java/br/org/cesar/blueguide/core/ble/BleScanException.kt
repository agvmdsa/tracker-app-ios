package br.org.cesar.blueguide.core.ble

sealed class BleScanException(message: String) : Exception(message) {

    class PermissionDenied : BleScanException("BLUETOOTH_SCAN permission not granted")

    class BluetoothUnavailable : BleScanException("Bluetooth is off or unavailable")

    /** [errorCode] is one of the `ScanCallback.SCAN_FAILED_*` constants. */
    class ScanFailed(val errorCode: Int) : BleScanException("BLE scan failed (code $errorCode)")
}
