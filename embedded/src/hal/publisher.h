// Output channel for text lines (Bridge pattern): serial today; an MQTT publisher can implement
// the same interface later (US4). Implementations must never block the caller.
#pragma once

class IPublisher {
public:
    virtual ~IPublisher() = default;
    // Queues one line (without the trailing newline). Returns false when the line was dropped
    // because the queue was full (the drop is counted by the implementation).
    virtual bool publishLine(const char* line) = 0;
};
