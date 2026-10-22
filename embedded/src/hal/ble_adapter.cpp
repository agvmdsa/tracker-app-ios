#include "ble_adapter.h"

#include <NimBLEDevice.h>

#include <atomic>
#include <vector>

#include "clock.h"

namespace {
// The shared protocol contract (contracts/ble-advertising.md); same value as the iOS app's
// BeaconProtocol.serviceUUID.
constexpr const char* kServiceUuid = "fc979812-36dd-4722-9316-d2a7908888dd";

EspClock gClock;

// Runs in the NimBLE host task for every advertisement. It only filters the raw payload and hands
// the result over: no allocation, no blocking, no I/O (constitution, Principle V).
class ScanForwarder final : public NimBLEScanCallbacks {
public:
    IObservationSink* sink = nullptr;
    std::atomic<bool> wanted{false};  // false once stopScanning() was called

    void onResult(const NimBLEAdvertisedDevice* device) override {
        if (sink == nullptr) return;
        // The payload holds the advertisement and, for an active scan, the scan response.
        const std::vector<uint8_t>& payload = device->getPayload();
        domain::Observation observation;
        if (domain::observeAdvertisement(payload.data(), payload.size(), device->getRSSI(), gClock.nowMs(),
                                         observation)) {
            sink->onObservation(observation);
        }
    }

    // A scan that runs forever should not end; if the stack ends it anyway, listen again so the
    // collector never goes quiet without anyone asking it to.
    void onScanEnd(const NimBLEScanResults&, int) override {
        if (wanted.load()) NimBLEDevice::getScan()->start(0, false, true);
    }
};

ScanForwarder gForwarder;
}  // namespace

bool NimBleRadio::ensureInitialised(const char* deviceName) {
    if (!initialised_) {
        if (!NimBLEDevice::init(deviceName)) return false;
        initialised_ = true;
    }
    return true;
}

bool NimBleRadio::startAdvertising(const AdvertisingParams& params) {
    if (!ensureInitialised(params.beaconId)) return false;
    NimBLEDevice::setPower(static_cast<int8_t>(params.powerDbm));

    NimBLEAdvertising* advertising = NimBLEDevice::getAdvertising();
    advertising->stop();
    // Non-connectable, undirected. In the library this clears the flags field, so the
    // advertisement is built explicitly below with its own flags.
    advertising->setConnectableMode(BLE_GAP_CONN_MODE_NON);

    // Primary packet: flags (3 bytes) + complete 128-bit service UUID (18) + complete local name
    // = the 8-hex beacon ID (10) = 31 bytes, the legacy limit. Nothing else may be added.
    NimBLEAdvertisementData data;
    data.setFlags(BLE_HS_ADV_F_DISC_GEN | BLE_HS_ADV_F_BREDR_UNSUP);
    data.setCompleteServices(NimBLEUUID(kServiceUuid));
    data.setName(params.beaconId, /*isComplete=*/true);
    payloadBytes_ = static_cast<uint8_t>(data.getPayload().size());
    if (!advertising->setAdvertisementData(data)) return false;
    advertising->enableScanResponse(false);

    // Intervals are in 0.625 ms units: 100 ms = 160 units.
    const uint16_t units = static_cast<uint16_t>((params.intervalMs * 16u) / 10u);
    advertising->setMinInterval(units);
    advertising->setMaxInterval(units);
    return advertising->start();
}

void NimBleRadio::stopAdvertising() {
    if (!initialised_) return;
    NimBLEDevice::getAdvertising()->stop();
}

bool NimBleRadio::isAdvertising() const {
    return initialised_ && NimBLEDevice::getAdvertising()->isAdvertising();
}

bool NimBleRadio::startScanning(const ScanParams& params, IObservationSink& sink) {
    if (!ensureInitialised("")) return false;  // scan only: the collector advertises nothing

    gForwarder.sink = &sink;
    gForwarder.wanted.store(true);

    NimBLEScan* scan = NimBLEDevice::getScan();
    scan->setScanCallbacks(&gForwarder, /*wantDuplicates=*/true);
    scan->setActiveScan(true);    // also reads a name carried in a scan response (iOS anchors)
    scan->setDuplicateFilter(0);  // report every advertisement, not only the first of each device
    scan->setMaxResults(0);       // callbacks only: the library keeps no results
    scan->setInterval(static_cast<uint16_t>(params.intervalMs));
    scan->setWindow(static_cast<uint16_t>(params.windowMs));
    return scan->start(0, /*isContinue=*/false, /*restart=*/true);  // 0 = until stopped
}

void NimBleRadio::stopScanning() {
    if (!initialised_) return;
    gForwarder.wanted.store(false);
    NimBLEDevice::getScan()->stop();
}

bool NimBleRadio::isScanning() const {
    return initialised_ && NimBLEDevice::getScan()->isScanning();
}
