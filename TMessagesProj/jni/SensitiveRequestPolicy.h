#pragma once

#include <cstdint>
#include <string>
#include <vector>

namespace SensitiveRequestPolicy {

enum class Verdict {
    Allow,
    Confirm,
    Reject
};

struct Classification {
    Verdict verdict;
    uint32_t constructor;
    uint32_t offset;
};

enum class DetailKind : int32_t {
    Recipient = 1,
    Chat = 2,
    User = 3,
    Bot = 4,
    Stars = 5,
    Grams = 6,
    Gift = 7,
    Form = 8,
    InvoiceHash = 9,
    GiftId = 10,
    Anonymous = 11,
    Upgrade = 12,
    GiftMessage = 13,
    SavedGiftId = 14
};

struct Detail {
    DetailKind kind;
    int64_t value;
    std::string text;
};

Classification classify(const uint8_t *data, uint32_t length);

std::vector<Detail> describe(const uint8_t *data, uint32_t length, uint32_t constructor);

std::vector<uint32_t> protectedConstructors();

const char *methodName(uint32_t constructor);

}
