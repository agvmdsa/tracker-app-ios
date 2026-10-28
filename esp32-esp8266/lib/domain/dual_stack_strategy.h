// Decides which IP address family to attempt next for the MQTT broker connection, given how long
// the current attempt has run — tries IPv6 first, falls back to IPv4 automatically if it doesn't
// connect within a timeout (the IPv6 "infra failure detection" requirement: the device notices
// the preferred path isn't there and recovers on its own, rather than just failing). Pure logic:
// no Arduino/network headers, so the fallback timing is tested on the host; the actual sockets
// are in hal/esp_mqtt_publisher.cpp.
#pragma once

#include <cstdint>

namespace domain {

enum class AddressFamily { V6, V4 };

class DualStackConnectStrategy {
public:
    // `v6TimeoutMs`: how long to let an IPv6 attempt run before giving up on it for this attempt
    // and switching to IPv4. `hasV6`: false when no IPv6 address is configured at all — then
    // every attempt goes straight to IPv4, skipping the IPv6 phase entirely.
    DualStackConnectStrategy(uint32_t v6TimeoutMs, bool hasV6) : v6TimeoutMs_(v6TimeoutMs), hasV6_(hasV6) {}

    // Call once when starting a fresh connection attempt (e.g. after Wi-Fi comes up, or after a
    // previous connection was lost) — always retries IPv6 first, since a disconnect doesn't mean
    // IPv6 itself is gone, just that this one connection was.
    void reset(uint32_t nowMs) {
        family_ = hasV6_ ? AddressFamily::V6 : AddressFamily::V4;
        attemptStartMs_ = nowMs;
    }

    // Call periodically while not yet connected. Returns the family that should be in use right
    // now; switches itself from V6 to V4 once v6TimeoutMs_ has passed without connecting.
    AddressFamily poll(uint32_t nowMs) {
        if (family_ == AddressFamily::V6 && (nowMs - attemptStartMs_) >= v6TimeoutMs_) {
            family_ = AddressFamily::V4;
            attemptStartMs_ = nowMs;
        }
        return family_;
    }

    AddressFamily currentFamily() const { return family_; }

private:
    uint32_t v6TimeoutMs_;
    bool hasV6_;
    uint32_t attemptStartMs_ = 0;
    AddressFamily family_ = AddressFamily::V4;
};

}  // namespace domain
