#include "fingerprint_payload.h"

#include <cstdio>

namespace domain {

namespace {

// Advances `offset` by what was written, or leaves it alone and returns false when it didn't
// fit — same truncation contract as record_format.cpp's `finish`.
bool grew(int written, std::size_t cap, std::size_t& offset) {
    if (written < 0 || static_cast<std::size_t>(written) >= cap - offset) return false;
    offset += static_cast<std::size_t>(written);
    return true;
}

}  // namespace

int formatFingerprintBatch(char* buf, std::size_t cap, const RawRecord* records, std::size_t count,
                           const char* platform) {
    if (cap == 0) return -1;
    std::size_t offset = 0;
    if (!grew(std::snprintf(buf + offset, cap - offset, "["), cap, offset)) return -1;
    for (std::size_t i = 0; i < count; ++i) {
        const RawRecord& r = records[i];
        const int written = std::snprintf(
            buf + offset, cap - offset,
            "%s{\"nodeID\":\"%s\",\"platform\":\"%s\",\"beaconID\":\"%s\",\"rssi\":%d,"
            "\"session\":\"%s\",\"point\":\"%s\",\"x\":%.2f,\"y\":%.2f}",
            i == 0 ? "" : ",", r.node, platform, r.beacon, r.rssi, r.session, r.point,
            static_cast<double>(r.x), static_cast<double>(r.y));
        if (!grew(written, cap, offset)) return -1;
    }
    if (!grew(std::snprintf(buf + offset, cap - offset, "]"), cap, offset)) return -1;
    return static_cast<int>(offset);
}

}  // namespace domain
