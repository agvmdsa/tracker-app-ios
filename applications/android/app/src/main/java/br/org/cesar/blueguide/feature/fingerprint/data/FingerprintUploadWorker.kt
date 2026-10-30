package br.org.cesar.blueguide.feature.fingerprint.data

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File

/** One recording's upload, run by WorkManager when there is a network: [RecordingUpload] does
 *  the work, this tells WorkManager whether to try again. */
@HiltWorker
class FingerprintUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val upload: RecordingUpload
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val csv = inputData.getString(KEY_CSV)?.let(::File) ?: return failed("no recording given")
        return when (val outcome = upload.upload(csv)) {
            is RecordingUpload.Outcome.Uploaded -> Result.success(workDataOf(KEY_ROWS to outcome.rows))
            is RecordingUpload.Outcome.TryAgain -> {
                Log.w(TAG, "${csv.name}: ${outcome.sent} rows in, trying again later: ${outcome.reason}")
                // runAttemptCount counts the attempts before this one.
                if (runAttemptCount + 1 < MAX_ATTEMPTS) Result.retry() else failed(outcome.reason)
            }
            is RecordingUpload.Outcome.Failed -> failed(outcome.reason)
        }
    }

    private fun failed(reason: String): Result {
        Log.w(TAG, "upload failed: $reason")
        return Result.failure(workDataOf(KEY_REASON to reason))
    }

    companion object {
        const val KEY_CSV = "csv"
        const val KEY_ROWS = "rows"
        const val KEY_REASON = "reason"

        /** With the backoff from 30 s doubling each time, about 8 hours of trying. Time without a
         *  network doesn't count: the work only runs with one. */
        const val MAX_ATTEMPTS = 10

        private const val TAG = "FingerprintUpload"
    }
}
