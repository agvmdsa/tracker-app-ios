// Output channel for labelled training rows (Bridge pattern, alongside IPublisher's serial
// lines): a real implementation sends these over Wi-Fi/HTTP to the positioning API's
// `/fingerprints` endpoint; the host tests use a capturing fake. Implementations must never block
// the caller — queue internally and flush/retry on their own schedule, dropping and counting on
// overflow rather than blocking (Principle V).
#pragma once

#include "record_format.h"

class IFingerprintUploader {
public:
    virtual ~IFingerprintUploader() = default;
    // Queues one recorded row. `record`'s char pointers (node/beacon/session/point) are only
    // valid for the duration of this call — an implementation that doesn't upload immediately
    // must copy them into its own storage before returning, not retain the pointers. Returns
    // false when the row was dropped (queue full, or uploading is disabled/not configured) — the
    // caller counts the drop but recording continues regardless.
    virtual bool queueRow(const domain::RawRecord& record) = 0;
};
