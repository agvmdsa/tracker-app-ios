// NVS-backed settings store (Adapter over Arduino Preferences). Namespace `tracker`;
// keys: boots (restart counter), role, id, pwr, adv, win (contracts/configuration.md).
#pragma once

#include <Preferences.h>

#include "store.h"

class NvsStore final : public IStore {
public:
    // Opens the NVS namespace. Returns false if it could not be opened.
    bool begin();

    uint32_t incrementBootCount() override;
    domain::Overrides loadOverrides() override;
    bool saveOverrides(const domain::Overrides& overrides) override;
    void clearOverrides() override;

private:
    Preferences prefs_;
    bool open_ = false;
};
