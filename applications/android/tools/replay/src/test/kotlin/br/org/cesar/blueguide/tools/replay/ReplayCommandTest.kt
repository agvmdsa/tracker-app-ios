package br.org.cesar.blueguide.tools.replay

import br.org.cesar.blueguide.core.dataset.FingerprintCsv
import br.org.cesar.blueguide.core.dataset.FingerprintLabel
import br.org.cesar.blueguide.core.dataset.FingerprintRecord
import br.org.cesar.blueguide.core.dataset.FingerprintRecordingMetadata
import br.org.cesar.blueguide.core.dataset.FingerprintRecordingMetadata.BeaconSnapshot
import br.org.cesar.blueguide.core.dataset.FingerprintRecordingMetadata.PositioningSnapshot
import br.org.cesar.blueguide.core.model.Anchor
import br.org.cesar.blueguide.core.model.DeviceId
import br.org.cesar.blueguide.core.model.Point2D
import br.org.cesar.blueguide.core.positioning.PositioningSetup
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Instant

/** Recordings as the app writes them, replayed from the command line. */
class ReplayCommandTest {

    private val directory: File = Files.createTempDirectory("recordings").toFile()
    private val ids = listOf("9F3C1A02", "3F8A1C04", "1A2B3C4D", "B7E2F910").map(::DeviceId)
    private val anchors = listOf(Point2D(0.0, 0.0), Point2D(2.5, 0.0), Point2D(2.5, 2.5), Point2D(0.0, 2.5))
        .mapIndexed { index, position -> Anchor(ids[index], "ABCD"[index].toString(), position) }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    /** [seconds] of every anchor heard ten times a second at [rssi], labelled as standing at
     *  [standing]; [setup] null is a recording from before the calibration was kept. */
    private fun recording(
        name: String,
        standing: Point2D = Point2D(1.25, 1.25),
        rssi: Int = -70,
        seconds: Int = 5,
        setup: PositioningSetup? = PositioningSetup(anchors)
    ): File {
        val startMs = 10_000L
        val label = requireNotNull(FingerprintLabel.of("S1", name.substringAfterLast('_'), standing.x, standing.y))
        val rows = (0 until seconds * 10).flatMap { tick ->
            ids.mapIndexed { index, beacon ->
                val atMs = startMs + tick * 100L + index
                FingerprintRecord(
                    atMs,
                    Instant.ofEpochMilli(
                        atMs
                    ),
                    DeviceId("0B5E7E01"), "Pixel 9", "15", beacon, rssi, null, null, null, label
                )
            }
        }
        val csv = File(directory, "$name.csv")
        csv.writeText((listOf(FingerprintCsv.HEADER) + rows.map(FingerprintCsv::line)).joinToString("\n", postfix = "\n"))
        val metadata = FingerprintRecordingMetadata(
            schemaVersion = if (setup == null) 1 else FingerprintRecordingMetadata.CURRENT_SCHEMA_VERSION,
            platform = "Android",
            node = "0B5E7E01",
            model = "Pixel 9",
            systemVersion = "15",
            session = label.session,
            point = label.point,
            x = standing.x,
            y = standing.y,
            csvFile = csv.name,
            startedTMs = startMs,
            endedTMs = startMs + seconds * 1_000L,
            startedTimestampMs = startMs,
            endedTimestampMs = startMs + seconds * 1_000L,
            plannedDurationS = seconds.toDouble(),
            recordsPerBeacon = ids.associate { it.value to seconds * 10 },
            scanApi = "",
            orientationConvention = "",
            orientationRateHz = 50.0,
            altitudeConvention = "",
            motionActivityConvention = "",
            beacons = anchors.map(BeaconSnapshot::of),
            positioning = setup?.let(PositioningSnapshot::of)
        )
        File(directory, "$name.json").writeText(metadata.toJson())
        return csv
    }

    private class Run(val status: Int, val out: List<String>, val err: String) {
        val header: List<String> get() = out.first().split(",")
        val rows: List<Map<String, String>> get() = out.drop(1).map { header.zip(it.split(",")).toMap() }
    }

    private fun run(vararg args: String): Run {
        val out = StringBuilder()
        val err = StringBuilder()
        val status = ReplayCommand.run(args.toList(), out, err)
        return Run(status, out.lines().filter { it.isNotEmpty() }, err.toString())
    }

