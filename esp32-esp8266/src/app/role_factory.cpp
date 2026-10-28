#include "role_factory.h"

IRole* RoleFactory::select(domain::Role role) const {
    return role == domain::Role::Beacon ? beacon_ : collector_;
}
