package br.org.cesar.blueguide.di

import javax.inject.Qualifier

/** Lives as long as the app (`SupervisorJob` + `Dispatchers.Default`): for work shared by every
 *  screen, like the one BLE scan. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/** `Dispatchers.IO`, injected so tests can run the work on their own dispatcher. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** Where the fingerprint recordings (CSV + JSON) are written: the app's private files. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RecordingsDirectory

/** Where the uploads note how many of each recording's rows the API has accepted. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UploadProgressDirectory
