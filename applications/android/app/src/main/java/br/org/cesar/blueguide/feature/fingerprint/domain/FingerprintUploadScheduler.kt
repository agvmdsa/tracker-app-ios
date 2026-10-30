package br.org.cesar.blueguide.feature.fingerprint.domain

import kotlinx.coroutines.flow.Flow

/** How a recording's upload to the API is going. */
sealed interface UploadStatus {
    /** Waiting for a network or for its next try, or sending. [attempt] counts from 1. */
    data class Pending(val attempt: Int) : UploadStatus

    /** Every row is in the API. */
    data class Uploaded(val rows: Int) : UploadStatus

    /** It won't go through on its own; [reason] says why. */
    data class Failed(val reason: String) : UploadStatus
}

/**
 * Sends finished recordings to the API's `POST /fingerprints` in the background, from their
 * files, so an upload outlives the screen and the app being closed, and waits for a network.
 * The CSV stays on the phone either way.
 */
interface FingerprintUploadScheduler {
    /** Sends [recording] as soon as there is a network, trying again if it fails. Rows the API
     *  already accepted are not sent again. */
    fun schedule(recording: StoredRecording)

    /** Stops sending [recording]: it is being deleted. */
    fun cancel(recording: StoredRecording)

    /** Each recording's upload by [StoredRecording.name]. One never scheduled, or whose outcome
     *  is long past, isn't there. */
    val statuses: Flow<Map<String, UploadStatus>>
}
