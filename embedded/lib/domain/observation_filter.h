// Observation filter (contracts/ble-advertising.md, "Collector role: what the ESP32 accepts").
//
// An advertisement becomes an Observation only if both hold: the project's service UUID is present
// and the local name is exactly 8 hexadecimal characters. The identity is the ID in the name, never
// the sender's address, and the RSSI is passed through as received.
//
// The filter reads the raw payload (a sequence of AD structures: length, type, data) instead of
// asking the BLE library for a name string, so it never allocates and is safe to run inside the
// radio callback (constitution, Principle V). Pure logic: no Arduino headers.
#pragma once

#include <cstddef>
#include <cstdint>

#include "beacon_id.h"

namespace domain {

// The service UUID fc979812-36dd-4722-9316-d2a7908888dd as it appears in an advertisement
// (least significant byte first).
extern const uint8_t kProjectServiceUuid[16];

// One received advertisement of the project's protocol. The collector adds the boot number, the
// node and the label when it prints the record.
struct Observation {
    uint32_t tMs = 0;  // monotonic milliseconds when it was received
    BeaconId beacon{};
    int8_t rssi = 0;  // dBm, as received
};

// `payload` is the advertisement, followed by the scan response when the scanner merged one.
// Returns true and fills `out` only for an advertisement of the protocol; otherwise `out` is
// left unchanged. A value that does not fit an RSSI (outside -128..127) is refused, not wrapped.
bool observeAdvertisement(const uint8_t* payload, std::size_t length, int rssi, uint32_t tMs, Observation& out);

}  // namespace domain
