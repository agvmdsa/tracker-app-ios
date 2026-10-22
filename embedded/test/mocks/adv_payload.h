// Builds advertisement payloads (a sequence of AD structures: length, type, data) for the
// host-side tests, so the observation filter sees the same bytes the radio would deliver.
#pragma once

#include <cstddef>
#include <cstdint>
#include <cstring>
#include <vector>

#include "observation_filter.h"

class AdPayload {
public:
    AdPayload& flags(uint8_t value = 0x06) { return field(0x01, &value, 1); }

    // Complete (0x07) or incomplete (0x06) list holding one 128-bit service UUID.
    AdPayload& serviceUuid(const uint8_t (&uuid)[16], bool complete = true) {
        return field(complete ? 0x07 : 0x06, uuid, sizeof(uuid));
    }
    AdPayload& projectServiceUuid() { return serviceUuid(domain::kProjectServiceUuid); }

    // Complete (0x09) or shortened (0x08) local name.
    AdPayload& name(const char* text, bool complete = true) {
        return field(complete ? 0x09 : 0x08, reinterpret_cast<const uint8_t*>(text), std::strlen(text));
    }

    AdPayload& field(uint8_t type, const uint8_t* data, std::size_t length) {
        bytes_.push_back(static_cast<uint8_t>(length + 1));
        bytes_.push_back(type);
        bytes_.insert(bytes_.end(), data, data + length);
        return *this;
    }

    // What a scanner holds after an active scan: the advertisement followed by the scan response.
    AdPayload& append(const AdPayload& other) {
        bytes_.insert(bytes_.end(), other.bytes_.begin(), other.bytes_.end());
        return *this;
    }

    // Raw bytes, for malformed payloads.
    AdPayload& raw(const std::vector<uint8_t>& data) {
        bytes_.insert(bytes_.end(), data.begin(), data.end());
        return *this;
    }

    const uint8_t* data() const { return bytes_.data(); }
    std::size_t size() const { return bytes_.size(); }
    const std::vector<uint8_t>& bytes() const { return bytes_; }

    // The exact primary packet the beacon role advertises (contracts/ble-advertising.md):
    // flags + complete 128-bit service UUID + complete name = 31 bytes.
    static AdPayload beaconPacket(const char* id) { return AdPayload().flags().projectServiceUuid().name(id); }

private:
    std::vector<uint8_t> bytes_;
};
