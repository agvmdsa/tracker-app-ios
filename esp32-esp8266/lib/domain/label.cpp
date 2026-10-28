#include "label.h"

#include <cstring>

namespace domain {

namespace {
constexpr std::size_t kMaxTokenLength = 16;

bool isAllowed(char c) {
    return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '_' || c == '-';
}
}  // namespace

bool isLabelToken(const char* token) {
    if (token == nullptr) return false;
    const std::size_t length = std::strlen(token);
    if (length == 0 || length > kMaxTokenLength) return false;
    for (std::size_t i = 0; i < length; ++i) {
        if (!isAllowed(token[i])) return false;
    }
    return true;
}

bool makeLabel(const char* session, const char* point, float x, float y, CollectionLabel& out) {
    if (!isLabelToken(session) || !isLabelToken(point)) return false;
    std::strcpy(out.session, session);  // length already checked above
    std::strcpy(out.point, point);
    out.x = x;
    out.y = y;
    return true;
}

}  // namespace domain
