// Composition root (task T026): builds the adapters, the supervisor and the serial console.
// All objects have static storage; nothing is allocated in steady state (constitution, V).
//
// Roles are registered in the RoleFactory below as the stories land: the beacon role (US1) and the
// collector role (US2) are in. The Arduino `loopTask` plays the supervisor task, and it also drives
// the collector's RecordTask through the supervisor's tick.
#include <Arduino.h>

#include <cstring>

// A relative path, not a bare "config.h": a bare name is resolved by searching every include
// directory in order, and the toolchain itself ships its own generic config.h (e.g.
// xtensa-esp32-elf/include/config.h) that can be found instead of this project's when
// include/config.h doesn't exist yet — silently turning "you forgot to create config.h" into a
// wall of unrelated "not declared in this scope" errors. The relative path names this exact file,
// so there's nothing else it could ever match.
#if __has_include("../include/config.h")
#include "../include/config.h"
#else
#warning "include/config.h not found: building with config.example.h (placeholders). Copy it to config.h and edit it."
#include "../include/config.example.h"
#endif

#include "app/beacon_role.h"
#include "app/collector_role.h"
#include "app/role_factory.h"
#include "app/rtos_observation_queue.h"
#include "app/serial_task.h"
#include "app/supervisor.h"
#include "device_config.h"
#include "hal/ble_adapter.h"
#include "hal/clock.h"
#include "hal/esp_mqtt_publisher.h"
#include "hal/http_fingerprint_uploader.h"
#include "hal/nvs_store.h"
#include "hal/system_info.h"

namespace {

constexpr const char* kFirmwareVersion = "0.1.0";

void copyString(char* destination, std::size_t capacity, const char* source) {
    std::strncpy(destination, source, capacity - 1);
    destination[capacity - 1] = '\0';
}

domain::DeviceConfiguration buildDefaults() {
    domain::DeviceConfiguration c{};
    c.role = (std::strcmp(DEFAULT_ROLE, "collector") == 0) ? domain::Role::Collector : domain::Role::Beacon;
    copyString(c.beaconId, sizeof(c.beaconId), DEFAULT_BEACON_ID);
    c.advPowerDbm = DEFAULT_ADV_POWER_DBM;
    c.advIntervalMs = DEFAULT_ADV_INTERVAL_MS;
    c.scanIntervalMs = DEFAULT_SCAN_INTERVAL_MS;
    c.scanWindowMs = DEFAULT_SCAN_WINDOW_MS;
    c.windowMs = DEFAULT_WINDOW_MS;
    c.serialBaud = SERIAL_BAUD;
    c.wifiEnabled = ENABLE_WIFI != 0;
    c.mqttEnabled = ENABLE_MQTT != 0;
    copyString(c.wifiSsid, sizeof(c.wifiSsid), WIFI_SSID);
    copyString(c.wifiPassword, sizeof(c.wifiPassword), WIFI_PASSWORD);
    copyString(c.mqttHost, sizeof(c.mqttHost), MQTT_HOST);
    copyString(c.mqttHostV6, sizeof(c.mqttHostV6), MQTT_HOST_V6);
    c.mqttPort = MQTT_PORT;
    copyString(c.mqttUser, sizeof(c.mqttUser), MQTT_USER);
    copyString(c.mqttPassword, sizeof(c.mqttPassword), MQTT_PASSWORD);
    copyString(c.topicPrefix, sizeof(c.topicPrefix), TOPIC_PREFIX);
    copyString(c.nodeId, sizeof(c.nodeId), NODE_ID);
    copyString(c.apiHost, sizeof(c.apiHost), API_HOST);
    copyString(c.apiKey, sizeof(c.apiKey), API_KEY);
    return c;
}

// The roles report events through the supervisor, which is built after them (it needs the role
// factory). This forwarder breaks the cycle: its target is set in setup() before begin().
class EventForwarder final : public IEventSink {
public:
    IEventSink* target = nullptr;
    void event(const char* level, const char* code, const char* message) override {
        if (target != nullptr) target->event(level, code, message);
    }
};

EventForwarder gEvents;
EspClock gClock;
EspSystemInfo gSystem;
NvsStore gStore;
SerialConsole gConsole;
NimBleRadio gRadio;
RtosObservationQueue gObservations;
HttpFingerprintUploader gUploader;
EspMqttPublisher gMqttPublisher(gEvents);
BeaconRole gBeacon(gRadio, gEvents);
CollectorRole gCollector(gRadio, gObservations, gConsole, gUploader, gMqttPublisher, gClock, gEvents);
RoleFactory gRoles(/*beacon=*/&gBeacon, /*collector=*/&gCollector);
Supervisor gSupervisor(buildDefaults(), gStore, gClock, gSystem, gConsole, gConsole, gRoles, kFirmwareVersion);

}  // namespace

void setup() {
    gConsole.begin(SERIAL_BAUD);
    gStore.begin();
    gObservations.begin();
    gEvents.target = &gSupervisor;
    gSupervisor.begin();

    // After begin(), not before: this is the resolved config (defaults + NVS overrides), the
    // same one `status` reports — calling .begin() on these before here would silently ignore
    // wifiEnabled/mqttEnabled and any NVS-saved override of the Wi-Fi/MQTT settings.
    const domain::DeviceConfiguration& config = gSupervisor.config();
    if (config.wifiEnabled) {
        gUploader.begin(config.wifiSsid, config.wifiPassword, config.apiHost, config.apiKey);
        if (config.mqttEnabled) {
            gMqttPublisher.begin(config.wifiSsid, config.wifiPassword, config.mqttHost, config.mqttHostV6,
                                 config.mqttPort, config.mqttUser, config.mqttPassword, config.topicPrefix,
                                 config.nodeId);
        }
    }
}

void loop() {
    gSupervisor.tick();
    // Both are only ever active when wifiEnabled (and, for MQTT, mqttEnabled) were on at boot —
    // a no-op call otherwise, so it's safe to run unconditionally even in the beacon role.
    gUploader.poll();
    gMqttPublisher.poll();
    vTaskDelay(pdMS_TO_TICKS(10));
}
