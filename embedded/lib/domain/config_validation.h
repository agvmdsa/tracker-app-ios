// Configuration validation and precedence (data-model.md, contracts/configuration.md).
// Pure logic: no Arduino headers.
#pragma once

#include "device_config.h"

namespace domain {

enum class ConfigError {
    None,
    BadBeaconId,
    AdvIntervalTooShort,
    WindowTooShort,
    ScanWindowExceedsInterval,
    BadNodeId,
};

// Rules (data-model.md): windowMs >= 250; advIntervalMs >= 100; scanWindowMs <= scanIntervalMs;
// nodeId is 6 hex characters; the beacon role needs a valid 8-hex beaconId.
ConfigError validate(const DeviceConfiguration& config);

// Stable machine-readable code for an error, used in `E` event lines (e.g. "bad_id").
const char* errorCode(ConfigError error);

// Precedence: NVS overrides > build-time defaults.
DeviceConfiguration resolveConfig(const DeviceConfiguration& defaults, const Overrides& overrides);

}  // namespace domain
