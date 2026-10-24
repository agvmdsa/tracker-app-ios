// Serial line formatting (contracts/serial-protocol.md). Every function writes into a
// caller-provided buffer with snprintf (no heap, no Arduino String) and returns the number of
// characters written, or -1 when the line does not fit. Pure logic: no Arduino headers.
#pragma once

#include <cstddef>
#include <cstdint>

namespace domain {

struct LiveLine {  // L: collector idle, one per beacon per second, unlabelled
    uint32_t tMs;
    uint32_t boot;
    const char* node;
    const char* beacon;
    int rssiLast;
    float ratePerS;
};

struct RawRecord {  // R: collector recording
    uint32_t tMs;
    uint32_t boot;
    const char* node;
    const char* beacon;
    int rssi;
    const char* session;
    const char* point;
    float x;
    float y;
};

struct WindowRecord {  // W: window aggregate; when `missing`, the statistics are left empty
    uint32_t winStartMs;
    uint32_t winMs;
    uint32_t boot;
    const char* node;
    const char* beacon;
    uint32_t count;
    float mean;
    float median;
    float stdDev;
    int minRssi;
    int maxRssi;
    bool missing;
    const char* session;
    const char* point;
    float x;
    float y;
};

struct SummaryLine {  // S: on `rec stop`, one per beacon
    uint32_t tMs;
    uint32_t boot;
    const char* node;
    const char* session;
    const char* point;
    const char* beacon;
    uint32_t records;
    float ratePerS;
};

struct BeaconHealthLine {  // B
    uint32_t tMs;
    uint32_t boot;
    const char* beacon;
    float ratePerS;
    uint32_t gaps;
};

struct HealthLine {  // H
    uint32_t tMs;
    uint32_t boot;
    const char* node;
    uint32_t restarts;
    uint32_t uptimeS;
    uint32_t dropQueue;
    uint32_t dropSerial;
    uint32_t dropMqtt;
    uint32_t wifiReconnects;
    uint32_t mqttReconnects;
    uint32_t heapFree;
    uint32_t heapMin;
    int wifiRssi;
    int advPowerDbm;
    uint32_t advIntervalMs;
};

// `# fw=<version> node=<id> boot=<n> role=<role> id=<beaconId> baud=<n>`
int formatHeader(char* buf, std::size_t cap, const char* fw, const char* node, uint32_t boot,
                 const char* role, const char* beaconId, uint32_t baud);
int formatLive(char* buf, std::size_t cap, const LiveLine& line);
int formatRaw(char* buf, std::size_t cap, const RawRecord& record);
int formatWindow(char* buf, std::size_t cap, const WindowRecord& record);
int formatSummary(char* buf, std::size_t cap, const SummaryLine& line);
int formatBeaconHealth(char* buf, std::size_t cap, const BeaconHealthLine& line);
int formatHealth(char* buf, std::size_t cap, const HealthLine& line);
// `E,<t_ms>,<boot>,<level>,<code>,<message>`; commas and line breaks in the message become
// ';' and ' ' so the field count stays fixed.
int formatEvent(char* buf, std::size_t cap, uint32_t tMs, uint32_t boot, const char* level,
                const char* code, const char* message);

}  // namespace domain
