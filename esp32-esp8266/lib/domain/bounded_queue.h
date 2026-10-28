// Bounded FIFO with a drop-newest policy and a drop counter (data-model.md, "Queues and bounds").
// Storage is part of the object, so the queue never allocates. Pure logic: no Arduino headers.
//
// Not thread-safe on its own. Between FreeRTOS tasks the firmware uses a FreeRTOS queue that
// applies the same drop-newest-and-count policy; this class is the testable policy and the
// storage for single-context use.
#pragma once

#include <cstddef>
#include <cstdint>

namespace domain {

template <typename T, std::size_t N>
class BoundedQueue {
    static_assert(N > 0, "capacity must be positive");

public:
    // Returns false (and counts a drop) when the queue is full; the newest item is the one lost.
    bool push(const T& value) {
        if (count_ == N) {
            ++dropped_;
            return false;
        }
        items_[(head_ + count_) % N] = value;
        ++count_;
        return true;
    }

    bool pop(T& out) {
        if (count_ == 0) return false;
        out = items_[head_];
        head_ = (head_ + 1) % N;
        --count_;
        return true;
    }

    std::size_t size() const { return count_; }
    static constexpr std::size_t capacity() { return N; }
    uint32_t dropped() const { return dropped_; }

private:
    T items_[N];
    std::size_t head_ = 0;
    std::size_t count_ = 0;
    uint32_t dropped_ = 0;
};

}  // namespace domain
