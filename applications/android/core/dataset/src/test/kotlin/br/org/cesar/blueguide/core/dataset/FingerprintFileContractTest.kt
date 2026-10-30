package br.org.cesar.blueguide.core.dataset

import br.org.cesar.blueguide.core.dataset.ContractSamples.QUOTED_MODEL
import br.org.cesar.blueguide.core.dataset.ContractSamples.fingerprintRecord
import br.org.cesar.blueguide.core.dataset.FingerprintRecordingMetadata.BeaconSnapshot
import br.org.cesar.blueguide.core.dataset.FingerprintRecordingMetadata.PositioningSnapshot
import br.org.cesar.blueguide.core.model.Anchor
import br.org.cesar.blueguide.core.model.DeviceId
import br.org.cesar.blueguide.core.model.Point2D
import br.org.cesar.blueguide.core.positioning.PositioningSetup
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The expected files under `src/test/resources/contracts/`. */
internal object Fixtures {
    fun text(name: String): String =
        requireNotNull(Fixtures::class.java.getResource("/contracts/$name")) { "missing contracts/$name" }.readText()
}

/**
 * The CSV and the metadata JSON a recording writes are a format shared with the iOS app (the
 * reference: its `FingerprintRecord` and `FingerprintRecordingMetadata.CodingKeys`), the ESP32
 * collector's `R` records and the training pipeline. The expected files are the format itself:
 * when one of these fails, the change broke the format, not the fixture.
 */
class FingerprintFileContractTest {

    private val csvLines = Fixtures.text("fingerprint.csv").lines()

    private val metadata = FingerprintRecordingMetadata(
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
        ),
        positioning = PositioningSnapshot(
            centerRssi = -72.0,
            touchRssi = -38.0,
            maxSpeedKmh = 2.0,
            referenceRssi = -66.5,
            pathLossExponent = 1.5,
            usesTouchRssi = true,
            rssiWindowMs = 3_000,
            refreshIntervalMs = 1_000
        )
    )

    @Test
    fun `the csv header is the shared column list`() {
        assertEquals(csvLines[0], FingerprintCsv.HEADER)
    }

    @Test
    fun `a row with every sensor, with free text quoted`() {
        assertEquals(csvLines[1], FingerprintCsv.line(fingerprintRecord(model = QUOTED_MODEL, withSensors = true)))
    }

    @Test
    fun `a row without sensors leaves their columns empty`() {
        assertEquals(csvLines[2], FingerprintCsv.line(fingerprintRecord(model = "Pixel 8", withSensors = false)))
    }

    /** What is read is what was written: reading the expected file and writing it again gives
     *  the same text, the quotes and the empty columns included. */
    @Test
    fun `the expected csv reads back to the same rows`() {
        val records = FingerprintCsv.records(csvLines.asSequence()).toList()

        assertEquals(csvLines.drop(1).filter { it.isNotEmpty() }, records.map(FingerprintCsv::line))
        assertEquals(QUOTED_MODEL, records[0].model)
        assertEquals(fingerprintRecord(model = "Pixel 8", withSensors = false), records[1])
    }

    @Test
    fun `the metadata json has the iOS keys, schema_version, null heights and the positioning setup`() {
        assertEquals(
            Json.parseToJsonElement(Fixtures.text("fingerprint_metadata.json")),
            Json.parseToJsonElement(metadata.toJson())
        )
    }

    @Test
    fun `the expected metadata json reads back to the same metadata`() {
        assertEquals(metadata, FingerprintRecordingMetadata.fromJson(Fixtures.text("fingerprint_metadata.json")))
    }

    /** What phones recorded before version 2, and the iOS app still writes. */
    @Test
    fun `a version 1 json reads, without a positioning setup`() {
        val old = FingerprintRecordingMetadata.fromJson(Fixtures.text("fingerprint_metadata_v1.json"))

        assertEquals(1, old.schemaVersion)
        assertNull(old.positioning)
        assertNull(old.positioningSetup)
        assertEquals(metadata.copy(schemaVersion = 1, positioning = null), old)
    }

    /** A recording is replayed with the setup it was made with: the beacons where they were,
     *  and the calibration of the moment. */
    @Test
    fun `the positioning setup a recording keeps is the one it was made with`() {
        val anchors = listOf(
            Anchor(DeviceId("9F3C1A02"), "A", Point2D(0.0, 0.0), height = 1.2),
            Anchor(DeviceId("BEAC0001"), "B", Point2D(2.5, 0.0))
        )
        val setup = PositioningSetup(anchors, centerRssi = -72.0, touchRssi = -38.0, maxSpeedKmh = null)

        val snapshot = PositioningSnapshot.of(setup)
        val recorded = metadata.copy(beacons = anchors.map(BeaconSnapshot::of), positioning = snapshot)

        assertEquals(setup.calibration.referenceRssi, snapshot.referenceRssi, 0.0)
        assertEquals(setup.calibration.exponent, snapshot.pathLossExponent, 0.0)
        assertEquals(setup, FingerprintRecordingMetadata.fromJson(recorded.toJson()).positioningSetup)
    }
}
