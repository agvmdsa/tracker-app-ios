package br.org.cesar.blueguide.feature.fingerprint.data

import br.org.cesar.blueguide.core.dataset.FingerprintCsv
import br.org.cesar.blueguide.core.dataset.FingerprintCsvException
import br.org.cesar.blueguide.core.network.FingerprintUploading
import br.org.cesar.blueguide.core.network.PositioningReportingError
import br.org.cesar.blueguide.di.UploadProgressDirectory
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * Sends one recording's rows to the API from its CSV, in batches: what [FingerprintUploadWorker]
 * runs. The API keeps every row it is sent, so a batch sent twice would be in the dataset twice:
 * how many rows it has accepted is written to [progressDirectory] after each batch, and a new
 * attempt carries on from there.
 */
class RecordingUpload(
    private val api: FingerprintUploading,
    private val progressDirectory: File,
    /** A long recording is tens of thousands of rows, too many for one request. */
    private val batchSize: Int
) {
    @Inject
    constructor(api: FingerprintUploading, @UploadProgressDirectory progressDirectory: File) :
        this(api, progressDirectory, DEFAULT_BATCH_SIZE)

    sealed interface Outcome {
        /** Every row is in the API: [rows] in all. */
        data class Uploaded(val rows: Int) : Outcome

        /** Worth trying again later — no network, or the API down or busy. [sent] rows are in. */
        data class TryAgain(val sent: Int, val reason: String) : Outcome

        /** Won't go through however often it is tried. */
        data class Failed(val reason: String) : Outcome
    }

    suspend fun upload(csv: File): Outcome {
        if (!csv.exists()) return Outcome.Failed("the recording was deleted")
        val progress = progressFile(csv)
        var sent = sentRows(progress)
        return try {
            csv.useLines { lines ->
                FingerprintCsv.records(lines).drop(sent).chunked(batchSize).forEach { batch ->
                    api.upload(batch)
                    sent += batch.size
                    writeSentRows(progress, sent)
                }
            }
            Outcome.Uploaded(sent)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FingerprintCsvException) {
            Outcome.Failed("the recording can't be read (${e.message})")
        } catch (e: PositioningReportingError.ServerError) {
            if (e.statusCode == 408 || e.statusCode == 429 || e.statusCode >= 500) {
                Outcome.TryAgain(sent, "the API answered ${e.statusCode}")
            } else {
                Outcome.Failed("the API refused the rows (${e.statusCode})")
            }
        } catch (e: Exception) {
            // The network, mostly: no connection, a timeout, a dropped connection.
            Outcome.TryAgain(sent, e.message ?: e.javaClass.simpleName)
        }
    }

    /** Forgets how far [csv]'s upload got: the recording is being deleted. */
    fun forget(csv: File) {
        progressFile(csv).delete()
    }

    private fun progressFile(csv: File) = File(progressDirectory, "${csv.nameWithoutExtension}.sent")

    private fun sentRows(progress: File): Int = if (progress.exists()) progress.readText().trim().toIntOrNull() ?: 0 else 0

    /** Written whole or not at all, so a crash in the middle never leaves a wrong count. */
    private fun writeSentRows(progress: File, rows: Int) {
        progressDirectory.mkdirs()
        val partial = File(progressDirectory, "${progress.name}.tmp")
        partial.writeText(rows.toString())
        if (!partial.renameTo(progress)) throw IOException("could not save the upload's progress")
    }

    companion object {
        const val DEFAULT_BATCH_SIZE = 1_000
    }
}
