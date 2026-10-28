#include "sighting_payload.h"

#include <cstdio>

namespace domain {

int formatSightingPayload(char* buf, std::size_t cap, const char* node, const char* beacon, int rssi) {
    const int written = std::snprintf(
        buf, cap, "{\"observerID\":\"%s\",\"anchorID\":\"%s\",\"rssi\":%d}", node, beacon, rssi);
    if (written < 0 || static_cast<std::size_t>(written) >= cap) return -1;
    return written;
}

}  // namespace domain
