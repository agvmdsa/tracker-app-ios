#include "record_format.h"

#include <cstdio>
#include <cstring>

namespace domain {

namespace {

// Portable across targets where uint32_t is `unsigned int` or `unsigned long`.
unsigned long u(uint32_t value) { return static_cast<unsigned long>(value); }

int finish(int written, std::size_t cap) {
    if (written < 0 || static_cast<std::size_t>(written) >= cap) return -1;
    return written;
}

}  // namespace

int formatHeader(char* buf, std::size_t cap, const char* fw, const char* node, uint32_t boot,
                 const char* role, const char* beaconId, uint32_t baud) {
    return finish(std::snprintf(buf, cap, "# fw=%s node=%s boot=%lu role=%s id=%s baud=%lu", fw, node,
                                u(boot), role, beaconId, u(baud)),
                  cap);
}

int formatLive(char* buf, std::size_t cap, const LiveLine& l) {
    return finish(std::snprintf(buf, cap, "L,%lu,%lu,%s,%s,%d,%.1f", u(l.tMs), u(l.boot), l.node, l.beacon,
                                l.rssiLast, static_cast<double>(l.ratePerS)),
                  cap);
}

int formatRaw(char* buf, std::size_t cap, const RawRecord& r) {
    return finish(std::snprintf(buf, cap, "R,%lu,%lu,%s,%s,%d,%s,%s,%.2f,%.2f", u(r.tMs), u(r.boot), r.node,
                                r.beacon, r.rssi, r.session, r.point, static_cast<double>(r.x),
                                static_cast<double>(r.y)),
                  cap);
}

int formatWindow(char* buf, std::size_t cap, const WindowRecord& w) {
    if (w.missing) {
        // count is 0 and the five statistics are empty fields.
        return finish(std::snprintf(buf, cap, "W,%lu,%lu,%lu,%s,%s,0,,,,,,1,%s,%s,%.2f,%.2f", u(w.winStartMs),
                                    u(w.winMs), u(w.boot), w.node, w.beacon, w.session, w.point,
                                    static_cast<double>(w.x), static_cast<double>(w.y)),
                      cap);
    }
    return finish(std::snprintf(buf, cap, "W,%lu,%lu,%lu,%s,%s,%lu,%.1f,%.1f,%.1f,%d,%d,0,%s,%s,%.2f,%.2f",
                                u(w.winStartMs), u(w.winMs), u(w.boot), w.node, w.beacon, u(w.count),
                                static_cast<double>(w.mean), static_cast<double>(w.median),
                                static_cast<double>(w.stdDev), w.minRssi, w.maxRssi, w.session, w.point,
                                static_cast<double>(w.x), static_cast<double>(w.y)),
                  cap);
}

int formatSummary(char* buf, std::size_t cap, const SummaryLine& s) {
    return finish(std::snprintf(buf, cap, "S,%lu,%lu,%s,%s,%s,%s,%lu,%.1f", u(s.tMs), u(s.boot), s.node,
                                s.session, s.point, s.beacon, u(s.records), static_cast<double>(s.ratePerS)),
                  cap);
}

int formatBeaconHealth(char* buf, std::size_t cap, const BeaconHealthLine& b) {
    return finish(std::snprintf(buf, cap, "B,%lu,%lu,%s,%.1f,%lu", u(b.tMs), u(b.boot), b.beacon,
                                static_cast<double>(b.ratePerS), u(b.gaps)),
                  cap);
}

int formatHealth(char* buf, std::size_t cap, const HealthLine& h) {
    return finish(std::snprintf(buf, cap, "H,%lu,%lu,%s,%lu,%lu,%lu,%lu,%lu,%lu,%lu,%lu,%lu,%d,%d,%lu", u(h.tMs),
                                u(h.boot), h.node, u(h.restarts), u(h.uptimeS), u(h.dropQueue),
                                u(h.dropSerial), u(h.dropMqtt), u(h.wifiReconnects), u(h.mqttReconnects),
                                u(h.heapFree), u(h.heapMin), h.wifiRssi, h.advPowerDbm, u(h.advIntervalMs)),
                  cap);
}

int formatEvent(char* buf, std::size_t cap, uint32_t tMs, uint32_t boot, const char* level,
                const char* code, const char* message) {
    char clean[96];
    std::size_t i = 0;
    for (; message != nullptr && message[i] != '\0' && i < sizeof(clean) - 1; ++i) {
        const char c = message[i];
        clean[i] = (c == ',') ? ';' : (c == '\n' || c == '\r') ? ' ' : c;
    }
    clean[i] = '\0';
    return finish(std::snprintf(buf, cap, "E,%lu,%lu,%s,%s,%s", u(tMs), u(boot), level, code, clean), cap);
}

}  // namespace domain
