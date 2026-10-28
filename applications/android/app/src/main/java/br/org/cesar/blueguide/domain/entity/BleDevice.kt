package br.org.cesar.blueguide.domain.entity

data class BleDevice(
    val address: String,
    val name: String?,
    /** Null for a peer whose build predates the id; see [BleDevice.key]. */
    val id: DeviceId? = null
) {
    /**
     * What the device list keys on.
     *
     * [id] when the peer advertises one, so a phone stays one row while the BLE address
     * it advertises from rotates underneath. [address] otherwise, which still duplicates
     * on rotation but is the best an id-less peer can offer.
     */
    val key: String
        get() = id?.value ?: address
}
