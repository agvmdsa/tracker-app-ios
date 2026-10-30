package br.org.cesar.blueguide.feature.fingerprint.data

import br.org.cesar.blueguide.core.dataset.ContractSamples
import br.org.cesar.blueguide.core.dataset.FingerprintCsv
import br.org.cesar.blueguide.core.dataset.FingerprintRecord
import br.org.cesar.blueguide.core.network.FingerprintUploading
import br.org.cesar.blueguide.core.network.PositioningReportingError
import br.org.cesar.blueguide.feature.fingerprint.data.RecordingUpload.Outcome
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

private class FakeApi : FingerprintUploading {
    val accepted = mutableListOf<FingerprintRecord>()
    val requests = mutableListOf<Int>()

    /** Thrown on the request with this index in [requests] (from 0), once. */
    var failing: Pair<Int, Exception>? = null

    override suspend fun upload(records: List<FingerprintRecord>) {
        val index = requests.size
        requests += records.size
        failing?.takeIf { it.first == index }?.let {
            failing = null
            throw it.second
        }
        accepted += records
    }
}

/** A recording's upload from its file, in batches, without ever sending a row twice. */
class RecordingUploadTest {

    private val directory: File = Files.createTempDirectory("upload").toFile()
    private val progress = File(directory, "progress")
    private val api = FakeApi()
    private val upload = RecordingUpload(api, progress, batchSize = 2)

    private val rows = (1..5).map { ContractSamples.fingerprintRecord(model = "Pixel 9", withSensors = false).copy(rssi = -60 - it) }

    private fun recording(name: String = "20261030-100000_S1_P1", records: List<FingerprintRecord> = rows): File =
        File(directory, "$name.csv").apply {
            writeText((listOf(FingerprintCsv.HEADER) + records.map(FingerprintCsv::line)).joinToString("\n"))
        }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun `the rows go in batches, in the order they were recorded`() = runTest {
        assertEquals(Outcome.Uploaded(5), upload.upload(recording()))

        assertEquals(listOf(2, 2, 1), api.requests)
        assertEquals(rows, api.accepted)
    }

    /** The API keeps every row it is sent: what it accepted before the failure is not sent again. */
    @Test
    fun `a new attempt carries on from the rows the API already accepted`() = runTest {
        val csv = recording()
        api.failing = 1 to IOException("connection reset")

        val first = upload.upload(csv)
        val second = upload.upload(csv)

        assertEquals(Outcome.TryAgain(sent = 2, reason = "connection reset"), first)
        assertEquals(Outcome.Uploaded(5), second)
        assertEquals("each row once", rows, api.accepted)
    }

    @Test
    fun `a recording already uploaded sends nothing again`() = runTest {
        val csv = recording()
        upload.upload(csv)
        api.requests.clear()

        assertEquals(Outcome.Uploaded(5), upload.upload(csv))
        assertTrue(api.requests.isEmpty())
    }

    @Test
    fun `the API being down or busy is worth another try, a refusal is not`() = runTest {
        for (code in listOf(500, 503, 429, 408)) {
            api.requests.clear()
            api.failing = 0 to PositioningReportingError.ServerError(code)
            assertTrue("$code", upload.upload(recording("down_$code")) is Outcome.TryAgain)
        }
        for (code in listOf(400, 401, 422)) {
            api.requests.clear()
            api.failing = 0 to PositioningReportingError.ServerError(code)
            assertTrue("$code", upload.upload(recording("refused_$code")) is Outcome.Failed)
        }
    }

    @Test
    fun `a deleted or unreadable recording fails for good`() = runTest {
        assertTrue(upload.upload(File(directory, "gone.csv")) is Outcome.Failed)

        val broken = File(directory, "broken.csv").apply { writeText("not,a,recording\n1,2,3") }
        assertTrue(upload.upload(broken) is Outcome.Failed)
        assertTrue(api.requests.isEmpty())
    }

    /** A recording deleted and recorded again under the same name starts from nothing. */
    @Test
    fun `forgetting a recording's progress sends it whole next time`() = runTest {
        val csv = recording()
        upload.upload(csv)

        upload.forget(csv)
        api.accepted.clear()

        assertEquals(Outcome.Uploaded(5), upload.upload(csv))
        assertEquals(rows, api.accepted)
    }
}
