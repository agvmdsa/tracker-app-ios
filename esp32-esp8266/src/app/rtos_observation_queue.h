// RtosObservationQueue: the observation queue of the board (data-model.md: 128 events, drop newest
// and count). A FreeRTOS static queue, so it is safe between the radio's task (producer) and the
// supervisor's loop (consumer) and allocates nothing after boot. A full queue refuses the newest
// observation and counts it (FR-013).
#pragma once

#include <freertos/FreeRTOS.h>
#include <freertos/queue.h>

#include <atomic>
#include <cstdint>

#include "observation_queue.h"

class RtosObservationQueue final : public IObservationQueue {
public:
    static constexpr std::size_t kCapacity = 128;

    // Creates the queue over the storage inside this object. Call once, early in setup().
    void begin();

    // Called in the radio's context: never blocks.
    void onObservation(const domain::Observation& observation) override;
    bool pop(domain::Observation& out) override;
    uint32_t dropped() const override { return dropped_.load(); }

private:
    StaticQueue_t control_{};
    uint8_t storage_[kCapacity * sizeof(domain::Observation)]{};
    QueueHandle_t handle_ = nullptr;
    std::atomic<uint32_t> dropped_{0};
};
