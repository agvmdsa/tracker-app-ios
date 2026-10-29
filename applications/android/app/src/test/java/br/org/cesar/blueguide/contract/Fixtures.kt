package br.org.cesar.blueguide.contract

import br.org.cesar.blueguide.core.dataset.FingerprintLabel
import br.org.cesar.blueguide.core.dataset.FingerprintRecord
import br.org.cesar.blueguide.core.model.DeviceId
import br.org.cesar.blueguide.core.sensors.AltitudeSample
import br.org.cesar.blueguide.core.sensors.DeviceMotionSample
import br.org.cesar.blueguide.core.sensors.MotionActivitySample
import br.org.cesar.blueguide.core.sensors.MotionActivityState
import java.time.Instant

/** The expected files under `src/test/resources/contracts/`. */
internal object Fixtures {
    fun text(name: String): String =
        requireNotNull(Fixtures::class.java.getResource("/contracts/$name")) { "missing contracts/$name" }.readText()
}

/** The values the expected files were written from, shared by the file and the API contracts. */
internal object ContractSamples {
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
