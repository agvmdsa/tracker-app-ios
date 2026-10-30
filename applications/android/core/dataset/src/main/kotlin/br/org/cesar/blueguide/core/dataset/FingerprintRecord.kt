package br.org.cesar.blueguide.core.dataset

import br.org.cesar.blueguide.core.model.AltitudeSample
import br.org.cesar.blueguide.core.model.DeviceId
import br.org.cesar.blueguide.core.model.DeviceMotionSample
import br.org.cesar.blueguide.core.model.MotionActivitySample
import java.time.Instant

/**
 * One row of a fingerprinting dataset: a single advertisement as this phone received it, how
 * the phone was being held at the time, where it was, and what it was doing — the
 * counterpart of the iOS app's `FingerprintRecord`. [FingerprintCsv] writes and reads it.
 */
data class FingerprintRecord(
    /** `t_ms`: monotonic milliseconds (`SystemClock.elapsedRealtime()`), for ordering and
     *  windowing the rows. */
    val monotonicMs: Long,
    /** `timestamp_ms`: Unix time, for lining recordings up across devices. */
    val seenAt: Instant,
    /** The recording phone's own install id. */
    val node: DeviceId,
    val model: String,
    val systemVersion: String,
    val beacon: DeviceId,
    val rssi: Int,
    val orientation: DeviceMotionSample?,
    val altitude: AltitudeSample?,
    val motionActivity: MotionActivitySample?,
    val label: FingerprintLabel
)
