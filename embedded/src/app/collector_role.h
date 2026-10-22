// CollectorRole (US2): listens for the advertisements of the project protocol so a labelled
// fingerprint dataset can be built (contracts/serial-protocol.md). It starts the scan with the
// configured interval and window, and hands what the radio queued to the RecordTask, which prints
// the lines. It never advertises. Pure logic: no Arduino headers.
#pragma once

#include "device_config.h"
#include "events.h"
#include "hal/ble_port.h"
#include "hal/clock.h"
#include "hal/publisher.h"
#include "observation_queue.h"
#include "record_task.h"
#include "role.h"

class CollectorRole final : public IRole {
public:
    CollectorRole(IBleRadio& radio, IObservationQueue& queue, IPublisher& out, IClock& clock, IEventSink& events)
        : radio_(radio), queue_(queue), events_(events), task_(queue, out, clock, events) {}

    // Checks the scan interval and window, starts the scan and reports them. Returns false (after
    // reporting why) when the parameters are invalid or the radio could not start.
    bool start(const domain::DeviceConfiguration& config) override;
    void stop() override;
    void poll() override { task_.poll(); }
    IRecordingControl* recording() override { return &task_; }
    const char* name() const override { return "collector"; }

private:
    IBleRadio& radio_;
    IObservationQueue& queue_;
    IEventSink& events_;
    RecordTask task_;
};
