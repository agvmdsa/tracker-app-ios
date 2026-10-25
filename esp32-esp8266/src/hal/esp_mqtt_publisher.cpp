#include "hal/esp_mqtt_publisher.h"

#include <WiFi.h>

#include <cstring>

#include "sighting_payload.h"

namespace {

void copyString(char* destination, std::size_t capacity, const char* source) {
    std::strncpy(destination, source, capacity - 1);
    destination[capacity - 1] = '\0';
}

}  // namespace

void EspMqttPublisher::begin(const char* wifiSsid, const char* wifiPassword, const char* host,
                             const char* hostV6, uint16_t port, const char* user, const char* password,
                             const char* topicPrefix, const char* nodeId) {
    copyString(wifiSsid_, sizeof(wifiSsid_), wifiSsid);
    copyString(wifiPassword_, sizeof(wifiPassword_), wifiPassword);
    enabled_ = host[0] != '\0';
    if (!enabled_) return;

    copyString(host_, sizeof(host_), host);
    copyString(hostV6_, sizeof(hostV6_), hostV6);
    port_ = port;
    strategy_ = domain::DualStackConnectStrategy(5000, hostV6_[0] != '\0');
    std::snprintf(topic_, sizeof(topic_), "%s/%s/sightings", topicPrefix, nodeId);
    if (user[0] != '\0') client_.setCredentials(user, password);
    if (hostV6_[0] != '\0') {
        WiFi.enableIPv6();
        events_.event("info", "mqtt_dual_stack", "IPv6 configured, will be tried before IPv4 on each connection attempt");
    }
}

bool EspMqttPublisher::publishSighting(const char* node, const char* beacon, int rssi) {
    if (!enabled_ || !client_.connected()) return false;
    char body[128];
    if (domain::formatSightingPayload(body, sizeof(body), node, beacon, rssi) < 0) return false;
    return client_.publish(topic_, 0, false, body) != 0;
}

void EspMqttPublisher::poll() {
    if (!enabled_) return;

    connectWifiIfNeeded();
    client_.loop();

    const bool wifiConnected = WiFi.status() == WL_CONNECTED;
    if (!wifiConnected) {
        wifiWasConnected_ = false;
        return;
    }
    if (!wifiWasConnected_) {
        // Wi-Fi just came up: start a fresh attempt, trying IPv6 first again if it's configured
        // — the timeout clock starts now, not whenever Wi-Fi itself happened to begin connecting.
        wifiWasConnected_ = true;
        strategy_.reset(millis());
        connectingFamily_ = strategy_.currentFamily();
        connectToCurrentFamily();
        return;
    }

    if (client_.connected()) {
        if (!mqttWasConnected_) {
            mqttWasConnected_ = true;
            events_.event("info", "mqtt_connected",
                          connectingFamily_ == domain::AddressFamily::V6 ? "connected over IPv6" : "connected over IPv4");
        }
        return;
    }
    mqttWasConnected_ = false;

    const domain::AddressFamily wanted = strategy_.poll(millis());
    if (wanted != connectingFamily_) {
        connectingFamily_ = wanted;
        if (wanted == domain::AddressFamily::V4) {
            events_.event("warn", "mqtt_ipv6_fallback", "IPv6 did not connect in time, falling back to IPv4");
        }
        connectToCurrentFamily();
    } else if (client_.disconnected()) {
        connectToCurrentFamily();
    }
}

void EspMqttPublisher::connectToCurrentFamily() {
    const char* host = connectingFamily_ == domain::AddressFamily::V6 ? hostV6_ : host_;
    client_.setServer(host, port_);
    client_.connect();
}

void EspMqttPublisher::connectWifiIfNeeded() {
    if (WiFi.status() == WL_CONNECTED) return;
    if (WiFi.status() != WL_DISCONNECTED) return;  // already trying
    WiFi.begin(wifiSsid_, wifiPassword_);
}
