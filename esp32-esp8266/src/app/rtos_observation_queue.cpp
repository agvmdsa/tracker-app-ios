#include "rtos_observation_queue.h"

void RtosObservationQueue::begin() {
    handle_ = xQueueCreateStatic(kCapacity, sizeof(domain::Observation), storage_, &control_);
}

void RtosObservationQueue::onObservation(const domain::Observation& observation) {
    if (handle_ == nullptr || xQueueSend(handle_, &observation, 0) != pdTRUE) dropped_.fetch_add(1);
}

bool RtosObservationQueue::pop(domain::Observation& out) {
    return handle_ != nullptr && xQueueReceive(handle_, &out, 0) == pdTRUE;
}
