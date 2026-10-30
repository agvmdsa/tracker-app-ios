package br.org.cesar.blueguide.core.positioning

import br.org.cesar.blueguide.core.model.Point2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.log10
import kotlin.math.sqrt

/** The log-distance model, and its calibration from the "RSSI no centro" and "RSSI encostado" readings. */
class PathLossModelTest {

    private val squareCorners = listOf(Point2D(0.0, 0.0), Point2D(2.5, 0.0), Point2D(2.5, 2.5), Point2D(0.0, 2.5))
    private val squareCenter = Point2D(1.25, 1.25)

    @Test
    fun `seventy dBm is the distance from a corner to the center of the bench square`() {
        assertEquals(2.5 / sqrt(2.0), PathLossModel.distanceMeters(-70.0), 0.02)
    }

    @Test
    fun `the center distance is the mean distance from the anchors to their middle`() {
        assertEquals(2.5 / sqrt(2.0), requireNotNull(PathLossModel.centerDistanceMeters(squareCorners)), 1e-9)
        assertNull("one anchor has no middle to be away from", PathLossModel.centerDistanceMeters(listOf(Point2D(1.0, 1.0))))
        assertNull(PathLossModel.centerDistanceMeters(emptyList()))
    }

    @Test
    fun `thirty dBm is the touching distance`() {
        assertEquals(PathLossModel.TOUCH_DISTANCE_METERS, PathLossModel.distanceMeters(-30.0), 1e-9)
    }

    /** Two readings, two unknowns: the center RSSI is the center distance and the touching RSSI is
     *  the touching distance, which fixes both the RSSI at 1 m and the path loss exponent. */
    @Test
    fun `the calibration reads the center and the touching RSSI as their distances`() {
        val calibration = PathLossModel.calibration(centerRssi = -76.0, centerDistanceMeters = 3.0, touchRssi = -38.0)

        assertTrue(calibration.usesTouchRssi)
        assertEquals(3.0, calibration.distanceMeters(-76.0), 1e-9)
        assertEquals(PathLossModel.TOUCH_DISTANCE_METERS, calibration.distanceMeters(-38.0), 1e-9)
        // 38 dB over a 30x change in distance.
        assertEquals(38 / (10 * log10(30.0)), calibration.exponent, 1e-9)
    }

    /** A touching RSSI no stronger than the center's would make the signal grow with the distance:
     *  it is ignored, and the center alone calibrates with the nominal exponent. */
    @Test
    fun `a touching RSSI no stronger than the center is ignored`() {
        for (touch in listOf(-76.0, -80.0)) {
            val calibration = PathLossModel.calibration(-76.0, 3.0, touch)

            assertFalse("$touch dBm", calibration.usesTouchRssi)
            assertEquals(PathLossModel.PATH_LOSS_EXPONENT, calibration.exponent, 1e-9)
            assertEquals(3.0, calibration.distanceMeters(-76.0), 1e-9)
        }
    }

    @Test
    fun `an implausible exponent ignores the touching RSSI`() {
        val calibration = PathLossModel.calibration(-95.0, 1.77, -15.0)

        assertFalse(calibration.usesTouchRssi)
        assertEquals(1.77, calibration.distanceMeters(-95.0), 1e-9)
    }

    @Test
    fun `anchors closer than the touching distance ignore the touching RSSI`() {
        val calibration = PathLossModel.calibration(-40.0, 0.05, -30.0)

        assertFalse(calibration.usesTouchRssi)
        assertEquals(0.05, calibration.distanceMeters(-40.0), 1e-9)
    }

    @Test
    fun `without a touching RSSI the center alone calibrates`() {
        val calibration = PathLossModel.calibration(-76.0, 3.0, null)

        assertFalse(calibration.usesTouchRssi)
        assertEquals(3.0, calibration.distanceMeters(-76.0), 1e-9)
    }

    @Test
    fun `reference RSSI means one metre`() {
        assertEquals(1.0, PathLossModel.distanceMeters(PathLossModel.REFERENCE_RSSI), TOLERANCE)
    }

    @Test
    fun `a weaker signal reads as further away`() {
        assertTrue(PathLossModel.distanceMeters(-80.0) > PathLossModel.distanceMeters(-60.0))
    }

    @Test
    fun `rssi for a distance is the inverse of the distance for an rssi`() {
        val calibration = PathLossModel.calibration(-70.0, 2.0, -36.0)

        assertEquals(3.3, calibration.distanceMeters(calibration.rssi(atDistance = 3.3)), 1e-9)
    }

    private companion object {
        const val TOLERANCE = 1e-6
    }
}
