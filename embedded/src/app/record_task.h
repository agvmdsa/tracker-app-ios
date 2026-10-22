// RecordTask (T044): the collector's processing of what the radio heard. It drains the observation
// queue and prints the serial lines of contracts/serial-protocol.md:
//   idle       -> `L` lines, once per second per beacon, unlabelled
//   recording  -> `R` lines, one per advertisement, carrying the active label
//   stop       -> `S` lines, one per beacon
//
// It is polled from the supervisor's tick, the same context that applies `label` and `rec`, so the
// label and the recording state need no lock. Before any command changes that state it drains the
// queue first, so an advertisement already received is handled under the state it arrived in.
// Pure logic: no Arduino headers.
#pragma once

#include <cstdint>

#include "events.h"
#include "hal/clock.h"
#include "hal/publisher.h"
#include "live_view.h"
#include "observation_queue.h"
#include "recording_control.h"
#include "recording_state.h"

class RecordTask final : public IRecordingControl {
public:
    RecordTask(IObservationQueue& queue, IPublisher& out, IClock& clock, IEventSink& events)
        : queue_(queue), out_(out), clock_(clock), events_(events) {}

    // Starts a session of the collector: remembers the node and the boot number that go in every
    // line and restarts the live view. The label, if any, is kept.
    void begin(const char* nodeId, uint32_t bootId);

    // Handles every queued observation, then prints the live lines that are due (idle only).
    void poll();

    void setLabel(const domain::CollectionLabel& label) override;
    domain::RecordingState::StartResult startRecording() override;
    bool stopRecording(RecordingSummary& summary) override;

private:
    void drain();
    void handle(const domain::Observation& observation);
    void printLive();
    void print(const char* line) { out_.publishLine(line); }

    IObservationQueue& queue_;
    IPublisher& out_;
    IClock& clock_;
    IEventSink& events_;
    domain::RecordingState state_;
    domain::LiveView live_;
    char node_[7] = {0};
    uint32_t boot_ = 0;
    bool tableFullReported_ = false;
};
