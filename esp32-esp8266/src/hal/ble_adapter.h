// NimBleRadio: the BLE radio port over NimBLE-Arduino 2.5.1 (Adapter pattern).
// The advertising half serves the beacon role (US1), the scanning half the collector (US2).
// Hardware detail stays in the .cpp; this header exposes only the pure interface.
#pragma once

#include "ble_port.h"

class NimBleRadio final : public IBleRadio {
public:
    bool startAdvertising(const AdvertisingParams& params) override;
    void stopAdvertising() override;
    bool isAdvertising() const override;
    uint8_t advertisingPayloadBytes() const override { return payloadBytes_; }

    bool startScanning(const ScanParams& params, IObservationSink& sink) override;
    void stopScanning() override;
    bool isScanning() const override;

private:
    // Brings the NimBLE stack up once, whichever half is used first.
    bool ensureInitialised(const char* deviceName);

    bool initialised_ = false;
    uint8_t payloadBytes_ = 0;
};
