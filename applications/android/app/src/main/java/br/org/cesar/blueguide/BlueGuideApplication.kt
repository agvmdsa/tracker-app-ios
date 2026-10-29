package br.org.cesar.blueguide

import android.app.Application
import android.os.StrictMode

class BlueGuideApplication : Application() {

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
