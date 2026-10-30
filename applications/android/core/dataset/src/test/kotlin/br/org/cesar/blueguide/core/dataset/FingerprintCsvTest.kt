package br.org.cesar.blueguide.core.dataset

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reading what other tools left: spreadsheet edits, cut files, files that aren't recordings. */
class FingerprintCsvTest {

    private val row = "4321,1767323045678,AABBCCDD,\"Pixel 8\",\"Android 15\",9F3C1A02,-67,,,,,,,,S1,P3,2.00,4.50"

    private fun read(vararg lines: String) = FingerprintCsv.records(lines.asSequence()).toList()

    @Test
    fun `a header saved with a byte order mark and a trailing empty line still read`() {
        val records = read("\uFEFF" + FingerprintCsv.HEADER, row, "")

        assertEquals(4321L, records.single().monotonicMs)
        assertNull(records.single().orientation)
    }

    @Test
    fun `a quoted field keeps its commas and doubled quotes`() {
        val records = read(FingerprintCsv.HEADER, row.replace("\"Pixel 8\"", "\"a,b \"\"c\"\"\""))

        assertEquals("a,b \"c\"", records.single().model)
    }

    @Test
    fun `a file that is not a recording is refused at its header`() {
        val error = assertThrows(FingerprintCsvException::class.java) { read("time,rssi", "1,-60") }

        assertEquals(1, error.line)
    }

    @Test
    fun `a bad row names its line`() {
        val error = assertThrows(FingerprintCsvException::class.java) {
            read(FingerprintCsv.HEADER, row, row.replace(",-67,", ",strong,"))
        }

        assertEquals(3, error.line)
        assertTrue(error.message, error.message!!.contains("rssi"))
    }

    @Test
    fun `a row cut short is refused`() {
        val error = assertThrows(FingerprintCsvException::class.java) { read(FingerprintCsv.HEADER, row.substringBeforeLast(",")) }

        assertEquals(2, error.line)
    }

    @Test
    fun `an empty file is not a recording`() {
        assertThrows(FingerprintCsvException::class.java) { read() }
    }

    /** A recording of tens of thousands of rows is read row by row: a bad row late in the file
     *  doesn't stop the ones before it from being used. */
    @Test
    fun `rows are read as they are asked for`() {
        val records = FingerprintCsv.records(sequenceOf(FingerprintCsv.HEADER, row, "not a row"))

        assertEquals(4321L, records.first().monotonicMs)
    }

    @Test
    fun `metadata without schema_version is version 1, and unknown keys are skipped`() {
        val text = Fixtures.text("fingerprint_metadata.json")
            .replace("\"schema_version\": 1,", "")
            .replace("\"platform\":", "\"added_later\": true, \"platform\":")

        assertEquals(1, FingerprintRecordingMetadata.fromJson(text).schemaVersion)
    }

    @Test
    fun `json that is not a recording's metadata is refused`() {
        assertThrows(IllegalArgumentException::class.java) { FingerprintRecordingMetadata.fromJson("{\"platform\": \"Android\"}") }
        assertThrows(IllegalArgumentException::class.java) { FingerprintRecordingMetadata.fromJson("[1, 2]") }
    }
}
