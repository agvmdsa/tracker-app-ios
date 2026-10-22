// LiveView: what the collector prints while idle (contracts/serial-protocol.md, `L` lines). Once a
// second it reports each beacon heard since the previous report, with the last RSSI and the rate
// over that interval. A beacon that fell silent produces no line and gives its slot back, so the
// table stays bounded however many devices pass by. Storage is fixed (no heap). Pure logic: no
// Arduino headers.
#pragma once

#include <cstddef>
#include <cstdint>

#include "beacon_id.h"
#include "observation_filter.h"

namespace domain {

class LiveView {
public:
    static constexpr std::size_t kMaxBeacons = 16;
    static constexpr uint32_t kReportIntervalMs = 1000;

    struct Entry {
        BeaconId beacon;
        int rssiLast = 0;
        float ratePerS = 0.0f;
    };

    // Forgets everything and starts the first second at `nowMs`.
    void reset(uint32_t nowMs);

    void observe(const Observation& observation);

    // When a second has passed since the last report, fills `out` (up to `capacity` entries, in
    // order of first appearance) with each beacon heard since, restarts the counters and returns
    // how many entries were written. Returns 0 when it is not time yet or nothing was heard.
    std::size_t report(uint32_t nowMs, Entry* out, std::size_t capacity);

    std::size_t tracked() const { return count_; }

private:
    struct Slot {
        BeaconId beacon;
        int rssiLast = 0;
        uint32_t count = 0;
    };

    Slot slots_[kMaxBeacons];
    std::size_t count_ = 0;
    uint32_t lastReportMs_ = 0;
};

}  // namespace domain
