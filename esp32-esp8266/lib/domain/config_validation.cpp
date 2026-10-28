#include "config_validation.h"

#include <cstring>

#include "beacon_id.h"

namespace domain {

namespace {
constexpr uint32_t kMinAdvIntervalMs = 100;
constexpr uint32_t kMinWindowMs = 250;
constexpr unsigned kNodeIdLength = 6;
}  // namespace

ConfigError validate(const DeviceConfiguration& config) {
    if (config.role == Role::Beacon) {
        BeaconId ignored;
        if (!parseBeaconId(config.beaconId, ignored)) return ConfigError::BadBeaconId;
    }
    if (config.advIntervalMs < kMinAdvIntervalMs) return ConfigError::AdvIntervalTooShort;
    if (config.windowMs < kMinWindowMs) return ConfigError::WindowTooShort;
    if (config.scanWindowMs > config.scanIntervalMs) return ConfigError::ScanWindowExceedsInterval;
    if (!isHexOfLength(config.nodeId, kNodeIdLength)) return ConfigError::BadNodeId;
    return ConfigError::None;
}

const char* errorCode(ConfigError error) {
    switch (error) {
        case ConfigError::None: return "ok";
        case ConfigError::BadBeaconId: return "bad_id";
        case ConfigError::AdvIntervalTooShort: return "adv_interval_too_short";
        case ConfigError::WindowTooShort: return "window_too_short";
        case ConfigError::ScanWindowExceedsInterval: return "scan_window_exceeds_interval";
        case ConfigError::BadNodeId: return "bad_node_id";
    }
    return "unknown";
}

DeviceConfiguration resolveConfig(const DeviceConfiguration& defaults, const Overrides& overrides) {
    DeviceConfiguration resolved = defaults;
    if (overrides.hasRole) resolved.role = overrides.role;
    if (overrides.hasBeaconId) {
        std::strncpy(resolved.beaconId, overrides.beaconId, sizeof(resolved.beaconId) - 1);
        resolved.beaconId[sizeof(resolved.beaconId) - 1] = '\0';
    }
    if (overrides.hasAdvPowerDbm) resolved.advPowerDbm = overrides.advPowerDbm;
    if (overrides.hasAdvIntervalMs) resolved.advIntervalMs = overrides.advIntervalMs;
    if (overrides.hasWindowMs) resolved.windowMs = overrides.windowMs;
    return resolved;
}

}  // namespace domain
