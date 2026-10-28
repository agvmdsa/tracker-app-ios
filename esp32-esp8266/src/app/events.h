// Event sink: how roles and the supervisor report info/warn/error events (the `E` lines of
// contracts/serial-protocol.md). Pure interface.
#pragma once

class IEventSink {
public:
    virtual ~IEventSink() = default;
    // level: "info", "warn" or "error"; code: a short machine-readable code; message: free text
    // (commas are replaced when formatted).
    virtual void event(const char* level, const char* code, const char* message) = 0;
};
