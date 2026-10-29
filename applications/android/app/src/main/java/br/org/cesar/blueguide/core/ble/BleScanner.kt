package br.org.cesar.blueguide.core.ble

import br.org.cesar.blueguide.core.model.DeviceSighting
import kotlinx.coroutines.flow.Flow

interface BleScanner {
    /**
     * Cold flow of sightings of *other BlueGuide users*, recognised by
     * [BlueGuideProtocol.SERVICE_UUID]. Everything else nearby is filtered out.
     */
    fun scan(): Flow<DeviceSighting>
}
