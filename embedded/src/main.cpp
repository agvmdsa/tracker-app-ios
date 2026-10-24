// Composition root (task T026): builds the adapters, the supervisor and the serial console.
// All objects have static storage; nothing is allocated in steady state (constitution, V).
//
// Roles are registered in the RoleFactory below as the stories land: the beacon role (US1) and the
// collector role (US2) are in. The Arduino `loopTask` plays the supervisor task, and it also drives
// the collector's RecordTask through the supervisor's tick.
#include <Arduino.h>

#include <cstring>

#if __has_include("config.h")
#include "config.h"
#else
#warning "include/config.h not found: building with config.example.h (placeholders). Copy it to config.h and edit it."
#include "config.example.h"
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
BeaconRole gBeacon(gRadio, gEvents);
CollectorRole gCollector(gRadio, gObservations, gConsole, gUploader, gClock, gEvents);
RoleFactory gRoles(/*beacon=*/&gBeacon, /*collector=*/&gCollector);
Supervisor gSupervisor(buildDefaults(), gStore, gClock, gSystem, gConsole, gConsole, gRoles, kFirmwareVersion);

}  // namespace

void setup() {
    gConsole.begin(SERIAL_BAUD);
    gStore.begin();
    gObservations.begin();
    gUploader.begin(WIFI_SSID, WIFI_PASSWORD, API_HOST, API_KEY);
    gEvents.target = &gSupervisor;
    gSupervisor.begin();
}

void loop() {
    gSupervisor.tick();
    // Only ever does Wi-Fi/HTTP work when the collector role actually queued rows (see
    // HttpFingerprintUploader::poll) — a no-op call otherwise, so it's safe to run unconditionally
    // even in the beacon role or with uploading disabled.
    gUploader.poll();
    vTaskDelay(pdMS_TO_TICKS(10));
}
