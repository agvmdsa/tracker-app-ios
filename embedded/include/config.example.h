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

// Network features are off by default (the collector runs with Wi-Fi off to protect the scan rate).
#define ENABLE_WIFI 0
#define ENABLE_MQTT 0

// Wi-Fi credentials (placeholders).
#define WIFI_SSID "your-network"
#define WIFI_PASSWORD "your-password"

// MQTT broker (placeholders).
#define MQTT_HOST "192.168.0.10"
#define MQTT_PORT 1883
#define MQTT_USER ""
#define MQTT_PASSWORD ""
#define TOPIC_PREFIX "tracker"

// Positioning API (placeholders). Uploads labelled recordings to POST /fingerprints for
// training; needs ENABLE_WIFI 1 too. Leave API_HOST empty to disable uploading (e.g. while
// collecting over serial only, to protect the scan rate, same as ENABLE_WIFI 0 above).
#define API_HOST "api-tracker-app.onrender.com"
#define API_KEY "replace-with-the-render-api-key-value"

// Node identifier: 6 hexadecimal characters. Leave empty to derive it from the board's unique ID.
#define NODE_ID ""
