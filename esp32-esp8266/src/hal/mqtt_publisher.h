// Output channel for live sighting telemetry over MQTT (Bridge pattern, alongside IPublisher's
// serial lines and IFingerprintUploader's HTTP batch uploads). This is the collector's
// continuous, unlabelled RSSI stream — every observation, idle or recording alike — as opposed
// to POST /fingerprints, which only sends a labelled recording's rows, once, after it finishes.
// Implementations must never block the caller.
#pragma once

class IMqttPublisher {
public:
    virtual ~IMqttPublisher() = default;
    // Publishes one sighting. Returns false when it couldn't (not configured, not connected) —
    // the caller doesn't retry, same drop-and-count policy as the rest of this firmware.
    virtual bool publishSighting(const char* node, const char* beacon, int rssi) = 0;
};
