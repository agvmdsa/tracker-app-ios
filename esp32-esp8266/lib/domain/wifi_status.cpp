#include "wifi_status.h"

#include <cstdio>
#include <cstring>

namespace domain {

namespace {

bool isSuccess(int httpStatus) { return httpStatus >= 200 && httpStatus < 300; }

void fill(NetworkEvent& event, const char* level, const char* code, const char* message) {
    event.level = level;
    event.code = code;
    std::snprintf(event.message, sizeof(event.message), "%s", message);
}

}  // namespace

const char* wifiLinkName(WifiLink link) {
    switch (link) {
        case WifiLink::Off: return "off";
        case WifiLink::Connecting: return "connecting";
        case WifiLink::Connected: return "connected";
        case WifiLink::NoNetwork: return "no_network";
        case WifiLink::AuthFailed: return "auth_failed";
        case WifiLink::Lost: return "lost";
    }
    return "unknown";
}

bool WifiStatusTracker::update(WifiLink link, const char* ip, NetworkEvent& event) {
    const bool changed = link != link_;
    link_ = link;
    if (link != WifiLink::Connected) ip_[0] = '\0';
    if (!changed) return false;

    switch (link) {
        case WifiLink::Connected:
            std::snprintf(ip_, sizeof(ip_), "%s", ip != nullptr ? ip : "");
            event.level = "info";
            event.code = "wifi_connected";
            std::snprintf(event.message, sizeof(event.message), "ip=%s", ip_);
            return true;
        case WifiLink::NoNetwork:
            fill(event, "warn", "wifi_no_network",
                 "network not found: check WIFI_SSID and that it is 2.4 GHz (the ESP32 has no 5 GHz)");
            return true;
        case WifiLink::AuthFailed:
            fill(event, "error", "wifi_auth_failed", "the network refused the password: check WIFI_PASSWORD");
            return true;
        case WifiLink::Lost:
            fill(event, "warn", "wifi_lost", "connection lost; reconnecting");
            return true;
        case WifiLink::Off:
        case WifiLink::Connecting:
            return false;
    }
    return false;
}

bool UploadStatusTracker::onBatch(int httpStatus, std::size_t rows, NetworkEvent& event) {
    const bool success = isSuccess(httpStatus);
    if (success) {
        uploadedRows_ += static_cast<uint32_t>(rows);
    } else {
        ++failedBatches_;
    }
    // Every success looks the same; failures differ by code.
    const bool changed = !hasLast_ || (success ? !isSuccess(lastStatus_) : lastStatus_ != httpStatus);
    hasLast_ = true;
    lastStatus_ = httpStatus;
    if (!changed) return false;

    if (success) {
        event.level = "info";
        event.code = "upload_ok";
        std::snprintf(event.message, sizeof(event.message), "http=%d rows=%u", httpStatus,
                      static_cast<unsigned>(rows));
        return true;
    }
    const char* hint = httpStatus < 0                         ? "no connection to the API (check API_HOST and the Wi-Fi)"
                       : (httpStatus == 401 || httpStatus == 403) ? "the API refused the key: check API_KEY"
                                                                   : "the API answered with an error";
    event.level = "warn";
    event.code = "upload_failed";
    std::snprintf(event.message, sizeof(event.message), "http=%d rows=%u dropped: %s", httpStatus,
                  static_cast<unsigned>(rows), hint);
    return true;
}

int formatNetworkStatus(char* buf, std::size_t cap, WifiLink link, const char* ip, uint32_t uploadedRows,
                        uint32_t failedBatches, uint32_t droppedRows) {
    const int written = std::snprintf(buf, cap, "wifi=%s ip=%s uploaded=%lu failed=%lu dropped=%lu",
                                      wifiLinkName(link), (ip != nullptr && ip[0] != '\0') ? ip : "-",
                                      static_cast<unsigned long>(uploadedRows),
                                      static_cast<unsigned long>(failedBatches),
                                      static_cast<unsigned long>(droppedRows));
    if (written < 0 || static_cast<std::size_t>(written) >= cap) return -1;
    return written;
}

}  // namespace domain
