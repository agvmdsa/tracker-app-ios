// System information and control (Adapter). Interface plus the ESP32 implementation declaration;
// the implementation is in system_info.cpp. No Arduino dependency in this header.
#pragma once

#include <cstdint>

class ISystemInfo {
public:
    virtual ~ISystemInfo() = default;
    virtual uint64_t chipId() const = 0;  // 48-bit MAC on the ESP32
    virtual uint32_t freeHeapBytes() const = 0;
    virtual uint32_t minFreeHeapBytes() const = 0;
    virtual void reboot() = 0;  // gives pending serial output a moment, then restarts
};

class EspSystemInfo final : public ISystemInfo {
public:
    uint64_t chipId() const override;
    uint32_t freeHeapBytes() const override;
    uint32_t minFreeHeapBytes() const override;
    void reboot() override;
};
