#include "beacon_id.h"

namespace domain {

namespace {

bool isHexDigit(char c) {
    return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f');
}

char toUpperHex(char c) {
    return (c >= 'a' && c <= 'f') ? static_cast<char>(c - 'a' + 'A') : c;
}

}  // namespace

bool isHexOfLength(const char* text, unsigned length) {
    if (text == nullptr) return false;
    for (unsigned i = 0; i < length; ++i) {
        if (!isHexDigit(text[i])) return false;  // also stops at the NUL terminator
    }
    return text[length] == '\0';
}

bool parseBeaconId(const char* text, BeaconId& out) {
    if (!isHexOfLength(text, 8)) return false;
    for (unsigned i = 0; i < 8; ++i) out.value[i] = toUpperHex(text[i]);
    out.value[8] = '\0';
    return true;
}

}  // namespace domain
