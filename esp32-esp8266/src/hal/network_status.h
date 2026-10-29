// Network status port: what the `status` command reports about the Wi-Fi link and the uploads
// (the `# status net` line). Pure interface; HttpFingerprintUploader is the board implementation,
// and a board without Wi-Fi configured simply has none.
#pragma once

#include <cstddef>

class INetworkStatus {
public:
    virtual ~INetworkStatus() = default;
    // Writes the line's text after "# status net " (e.g. "wifi=connected ip=192.168.0.23 uploaded=40
    // failed=0 dropped=0"). Returns the number of characters written, or -1 when it doesn't fit.
    virtual int describe(char* buf, std::size_t cap) const = 0;
};
