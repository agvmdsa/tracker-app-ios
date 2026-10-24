#include "record_task.h"

#include <cstring>

#include "record_format.h"

namespace {
constexpr std::size_t kLineBytes = 128;  // matches the serial output queue line size
}

void RecordTask::begin(const char* nodeId, uint32_t bootId) {
    std::strncpy(node_, nodeId, sizeof(node_) - 1);
    node_[sizeof(node_) - 1] = '\0';
    boot_ = bootId;
    live_.reset(clock_.nowMs());
}

void RecordTask::poll() {
    drain();
    if (!state_.recording()) printLive();
}

void RecordTask::setLabel(const domain::CollectionLabel& label) {
    drain();  // what was already heard keeps the old label
    state_.setLabel(label);
}

domain::RecordingState::StartResult RecordTask::startRecording() {
    drain();  // what was heard before `rec start` is not part of the recording
    const auto result = state_.start(clock_.nowMs());
    if (result == domain::RecordingState::StartResult::Started) tableFullReported_ = false;
    return result;
}

bool RecordTask::stopRecording(RecordingSummary& summary) {
    drain();  // what was heard before `rec stop` still belongs to the recording
    const uint32_t now = clock_.nowMs();
    if (!state_.stop(now)) return false;

    const domain::CollectionLabel& label = state_.label();
    char line[kLineBytes];
    for (std::size_t i = 0; i < state_.beaconCount(); ++i) {
        const domain::RecordingState::BeaconTotals totals = state_.totals(i);
        const domain::SummaryLine summaryLine{now,         boot_,
                                              node_,       label.session,
                                              label.point, totals.beacon.value,
                                              totals.records, totals.ratePerS};
        if (domain::formatSummary(line, sizeof(line), summaryLine) > 0) print(line);
    }
    summary.records = state_.totalRecords();
    summary.beacons = static_cast<uint32_t>(state_.beaconCount());
    live_.reset(now);  // back to idle: the live view starts a fresh second
    return true;
}

void RecordTask::drain() {
    // Bounded by the queue capacity (128): the radio cannot add more than that while this runs.
    domain::Observation observation;
    while (queue_.pop(observation)) handle(observation);
}

void RecordTask::handle(const domain::Observation& observation) {
    // Published regardless of recording state: this is the continuous live telemetry feed, not
    // the labelled fingerprint dataset (only `uploader_` below cares whether a recording is on).
    mqtt_.publishSighting(node_, observation.beacon.value, observation.rssi);

    if (!state_.recording()) {
        live_.observe(observation);
        return;
    }
    const domain::CollectionLabel* label = state_.record(observation);
    if (label == nullptr) {  // recording, so the only reason is a full beacon table
        if (!tableFullReported_) {
            tableFullReported_ = true;
            events_.event("warn", "beacon_table_full", "too many beacons heard; the extra ones are not recorded");
        }
        return;
    }
    const domain::RawRecord record{observation.tMs,    boot_,          node_,       observation.beacon.value,
                                   observation.rssi,   label->session, label->point, label->x,
                                   label->y};
    char line[kLineBytes];
    if (domain::formatRaw(line, sizeof(line), record) > 0) print(line);
    uploader_.queueRow(record);
}

void RecordTask::printLive() {
    domain::LiveView::Entry entries[domain::LiveView::kMaxBeacons];
    const uint32_t now = clock_.nowMs();
    const std::size_t count = live_.report(now, entries, domain::LiveView::kMaxBeacons);
    char line[kLineBytes];
    for (std::size_t i = 0; i < count; ++i) {
        const domain::LiveLine liveLine{now, boot_, node_, entries[i].beacon.value, entries[i].rssiLast,
                                        entries[i].ratePerS};
        if (domain::formatLive(line, sizeof(line), liveLine) > 0) print(line);
    }
}
