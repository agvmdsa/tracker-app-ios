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
    if (!enabled_) return;
    connectWifiIfNeeded();
    if (WiFi.status() != WL_CONNECTED) return;
    if (dueToFlush()) {
        flush();
        lastFlushMs_ = millis();
    }
}

void HttpFingerprintUploader::connectWifiIfNeeded() {
    if (WiFi.status() == WL_CONNECTED) return;
    if (WiFi.status() != WL_DISCONNECTED) return;  // already trying
    WiFi.begin(wifiSsid_, wifiPassword_);
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
    if (domain::formatFingerprintBatch(body, sizeof(body), records, n, "ESP32") < 0) {
        ++failedBatches_;
        return;
    }

    HTTPClient http;
    const String url = String("https://") + apiHost_ + "/fingerprints";
    if (!http.begin(url)) {
        ++failedBatches_;
        return;
    }
    http.addHeader("Content-Type", "application/json");
    http.addHeader("X-API-Key", apiKey_);
    const int status = http.POST(reinterpret_cast<uint8_t*>(body), std::strlen(body));
    http.end();

    if (status >= 200 && status < 300) {
        uploaded_ += static_cast<uint32_t>(n);
    } else {
        ++failedBatches_;
    }
}
