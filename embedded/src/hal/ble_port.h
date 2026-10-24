// BLE radio port (Adapter over NimBLE). Pure interface: no Arduino/NimBLE dependency.
// The advertising half serves the beacon role (US1); the scanning half serves the collector (US2).
#pragma once

#include <cstdint>

#include "observation_filter.h"

struct AdvertisingParams {
    char beaconId[9];  // 8 uppercase hex characters, also used as the advertised local name
    int powerDbm;
    uint32_t intervalMs;
};

struct ScanParams {
    uint32_t intervalMs;  // time between the starts of two consecutive scan windows
    uint32_t windowMs;    // how long the radio listens inside each interval (<= intervalMs)
};

// Receives each advertisement of the project's protocol, already filtered, in the radio's own
// context. Implementations must only enqueue: no allocation, no blocking, no I/O (Principle V).
class IObservationSink {
public:
    virtual ~IObservationSink() = default;
    virtual void onObservation(const domain::Observation& observation) = 0;
};

class IBleRadio {
public:
    virtual ~IBleRadio() = default;
    // Starts non-connectable, undirected advertising of the project protocol
    // (contracts/ble-advertising.md). Returns false if the radio could not start.
    virtual bool startAdvertising(const AdvertisingParams& params) = 0;
    virtual void stopAdvertising() = 0;
    virtual bool isAdvertising() const = 0;
    // Size in bytes of the primary advertising payload that was built (31 is the legacy limit);
    // reported at startup so the 31-byte budget of the contract can be checked on the board.
    virtual uint8_t advertisingPayloadBytes() const = 0;

    // Starts an active scan with the duplicate filter off, so every advertisement is reported
    // (research R3). Each one that passes the observation filter goes to `sink`, which must outlive
    // the scan. Returns false if the radio could not start.
    virtual bool startScanning(const ScanParams& params, IObservationSink& sink) = 0;
    virtual void stopScanning() = 0;
    virtual bool isScanning() const = 0;
};

