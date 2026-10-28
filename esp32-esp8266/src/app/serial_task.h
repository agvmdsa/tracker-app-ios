// SerialConsole: the serial output queue and the command reader (T024).
//
// Everything is allocated statically. The output queue holds 64 lines of 128 bytes; when it is
// full the newest line is dropped and counted (FR-013). The task reads input lines, parses them
// with the domain parser and queues the result for the supervisor (ICommandSource). Implements
// IPublisher so any code can queue a line without blocking.
#pragma once

#include <Arduino.h>
#include <freertos/FreeRTOS.h>
#include <freertos/queue.h>
#include <freertos/task.h>

#include <atomic>
#include <cstdint>

#include "app/command_source.h"
#include "command_parser.h"
#include "hal/publisher.h"

class SerialConsole final : public IPublisher, public ICommandSource {
public:
    static constexpr std::size_t kLineBytes = 128;
    static constexpr std::size_t kLineCount = 64;
    static constexpr std::size_t kCommandCount = 8;
    static constexpr std::size_t kStackBytes = 4096;

    // Starts the serial port and the task. Call once, early in setup().
    void begin(uint32_t baud);

    bool publishLine(const char* line) override;
    bool pollCommand(domain::Command& out) override;

    uint32_t dropped() const { return dropped_.load(); }

private:
    static void taskEntry(void* self);
    void run();
    void handleInputChar(char c);

    StaticQueue_t outControl_{};
    uint8_t outStorage_[kLineCount * kLineBytes]{};
    QueueHandle_t outQueue_ = nullptr;

    StaticQueue_t cmdControl_{};
    uint8_t cmdStorage_[kCommandCount * sizeof(domain::Command)]{};
    QueueHandle_t cmdQueue_ = nullptr;

    StaticTask_t taskControl_{};
    StackType_t taskStack_[kStackBytes]{};

    char input_[96]{};
    std::size_t inputLength_ = 0;
    bool inputOverflow_ = false;
    std::atomic<uint32_t> dropped_{0};
};
