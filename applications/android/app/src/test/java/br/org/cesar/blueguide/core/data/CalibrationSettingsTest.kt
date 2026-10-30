package br.org.cesar.blueguide.core.data

import br.org.cesar.blueguide.core.model.Anchor
import br.org.cesar.blueguide.core.model.DeviceId
import br.org.cesar.blueguide.core.model.Point2D
import br.org.cesar.blueguide.core.positioning.MotionLimit
import br.org.cesar.blueguide.core.positioning.PathLossModel
import br.org.cesar.blueguide.core.positioning.PositioningSetup
import org.junit.Assert.assertEquals
import org.junit.Test

/** The saved calibration as the positioning engine uses it, on the Positioning screen and in
 *  each fingerprint recording alike. */
class CalibrationSettingsTest {

    private val anchors = listOf(Anchor(DeviceId("9F3C1A02"), "A", Point2D(0.0, 0.0)))

    @Test
    fun `settings never saved are the defaults, with the speed limit on`() {
        assertEquals(
            PositioningSetup(
                anchors,
                PathLossModel.DEFAULT_CENTER_RSSI,
                PathLossModel.DEFAULT_TOUCH_RSSI,
                MotionLimit.DEFAULT_MAX_SPEED_KMH
            ),
            CalibrationSettings().positioningSetup(anchors)
        )
    }

    @Test
    fun `saved settings are used as they are`() {
        val settings = CalibrationSettings(centerRssi = -76.0, touchRssi = -38.0, isSpeedLimited = true, maxSpeedKmh = 4.0)

        assertEquals(PositioningSetup(anchors, -76.0, -38.0, 4.0), settings.positioningSetup(anchors))
    }

    /** The speed stays saved, and shown, while the limit is off; the engine just doesn't limit. */
    @Test
    fun `with the speed limit off the engine follows every fix, and the speed is kept`() {
        val settings = CalibrationSettings(isSpeedLimited = false, maxSpeedKmh = 4.0)

        assertEquals(null, settings.positioningSetup(anchors).maxSpeedKmh)
        assertEquals(4.0, settings.maxSpeedKmhOrDefault, 0.0)
    }
}
