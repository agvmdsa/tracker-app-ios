// Builds the JSON body for one live sighting published over MQTT — the ESP32 collector's
// continuous RSSI telemetry (every observation, idle or recording alike), as opposed to
// POST /fingerprints' one-shot batch upload after a finished recording. Field names match the
// API's SightingIn shape; only the fields the ESP32 actually has (no seenAt/sampleCount/etc,
// which are phone-only or need a wall clock the ESP32 doesn't have — the API falls back to its
// own received-at time when seenAt is absent). Pure logic: no Arduino headers, tested on the
// host.
#pragma once

#include <cstddef>

namespace domain {

// Returns the number of characters written, or -1 when it doesn't fit `cap` (same contract as
// fingerprint_payload.h/record_format.h).
int formatSightingPayload(char* buf, std::size_t cap, const char* node, const char* beacon, int rssi);

}  // namespace domain
