// RoleFactory (Factory Method pattern): picks the role object for the validated configuration.
// The role objects are created once at boot (static storage) and handed in, so selecting a role
// never allocates. Pure logic: no Arduino headers.
#pragma once

#include "device_config.h"
#include "role.h"

class RoleFactory {
public:
    // A null pointer means that role is not available in this build.
    RoleFactory(IRole* beacon, IRole* collector) : beacon_(beacon), collector_(collector) {}

    IRole* select(domain::Role role) const;

private:
    IRole* beacon_;
    IRole* collector_;
};
