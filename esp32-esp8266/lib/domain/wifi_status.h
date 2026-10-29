// What the collector says about its Wi-Fi link and its uploads to the positioning API: which
// changes become an `E` line (connected with the IP, network not found, wrong password, lost;
// the first upload that worked, a failed one with its HTTP code) and the text of the `# status net`
// line. Only changes are reported, so a network that stays down, or an API that keeps refusing,
// prints one line and not one per attempt. Pure logic: no Arduino headers, tested on the host; the
// board side (HttpFingerprintUploader) maps WiFi.status() and the HTTP code onto this.
#pragma once

#include <cstddef>
#include <cstdint>

namespace domain {

enum class WifiLink {
    Off,         // Wi-Fi disabled in the configuration
    Connecting,  // trying, no answer yet
    Connected,
    NoNetwork,   // the SSID was not found
    AuthFailed,  // the network refused the password
    Lost,        // was connected, dropped
};

// "off", "connecting", "connected", "no_network", "auth_failed", "lost".
const char* wifiLinkName(WifiLink link);

// One event to report, in IEventSink's terms. `message` is owned by the event.
struct NetworkEvent {
    const char* level = "";
    const char* code = "";
    char message[96] = {0};
};

class WifiStatusTracker {
public:
    // Records the link as the radio reports it now. Returns true, filling `event`, when the link
    // changed to something worth telling: connecting itself is silent (it is the normal state
    // right after boot); the IP of a connection is reported once.
    bool update(WifiLink link, const char* ip, NetworkEvent& event);

    WifiLink link() const { return link_; }
    // The IP of the current connection, "" when not connected.
    const char* ip() const { return ip_; }

private:
    WifiLink link_ = WifiLink::Connecting;
    char ip_[40] = {0};
};

class UploadStatusTracker {
public:
    // Records one batch POSTed to /fingerprints: `httpStatus` as HTTPClient returns it (2xx
    // success, other HTTP codes refused, negative when there was no connection to the API).
    // Returns true, filling `event`, when the result differs from the previous batch's: the first
    // success, a failure, a different failure code, or a recovery.
    bool onBatch(int httpStatus, std::size_t rows, NetworkEvent& event);

    uint32_t uploadedRows() const { return uploadedRows_; }
    uint32_t failedBatches() const { return failedBatches_; }

private:
    bool hasLast_ = false;
    int lastStatus_ = 0;
    uint32_t uploadedRows_ = 0;
    uint32_t failedBatches_ = 0;
};

// "wifi=<link> ip=<ip or -> uploaded=<rows> failed=<batches> dropped=<rows>", for the
// `# status net` line. Returns the number of characters written, or -1 when it doesn't fit `cap`
// (same contract as record_format.h).
int formatNetworkStatus(char* buf, std::size_t cap, WifiLink link, const char* ip, uint32_t uploadedRows,
                        uint32_t failedBatches, uint32_t droppedRows);

}  // namespace domain
