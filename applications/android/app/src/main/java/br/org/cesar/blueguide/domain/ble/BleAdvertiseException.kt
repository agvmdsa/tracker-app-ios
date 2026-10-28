package br.org.cesar.blueguide.domain.ble

sealed class BleAdvertiseException(message: String) : Exception(message) {

    class PermissionDenied : BleAdvertiseException("BLUETOOTH_ADVERTISE permission not granted")

    class BluetoothUnavailable : BleAdvertiseException("Bluetooth is off or unavailable")

    /** Not every chipset can act as a peripheral, so broadcast mode is not universal. */
    class AdvertisingUnsupported : BleAdvertiseException("This device cannot advertise")

    /** [errorCode] is one of the `AdvertiseCallback.ADVERTISE_FAILED_*` constants. */
    class AdvertiseFailed(val errorCode: Int) :
        BleAdvertiseException("BLE advertise failed (code $errorCode)")
}
