package br.org.cesar.blueguide.di

import br.org.cesar.blueguide.core.positioning.Multilateration
import br.org.cesar.blueguide.core.positioning.PositionEstimator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object PositioningModule {

    /** How the app places the phone from the ranges. The one line to change to try a trained
     *  model (a correction of the multilateration's error, say) in place of the solver. */
    @Provides
    fun positionEstimator(): PositionEstimator = Multilateration
}
