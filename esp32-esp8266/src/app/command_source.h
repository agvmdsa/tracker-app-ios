// Source of parsed serial commands for the supervisor. Pure interface.
#pragma once

#include "command_parser.h"

class ICommandSource {
public:
    virtual ~ICommandSource() = default;
    // Non-blocking. Returns true and fills `out` when a command line was received (including
    // lines that failed to parse, so the supervisor can answer with ERR).
    virtual bool pollCommand(domain::Command& out) = 0;
};
