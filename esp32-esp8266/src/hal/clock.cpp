#include "clock.h"

#include <esp_timer.h>

uint32_t EspClock::nowMs() const {
    return static_cast<uint32_t>(esp_timer_get_time() / 1000);
}
