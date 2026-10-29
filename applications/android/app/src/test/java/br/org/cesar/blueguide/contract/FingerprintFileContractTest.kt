package br.org.cesar.blueguide.contract

import br.org.cesar.blueguide.contract.ContractSamples.QUOTED_MODEL
import br.org.cesar.blueguide.contract.ContractSamples.fingerprintRecord
import br.org.cesar.blueguide.core.dataset.FingerprintRecordFormat
import br.org.cesar.blueguide.core.dataset.FingerprintRecordingMetadata
import br.org.cesar.blueguide.core.dataset.FingerprintRecordingMetadata.BeaconSnapshot
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The CSV and the metadata JSON a recording writes are a format shared with the iOS app (the
 * reference: its `FingerprintRecord` and `FingerprintRecordingMetadata.CodingKeys`), the ESP32
 * collector's `R` records and the training pipeline. The expected files are the format itself:
 * when one of these fails, the change broke the format, not the fixture.
 */
class FingerprintFileContractTest {

    private val csvLines = Fixtures.text("fingerprint.csv").lines()

    @Test
    fun `the csv header is the shared column list`() {
        assertEquals(csvLines[0], FingerprintRecordFormat.CSV_HEADER)
    }

    @Test
    fun `a row with every sensor, with free text quoted`() {
        assertEquals(csvLines[1], fingerprintRecord(model = QUOTED_MODEL, withSensors = true).csvLine)
    }

    @Test
    fun `a row without sensors leaves their columns empty`() {
        assertEquals(csvLines[2], fingerprintRecord(model = "Pixel 8", withSensors = false).csvLine)
    }

    @Test
    fun `the metadata json has the iOS keys, schema_version and null heights included`() {
        val metadata = FingerprintRecordingMetadata(
            platform = "Android",
            node = "AABBCCDD",
            model = "Pixel 8",
            systemVersion = "Android 15",
            session = "S1",
            point = "P3",
            x = 2.0,
            y = 4.5,
            csvFile = "20260102-030405_S1_P3.csv",
            startedTMs = 1_000,
            endedTMs = 61_000,
            startedTimestampMs = 1_767_323_045_678,
            endedTimestampMs = 1_767_323_105_678,
            plannedDurationS = 60.0,
            recordsPerBeacon = mapOf("9F3C1A02" to 412, "BEAC0001" to 398),
            scanApi = "scan settings",
            orientationConvention = "orientation convention",
            orientationRateHz = 50.0,
            altitudeConvention = "altitude convention",
            motionActivityConvention = "motion activity convention",
            beacons = listOf(
                BeaconSnapshot(id = "9F3C1A02", label = "A", x = 0.0, y = 0.0, heightM = 1.2),
                BeaconSnapshot(id = "BEAC0001", label = "B", x = 2.5, y = 0.0, heightM = null)
            )
        )

        assertEquals(
            Json.parseToJsonElement(Fixtures.text("fingerprint_metadata.json")),
            Json.parseToJsonElement(metadata.toJson())
        )
    }
}
