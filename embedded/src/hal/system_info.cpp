#include "system_info.h"

#include <Arduino.h>
#include <esp_system.h>

uint64_t EspSystemInfo::chipId() const { return ESP.getEfuseMac(); }

uint32_t EspSystemInfo::freeHeapBytes() const { return esp_get_free_heap_size(); }

uint32_t EspSystemInfo::minFreeHeapBytes() const { return esp_get_minimum_free_heap_size(); }

void EspSystemInfo::reboot() {
    delay(250);  // let the serial task flush the "OK rebooting" line
    esp_restart();
}
