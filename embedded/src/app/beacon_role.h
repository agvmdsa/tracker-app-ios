// BeaconRole (US1): advertises the project protocol so iPhones can localise themselves from it
// (contracts/ble-advertising.md). It depends only on the radio port and the event sink, so
// Wi-Fi, MQTT, other beacons or a phone cannot affect it (FR-019). Pure logic: no Arduino headers.
#pragma once

#include "device_config.h"
#include "events.h"
#include "hal/ble_port.h"
#include "role.h"

class BeaconRole final : public IRole {
public:
    BeaconRole(IBleRadio& radio, IEventSink& events) : radio_(radio), events_(events) {}

    // Validates the identifier and the interval, starts advertising and reports the effective
    // identifier, power, interval and payload size (FR-018). Returns false (after reporting why)
    // without ever advertising when the configuration is invalid (FR-003).
    bool start(const domain::DeviceConfiguration& config) override;
    void stop() override;
    const char* name() const override { return "beacon"; }

private:
    IBleRadio& radio_;
    IEventSink& events_;
};
