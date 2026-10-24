#include "supervisor.h"

#include <cstdio>
#include <cstring>

#include "config_validation.h"
#include "node_id.h"
#include "record_format.h"

namespace {

constexpr std::size_t kLineBytes = 128;  // matches the serial output queue line size
constexpr int kMaxCommandsPerTick = 8;

unsigned long u(uint32_t value) { return static_cast<unsigned long>(value); }

const char* roleName(domain::Role role) { return role == domain::Role::Beacon ? "beacon" : "collector"; }

const char* stateName(Supervisor::State state) {
    switch (state) {
        case Supervisor::State::Boot: return "boot";
        case Supervisor::State::LoadConfig: return "load_config";
        case Supervisor::State::Validate: return "validate";
        case Supervisor::State::StartRole: return "start_role";
        case Supervisor::State::RunningBeacon: return "running_beacon";
        case Supervisor::State::RunningCollector: return "running_collector";
        case Supervisor::State::ConfigError: return "config_error";
    }
    return "unknown";
}

const char* describe(domain::ConfigError error) {
    switch (error) {
        case domain::ConfigError::BadBeaconId: return "beacon id must be 8 hexadecimal characters";
        case domain::ConfigError::AdvIntervalTooShort: return "advertising interval must be at least 100 ms";
        case domain::ConfigError::WindowTooShort: return "aggregation window must be at least 250 ms";
        case domain::ConfigError::ScanWindowExceedsInterval: return "scan window must not exceed the scan interval";
        case domain::ConfigError::BadNodeId: return "node id must be 6 hexadecimal characters";
        case domain::ConfigError::None: return "ok";
    }
    return "invalid configuration";
}

const char* describeParseError(const char* code) {
    if (std::strcmp(code, "bad_id") == 0) return "beacon id must be 8 hexadecimal characters";
    if (std::strcmp(code, "bad_value") == 0) return "value not allowed or out of range";
    if (std::strcmp(code, "bad_args") == 0) return "wrong number of arguments";
    if (std::strcmp(code, "bad_label") == 0) return "usage: label <session> <point> <x> <y> with [A-Za-z0-9_-]";
    if (std::strcmp(code, "too_long") == 0) return "line too long";
    return "type help for the list of commands";
}

}  // namespace

Supervisor::Supervisor(const domain::DeviceConfiguration& defaults, IStore& store, IClock& clock,
                       ISystemInfo& system, IPublisher& out, ICommandSource& commands, const RoleFactory& roles,
                       const char* firmwareVersion)
    : defaults_(defaults),
      store_(store),
      clock_(clock),
      system_(system),
      out_(out),
      commands_(commands),
      roles_(roles),
      firmware_(firmwareVersion) {}

void Supervisor::begin() {
    state_ = State::Boot;
    boot_ = store_.incrementBootCount();

    state_ = State::LoadConfig;
    overrides_ = store_.loadOverrides();
    config_ = domain::resolveConfig(defaults_, overrides_);
    if (config_.nodeId[0] == '\0') domain::nodeIdFromChipId(system_.chipId(), config_.nodeId);
    config_.bootId = boot_;

    char line[kLineBytes];
    if (domain::formatHeader(line, sizeof(line), firmware_, config_.nodeId, boot_, roleName(config_.role),
                             config_.beaconId, config_.serialBaud) > 0) {
        reply(line);
    }

    state_ = State::Validate;
    const domain::ConfigError error = domain::validate(config_);
    if (error != domain::ConfigError::None) {
        enterConfigError(domain::errorCode(error), describe(error));
        return;
    }

    state_ = State::StartRole;
    IRole* role = roles_.select(config_.role);
    if (role == nullptr) {
        enterConfigError("role_unavailable", "the selected role is not available in this build");
        return;
    }
    if (!role->start(config_)) {
        state_ = State::ConfigError;  // the role already reported why through event()
        return;
    }
    active_ = role;
    state_ = (config_.role == domain::Role::Beacon) ? State::RunningBeacon : State::RunningCollector;
}

void Supervisor::tick() {
    if (active_ != nullptr) active_->poll();
    domain::Command command;
    for (int i = 0; i < kMaxCommandsPerTick && commands_.pollCommand(command); ++i) handle(command);
}

void Supervisor::event(const char* level, const char* code, const char* message) {
    char line[kLineBytes];
    if (domain::formatEvent(line, sizeof(line), clock_.nowMs(), boot_, level, code, message) > 0) reply(line);
}

void Supervisor::reply(const char* line) { out_.publishLine(line); }

void Supervisor::enterConfigError(const char* code, const char* message) {
    state_ = State::ConfigError;
    active_ = nullptr;
    event("error", code, message);
}

