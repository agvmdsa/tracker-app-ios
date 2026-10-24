// RecordingState (data-model.md): idle or recording, with the records counted per beacon, the
// observed rate (records / elapsed seconds) and the summary printed on stop (US2 scenario 3).
//
// The active label lives here. Each record takes the label that is active at the moment it is
// counted, so a label change affects only later records. Storage is fixed (no heap). Pure logic: no
// Arduino headers.
#pragma once

#include <cstddef>
#include <cstdint>

#include "beacon_id.h"
#include "label.h"
#include "observation_filter.h"

namespace domain {

class RecordingState {
public:
    // Beacons tracked in one recording. Far above the handful of anchors a room has; a beacon
    // beyond the table is refused and counted in `untracked()` instead of corrupting anything.
    static constexpr std::size_t kMaxBeacons = 16;

    enum class StartResult { Started, AlreadyRecording, NoLabel };

    struct BeaconTotals {
        BeaconId beacon;
        uint32_t records = 0;
        float ratePerS = 0.0f;
    };

    bool recording() const { return recording_; }

    // The active label. Setting it while idle keeps it for the next recording.
    void setLabel(const CollectionLabel& label) {
        label_ = label;
        hasLabel_ = true;
    }
    bool hasLabel() const { return hasLabel_; }
    const CollectionLabel& label() const { return label_; }

    // Every record carries exactly one label, so a recording cannot start without one.
    StartResult start(uint32_t nowMs);

    // Counts one observation. Returns the label this record carries (the active one), or nullptr
    // when nothing was counted: idle, or the beacon table is full.
    const CollectionLabel* record(const Observation& observation);

    // Ends the recording and freezes the elapsed time; false when none was running.
    bool stop(uint32_t nowMs);

    // Totals of the current or last recording, in order of first appearance. The rate is
    // records / elapsed seconds of the finished recording, so read it after stop().
    std::size_t beaconCount() const { return count_; }
    BeaconTotals totals(std::size_t index) const;
    uint32_t totalRecords() const;
    uint32_t untracked() const { return untracked_; }

private:
    struct Slot {
        BeaconId beacon;
        uint32_t records = 0;
    };

    Slot slots_[kMaxBeacons];
    std::size_t count_ = 0;
    uint32_t untracked_ = 0;
    uint32_t startMs_ = 0;
    uint32_t elapsedMs_ = 0;
    bool recording_ = false;
    CollectionLabel label_{};
    bool hasLabel_ = false;
};

}  // namespace domain
