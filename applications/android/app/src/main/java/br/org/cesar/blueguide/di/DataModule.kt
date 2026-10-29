package br.org.cesar.blueguide.di

import br.org.cesar.blueguide.core.data.AnchorRepository
import br.org.cesar.blueguide.core.data.AnchorStore
import br.org.cesar.blueguide.core.data.CalibrationRepository
import br.org.cesar.blueguide.core.data.prefs.SharedPrefsAnchorRepository
import br.org.cesar.blueguide.core.data.prefs.SharedPrefsCalibrationRepository
import br.org.cesar.blueguide.feature.positioning.data.SharedPrefsSavedLocationRepository
import br.org.cesar.blueguide.feature.positioning.domain.SavedLocationRepository
import br.org.cesar.blueguide.feature.positioning.domain.SavedLocationStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    abstract fun anchorRepository(impl: SharedPrefsAnchorRepository): AnchorRepository

    @Binds
    abstract fun calibrationRepository(impl: SharedPrefsCalibrationRepository): CalibrationRepository

    @Binds
    abstract fun savedLocationRepository(impl: SharedPrefsSavedLocationRepository): SavedLocationRepository

    companion object {
        /** One for the whole app: the Positioning and Fingerprint tabs used to keep a copy each,
         *  so an anchor added on one didn't show on the other until it re-read the disk. */
        @Provides
        @Singleton
        fun anchorStore(repository: AnchorRepository): AnchorStore = AnchorStore(repository)

        @Provides
        @Singleton
        fun savedLocationStore(repository: SavedLocationRepository): SavedLocationStore = SavedLocationStore(repository)
    }
}