void Supervisor::handle(const domain::Command& command) {
    char line[kLineBytes];
    switch (command.type) {
        case domain::CommandType::Invalid:
            if (std::strcmp(command.errorCode, "empty") == 0) return;
            std::snprintf(line, sizeof(line), "ERR %s %s", command.errorCode, describeParseError(command.errorCode));
            reply(line);
            return;
        case domain::CommandType::Help:
            printHelp();
            return;
        case domain::CommandType::Status:
            printStatus();
            return;
        case domain::CommandType::Role:
            overrides_.hasRole = true;
            overrides_.role = command.role;
            store_.saveOverrides(overrides_);
            std::snprintf(line, sizeof(line), "OK role=%s (applies after reboot)", roleName(command.role));
            reply(line);
            return;
        case domain::CommandType::ConfigSave:
            store_.saveOverrides(overrides_);
            reply("OK config saved");
            return;
        case domain::CommandType::ConfigReset:
            store_.clearOverrides();
            overrides_ = domain::Overrides{};
            reply("OK config reset (applies after reboot)");
            return;
        case domain::CommandType::Reboot:
            reply("OK rebooting");
            system_.reboot();
            return;
        case domain::CommandType::Id:
            overrides_.hasBeaconId = true;
            std::strncpy(overrides_.beaconId, command.id.value, sizeof(overrides_.beaconId) - 1);
            overrides_.beaconId[sizeof(overrides_.beaconId) - 1] = '\0';
            store_.saveOverrides(overrides_);
            std::snprintf(line, sizeof(line), "OK id=%s (applies after reboot)", overrides_.beaconId);
            reply(line);
            return;
        case domain::CommandType::Power:
            overrides_.hasAdvPowerDbm = true;
            overrides_.advPowerDbm = command.value;
            store_.saveOverrides(overrides_);
            std::snprintf(line, sizeof(line), "OK power=%d (applies after reboot)", command.value);
            reply(line);
            return;
        case domain::CommandType::Interval:
            overrides_.hasAdvIntervalMs = true;
            overrides_.advIntervalMs = static_cast<uint32_t>(command.value);
            store_.saveOverrides(overrides_);
            std::snprintf(line, sizeof(line), "OK interval=%d (applies after reboot)", command.value);
            reply(line);
            return;
        case domain::CommandType::Window:
            reply("ERR not_supported command not available in this build yet");
            return;
        case domain::CommandType::Label:
        case domain::CommandType::RecStart:
        case domain::CommandType::RecStop:
            handleRecording(command);
            return;
    }
}

void Supervisor::handleRecording(const domain::Command& command) {
    IRecordingControl* recording = (active_ != nullptr) ? active_->recording() : nullptr;
    if (recording == nullptr) {
        reply("ERR not_collector label and rec need the collector role (role collector, then reboot)");
        return;
    }

    char line[kLineBytes];
    switch (command.type) {
        case domain::CommandType::Label:
            recording->setLabel(command.label);
            std::snprintf(line, sizeof(line), "OK label=%s,%s,%.2f,%.2f", command.label.session,
                          command.label.point, static_cast<double>(command.label.x),
                          static_cast<double>(command.label.y));
            reply(line);
            return;
        case domain::CommandType::RecStart:
            switch (recording->startRecording()) {
                case domain::RecordingState::StartResult::Started:
                    reply("OK recording started");
                    return;
                case domain::RecordingState::StartResult::AlreadyRecording:
                    reply("ERR already_recording send rec stop first");
                    return;
                case domain::RecordingState::StartResult::NoLabel:
                    reply("ERR no_label set one first: label <session> <point> <x> <y>");
                    return;
            }
            return;
        case domain::CommandType::RecStop: {
            RecordingSummary summary;
            if (!recording->stopRecording(summary)) {
                reply("ERR not_recording nothing to stop");
                return;
            }
            std::snprintf(line, sizeof(line), "OK recording stopped records=%lu beacons=%lu", u(summary.records),
                          u(summary.beacons));
            reply(line);
            return;
        }
        default:
            return;
    }
}

void Supervisor::printHelp() {
    reply("# help commands (one per line, case-insensitive):");
    reply("# help   help | status | reboot");
    reply("# help   role beacon|collector      id <8hex>      power <dBm>      interval <ms>");
    reply("# help   window <ms>                label <session> <point> <x> <y>");
    reply("# help   rec start|stop             config save|reset");
}

void Supervisor::printStatus() {
    char line[kLineBytes];
    std::snprintf(line, sizeof(line), "# status state=%s role=%s id=%s power=%d interval=%lu window=%lu",
                  stateName(state_), roleName(config_.role), config_.beaconId, config_.advPowerDbm,
                  u(config_.advIntervalMs), u(config_.windowMs));
    reply(line);
    std::snprintf(line, sizeof(line), "# status wifi=%s mqtt=%s node=%s boot=%lu heap_free=%lu heap_min=%lu",
                  config_.wifiEnabled ? "on" : "off", config_.mqttEnabled ? "on" : "off", config_.nodeId,
                  u(boot_), u(system_.freeHeapBytes()), u(system_.minFreeHeapBytes()));
    reply(line);
}
