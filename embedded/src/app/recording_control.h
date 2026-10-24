// The commands a role that records understands (contracts/serial-protocol.md: `label`, `rec start`,
// `rec stop`). The supervisor reaches the collector through this interface, so it knows nothing
// about scanning. Pure interface.
#pragma once

#include <cstdint>

#include "label.h"
#include "recording_state.h"

struct RecordingSummary {
    uint32_t records = 0;
    uint32_t beacons = 0;
};

class IRecordingControl {
public:
    virtual ~IRecordingControl() = default;

    // Effective immediately: advertisements already received keep the label they had.
    virtual void setLabel(const domain::CollectionLabel& label) = 0;
    // Refused without a label (every record carries exactly one) or while already recording.
    virtual domain::RecordingState::StartResult startRecording() = 0;
    // Prints one `S` line per beacon and fills `summary`; false when nothing was recording.
    virtual bool stopRecording(RecordingSummary& summary) = 0;
};
