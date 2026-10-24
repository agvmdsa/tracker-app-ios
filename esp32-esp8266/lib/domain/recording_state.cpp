#include "recording_state.h"

#include <cstring>

namespace domain {

RecordingState::StartResult RecordingState::start(uint32_t nowMs) {
    if (recording_) return StartResult::AlreadyRecording;
    if (!hasLabel_) return StartResult::NoLabel;
    count_ = 0;
    untracked_ = 0;
    startMs_ = nowMs;
    elapsedMs_ = 0;
    recording_ = true;
    return StartResult::Started;
}

const CollectionLabel* RecordingState::record(const Observation& observation) {
    if (!recording_) return nullptr;

    Slot* slot = nullptr;
    for (std::size_t i = 0; i < count_; ++i) {
        if (std::strcmp(slots_[i].beacon.value, observation.beacon.value) == 0) {
            slot = &slots_[i];
            break;
        }
    }
    if (slot == nullptr) {
        if (count_ == kMaxBeacons) {
            ++untracked_;
            return nullptr;
        }
        slot = &slots_[count_++];
        slot->beacon = observation.beacon;
        slot->records = 0;
    }
    ++slot->records;
    return &label_;
}

bool RecordingState::stop(uint32_t nowMs) {
    if (!recording_) return false;
    recording_ = false;
    elapsedMs_ = nowMs - startMs_;
    return true;
}

RecordingState::BeaconTotals RecordingState::totals(std::size_t index) const {
    BeaconTotals totals;
    if (index >= count_) return totals;
    totals.beacon = slots_[index].beacon;
    totals.records = slots_[index].records;
    totals.ratePerS = (elapsedMs_ == 0) ? 0.0f : static_cast<float>(totals.records) / (static_cast<float>(elapsedMs_) / 1000.0f);
    return totals;
}

uint32_t RecordingState::totalRecords() const {
    uint32_t total = 0;
    for (std::size_t i = 0; i < count_; ++i) total += slots_[i].records;
    return total;
}

}  // namespace domain
