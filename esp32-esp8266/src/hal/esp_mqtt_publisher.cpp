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

void EspMqttPublisher::begin(const char* wifiSsid, const char* wifiPassword, const char* host, uint16_t port,
                             const char* user, const char* password, const char* topicPrefix,
                             const char* nodeId) {
    copyString(wifiSsid_, sizeof(wifiSsid_), wifiSsid);
    copyString(wifiPassword_, sizeof(wifiPassword_), wifiPassword);
    enabled_ = host[0] != '\0';
    if (!enabled_) return;

    std::snprintf(topic_, sizeof(topic_), "%s/%s/sightings", topicPrefix, nodeId);
    client_.setServer(host, port);
    if (user[0] != '\0') client_.setCredentials(user, password);
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
    if (WiFi.status() == WL_CONNECTED && client_.disconnected()) {
        client_.connect();
    }
}

void EspMqttPublisher::connectWifiIfNeeded() {
    if (WiFi.status() == WL_CONNECTED) return;
    if (WiFi.status() != WL_DISCONNECTED) return;  // already trying
    WiFi.begin(wifiSsid_, wifiPassword_);
}
