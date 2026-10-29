package br.org.cesar.blueguide.di

import br.org.cesar.blueguide.core.network.FingerprintUploading
import br.org.cesar.blueguide.core.network.PositioningReporting
import br.org.cesar.blueguide.core.network.http.PositioningApiClient
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {

    @Binds
    abstract fun reporting(client: PositioningApiClient): PositioningReporting

    @Binds
    abstract fun uploading(client: PositioningApiClient): FingerprintUploading

    companion object {
        /** One HTTP client (and its connection pool) for every caller. */
        @Provides
        @Singleton
        fun apiClient(): PositioningApiClient = PositioningApiClient()
    }
}
