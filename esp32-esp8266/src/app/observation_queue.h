// Observation queue: where the radio callback leaves each observation for the collector to pick up
// (data-model.md, "Queues and bounds": 128 events, drop newest and count). The producer side is the
// radio's context, so it only enqueues. On the board it is a FreeRTOS queue (rtos_observation_queue.h);
// the host tests use a bounded queue with the same policy. Pure interface.
#pragma once

#include <cstdint>

#include "hal/ble_port.h"

class IObservationQueue : public IObservationSink {
public:
    // Takes the oldest observation; false when the queue is empty.
    virtual bool pop(domain::Observation& out) = 0;
    // Observations lost because the queue was full (reported as `drop_queue` in the health line).
    virtual uint32_t dropped() const = 0;
};
