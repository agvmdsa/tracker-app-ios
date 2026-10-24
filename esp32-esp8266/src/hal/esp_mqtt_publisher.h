// EspMqttPublisher: the real IMqttPublisher, over Wi-Fi to an MQTT broker (bertmelis/espMqttClient).
// Board-only — not in the native test filter, the same way ble_adapter.cpp and
// http_fingerprint_uploader.cpp aren't; sighting_payload.h's body-building is what gets tested
// on the host.
//
// Wi-Fi is managed independently of HttpFingerprintUploader's own connectWifiIfNeeded() — both
// just check WiFi.status() and call WiFi.begin() if needed, which is safe to call redundantly;
// there's no single shared Wi-Fi manager in this firmware yet, and adding one is more than this
// needs today.
#pragma once

#include <espMqttClient.h>

#include "hal/mqtt_publisher.h"

class EspMqttPublisher final : public IMqttPublisher {
public:
    // Call once at boot. Stays disabled (publishSighting always returns false, and poll() is a
    // no-op) when `host` is empty — same convention as HttpFingerprintUploader's apiHost.
    void begin(const char* wifiSsid, const char* wifiPassword, const char* host, uint16_t port,
               const char* user, const char* password, const char* topicPrefix, const char* nodeId);

    bool publishSighting(const char* node, const char* beacon, int rssi) override;

    // Services the Wi-Fi/MQTT connections and the client's own event loop. Call periodically,
    // e.g. once per main loop iteration.
    void poll();

private:
    void connectWifiIfNeeded();

    espMqttClient client_{espMqttClientTypes::UseInternalTask::NO};
    char wifiSsid_[33] = {0};
    char wifiPassword_[65] = {0};
    char topic_[64] = {0};
    bool enabled_ = false;
};
