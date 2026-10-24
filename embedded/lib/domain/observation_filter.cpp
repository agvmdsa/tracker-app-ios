#include "observation_filter.h"

#include <cstring>

namespace domain {

const uint8_t kProjectServiceUuid[16] = {0xdd, 0x88, 0x88, 0x90, 0xa7, 0xd2, 0x16, 0x93,
                                         0x22, 0x47, 0xdd, 0x36, 0x12, 0x98, 0x97, 0xfc};

namespace {

// AD structure types (Bluetooth Core Specification, Assigned Numbers).
constexpr uint8_t kIncompleteUuid128List = 0x06;
constexpr uint8_t kCompleteUuid128List = 0x07;
constexpr uint8_t kShortenedLocalName = 0x08;
constexpr uint8_t kCompleteLocalName = 0x09;

constexpr std::size_t kUuidBytes = sizeof(kProjectServiceUuid);
constexpr std::size_t kBeaconIdLength = 8;

bool listHasProjectUuid(const uint8_t* data, std::size_t length) {
    for (std::size_t offset = 0; offset + kUuidBytes <= length; offset += kUuidBytes) {
        if (std::memcmp(data + offset, kProjectServiceUuid, kUuidBytes) == 0) return true;
    }
    return false;
}

}  // namespace

bool observeAdvertisement(const uint8_t* payload, std::size_t length, int rssi, uint32_t tMs, Observation& out) {
    if (payload == nullptr) return false;
    if (rssi < INT8_MIN || rssi > INT8_MAX) return false;

    bool hasUuid = false;
    const uint8_t* name = nullptr;
    std::size_t nameLength = 0;

    std::size_t offset = 0;
    while (offset < length) {
        const std::size_t fieldLength = payload[offset];  // the type byte plus the data
        if (fieldLength == 0) break;                      // padding: nothing meaningful follows
        if (offset + 1 + fieldLength > length) break;     // truncated field: ignore the rest
        const uint8_t type = payload[offset + 1];
        const uint8_t* data = payload + offset + 2;
        const std::size_t dataLength = fieldLength - 1;

        switch (type) {
            case kIncompleteUuid128List:
            case kCompleteUuid128List:
                if (listHasProjectUuid(data, dataLength)) hasUuid = true;
                break;
            case kShortenedLocalName:
            case kCompleteLocalName:
                // A complete name wins over a shortened one when both are present.
                if (name == nullptr || type == kCompleteLocalName) {
                    name = data;
                    nameLength = dataLength;
                }
                break;
            default:
                break;
        }
        offset += 1 + fieldLength;
    }

    if (!hasUuid || name == nullptr || nameLength != kBeaconIdLength) return false;

    char text[kBeaconIdLength + 1];
    std::memcpy(text, name, kBeaconIdLength);
    text[kBeaconIdLength] = '\0';
    BeaconId id;
    if (!parseBeaconId(text, id)) return false;  // also refuses a NUL or a space inside the name

    out.tMs = tMs;
    out.beacon = id;
    out.rssi = static_cast<int8_t>(rssi);
    return true;
}

}  // namespace domain
