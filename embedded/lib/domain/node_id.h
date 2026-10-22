// Node ID derived from the chip's unique identifier. Pure logic: no Arduino headers.
#pragma once

#include <cstdint>

namespace domain {

// Writes 6 uppercase hexadecimal characters (the low 24 bits of `chipId`, which on the ESP32 is
// the 48-bit MAC) plus a NUL terminator into `out` (7 bytes).
void nodeIdFromChipId(uint64_t chipId, char out[7]);

}  // namespace domain
