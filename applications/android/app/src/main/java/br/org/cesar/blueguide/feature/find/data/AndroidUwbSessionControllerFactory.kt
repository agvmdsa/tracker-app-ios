package br.org.cesar.blueguide.feature.find.data

import android.content.Context
import br.org.cesar.blueguide.feature.find.domain.UwbSessionController
import br.org.cesar.blueguide.feature.find.domain.UwbSessionControllerFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

class AndroidUwbSessionControllerFactory @Inject constructor(
    @ApplicationContext private val context: Context
) : UwbSessionControllerFactory {
    override fun create(scope: CoroutineScope): UwbSessionController = AndroidUwbSessionController(context, scope)
}
