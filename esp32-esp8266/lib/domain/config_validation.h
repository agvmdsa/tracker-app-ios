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

// Whether the board turns Wi-Fi on: only the collector uses it (fingerprint uploads, MQTT), so a
// beacon never does, even when built with wifiEnabled. One config.h then serves every board, and a
// beacon's radio stays for BLE alone (Wi-Fi shared it, and its current peak set off the brownout
// detector). The role applies after a reboot, so this is decided once, at boot.
bool usesWifi(const DeviceConfiguration& config);
// MQTT rides on that Wi-Fi: the collector, with both wifiEnabled and mqttEnabled.
bool usesMqtt(const DeviceConfiguration& config);

}  // namespace domain
