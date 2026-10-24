import Foundation

struct BeaconSighting {
    let beaconID: BeaconID
    let rssi: Int
    /// Wall-clock time it was received: lines up recordings from different devices, but jumps when
    /// the clock is adjusted.
    let seenAt: Date
    /// Monotonic milliseconds when it was received (`MonotonicClock`): never jumps, so it is what to
    /// order and window a recording's rows by.
    let monotonicMs: Int64

    init(beaconID: BeaconID, rssi: Int, seenAt: Date, monotonicMs: Int64 = MonotonicClock.nowMs()) {
        self.beaconID = beaconID
        self.rssi = rssi
        self.seenAt = seenAt
        self.monotonicMs = monotonicMs
    }
}

enum MonotonicClock {
    /// Milliseconds on `CLOCK_MONOTONIC`, which keeps counting while the phone sleeps and restarts
    /// on reboot: the same kind of clock as Android's `SystemClock.elapsedRealtime()` and the ESP32
    /// collector's `t_ms`. Only comparable between readings of the same device and boot.
    static func nowMs() -> Int64 {
        Int64(clock_gettime_nsec_np(CLOCK_MONOTONIC) / 1_000_000)
    }
}
