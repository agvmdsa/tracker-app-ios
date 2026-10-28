#include "collector_role.h"

#include <cstdio>

namespace {
constexpr uint32_t kMaxScanIntervalMs = 10240;  // the Bluetooth limit for a scan interval
}

bool CollectorRole::start(const domain::DeviceConfiguration& config) {
    const ScanParams params{config.scanIntervalMs, config.scanWindowMs};
    if (params.intervalMs == 0 || params.intervalMs > kMaxScanIntervalMs || params.windowMs == 0 ||
        params.windowMs > params.intervalMs) {
        events_.event("error", "bad_scan_params",
                      "scan interval must be 1 to 10240 ms and the window between 1 ms and the interval; not scanning");
        return false;
    }

    task_.begin(config.nodeId, config.bootId);
    if (!radio_.startScanning(params, queue_)) {
        events_.event("error", "radio_start_failed", "could not start BLE scanning");
        return false;
    }

    char message[64];
    std::snprintf(message, sizeof(message), "interval=%lu window=%lu", static_cast<unsigned long>(params.intervalMs),
                  static_cast<unsigned long>(params.windowMs));
    events_.event("info", "scanning", message);
    return true;
}

void CollectorRole::stop() { radio_.stopScanning(); }
