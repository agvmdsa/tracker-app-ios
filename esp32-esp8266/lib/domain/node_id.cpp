#include "node_id.h"

namespace domain {

void nodeIdFromChipId(uint64_t chipId, char out[7]) {
    static const char kDigits[] = "0123456789ABCDEF";
    for (int i = 0; i < 6; ++i) {
        const unsigned shift = static_cast<unsigned>(20 - 4 * i);  // most significant nibble first
        out[i] = kDigits[(chipId >> shift) & 0xF];
    }
    out[6] = '\0';
}

}  // namespace domain
