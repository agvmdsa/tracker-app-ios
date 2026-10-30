package br.org.cesar.blueguide.di

import br.org.cesar.blueguide.core.positioning.Multilateration
import br.org.cesar.blueguide.core.positioning.PathLossPositioningEngine
import br.org.cesar.blueguide.core.positioning.PositionEstimator
import br.org.cesar.blueguide.core.positioning.PositioningEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** How the app places the phone: the two lines to change to try another way. */
@Module
@InstallIn(SingletonComponent::class)
object PositioningModule {

    /** The whole engine, from the advertisements to the position shown. Not a singleton: each
     *  screen that observes gets its own, holding its own readings. */
    @Provides
    fun positioningEngine(estimator: PositionEstimator): PositioningEngine = PathLossPositioningEngine(estimator)

    /** Only the last step of the app's engine: the position from the ranges — where a model
     *  that corrects the multilateration's error goes, say. */
    @Provides
    fun positionEstimator(): PositionEstimator = Multilateration
}
