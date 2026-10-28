package br.org.cesar.blueguide.domain.ble

import br.org.cesar.blueguide.domain.entity.DeviceSighting
import kotlinx.coroutines.flow.Flow

interface BleScanner {
    /**
     * Cold flow of sightings of *other BlueGuide users*, recognised by
     * [BlueGuideProtocol.SERVICE_UUID]. Everything else nearby is filtered out.
     */
    fun scan(): Flow<DeviceSighting>
}