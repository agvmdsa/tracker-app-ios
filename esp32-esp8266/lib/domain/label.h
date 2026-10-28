// CollectionLabel: the ground-truth label of a recording (data-model.md).
// Constraint: session and point are <= 16 chars of [A-Za-z0-9_-]; x and y are metres in the
// session's local frame. Pure logic: no Arduino headers.
#pragma once

namespace domain {

struct CollectionLabel {
    char session[17] = {0};
    char point[17] = {0};
    float x = 0.0f;
    float y = 0.0f;
};

// True when `token` is 1 to 16 characters from [A-Za-z0-9_-] (so it can never contain a comma).
bool isLabelToken(const char* token);

// Builds a label; false (and `out` unchanged) when session or point is not a valid token.
bool makeLabel(const char* session, const char* point, float x, float y, CollectionLabel& out);

}  // namespace domain
