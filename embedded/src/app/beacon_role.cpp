#include "beacon_role.h"

#include <cstdio>
#include <cstring>

#include "beacon_id.h"

namespace {
constexpr uint32_t kMinAdvIntervalMs = 100;
}

bool BeaconRole::start(const domain::DeviceConfiguration& config) {
    domain::BeaconId id;
    if (!domain::parseBeaconId(config.beaconId, id)) {
        events_.event("error", "bad_id", "beacon id must be 8 hexadecimal characters; not advertising");
        return false;
    }
    if (config.advIntervalMs < kMinAdvIntervalMs) {
        events_.event("error", "adv_interval_too_short", "advertising interval must be at least 100 ms; not advertising");
        return false;
    }

    AdvertisingParams params{};
    std::strncpy(params.beaconId, id.value, sizeof(params.beaconId) - 1);
    params.powerDbm = config.advPowerDbm;
    params.intervalMs = config.advIntervalMs;
    if (!radio_.startAdvertising(params)) {
        events_.event("error", "radio_start_failed", "could not start BLE advertising");
        return false;
    }

    char message[96];
    std::snprintf(message, sizeof(message), "id=%s power=%d interval=%lu packet=%u", params.beaconId, params.powerDbm,
                  static_cast<unsigned long>(params.intervalMs), static_cast<unsigned>(radio_.advertisingPayloadBytes()));
    events_.event("info", "advertising", message);
    return true;
}

void BeaconRole::stop() { radio_.stopAdvertising(); }
