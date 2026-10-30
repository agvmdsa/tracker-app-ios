package br.org.cesar.blueguide.core.dataset

import br.org.cesar.blueguide.core.model.AltitudeSample
import br.org.cesar.blueguide.core.model.DeviceId
import br.org.cesar.blueguide.core.model.DeviceMotionSample
import br.org.cesar.blueguide.core.model.MotionActivitySample
import br.org.cesar.blueguide.core.model.MotionActivityState
import java.time.Instant

/** The values the expected files were written from: this module's `contracts/fingerprint.csv`
 *  and the app's `contracts/api_fingerprints.json`. */
object ContractSamples {
    /** A model name with a comma and a quote, so the CSV quoting is part of the contract. */
    const val QUOTED_MODEL = "Pixel 8,Pro \"X\""

    fun fingerprintRecord(model: String, withSensors: Boolean) = FingerprintRecord(
        monotonicMs = 123_456_789L,
        seenAt = Instant.parse("2026-01-02T03:04:05.678Z"),
        node = DeviceId("AABBCCDD"),
        model = model,
        systemVersion = "Android 15",
        beacon = DeviceId("9F3C1A02"),
        rssi = -67,
        orientation = if (withSensors) DeviceMotionSample(roll = 0.1234567, pitch = -1.5, yaw = 3.14159265) else null,
        altitude = if (withSensors) AltitudeSample(relativeAltitudeM = 1.23456, pressureKPa = 101.3254) else null,
        motionActivity = if (withSensors) MotionActivitySample(MotionActivityState.WALKING, confidence = 87) else null,
        label = requireNotNull(FingerprintLabel.of(session = "S1", point = "P3", x = 2.0, y = 4.5))
    )
}
