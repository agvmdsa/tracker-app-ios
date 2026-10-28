// BeaconId: the identity carried in the advertised local name (data-model.md).
// Pure logic: no Arduino headers.
#pragma once

namespace domain {

struct BeaconId {
    char value[9] = {0};  // 8 hexadecimal characters, uppercase, NUL-terminated
};

// True when `text` is exactly 8 hexadecimal characters (^[0-9A-Fa-f]{8}$). On success `out`
// holds the value normalised to uppercase; on failure `out` is left unchanged.
bool parseBeaconId(const char* text, BeaconId& out);

// True when `text` is exactly `length` hexadecimal characters (no surrounding whitespace).
bool isHexOfLength(const char* text, unsigned length);

}  // namespace domain
