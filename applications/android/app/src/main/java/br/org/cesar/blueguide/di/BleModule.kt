package br.org.cesar.blueguide.di

import br.org.cesar.blueguide.core.ble.BleAdvertiser
import br.org.cesar.blueguide.core.ble.BleScanner
import br.org.cesar.blueguide.core.ble.LocalDeviceIdProvider
import br.org.cesar.blueguide.core.ble.SharedBleScanner
import br.org.cesar.blueguide.core.ble.android.AndroidBleAdvertiser
import br.org.cesar.blueguide.core.ble.android.AndroidBleScanner
import br.org.cesar.blueguide.core.ble.android.SharedPrefsLocalDeviceIdProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BleModule {

    /** Everyone gets the shared scan; only it talks to the radio's. */
    @Binds
    abstract fun scanner(impl: SharedBleScanner): BleScanner

    @Binds
    abstract fun advertiser(impl: AndroidBleAdvertiser): BleAdvertiser

    @Binds
    abstract fun localDeviceId(impl: SharedPrefsLocalDeviceIdProvider): LocalDeviceIdProvider

    companion object {
        @Provides
        @Singleton
        fun sharedScanner(radio: AndroidBleScanner, @ApplicationScope scope: CoroutineScope): SharedBleScanner =
            SharedBleScanner(radio, scope)
    }
}
