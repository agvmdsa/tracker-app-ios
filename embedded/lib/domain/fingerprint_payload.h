// Builds the JSON body for `POST /fingerprints` (the positioning API's training-data endpoint)
// from a batch of recorded rows. Field names and shapes match `FingerprintRecordIn` in the API
// repo — only the fields the ESP32 actually has (no orientation/altitude/motion: those are
// phone-only sensors). Pure logic: no Arduino headers, so the JSON shape is tested on the host.
#pragma once

#include <cstddef>

#include "record_format.h"

namespace domain {

// Writes a JSON array of up to `count` rows into `buf`. Returns the number of characters
// written, or -1 when any row would make the body exceed `cap` — in that case `buf` holds a
// partial, unusable body and the caller should retry with fewer rows rather than send it.
// `node`/`beacon`/`session`/`point` are never quote/backslash/control characters (the project's
// token rules already forbid them), so no JSON string escaping is needed here.
int formatFingerprintBatch(char* buf, std::size_t cap, const RawRecord* records, std::size_t count,
                           const char* platform);

}  // namespace domain
