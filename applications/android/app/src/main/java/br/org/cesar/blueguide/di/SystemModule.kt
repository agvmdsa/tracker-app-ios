package br.org.cesar.blueguide.di

import android.os.Build
import android.os.SystemClock
import br.org.cesar.blueguide.core.system.DeviceInfo
import br.org.cesar.blueguide.core.system.MonotonicClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
object SystemModule {

    @Provides
    fun monotonicClock(): MonotonicClock = MonotonicClock { SystemClock.elapsedRealtime() }

    @Provides
    fun deviceInfo(): DeviceInfo = DeviceInfo(model = Build.MODEL, systemVersion = Build.VERSION.RELEASE)

    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
