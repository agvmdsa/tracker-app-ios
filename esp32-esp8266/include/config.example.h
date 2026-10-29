// Example configuration. Copy to config.h (which git ignores) and fill in the real values.
// NEVER commit config.h: it holds credentials. See specs/002-esp32-ble-firmware/contracts/configuration.md.
//
// Precedence at runtime: values written to NVS by serial commands override these defaults.
#pragma once

// Role: "beacon" or "collector".
#define DEFAULT_ROLE "beacon"

// Beacon identifier: exactly 8 hexadecimal characters. Replace per board.
#define DEFAULT_BEACON_ID "00000000"

// Advertising (beacon role). Power is reported at startup; the interval must be >= 100 ms.
#define DEFAULT_ADV_POWER_DBM 0
#define DEFAULT_ADV_INTERVAL_MS 100

// Scanning (collector role). The window must not exceed the interval. Values are to be measured.
#define DEFAULT_SCAN_INTERVAL_MS 100
#define DEFAULT_SCAN_WINDOW_MS 99

// Aggregation window in milliseconds (>= 250).
#define DEFAULT_WINDOW_MS 2000

#define SERIAL_BAUD 115200

// Wi-Fi is used only by the collector (fingerprint uploads, MQTT): a beacon never turns it on,
// even with ENABLE_WIFI 1, so the same config.h serves every board. Set ENABLE_WIFI 0 to collect
// over serial only (Wi-Fi shares the radio with the BLE scan and can lower its rate).
#define ENABLE_WIFI 1
#define ENABLE_MQTT 0

// Wi-Fi credentials: the lab network (2.4 GHz; the ESP32 has no 5 GHz).
#define WIFI_SSID "uaifai-brum"
#define WIFI_PASSWORD "bemvindoaocesar"

// MQTT broker (placeholders).
#define MQTT_HOST "192.168.0.10"
// IPv6 literal of the same broker (e.g. "2001:db8::10") — tried first, falls back to MQTT_HOST
// (IPv4) on its own after a few seconds if it doesn't connect. Leave empty to skip IPv6
// entirely and go straight to IPv4, same as before this existed. Only matters with ENABLE_MQTT
// 1; unverified on real hardware — there's no board/IPv6-capable network to test it against, see
// src/hal/esp_mqtt_publisher.h.
#define MQTT_HOST_V6 ""
#define MQTT_PORT 1883
#define MQTT_USER ""
#define MQTT_PASSWORD ""
#define TOPIC_PREFIX "tracker"

// Positioning API (placeholders). Uploads labelled recordings to POST /fingerprints for
// training; needs ENABLE_WIFI 1 too. Leave API_HOST empty to disable uploading (e.g. while
// collecting over serial only, to protect the scan rate, same as ENABLE_WIFI 0 above).
#define API_HOST "tracker-app-ios.onrender.com"
#define API_KEY "replace-with-the-render-api-key-value"

// Node identifier: 6 hexadecimal characters. Leave empty to derive it from the board's unique ID.
#define NODE_ID ""
