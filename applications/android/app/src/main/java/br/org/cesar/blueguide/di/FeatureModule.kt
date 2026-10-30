package br.org.cesar.blueguide.di

import android.content.Context
import br.org.cesar.blueguide.feature.ar.data.AndroidArPositionTrackerFactory
import br.org.cesar.blueguide.feature.ar.domain.ArPositionTrackerFactory
import br.org.cesar.blueguide.feature.find.data.AndroidUwbSessionControllerFactory
import br.org.cesar.blueguide.feature.find.data.BleUwbHandshakeChannel
import br.org.cesar.blueguide.feature.find.domain.HandshakeChannel
import br.org.cesar.blueguide.feature.find.domain.UwbSessionControllerFactory
import br.org.cesar.blueguide.feature.fingerprint.data.CsvFingerprintRecordingStore
import br.org.cesar.blueguide.feature.fingerprint.domain.FingerprintRecordingStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File

/** What only one feature uses. */
@Module
@InstallIn(SingletonComponent::class)
abstract class FeatureModule {

    @Binds
    abstract fun handshakeChannel(impl: BleUwbHandshakeChannel): HandshakeChannel

    @Binds
    abstract fun uwbSessionControllerFactory(impl: AndroidUwbSessionControllerFactory): UwbSessionControllerFactory

    @Binds
    abstract fun arPositionTrackerFactory(impl: AndroidArPositionTrackerFactory): ArPositionTrackerFactory

    @Binds
    abstract fun fingerprintRecordingStore(impl: CsvFingerprintRecordingStore): FingerprintRecordingStore

    companion object {
        @Provides
        @RecordingsDirectory
        fun recordingsDirectory(@ApplicationContext context: Context): File = File(context.filesDir, "Fingerprints")
    }
}
