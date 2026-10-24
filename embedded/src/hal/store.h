// Persistent settings (Adapter over NVS). Pure interface: no Arduino dependency.
#pragma once

#include <cstdint>

#include "device_config.h"

class IStore {
public:
    virtual ~IStore() = default;
    // Increments and returns the persistent restart counter (the boot id).
    virtual uint32_t incrementBootCount() = 0;
    virtual domain::Overrides loadOverrides() = 0;
    virtual bool saveOverrides(const domain::Overrides& overrides) = 0;
    // Erases the overrides; the restart counter is kept.
    virtual void clearOverrides() = 0;
};
