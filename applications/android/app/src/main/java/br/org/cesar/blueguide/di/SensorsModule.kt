package br.org.cesar.blueguide.di

import br.org.cesar.blueguide.core.sensors.AltitudeReading
import br.org.cesar.blueguide.core.sensors.DeviceMotionReading
import br.org.cesar.blueguide.core.sensors.MotionActivityReading
import br.org.cesar.blueguide.core.sensors.android.AndroidAltitudeReader
import br.org.cesar.blueguide.core.sensors.android.AndroidMotionActivityReader
import br.org.cesar.blueguide.core.sensors.android.AndroidOrientationReader
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Not singletons: each reader holds its own sensor listener, started and stopped by whoever
 *  asked for it (the fingerprint recorder). */
@Module
@InstallIn(SingletonComponent::class)
abstract class SensorsModule {

    @Binds
    abstract fun deviceMotion(impl: AndroidOrientationReader): DeviceMotionReading

    @Binds
    abstract fun altitude(impl: AndroidAltitudeReader): AltitudeReading

    @Binds
    abstract fun motionActivity(impl: AndroidMotionActivityReader): MotionActivityReading
}
