package br.org.cesar.blueguide.feature.ar.data

import android.content.Context
import br.org.cesar.blueguide.feature.ar.domain.ArPositionTrackerFactory
import br.org.cesar.blueguide.feature.ar.domain.ArPositionTracking
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

class AndroidArPositionTrackerFactory @Inject constructor(
    @ApplicationContext private val context: Context
) : ArPositionTrackerFactory {
    override fun create(scope: CoroutineScope): ArPositionTracking = AndroidArPositionTracker(context, scope)
}
