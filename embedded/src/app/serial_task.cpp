#include "serial_task.h"

#include <cstring>

void SerialConsole::begin(uint32_t baud) {
    Serial.begin(baud);
    outQueue_ = xQueueCreateStatic(kLineCount, kLineBytes, outStorage_, &outControl_);
    cmdQueue_ = xQueueCreateStatic(kCommandCount, sizeof(domain::Command), cmdStorage_, &cmdControl_);
    xTaskCreateStaticPinnedToCore(&SerialConsole::taskEntry, "serial", kStackBytes, this, 1, taskStack_,
                                  &taskControl_, tskNO_AFFINITY);
}

bool SerialConsole::publishLine(const char* line) {
    if (outQueue_ == nullptr || line == nullptr) return false;
    char item[kLineBytes];
    std::strncpy(item, line, kLineBytes - 1);
    item[kLineBytes - 1] = '\0';
    if (xQueueSend(outQueue_, item, 0) != pdTRUE) {
        dropped_.fetch_add(1);
        return false;
    }
    return true;
}

bool SerialConsole::pollCommand(domain::Command& out) {
    return cmdQueue_ != nullptr && xQueueReceive(cmdQueue_, &out, 0) == pdTRUE;
}

void SerialConsole::taskEntry(void* self) { static_cast<SerialConsole*>(self)->run(); }

void SerialConsole::run() {
    char line[kLineBytes];
    for (;;) {
        // Drain the output queue first, then look at the input.
        while (xQueueReceive(outQueue_, line, 0) == pdTRUE) Serial.println(line);
        while (Serial.available() > 0) handleInputChar(static_cast<char>(Serial.read()));
        vTaskDelay(pdMS_TO_TICKS(5));
    }
}

void SerialConsole::handleInputChar(char c) {
    if (c == '\n' || c == '\r') {
        if (inputOverflow_) {
            domain::Command bad;
            std::strncpy(bad.errorCode, "too_long", sizeof(bad.errorCode) - 1);
            xQueueSend(cmdQueue_, &bad, 0);
        } else if (inputLength_ > 0) {
            input_[inputLength_] = '\0';
            const domain::Command command = domain::parseCommand(input_);
            xQueueSend(cmdQueue_, &command, 0);
        }
        inputLength_ = 0;
        inputOverflow_ = false;
        return;
    }
    if (inputLength_ < sizeof(input_) - 1) {
        input_[inputLength_++] = c;
    } else {
        inputOverflow_ = true;
    }
}
