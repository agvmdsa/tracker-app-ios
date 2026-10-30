package br.org.cesar.blueguide.feature.fingerprint.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import br.org.cesar.blueguide.feature.fingerprint.domain.FingerprintUploadScheduler
import br.org.cesar.blueguide.feature.fingerprint.domain.StoredRecording
import br.org.cesar.blueguide.feature.fingerprint.domain.UploadStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** One unique [FingerprintUploadWorker] per recording, named and tagged after it. */
class WorkManagerFingerprintUploadScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val upload: RecordingUpload
) : FingerprintUploadScheduler {

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    override fun schedule(recording: StoredRecording) {
        val request = OneTimeWorkRequestBuilder<FingerprintUploadWorker>()
            .setInputData(workDataOf(FingerprintUploadWorker.KEY_CSV to recording.csv.path))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .addTag(TAG)
            .addTag(RECORDING_TAG_PREFIX + recording.name)
            .build()
        // KEEP: scheduling one already on its way leaves it be.
        workManager.enqueueUniqueWork(RECORDING_TAG_PREFIX + recording.name, ExistingWorkPolicy.KEEP, request)
    }

    override fun cancel(recording: StoredRecording) {
        workManager.cancelUniqueWork(RECORDING_TAG_PREFIX + recording.name)
        upload.forget(recording.csv)
    }

    override val statuses: Flow<Map<String, UploadStatus>>
        get() = workManager.getWorkInfosByTagFlow(TAG).map { infos ->
            infos
                .mapNotNull { info -> recordingName(info)?.let { name -> status(info)?.let { name to it } } }
                // A recording sent again keeps its finished work for a while: the latest wins,
                // and a pending one is the latest.
                .sortedBy { (_, status) -> status is UploadStatus.Pending }
                .toMap()
        }

    private fun recordingName(info: WorkInfo): String? =
        info.tags.firstOrNull { it.startsWith(RECORDING_TAG_PREFIX) }?.removePrefix(RECORDING_TAG_PREFIX)

    private fun status(info: WorkInfo): UploadStatus? = when (info.state) {
        WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED -> UploadStatus.Pending(info.runAttemptCount + 1)
        WorkInfo.State.SUCCEEDED -> UploadStatus.Uploaded(info.outputData.getInt(FingerprintUploadWorker.KEY_ROWS, 0))
        WorkInfo.State.FAILED -> UploadStatus.Failed(info.outputData.getString(FingerprintUploadWorker.KEY_REASON) ?: "unknown")
        WorkInfo.State.CANCELLED -> null
    }

    private companion object {
        const val TAG = "fingerprint-upload"
        const val RECORDING_TAG_PREFIX = "fingerprint-upload:"
        const val BACKOFF_SECONDS = 30L
    }
}