    @Test
    fun `a recording is one row a second, with where the app placed the phone and where it stood`() {
        val result = run(recording("20261030-100000_S1_P1").path)

        assertEquals(ReplayCommand.OK, result.status)
        assertEquals(ReplayTable.FIXED_COLUMNS, result.header.take(ReplayTable.FIXED_COLUMNS.size))
        assertEquals(listOf("1000", "2000", "3000", "4000", "5000"), result.rows.map { it["elapsed_ms"] })
        val row = result.rows.first()
        assertEquals("20261030-100000_S1_P1", row["recording"])
        assertEquals("1.250", row["x"])
        assertEquals("1.250", row["y"])
        assertEquals("0.000", row["error_m"])
        assertEquals("4", row["anchors_ranged"])
        assertEquals("-70.0", row["rssi_9F3C1A02"])
        assertTrue(result.err, result.err.contains("20261030-100000_S1_P1: 5 s, posição em 5; erro médio 0,00 m"))
    }

    /** Labelled at a corner, but every anchor reads the same: the app puts the phone in the
     *  middle, 1.77 m away, and that is the error, every second and in the summary. */
    @Test
    fun `the error is the distance from where the phone stood`() {
        val result = run(recording("20261030-100000_S1_P1", standing = Point2D(0.0, 0.0)).path)

        assertTrue(result.rows.all { it["error_m"] == "1.768" })
        assertTrue(result.err, result.err.contains("erro médio 1,77 m, p95 1,77 m, máximo 1,77 m"))
    }

    @Test
    fun `the options try another calibration on the same recording`() {
        val csv = recording("20261030-100000_S1_P1")

        val recorded = run(csv.path).rows.first().getValue("distance_9F3C1A02")
        val other = run("--center-rssi", "-60", csv.path).rows.first().getValue("distance_9F3C1A02")

        assertNotEquals(recorded, other)
    }

    @Test
    fun `a recording from before the calibration was kept replays with the defaults, and says so`() {
        val csv = recording("20261030-100000_S1_P1", setup = null)

        val withDefaults = run(csv.path)
        val chosen = run("--center-rssi", "-70", "--touch-rssi", "-30", csv.path)

        assertEquals(5, withDefaults.rows.size)
        assertTrue(withDefaults.err, withDefaults.err.contains("versão 1, sem a calibração"))
        assertTrue(chosen.err, !chosen.err.contains("sem a calibração"))
    }

    @Test
    fun `a folder is its recordings by name, and cut ones are left out`() {
        recording("20261030-110000_S1_P2")
        recording("20261030-100000_S1_P1")
        val cut = recording("20261030-120000_S1_P3")
        File(directory, "${cut.nameWithoutExtension}.json").delete()

        val result = run(directory.path)

        assertEquals(ReplayCommand.OK, result.status)
        assertEquals(listOf("20261030-100000_S1_P1", "20261030-110000_S1_P2"), result.rows.map { it["recording"] }.distinct())
        assertTrue(result.err, result.err.contains("20261030-120000_S1_P3.csv: sem o .json"))
        assertTrue(result.err, result.err.contains("Todas: 10 s, posição em 10"))
    }

    @Test
    fun `a recording that can't be read fails the run, and the others are still replayed`() {
        recording("20261030-100000_S1_P1")
        val broken = recording("20261030-110000_S1_P2")
        File(directory, "${broken.nameWithoutExtension}.json").writeText("{}")

        val result = run(directory.path)

        assertEquals(ReplayCommand.FAILED, result.status)
        assertEquals(5, result.rows.size)
        assertTrue(result.err, result.err.contains("20261030-110000_S1_P2.json"))
    }

    @Test
    fun `out writes the table to a file`() {
        val target = File(directory, "replay.csv")

        val result = run("--out", target.path, recording("20261030-100000_S1_P1").path)

        assertTrue(result.out.isEmpty())
        assertEquals(6, target.readLines().size)
    }

    @Test
    fun `wrong arguments show how to use it`() {
        assertEquals(ReplayCommand.USAGE_ERROR, run().status)
        assertEquals(ReplayCommand.USAGE_ERROR, run("--center-rssi", "strong", "a.csv").status)
        assertEquals(ReplayCommand.USAGE_ERROR, run("--colour", "a.csv").status)
        assertEquals(ReplayCommand.USAGE_ERROR, run(File(directory, "missing.csv").path).status)
        val help = run("--help")
        assertEquals(ReplayCommand.OK, help.status)
        assertTrue(help.out.first().startsWith("uso: replay"))
    }
}
