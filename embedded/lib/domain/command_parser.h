// Serial command parser (contracts/serial-protocol.md). Pure logic: no Arduino headers.
#pragma once

#include "beacon_id.h"
#include "device_config.h"
#include "label.h"

namespace domain {

enum class CommandType {
    Invalid,
    Help,
    Status,
    Role,
    Id,
    Power,
    Interval,
    Window,
    Label,
    RecStart,
    RecStop,
    ConfigSave,
    ConfigReset,
    Reboot,
};

struct Command {
    CommandType type = CommandType::Invalid;
    domain::Role role = domain::Role::Beacon;  // Role
    BeaconId id{};                             // Id
    int value = 0;                             // Power (dBm), Interval (ms), Window (ms)
    CollectionLabel label{};                   // Label
    // When type == Invalid: "empty", "too_long", "unknown_command", "bad_args", "bad_value",
    // "bad_id" or "bad_label". The caller prints "ERR <code> <text>" (nothing for "empty").
    char errorCode[16] = {0};
};

// Parses one input line. Case-insensitive command words; tolerant of CR/LF and extra spaces.
// Ranges: power -40..20 dBm, interval 100..10240 ms, window 250..3600000 ms.
Command parseCommand(const char* line);

}  // namespace domain
