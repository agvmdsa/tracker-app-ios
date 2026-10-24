#include "live_view.h"

#include <cstring>

namespace domain {

void LiveView::reset(uint32_t nowMs) {
    count_ = 0;
    lastReportMs_ = nowMs;
}

void LiveView::observe(const Observation& observation) {
    for (std::size_t i = 0; i < count_; ++i) {
        if (std::strcmp(slots_[i].beacon.value, observation.beacon.value) == 0) {
            slots_[i].rssiLast = observation.rssi;
            ++slots_[i].count;
            return;
        }
    }
    if (count_ == kMaxBeacons) return;  // table full: this one is not shown until a slot frees
    Slot& slot = slots_[count_++];
    slot.beacon = observation.beacon;
    slot.rssiLast = observation.rssi;
    slot.count = 1;
}

std::size_t LiveView::report(uint32_t nowMs, Entry* out, std::size_t capacity) {
    const uint32_t elapsedMs = nowMs - lastReportMs_;
    if (elapsedMs < kReportIntervalMs) return 0;
    lastReportMs_ = nowMs;

    const float elapsedS = static_cast<float>(elapsedMs) / 1000.0f;
    std::size_t written = 0;
    std::size_t kept = 0;
    for (std::size_t i = 0; i < count_; ++i) {
        Slot slot = slots_[i];
        if (slot.count == 0) continue;  // silent for a whole interval: drop it
        if (written < capacity) {
            out[written].beacon = slot.beacon;
            out[written].rssiLast = slot.rssiLast;
            out[written].ratePerS = static_cast<float>(slot.count) / elapsedS;
            ++written;
        }
        slot.count = 0;
        slots_[kept++] = slot;
    }
    count_ = kept;
    return written;
}

}  // namespace domain
