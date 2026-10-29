package br.org.cesar.blueguide.di

import br.org.cesar.blueguide.core.ble.BleAdvertiser
import br.org.cesar.blueguide.core.ble.BleScanner
import br.org.cesar.blueguide.core.ble.LocalDeviceIdProvider
import br.org.cesar.blueguide.core.ble.android.AndroidBleAdvertiser
import br.org.cesar.blueguide.core.ble.android.AndroidBleScanner
import br.org.cesar.blueguide.core.ble.android.SharedPrefsLocalDeviceIdProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class BleModule {

    @Binds
    abstract fun scanner(impl: AndroidBleScanner): BleScanner

    @Binds
    abstract fun advertiser(impl: AndroidBleAdvertiser): BleAdvertiser

    @Binds
    abstract fun localDeviceId(impl: SharedPrefsLocalDeviceIdProvider): LocalDeviceIdProvider
}
