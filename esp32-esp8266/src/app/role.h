// Role interface (Strategy pattern): the device runs exactly one role per run, chosen by the
// RoleFactory from the validated configuration. Pure interface.
#pragma once

#include "device_config.h"
#include "recording_control.h"

class IRole {
public:
    virtual ~IRole() = default;
    // Starts the role. Returns false when it could not start; the role has already reported the
    // reason through the event sink.
    virtual bool start(const domain::DeviceConfiguration& config) = 0;
    virtual void stop() = 0;
    virtual const char* name() const = 0;

    // Called on every supervisor tick, in the same context that applies the serial commands, so a
    // role needs no lock between its periodic work and those commands.
    virtual void poll() {}
    // The `label` and `rec` commands, for a role that records (the collector); nullptr otherwise.
    virtual IRecordingControl* recording() { return nullptr; }
};
