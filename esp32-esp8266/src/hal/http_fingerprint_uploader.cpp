#include "hal/http_fingerprint_uploader.h"

#include <HTTPClient.h>
#include <WiFi.h>

#include <cstring>

namespace {

void copyString(char* destination, std::size_t capacity, const char* source) {
    std::strncpy(destination, source, capacity - 1);
    destination[capacity - 1] = '\0';
}

}  // namespace

void HttpFingerprintUploader::begin(const char* wifiSsid, const char* wifiPassword, const char* apiHost,
                                    const char* apiKey) {
    copyString(wifiSsid_, sizeof(wifiSsid_), wifiSsid);
    copyString(wifiPassword_, sizeof(wifiPassword_), wifiPassword);
    copyString(apiHost_, sizeof(apiHost_), apiHost);
    copyString(apiKey_, sizeof(apiKey_), apiKey);
    wifiConfigured_ = true;
    enabled_ = apiHost[0] != '\0';
    lastFlushMs_ = millis();
}

bool HttpFingerprintUploader::queueRow(const domain::RawRecord& record) {
    if (!enabled_) return false;
    StoredRow row;
    row.tMs = record.tMs;
    row.boot = record.boot;
    copyString(row.node, sizeof(row.node), record.node);
    copyString(row.beacon, sizeof(row.beacon), record.beacon);
    row.rssi = record.rssi;
    copyString(row.session, sizeof(row.session), record.session);
    copyString(row.point, sizeof(row.point), record.point);
    row.x = record.x;
    row.y = record.y;
    return queue_.push(row);
}

void HttpFingerprintUploader::poll() {
    if (wifiConfigured_) trackWifi();
    if (!enabled_) return;
    connectWifiIfNeeded();
    if (WiFi.status() != WL_CONNECTED) return;
    if (dueToFlush()) {
        flush();
        lastFlushMs_ = millis();
    }
}

void HttpFingerprintUploader::trackWifi() {
    domain::WifiLink link = domain::WifiLink::Connecting;
    switch (WiFi.status()) {
        case WL_CONNECTED: link = domain::WifiLink::Connected; break;
        case WL_NO_SSID_AVAIL: link = domain::WifiLink::NoNetwork; break;
        case WL_CONNECT_FAILED: link = domain::WifiLink::AuthFailed; break;
        case WL_CONNECTION_LOST: link = domain::WifiLink::Lost; break;
        default: link = domain::WifiLink::Connecting; break;
    }
    const String ip = link == domain::WifiLink::Connected ? WiFi.localIP().toString() : String();
    domain::NetworkEvent event;
    if (wifi_.update(link, ip.c_str(), event)) events_.event(event.level, event.code, event.message);
}

void HttpFingerprintUploader::connectWifiIfNeeded() {
    const wl_status_t status = WiFi.status();
    if (status == WL_CONNECTED) return;
    const unsigned long now = millis();
    if (status == WL_STOPPED || status == WL_NO_SHIELD || status == WL_DISCONNECTED || status == WL_IDLE_STATUS) {
        // Not trying: the station starts as WL_STOPPED until the first WiFi.begin() (waiting for
        // WL_DISCONNECTED alone, as before, never connected), or the radio dropped back to idle.
        if (lastAttemptMs_ != 0 && now - lastAttemptMs_ < kRetryIntervalMs) return;
    } else if (status == WL_NO_SSID_AVAIL || status == WL_CONNECT_FAILED || status == WL_CONNECTION_LOST) {
        // A failed attempt: try again, but not on every loop.
        if (now - lastAttemptMs_ < kRetryIntervalMs) return;
        WiFi.disconnect();
    } else {
        return;  // already trying
    }
    lastAttemptMs_ = now;
    WiFi.begin(wifiSsid_, wifiPassword_);
}

int HttpFingerprintUploader::describe(char* buf, std::size_t cap) const {
    return domain::formatNetworkStatus(buf, cap, wifiConfigured_ ? wifi_.link() : domain::WifiLink::Off, wifi_.ip(),
                                       uploads_.uploadedRows(), uploads_.failedBatches(), queue_.dropped());
}

bool HttpFingerprintUploader::dueToFlush() const {
    if (queue_.size() == 0) return false;
    if (queue_.size() >= kBatchSize) return true;
    return (millis() - lastFlushMs_) >= kFlushIntervalMs;
}

void HttpFingerprintUploader::flush() {
    StoredRow rows[kBatchSize];
    domain::RawRecord records[kBatchSize];
    std::size_t n = 0;
    while (n < kBatchSize && queue_.pop(rows[n])) {
        records[n] = domain::RawRecord{rows[n].tMs,    rows[n].boot,    rows[n].node, rows[n].beacon,
                                       rows[n].rssi,   rows[n].session, rows[n].point, rows[n].x,
                                       rows[n].y};
        ++n;
    }
    if (n == 0) return;

    char body[kBodyCapacity];
    domain::NetworkEvent event;
    if (domain::formatFingerprintBatch(body, sizeof(body), records, n, "ESP32") < 0) {
        events_.event("error", "upload_body_too_large", "a batch did not fit the request body; dropped");
        return;
    }

    HTTPClient http;
    const String url = String("https://") + apiHost_ + "/fingerprints";
    if (!http.begin(url)) {
        if (uploads_.onBatch(-1, n, event)) events_.event(event.level, event.code, event.message);
        return;
    }
    http.addHeader("Content-Type", "application/json");
    http.addHeader("X-API-Key", apiKey_);
    const int status = http.POST(reinterpret_cast<uint8_t*>(body), std::strlen(body));
    http.end();

    if (uploads_.onBatch(status, n, event)) events_.event(event.level, event.code, event.message);
}
