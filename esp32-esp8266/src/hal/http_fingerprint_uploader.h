// HttpFingerprintUploader: the real IFingerprintUploader, over Wi-Fi/HTTPS to the positioning
// API's POST /fingerprints. Board-only (Arduino WiFi.h/HTTPClient.h) — not in the native test
// filter, the same way ble_adapter.cpp isn't; `fingerprint_payload.h`'s body-building is what
// gets tested on the host.
//
// queueRow() only ever copies into the internal buffer — never blocks, same contract as
// IFingerprintUploader promises. poll() does the Wi-Fi connect and the HTTP request, so it can
// block for the duration of one HTTPS call when a batch is due; call it from the main loop
// alongside the supervisor's tick, not from a latency-sensitive context. A batch that fails to
// upload (Wi-Fi down, the API unreachable, a non-2xx response) is dropped, not retried — simpler,
// at the cost of losing that batch's rows. It reports what happens as `E` lines (the Wi-Fi link
// with its IP, a network not found, a wrong password, a lost connection; the first upload that
// worked, a failed one with its HTTP code) and answers the `# status net` line, through
// wifi_status.h, which decides what is worth a line.
#pragma once

#include <cstddef>
#include <cstdint>

#include "bounded_queue.h"
#include "fingerprint_payload.h"
#include "app/events.h"
#include "hal/fingerprint_uploader.h"
#include "hal/network_status.h"
#include "wifi_status.h"

class HttpFingerprintUploader final : public IFingerprintUploader, public INetworkStatus {
public:
    explicit HttpFingerprintUploader(IEventSink& events) : events_(events) {}

    // Call once at boot. Uploading stays disabled (every queueRow() returns false, dropped and
    // counted) when `apiHost` is empty — same convention as wifiEnabled/mqttEnabled in
    // device_config.h.
    void begin(const char* wifiSsid, const char* wifiPassword, const char* apiHost, const char* apiKey);

    bool queueRow(const domain::RawRecord& record) override;

    // Reports Wi-Fi changes, connects (and retries every few seconds after a failure) and flushes a
    // batch when one is due (queue full, or the flush interval elapsed with rows waiting). Call
    // periodically, e.g. once per main loop iteration.
    void poll();

    int describe(char* buf, std::size_t cap) const override;

    uint32_t dropped() const { return queue_.dropped(); }
    uint32_t uploaded() const { return uploads_.uploadedRows(); }
    uint32_t failedBatches() const { return uploads_.failedBatches(); }

private:
    struct StoredRow {
        uint32_t tMs = 0;
        uint32_t boot = 0;
        char node[7] = {0};
        char beacon[9] = {0};
        int rssi = 0;
        char session[17] = {0};
        char point[17] = {0};
        float x = 0.0f;
        float y = 0.0f;
    };

    static constexpr std::size_t kCapacity = 64;
    static constexpr std::size_t kBatchSize = 20;
    static constexpr unsigned long kFlushIntervalMs = 5000;
    static constexpr std::size_t kBodyCapacity = 4096;
    // How long to wait before trying again after a network not found, a refused password or a
    // lost connection (the radio does not always retry on its own after those).
    static constexpr unsigned long kRetryIntervalMs = 10000;

    void trackWifi();
    void connectWifiIfNeeded();
    bool dueToFlush() const;
    void flush();

    domain::BoundedQueue<StoredRow, kCapacity> queue_;
    char wifiSsid_[33] = {0};
    char wifiPassword_[65] = {0};
    char apiHost_[64] = {0};
    char apiKey_[64] = {0};
    IEventSink& events_;
    bool wifiConfigured_ = false;
    bool enabled_ = false;
    unsigned long lastFlushMs_ = 0;
    unsigned long lastAttemptMs_ = 0;
    domain::WifiStatusTracker wifi_;
    domain::UploadStatusTracker uploads_;
};
