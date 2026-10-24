// Supervisor: the device state machine (data-model.md) and the serial command handling
// (contracts/serial-protocol.md). It depends only on the pure interfaces of src/hal and src/app,
// so it runs on the host in the unit tests. No Arduino headers.
//
//   Boot -> LoadConfig -> Validate -> StartRole -> Running(role)
//                            |            |
//                            +--------> ConfigError   (commands stay active)
#pragma once

#include <cstdint>

#include "command_source.h"
#include "device_config.h"
#include "events.h"
#include "hal/clock.h"
#include "hal/publisher.h"
#include "hal/store.h"
#include "hal/system_info.h"
#include "role.h"
#include "role_factory.h"

class Supervisor final : public IEventSink {
public:
    enum class State { Boot, LoadConfig, Validate, StartRole, RunningBeacon, RunningCollector, ConfigError };

    Supervisor(const domain::DeviceConfiguration& defaults, IStore& store, IClock& clock, ISystemInfo& system,
               IPublisher& out, ICommandSource& commands, const RoleFactory& roles, const char* firmwareVersion);

    // Runs Boot -> LoadConfig -> Validate -> StartRole once, printing the banner.
    void begin();
    // Handles the pending serial commands (a few per call). Call it periodically.
    void tick();

    void event(const char* level, const char* code, const char* message) override;

    State state() const { return state_; }
    const domain::DeviceConfiguration& config() const { return config_; }
    uint32_t bootId() const { return boot_; }

private:
    void handle(const domain::Command& command);
    void handleRecording(const domain::Command& command);
    void reply(const char* line);
    void enterConfigError(const char* code, const char* message);
    void printHelp();
    void printStatus();

    domain::DeviceConfiguration defaults_;
    domain::DeviceConfiguration config_{};
    domain::Overrides overrides_{};
    IStore& store_;
    IClock& clock_;
    ISystemInfo& system_;
    IPublisher& out_;
    ICommandSource& commands_;
    const RoleFactory& roles_;
    const char* firmware_;
    IRole* active_ = nullptr;
    State state_ = State::Boot;
    uint32_t boot_ = 0;
};
