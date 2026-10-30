package br.org.cesar.blueguide.feature.fingerprint.data

import br.org.cesar.blueguide.core.dataset.ContractSamples
import br.org.cesar.blueguide.core.dataset.FingerprintCsv
import br.org.cesar.blueguide.core.dataset.FingerprintLabel
import br.org.cesar.blueguide.core.dataset.FingerprintRecordingMetadata
import br.org.cesar.blueguide.core.model.Anchor
import br.org.cesar.blueguide.core.model.DeviceId
import br.org.cesar.blueguide.core.model.Point2D
import br.org.cesar.blueguide.core.positioning.PositioningSetup
import br.org.cesar.blueguide.feature.fingerprint.domain.FinishedRecording
import br.org.cesar.blueguide.feature.fingerprint.domain.StoredRecording
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.time.Instant

/** The recordings as files: what the Fingerprint tab lists, exports and deletes. */
class CsvFingerprintRecordingStoreTest {

    private val directory: File = Files.createTempDirectory("fingerprints").toFile()
    private val store = CsvFingerprintRecordingStore(directory)
    private val label = requireNotNull(FingerprintLabel.of("S1", "P3", 2.0, 4.5))
    private val startedAt = Instant.parse("2026-10-30T10:00:00Z")

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    private fun finished(setup: PositioningSetup = PositioningSetup()) = FinishedRecording(
        label = label,
        node = DeviceId("AABBCCDD"),
        model = "Pixel 9",
        systemVersion = "15",
        startedAt = startedAt,
        endedAt = startedAt.plusSeconds(60),
        startedMonotonicMs = 1_000,
        endedMonotonicMs = 61_000,
        plannedDurationSeconds = 60.0,
        recordsPerBeacon = mapOf(DeviceId("9F3C1A02") to 1),
        setup = setup
    )

    private fun file(name: String): File = File(directory, name).apply { writeText("x") }

    @Test
    fun `a recording is a CSV of its rows, and complete once its metadata is written`() {
        val writer = store.create(label, startedAt)
        val record = ContractSamples.fingerprintRecord(model = "Pixel 9", withSensors = false)
        writer.append(record)
        writer.flush()
        assertFalse("cut until it finishes", store.list().single().isComplete)

        val anchors = listOf(Anchor(DeviceId("9F3C1A02"), "A", Point2D(0.0, 0.0)))
        val recording = writer.finish(finished(PositioningSetup(anchors, centerRssi = -72.0)))

        assertEquals(listOf(recording), store.list())
        assertTrue(recording.name.endsWith("_S1_P3"))
        assertEquals(listOf(record), recording.csv.useLines { FingerprintCsv.records(it).toList() })
        val metadata = FingerprintRecordingMetadata.fromJson(requireNotNull(recording.metadata).readText())
        assertEquals("Android", metadata.platform)
        assertEquals(recording.csv.name, metadata.csvFile)
        assertEquals(PositioningSetup(anchors, centerRssi = -72.0), metadata.positioningSetup)
    }

    @Test
    fun `a discarded recording leaves nothing behind`() {
        store.create(label, startedAt).discard()

        assertTrue(store.list().isEmpty())
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    /** The app was killed before the time was up: only the CSV exists. It is still listed and
     *  exported, so nothing is lost by accident. */
    @Test
    fun `the list tells finished recordings from cut ones, newest first`() {
        val older = file("20261029-100000_S1_P01.csv")
        val olderJson = file("20261029-100000_S1_P01.json")
        val cut = file("20261029-100500_S1_P02.csv")
        file("notes.txt")

        assertEquals(listOf(StoredRecording(cut, null), StoredRecording(older, olderJson)), store.list())
    }

    @Test
    fun `exporting all is every recording's files, oldest first`() {
        val newer = StoredRecording(file("20261029-101000_S1_P03.csv"), file("20261029-101000_S1_P03.json"))
        val older = StoredRecording(file("20261029-100000_S1_P01.csv"), null)

        assertEquals(listOf(older.csv, newer.csv, newer.metadata), StoredRecording.filesOf(listOf(newer, older)))
    }

    @Test
    fun `deleting a recording deletes its CSV and its metadata`() {
        val recording = store.create(label, startedAt).finish(finished())

        assertTrue(store.delete(recording))

        assertTrue(directory.listFiles().orEmpty().isEmpty())
        assertFalse("already gone", store.delete(recording))
    }

    @Test
    fun `a recording that can't be created says so`() {
        directory.deleteRecursively()
        directory.writeText("a file where the folder should be")

        assertThrows(IOException::class.java) { store.create(label, startedAt) }
        directory.delete()
    }
}
