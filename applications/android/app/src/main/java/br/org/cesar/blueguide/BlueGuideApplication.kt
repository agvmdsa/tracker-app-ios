package br.org.cesar.blueguide

import android.app.Application
import android.os.StrictMode
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class BlueGuideApplication :
    Application(),
    Configuration.Provider {

    /** Builds the workers (`@HiltWorker`) with their dependencies: the fingerprint upload. */
    @Inject lateinit var workerFactory: HiltWorkerFactory

    /** WorkManager starts with this when first used, rather than on its own at launch (see the
     *  manifest), so its workers get [workerFactory]. */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) enableStrictMode()
    }

    /**
     * Logs (tag `StrictMode`), rather than crashes on, disk and network work on the main thread
     * and leaked resources: the existing code still has some, and the list it produces is what
     * the next refactoring phases work through. Debug builds only.
     */
    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .penaltyLog()
                .build()
        )
    }
}
