#include "command_parser.h"

#include <strings.h>

#include <cmath>
#include <cstdlib>
#include <cstring>

namespace domain {

namespace {

constexpr std::size_t kMaxLine = 96;
constexpr std::size_t kMaxTokens = 6;

bool isSpace(char c) { return c == ' ' || c == '\t' || c == '\r' || c == '\n'; }

Command invalid(const char* code) {
    Command c;
    std::strncpy(c.errorCode, code, sizeof(c.errorCode) - 1);
    c.errorCode[sizeof(c.errorCode) - 1] = '\0';
    return c;
}

bool parseInt(const char* token, long minValue, long maxValue, int& out) {
    if (token == nullptr || *token == '\0') return false;
    char* end = nullptr;
    const long value = std::strtol(token, &end, 10);
    if (*end != '\0' || value < minValue || value > maxValue) return false;
    out = static_cast<int>(value);
    return true;
}

bool parseFloat(const char* token, float& out) {
    if (token == nullptr || *token == '\0') return false;
    char* end = nullptr;
    const float value = std::strtof(token, &end);
    if (*end != '\0' || !std::isfinite(value)) return false;
    out = value;
    return true;
}

bool equals(const char* a, const char* b) { return strcasecmp(a, b) == 0; }

}  // namespace

Command parseCommand(const char* line) {
    if (line == nullptr) return invalid("empty");
    const std::size_t length = std::strlen(line);
    if (length > kMaxLine) return invalid("too_long");

    char buffer[kMaxLine + 1];
    std::memcpy(buffer, line, length + 1);

    const char* tokens[kMaxTokens];
    std::size_t count = 0;
    char* p = buffer;
    while (*p != '\0') {
        while (*p != '\0' && isSpace(*p)) ++p;
        if (*p == '\0') break;
        if (count == kMaxTokens) return invalid("bad_args");
        tokens[count++] = p;
        while (*p != '\0' && !isSpace(*p)) ++p;
        if (*p != '\0') {
            *p = '\0';
            ++p;
        }
    }
    if (count == 0) return invalid("empty");

    const char* word = tokens[0];
    Command command;

    if (equals(word, "help") || equals(word, "status") || equals(word, "reboot")) {
        if (count != 1) return invalid("bad_args");
        command.type = equals(word, "help") ? CommandType::Help
                     : equals(word, "status") ? CommandType::Status
                                              : CommandType::Reboot;
        return command;
    }
    if (equals(word, "rec")) {
        if (count != 2) return invalid("bad_args");
        if (equals(tokens[1], "start")) command.type = CommandType::RecStart;
        else if (equals(tokens[1], "stop")) command.type = CommandType::RecStop;
        else return invalid("bad_value");
        return command;
    }
    if (equals(word, "config")) {
        if (count != 2) return invalid("bad_args");
        if (equals(tokens[1], "save")) command.type = CommandType::ConfigSave;
        else if (equals(tokens[1], "reset")) command.type = CommandType::ConfigReset;
        else return invalid("bad_value");
        return command;
    }
    if (equals(word, "role")) {
        if (count != 2) return invalid("bad_args");
        if (equals(tokens[1], "beacon")) command.role = Role::Beacon;
        else if (equals(tokens[1], "collector")) command.role = Role::Collector;
        else return invalid("bad_value");
        command.type = CommandType::Role;
        return command;
    }
    if (equals(word, "id")) {
        if (count != 2) return invalid("bad_args");
        if (!parseBeaconId(tokens[1], command.id)) return invalid("bad_id");
        command.type = CommandType::Id;
        return command;
    }
    if (equals(word, "power")) {
        if (count != 2) return invalid("bad_args");
        if (!parseInt(tokens[1], -40, 20, command.value)) return invalid("bad_value");
        command.type = CommandType::Power;
        return command;
    }
    if (equals(word, "interval")) {
        if (count != 2) return invalid("bad_args");
        if (!parseInt(tokens[1], 100, 10240, command.value)) return invalid("bad_value");
        command.type = CommandType::Interval;
        return command;
    }
    if (equals(word, "window")) {
        if (count != 2) return invalid("bad_args");
        if (!parseInt(tokens[1], 250, 3600000, command.value)) return invalid("bad_value");
        command.type = CommandType::Window;
        return command;
    }
    if (equals(word, "label")) {
        if (count != 5) return invalid("bad_args");
        float x = 0.0f;
        float y = 0.0f;
        if (!parseFloat(tokens[3], x) || !parseFloat(tokens[4], y)) return invalid("bad_label");
        if (!makeLabel(tokens[1], tokens[2], x, y, command.label)) return invalid("bad_label");
        command.type = CommandType::Label;
        return command;
    }
    return invalid("unknown_command");
}

}  // namespace domain
