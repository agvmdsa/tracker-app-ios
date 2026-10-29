package br.org.cesar.blueguide.domain.position

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Which files make up a recording, for exporting it and telling a cut one apart. */
class FingerprintRecordingFilesTest {

    private val directory: File = Files.createTempDirectory("fingerprints").toFile()

    private fun file(name: String): File = File(directory, name).apply { writeText("x") }

    @Test
    fun `a finished recording is its CSV and its JSON`() {
        val csv = file("20261029-100000_S1_P01.csv")
        val json = file("20261029-100000_S1_P01.json")

        assertTrue(FingerprintRecorder.isComplete(csv))
        assertEquals(listOf(csv, json), FingerprintRecorder.filesOf(csv))
    }

    /** The app was killed before the time was up: only the CSV exists, and it is left out of the
     *  dataset, as in the iOS app. It is still exported, so nothing is lost by accident. */
    @Test
    fun `a CSV without its JSON is an incomplete recording`() {
        val csv = file("20261029-100500_S1_P02.csv")

        assertFalse(FingerprintRecorder.isComplete(csv))
        assertEquals(listOf(csv), FingerprintRecorder.filesOf(csv))
    }

    @Test
    fun `exporting all is every recording's files, oldest first`() {
        val newer = file("20261029-101000_S1_P03.csv")
        val newerJson = file("20261029-101000_S1_P03.json")
        val older = file("20261029-100000_S1_P01.csv")

        assertEquals(listOf(older, newer, newerJson), FingerprintRecorder.filesOf(listOf(newer, older)))
    }
}
