#include "nvs_store.h"

namespace {
constexpr const char* kNamespace = "tracker";
constexpr const char* kBoots = "boots";
constexpr const char* kRole = "role";
constexpr const char* kId = "id";
constexpr const char* kPower = "pwr";
constexpr const char* kAdvInterval = "adv";
constexpr const char* kWindow = "win";
}  // namespace

bool NvsStore::begin() {
    open_ = prefs_.begin(kNamespace, /*readOnly=*/false);
    return open_;
}

uint32_t NvsStore::incrementBootCount() {
    if (!open_) return 0;
    const uint32_t next = prefs_.getUInt(kBoots, 0) + 1;
    prefs_.putUInt(kBoots, next);
    return next;
}

domain::Overrides NvsStore::loadOverrides() {
    domain::Overrides ov{};
    if (!open_) return ov;
    if (prefs_.isKey(kRole)) {
        ov.hasRole = true;
        ov.role = (prefs_.getUChar(kRole, 0) == 1) ? domain::Role::Collector : domain::Role::Beacon;
    }
    if (prefs_.isKey(kId)) {
        ov.hasBeaconId = prefs_.getString(kId, ov.beaconId, sizeof(ov.beaconId)) > 0;
    }
    if (prefs_.isKey(kPower)) {
        ov.hasAdvPowerDbm = true;
        ov.advPowerDbm = prefs_.getInt(kPower, 0);
    }
    if (prefs_.isKey(kAdvInterval)) {
        ov.hasAdvIntervalMs = true;
        ov.advIntervalMs = prefs_.getUInt(kAdvInterval, 0);
    }
    if (prefs_.isKey(kWindow)) {
        ov.hasWindowMs = true;
        ov.windowMs = prefs_.getUInt(kWindow, 0);
    }
    return ov;
}

bool NvsStore::saveOverrides(const domain::Overrides& ov) {
    if (!open_) return false;
    if (ov.hasRole) prefs_.putUChar(kRole, ov.role == domain::Role::Collector ? 1 : 0);
    else prefs_.remove(kRole);
    if (ov.hasBeaconId) prefs_.putString(kId, ov.beaconId);
    else prefs_.remove(kId);
    if (ov.hasAdvPowerDbm) prefs_.putInt(kPower, ov.advPowerDbm);
    else prefs_.remove(kPower);
    if (ov.hasAdvIntervalMs) prefs_.putUInt(kAdvInterval, ov.advIntervalMs);
    else prefs_.remove(kAdvInterval);
    if (ov.hasWindowMs) prefs_.putUInt(kWindow, ov.windowMs);
    else prefs_.remove(kWindow);
    return true;
}

void NvsStore::clearOverrides() {
    if (!open_) return;
    prefs_.remove(kRole);
    prefs_.remove(kId);
    prefs_.remove(kPower);
    prefs_.remove(kAdvInterval);
    prefs_.remove(kWindow);
    // The restart counter (boots) is deliberately kept.
}
