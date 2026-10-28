package br.org.cesar.blueguide.domain.network

import br.org.cesar.blueguide.domain.position.FingerprintRecord

/**
 * Uploads a finished recording's rows to the positioning API's `POST /fingerprints` — the
 * labelled training data, as opposed to [PositioningReporting]'s computed-estimate reports.
 * Counterpart of the iOS app's `FingerprintUploading`.
 */
interface FingerprintUploading {
    suspend fun upload(records: List<FingerprintRecord>)
}
