// DeviceConfiguration and the NVS overrides (data-model.md, contracts/configuration.md).
// Pure logic: no Arduino headers.
#pragma once

#include <cstdint>

namespace domain {

enum class Role { Beacon, Collector };

struct DeviceConfiguration {
    Role role = Role::Beacon;
    char beaconId[9] = {0};  // beacon role: 8 hex characters
    int advPowerDbm = 0;
    uint32_t advIntervalMs = 100;
    uint32_t scanIntervalMs = 100;
    uint32_t scanWindowMs = 99;
    uint32_t windowMs = 2000;
    uint32_t serialBaud = 115200;
    bool wifiEnabled = false;
    bool mqttEnabled = false;
    char wifiSsid[33] = {0};
    char wifiPassword[65] = {0};
    char mqttHost[64] = {0};
    // IPv6 literal address of the same broker, e.g. "2001:db8::10". Empty = IPv6 not attempted,
    // every connection goes straight to mqttHost (IPv4) — see dual_stack_strategy.h.
    char mqttHostV6[46] = {0};
    uint16_t mqttPort = 1883;
    char mqttUser[32] = {0};
    char mqttPassword[64] = {0};
    char topicPrefix[16] = {'t', 'r', 'a', 'c', 'k', 'e', 'r', 0};
    // Fingerprint upload (POST /fingerprints): on only when wifiEnabled AND apiHost is non-empty.
    // Needs its own Wi-Fi, same tradeoff as mqttEnabled — see config.example.h.
    char apiHost[64] = {0};
    char apiKey[64] = {0};
    char nodeId[7] = {0};  // 6 hex characters
    // Number of this boot (the NVS counter). Not a setting and never persisted: the supervisor
    // fills it in before starting the role, so every printed line can say which boot it belongs to.
    uint32_t bootId = 0;
};

// Values written to NVS by serial commands. Each `has*` flag says whether the override exists.
struct Overrides {
    bool hasRole = false;
    Role role = Role::Beacon;
    bool hasBeaconId = false;
    char beaconId[9] = {0};
    bool hasAdvPowerDbm = false;
    int advPowerDbm = 0;
    bool hasAdvIntervalMs = false;
    uint32_t advIntervalMs = 0;
    bool hasWindowMs = false;
    uint32_t windowMs = 0;
};

}  // namespace domain
