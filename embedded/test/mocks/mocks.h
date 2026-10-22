// Header-only test doubles for the host-side tests. They implement the pure interfaces of
// src/hal and src/app, so supervisor and roles run on the host without a board.
#pragma once

#include <cstdint>
#include <cstring>
#include <deque>
#include <string>
#include <vector>

#include "adv_payload.h"
#include "app/command_source.h"
#include "app/events.h"
#include "app/observation_queue.h"
#include "app/recording_control.h"
#include "app/role.h"
#include "bounded_queue.h"
#include "hal/ble_port.h"
#include "hal/clock.h"
#include "hal/publisher.h"
#include "hal/store.h"
#include "hal/system_info.h"

struct FakeClock : IClock {
    uint32_t now = 1000;
    uint32_t nowMs() const override { return now; }
};

struct FakeStore : IStore {
    uint32_t boots = 0;
    domain::Overrides overrides{};
    int saves = 0;
    int clears = 0;
    uint32_t incrementBootCount() override { return ++boots; }
    domain::Overrides loadOverrides() override { return overrides; }
    bool saveOverrides(const domain::Overrides& o) override {
        overrides = o;
        ++saves;
        return true;
    }
    void clearOverrides() override {
        overrides = domain::Overrides{};
        ++clears;
    }
};

struct FakeSystem : ISystemInfo {
    uint64_t id = 0x112233445566ULL;
    uint32_t freeHeap = 150000;
    uint32_t minHeap = 130000;
    bool rebooted = false;
    uint64_t chipId() const override { return id; }
    uint32_t freeHeapBytes() const override { return freeHeap; }
    uint32_t minFreeHeapBytes() const override { return minHeap; }
    void reboot() override { rebooted = true; }
};

struct CapturePublisher : IPublisher {
    std::vector<std::string> lines;
    bool publishLine(const char* line) override {
        lines.emplace_back(line);
        return true;
    }
    bool hasLineStartingWith(const char* prefix) const {
        for (const auto& l : lines) {
            if (l.rfind(prefix, 0) == 0) return true;
        }
        return false;
    }
    bool hasLineContaining(const char* text) const {
        for (const auto& l : lines) {
            if (l.find(text) != std::string::npos) return true;
        }
        return false;
    }
};

struct ScriptedCommands : ICommandSource {
    std::deque<domain::Command> pending;
    void push(const char* line) { pending.push_back(domain::parseCommand(line)); }
    bool pollCommand(domain::Command& out) override {
        if (pending.empty()) return false;
        out = pending.front();
        pending.pop_front();
        return true;
    }
};

// Records what the supervisor asks of the recording half of a role.
struct FakeRecording : IRecordingControl {
    domain::RecordingState::StartResult startResult = domain::RecordingState::StartResult::Started;
    bool stopResult = true;
    RecordingSummary summary{};
    int labels = 0;
    int starts = 0;
    int stops = 0;
    domain::CollectionLabel lastLabel{};
    void setLabel(const domain::CollectionLabel& label) override {
        ++labels;
        lastLabel = label;
    }
    domain::RecordingState::StartResult startRecording() override {
        ++starts;
        return startResult;
    }
    bool stopRecording(RecordingSummary& out) override {
        ++stops;
        out = summary;
        return stopResult;
    }
};

struct FakeRole : IRole {
    bool startResult = true;
    int starts = 0;
    int polls = 0;
    domain::DeviceConfiguration lastConfig{};
    const char* label = "fake";
    IRecordingControl* control = nullptr;  // set it to give the role a recording half
    bool start(const domain::DeviceConfiguration& config) override {
        ++starts;
        lastConfig = config;
        return startResult;
    }
    void stop() override {}
    void poll() override { ++polls; }
    IRecordingControl* recording() override { return control; }
    const char* name() const override { return label; }
};

struct CaptureEvents : IEventSink {
    struct Event {
        std::string level;
        std::string code;
        std::string message;
    };
    std::vector<Event> events;
    void event(const char* level, const char* code, const char* message) override {
        events.push_back({level, code, message});
    }
    bool hasCode(const char* code) const {
        for (const auto& e : events) {
            if (e.code == code) return true;
        }
        return false;
    }
};

struct MockBleRadio : IBleRadio {
    bool startResult = true;
    int startCalls = 0;
    int stopCalls = 0;
    bool advertising = false;
    AdvertisingParams last{};
    bool startAdvertising(const AdvertisingParams& params) override {
        ++startCalls;
        last = params;
        advertising = startResult;
        return startResult;
    }
    void stopAdvertising() override {
        ++stopCalls;
        advertising = false;
    }
    bool isAdvertising() const override { return advertising; }
    uint8_t payloadBytes = 31;
    uint8_t advertisingPayloadBytes() const override { return payloadBytes; }

    // Scanning half.
    bool scanStartResult = true;
    int scanStartCalls = 0;
    int scanStopCalls = 0;
    bool scanning = false;
    ScanParams lastScan{};
    IObservationSink* sink = nullptr;
    bool startScanning(const ScanParams& params, IObservationSink& observer) override {
        ++scanStartCalls;
        lastScan = params;
        sink = &observer;
        scanning = scanStartResult;
        return scanStartResult;
    }
    void stopScanning() override {
        ++scanStopCalls;
        scanning = false;
    }
    bool isScanning() const override { return scanning; }

    // Delivers one received advertisement the way the real adapter does: the same domain filter,
    // then the sink. `payload` is the advertisement, plus the scan response when there is one.
    void inject(const AdPayload& payload, int rssi, uint32_t tMs) {
        domain::Observation observation;
        if (scanning && sink != nullptr &&
            domain::observeAdvertisement(payload.data(), payload.size(), rssi, tMs, observation)) {
            sink->onObservation(observation);
        }
    }
};

// Host stand-in for the FreeRTOS observation queue of the board: same capacity and the same
// drop-newest-and-count policy.
struct BoundedObservationQueue : IObservationQueue {
    domain::BoundedQueue<domain::Observation, 128> queue;
    void onObservation(const domain::Observation& observation) override { queue.push(observation); }
    bool pop(domain::Observation& out) override { return queue.pop(out); }
    uint32_t dropped() const override { return queue.dropped(); }
};
