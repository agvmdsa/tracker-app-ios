package br.org.cesar.blueguide.di

import javax.inject.Qualifier

/** `Dispatchers.IO`, injected so tests can run the work on their own dispatcher. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** Where the fingerprint recordings (CSV + JSON) are written: the app's private files. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RecordingsDirectory
