// EspMqttPublisher: the real IMqttPublisher, over Wi-Fi to an MQTT broker (bertmelis/espMqttClient).
// Board-only — not in the native test filter, the same way ble_adapter.cpp and
// http_fingerprint_uploader.cpp aren't; sighting_payload.h's body-building and
// dual_stack_strategy.h's fallback timing are what get tested on the host.
//
// Wi-Fi is managed independently of HttpFingerprintUploader's own connectWifiIfNeeded() — both
// just check WiFi.status() and call WiFi.begin() if needed, which is safe to call redundantly;
// there's no single shared Wi-Fi manager in this firmware yet, and adding one is more than this
// needs today.
//
// IPv6-first with an IPv4 fallback (dual_stack_strategy.h): if `hostV6` is set, every fresh
// connection attempt tries it first and switches to `host` (IPv4) on its own if IPv6 doesn't
// connect within the timeout — this can only be exercised on real hardware on a network that
// actually offers IPv6; it hasn't been (there's no board or IPv6-capable network available to
// validate it against), so treat the IPv6 half as unverified beyond "it compiles and the
// fallback *timing* logic is tested" until someone checks it on the real deployment network.
#pragma once

#include <espMqttClient.h>

#include "../app/events.h"
#include "dual_stack_strategy.h"
#include "hal/mqtt_publisher.h"

class EspMqttPublisher final : public IMqttPublisher {
public:
    explicit EspMqttPublisher(IEventSink& events) : events_(events) {}

    // Call once at boot. Stays disabled (publishSighting always returns false, and poll() is a
    // no-op) when `host` is empty — same convention as HttpFingerprintUploader's apiHost.
    // `hostV6` may be empty: then every attempt goes straight to IPv4, same as before this
    // existed.
    void begin(const char* wifiSsid, const char* wifiPassword, const char* host, const char* hostV6,
               uint16_t port, const char* user, const char* password, const char* topicPrefix,
               const char* nodeId);

    bool publishSighting(const char* node, const char* beacon, int rssi) override;

    // Services the Wi-Fi/MQTT connections and the client's own event loop. Call periodically,
    // e.g. once per main loop iteration.
    void poll();

private:
    void connectWifiIfNeeded();
    void connectToCurrentFamily();

    IEventSink& events_;
    espMqttClient client_{espMqttClientTypes::UseInternalTask::NO};
    domain::DualStackConnectStrategy strategy_{5000, false};
    domain::AddressFamily connectingFamily_ = domain::AddressFamily::V4;
    bool wifiWasConnected_ = false;
    bool mqttWasConnected_ = false;
    char wifiSsid_[33] = {0};
    char wifiPassword_[65] = {0};
    char host_[64] = {0};
    char hostV6_[46] = {0};
    uint16_t port_ = 1883;
    char topic_[64] = {0};
    bool enabled_ = false;
};
