// Time source (Adapter pattern). Interface and the ESP32 implementation declaration; the
// implementation is in clock.cpp. This header has no Arduino/ESP dependency.
#pragma once

#include <cstdint>

class IClock {
public:
    virtual ~IClock() = default;
    // Monotonic milliseconds since boot (restarts at 0 on every restart).
    virtual uint32_t nowMs() const = 0;
};

class EspClock final : public IClock {
public:
    uint32_t nowMs() const override;
};
