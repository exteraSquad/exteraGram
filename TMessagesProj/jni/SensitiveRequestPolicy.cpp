#include "SensitiveRequestPolicy.h"

#include <cstddef>
#include <utility>
#include <openssl/sha.h>

namespace {

constexpr uint32_t transferStarGift = 0x7f18176a;
constexpr uint32_t updateStarGiftPrice = 0xedbe6ccb;
constexpr uint32_t convertStarGift = 0x74bf076b;
constexpr uint32_t upgradeStarGift = 0xaed6e4f5;
constexpr uint32_t getStarGiftWithdrawalUrl = 0xc8ca817b;
constexpr uint32_t withdrawStarGift = 0xec2030ae;
constexpr uint32_t deleteStarGift = 0x3cdaf8aa;
constexpr uint32_t craftStarGift = 0xb0f9684f;
constexpr uint32_t sendStarsForm = 0x7998c914;
constexpr uint32_t sendPaymentForm = 0x2d03522f;
constexpr uint32_t sendPaidReaction = 0x58bbcb50;
constexpr uint32_t fulfillStarsSubscription = 0xcc5bebb3;
constexpr uint32_t changeStarsSubscription = 0xc7770878;
constexpr uint32_t sendStarGiftOffer = 0x8fb86b41;
constexpr uint32_t getStarsRevenueWithdrawalUrl = 0x2433dc92;
constexpr uint32_t acceptLoginToken = 0xe894ad4d;
constexpr uint32_t exportAuthorization = 0xe5bfffcd;
constexpr uint32_t resetAuthorizations = 0x9fab0d1a;
constexpr uint32_t resetAuthorization = 0xdf77f3bc;
constexpr uint32_t resetWebAuthorization = 0x2d01b9ef;
constexpr uint32_t resetWebAuthorizations = 0x682d2594;
constexpr uint32_t deleteAccount = 0x418d4e0b;
constexpr uint32_t updatePasswordSettings = 0xa59b102f;
constexpr uint32_t resetPassword = 0x9308ce1b;
constexpr uint32_t changePhone = 0x70c32edb;
constexpr uint32_t updateConnectedBot = 0x66a08c7e;
constexpr uint32_t acceptAuthorization = 0xf3ed4c73;
constexpr uint32_t editCreator = 0x8f38cd1f;
constexpr uint32_t editAdmin = 0x9a98ad68;
constexpr uint32_t deleteChannel = 0xc0111fe3;
constexpr uint32_t updateChannelUsername = 0x3514b3de;
constexpr uint32_t toggleChannelUsername = 0x50f24105;
constexpr uint32_t updateUsername = 0x3e0bdd7c;
constexpr uint32_t toggleUsername = 0x58d6b376;
constexpr uint32_t setAuthorizationTTL = 0xbf899aa0;
constexpr uint32_t sendTransfer = 0xd9e5d9e2;
constexpr uint32_t exportSecretPhrase = 0x9077c9ac;
constexpr uint32_t replaceWallet = 0xd8c72eec;
constexpr uint32_t enableBackup = 0x4500c525;
constexpr uint32_t disableBackup = 0x0b0bf0da;
constexpr uint32_t tonConnectSubmitConnectResult = 0xc2c00779;
constexpr uint32_t tonConnectSubmitResponse = 0xfac67d3c;

constexpr uint32_t sendMessage = 0xfef48f62;
constexpr uint32_t sendMedia = 0x0330e77f;
constexpr uint32_t forwardMessages = 0x13704a7c;
constexpr uint32_t sendMultiMedia = 0x1bf89d74;
constexpr uint32_t sendInlineBotResult = 0xc0cf7646;
constexpr uint32_t searchPosts = 0xf2c4f24d;
constexpr uint32_t sendGroupCallMessage = 0xb1d11410;

struct ProtectedRequest {
    uint32_t constructor;
    const char *name;
};

constexpr ProtectedRequest confirmRequired[] = {
        {transferStarGift, "payments.transferStarGift"},
        {updateStarGiftPrice, "payments.updateStarGiftPrice"},
        {convertStarGift, "payments.convertStarGift"},
        {upgradeStarGift, "payments.upgradeStarGift"},
        {getStarGiftWithdrawalUrl, "payments.getStarGiftWithdrawalUrl"},
        {withdrawStarGift, "payments.withdrawStarGift"},
        {deleteStarGift, "payments.deleteStarGift"},
        {craftStarGift, "payments.craftStarGift"},
        {sendStarsForm, "payments.sendStarsForm"},
        {sendPaymentForm, "payments.sendPaymentForm"},
        {sendPaidReaction, "messages.sendPaidReaction"},
        {fulfillStarsSubscription, "payments.fulfillStarsSubscription"},
        {changeStarsSubscription, "payments.changeStarsSubscription"},
        {sendStarGiftOffer, "payments.sendStarGiftOffer"},
        {getStarsRevenueWithdrawalUrl, "payments.getStarsRevenueWithdrawalUrl"},
        {acceptLoginToken, "auth.acceptLoginToken"},
        {exportAuthorization, "auth.exportAuthorization"},
        {resetAuthorizations, "auth.resetAuthorizations"},
        {resetAuthorization, "account.resetAuthorization"},
        {resetWebAuthorization, "account.resetWebAuthorization"},
        {resetWebAuthorizations, "account.resetWebAuthorizations"},
        {deleteAccount, "account.deleteAccount"},
        {updatePasswordSettings, "account.updatePasswordSettings"},
        {resetPassword, "account.resetPassword"},
        {changePhone, "account.changePhone"},
        {updateConnectedBot, "account.updateConnectedBot"},
        {acceptAuthorization, "account.acceptAuthorization"},
        {editCreator, "channels.editCreator"},
        {editAdmin, "channels.editAdmin"},
        {deleteChannel, "channels.deleteChannel"},
        {updateChannelUsername, "channels.updateUsername"},
        {toggleChannelUsername, "channels.toggleUsername"},
        {updateUsername, "account.updateUsername"},
        {toggleUsername, "account.toggleUsername"},
        {setAuthorizationTTL, "account.setAuthorizationTTL"},
        {sendTransfer, "wallet.sendTransfer"},
        {exportSecretPhrase, "wallet.exportSecretPhrase"},
        {replaceWallet, "wallet.replaceWallet"},
        {enableBackup, "wallet.enableBackup"},
        {disableBackup, "wallet.disableBackup"},
        {tonConnectSubmitConnectResult, "wallet.tonConnectSubmitConnectResult"},
        {tonConnectSubmitResponse, "wallet.tonConnectSubmitResponse"},
};

struct FlaggedRequest {
    uint32_t constructor;
    uint32_t confirmationFlags;
    const char *name;
};

constexpr FlaggedRequest flaggedRequests[] = {
        {sendMessage, (1u << 21) | (1u << 19), "messages.sendMessage"},
        {sendMedia, (1u << 21) | (1u << 19), "messages.sendMedia"},
        {forwardMessages, (1u << 21) | (1u << 19), "messages.forwardMessages"},
        {sendMultiMedia, (1u << 21) | (1u << 19), "messages.sendMultiMedia"},
        {sendInlineBotResult, 1u << 21, "messages.sendInlineBotResult"},
        {searchPosts, 1u << 2, "channels.searchPosts"},
        {sendGroupCallMessage, 1u << 0, "phone.sendGroupCallMessage"},
};

constexpr uint32_t rejectedWrappers[] = {
        0xcb9f372d, // invokeAfterMsg
        0x3dc4b4f0, // invokeAfterMsgs
        0xda9b0d0d, // invokeWithLayer
        0xc1cd5ea9, // initConnection
        0xbf9459b7, // invokeWithoutUpdates
        0xdd289f8e, // invokeWithBusinessConnection
        0x1df92984, // invokeWithGooglePlayIntegrity
        0xadbb0f94, // invokeWithReCaptcha
        0x0dae54f8, // invokeWithApnsSecret
};

constexpr uint32_t invokeWithMessagesRange = 0x365275f2;
constexpr uint32_t messageRange = 0x0ae30253;
constexpr uint32_t invokeWithTakeout = 0xaca9fd2e;

constexpr uint32_t maxWrapperDepth = 4;

constexpr uint32_t vectorConstructor = 0x1cb5c415;
constexpr uint32_t inputPeerSelf = 0x7da07ec9;
constexpr uint32_t inputPeerChat = 0x35a95cb9;
constexpr uint32_t inputPeerUser = 0xdde8a54c;
constexpr uint32_t inputPeerChannel = 0x27bcbbfc;
constexpr uint32_t inputPeerUserFromMessage = 0xa87b0a1c;
constexpr uint32_t inputPeerChannelFromMessage = 0xbd2a0840;
constexpr uint32_t inputUserSelf = 0xf7c1b13f;
constexpr uint32_t inputUser = 0xf21158c6;
constexpr uint32_t inputUserFromMessage = 0x1da448e2;
constexpr uint32_t inputChannel = 0xf35aec28;
constexpr uint32_t inputChannelFromMessage = 0x5b934f9d;
constexpr uint32_t starsAmount = 0xbbb6b4a3;
constexpr uint32_t starsTonAmount = 0x74aee3e0;
constexpr uint32_t inputSavedStarGiftUser = 0x69279795;
constexpr uint32_t inputSavedStarGiftSlug = 0x2085c238;
constexpr uint32_t inputSavedStarGiftChat = 0xf101aa7f;

constexpr uint32_t inputInvoiceStarGift = 0xe8625e92;

constexpr uint32_t inputInvoiceStarGiftResale = 0xe9b0c658;

constexpr uint32_t inputInvoiceStarGiftTransfer = 0x4a5f5bd9;

constexpr uint32_t inputInvoiceStarGiftUpgrade = 0x4d818d5d;

constexpr uint32_t inputInvoicePremiumGiftStars = 0xdabab2ef;

constexpr uint32_t maxNestedPeerDepth = 2;
constexpr uint32_t maxDescribedGifts = 8;
constexpr uint32_t maxTextLength = 128;

template<size_t N>
bool contains(const uint32_t (&values)[N], uint32_t value) {
    for (uint32_t candidate : values) {
        if (candidate == value) {
            return true;
        }
    }
    return false;
}

uint32_t readUint32(const uint8_t *data) {
    return (uint32_t) data[0] | ((uint32_t) data[1] << 8) | ((uint32_t) data[2] << 16) | ((uint32_t) data[3] << 24);
}

class Reader {
public:
    Reader(const uint8_t *data, uint32_t length) : data(data), length(length) {
    }

    bool ok() const {
        return valid;
    }

    void fail() {
        valid = false;
    }

    uint32_t u32() {
        if (!require(4)) {
            return 0;
        }
        uint32_t value = readUint32(data + position);
        position += 4;
        return value;
    }

    int64_t i64() {
        if (!require(8)) {
            return 0;
        }
        uint64_t low = readUint32(data + position);
        uint64_t high = readUint32(data + position + 4);
        position += 8;
        return (int64_t) (low | (high << 32));
    }

    void skip(uint32_t count) {
        if (require(count)) {
            position += count;
        }
    }

    void skipVector(uint32_t elementSize) {
        if (u32() != vectorConstructor) {
            fail();
            return;
        }
        uint32_t count = u32();
        if (!valid || count > (length - position) / elementSize) {
            fail();
            return;
        }
        position += count * elementSize;
    }

    std::string bytes() {
        if (!require(1)) {
            return {};
        }
        uint32_t size = data[position];
        uint32_t header = 1;
        if (size == 254) {
            if (!require(4)) {
                return {};
            }
            size = (uint32_t) data[position + 1] | ((uint32_t) data[position + 2] << 8) | ((uint32_t) data[position + 3] << 16);
            header = 4;
        } else if (size == 255) {
            fail();
            return {};
        }
        uint32_t padded = (header + size + 3) & ~3u;
        if (!require(padded)) {
            return {};
        }
        std::string value((const char *) data + position + header, size);
        position += padded;
        return value;
    }

private:
    bool require(uint32_t count) {
        if (!valid || length - position < count) {
            valid = false;
            return false;
        }
        return true;
    }

    const uint8_t *data;
    uint32_t length;
    uint32_t position = 0;
    bool valid = true;
};

bool readPeer(Reader &reader, int64_t &dialogId, uint32_t depth = 0) {
    uint32_t constructor = reader.u32();
    switch (constructor) {
        case inputPeerSelf:
            dialogId = 0;
            return reader.ok();
        case inputPeerChat:
        case inputPeerUser:
        case inputPeerChannel: {
            int64_t id = reader.i64();
            if (id <= 0) {
                reader.fail();
                return false;
            }
            dialogId = constructor == inputPeerUser ? id : -id;
            if (constructor != inputPeerChat) {
                reader.skip(8);
            }
            return reader.ok();
        }
        case inputPeerUserFromMessage:
        case inputPeerChannelFromMessage: {
            int64_t ignored;
            if (depth >= maxNestedPeerDepth) {
                reader.fail();
                return false;
            }
            readPeer(reader, ignored, depth + 1);
            reader.skip(4);
            int64_t id = reader.i64();
            if (id <= 0) {
                reader.fail();
                return false;
            }
            dialogId = constructor == inputPeerUserFromMessage ? id : -id;
            return reader.ok();
        }
        default:
            reader.fail();
            return false;
    }
}

bool readUser(Reader &reader, int64_t &userId) {
    switch (reader.u32()) {
        case inputUserSelf:
            userId = 0;
            return reader.ok();
        case inputUser:
            userId = reader.i64();
            if (userId <= 0) {
                reader.fail();
                return false;
            }
            reader.skip(8);
            return reader.ok();
        case inputUserFromMessage: {
            int64_t ignored;
            readPeer(reader, ignored, 1);
            reader.skip(4);
            userId = reader.i64();
            if (userId <= 0) {
                reader.fail();
                return false;
            }
            return reader.ok();
        }
        default:
            reader.fail();
            return false;
    }
}

bool readChannel(Reader &reader, int64_t &dialogId) {
    switch (reader.u32()) {
        case inputChannel: {
            int64_t id = reader.i64();
            if (id <= 0) {
                reader.fail();
                return false;
            }
            dialogId = -id;
            reader.skip(8);
            return reader.ok();
        }
        case inputChannelFromMessage: {
            int64_t ignored;
            readPeer(reader, ignored, 1);
            reader.skip(4);
            int64_t id = reader.i64();
            if (id <= 0) {
                reader.fail();
                return false;
            }
            dialogId = -id;
            return reader.ok();
        }
        default:
            reader.fail();
            return false;
    }
}

void addPeer(Reader &reader, std::vector<SensitiveRequestPolicy::Detail> &details, SensitiveRequestPolicy::DetailKind kind) {
    int64_t dialogId;
    if (readPeer(reader, dialogId)) {
        details.push_back({kind, dialogId, {}});
    }
}

void addUser(Reader &reader, std::vector<SensitiveRequestPolicy::Detail> &details, SensitiveRequestPolicy::DetailKind kind) {
    int64_t userId;
    if (readUser(reader, userId)) {
        details.push_back({kind, userId, {}});
    }
}

void addChannel(Reader &reader, std::vector<SensitiveRequestPolicy::Detail> &details) {
    int64_t dialogId;
    if (readChannel(reader, dialogId)) {
        details.push_back({SensitiveRequestPolicy::DetailKind::Chat, dialogId, {}});
    }
}

void addAmount(Reader &reader, std::vector<SensitiveRequestPolicy::Detail> &details) {
    uint32_t constructor = reader.u32();
    int64_t amount = reader.i64();
    if (constructor == starsAmount) {
        reader.skip(4);
        if (reader.ok()) {
            details.push_back({SensitiveRequestPolicy::DetailKind::Stars, amount, {}});
        }
    } else if (constructor == starsTonAmount) {
        if (reader.ok()) {
            details.push_back({SensitiveRequestPolicy::DetailKind::Grams, amount, {}});
        }
    } else {
        reader.fail();
    }
}

void addGiftSlug(std::vector<SensitiveRequestPolicy::Detail> &details, std::string slug) {
    if (!slug.empty() && slug.size() <= maxTextLength) {
        details.push_back({SensitiveRequestPolicy::DetailKind::Gift, 0, std::move(slug)});
    }
}

void addSavedGift(Reader &reader, std::vector<SensitiveRequestPolicy::Detail> &details) {
    switch (reader.u32()) {
        case inputSavedStarGiftUser:
            details.push_back({SensitiveRequestPolicy::DetailKind::GiftMessage, reader.u32(), {}});
            break;
        case inputSavedStarGiftSlug: {
            std::string slug = reader.bytes();
            if (reader.ok()) {
                addGiftSlug(details, std::move(slug));
            }
            break;
        }
        case inputSavedStarGiftChat: {
            addPeer(reader, details, SensitiveRequestPolicy::DetailKind::Chat);
            details.push_back({SensitiveRequestPolicy::DetailKind::SavedGiftId, reader.i64(), {}});
            break;
        }
        default:
            reader.fail();
            break;
    }
}

void addInvoice(Reader &reader, std::vector<SensitiveRequestPolicy::Detail> &details) {
    using SensitiveRequestPolicy::DetailKind;
    uint32_t invoice = reader.u32();
    switch (invoice) {
        case inputInvoiceStarGift: {
            uint32_t flags = reader.u32();
            addPeer(reader, details, DetailKind::Recipient);
            details.push_back({DetailKind::GiftId, reader.i64(), {}});
            details.push_back({DetailKind::Anonymous, (flags & 1) != 0, {}});
            details.push_back({DetailKind::Upgrade, (flags & 4) != 0, {}});
            break;
        }
        case inputInvoiceStarGiftResale: {
            uint32_t flags = reader.u32();
            addGiftSlug(details, reader.bytes());
            addPeer(reader, details, DetailKind::Recipient);
            details.push_back({DetailKind::Anonymous, (flags & 4) == 0, {}});
            break;
        }
        case inputInvoiceStarGiftTransfer:
            addSavedGift(reader, details);
            addPeer(reader, details, DetailKind::Recipient);
            break;
        case inputInvoiceStarGiftUpgrade:
            reader.skip(4);
            addSavedGift(reader, details);
            break;
        case inputInvoicePremiumGiftStars:
            reader.skip(4);
            addUser(reader, details, DetailKind::Recipient);
            break;
        default:
            break;
    }
}

}

namespace SensitiveRequestPolicy {

Classification classify(const uint8_t *data, uint32_t length) {
    if (data == nullptr) {
        return {Verdict::Reject, 0, 0};
    }
    uint32_t offset = 0;
    for (uint32_t depth = 0; depth <= maxWrapperDepth; depth++) {
        if (length - offset < 4) {
            return {Verdict::Reject, 0, offset};
        }
        uint32_t constructor = readUint32(data + offset);
        if (constructor == invokeWithMessagesRange) {
            if (length - offset < 16 || readUint32(data + offset + 4) != messageRange) {
                return {Verdict::Reject, constructor, offset};
            }
            offset += 16;
            continue;
        }
        if (constructor == invokeWithTakeout) {
            if (length - offset < 12) {
                return {Verdict::Reject, constructor, offset};
            }
            offset += 12;
            continue;
        }
        if (contains(rejectedWrappers, constructor)) {
            return {Verdict::Reject, constructor, offset};
        }
        for (const auto &request : confirmRequired) {
            if (request.constructor == constructor) {
                return {Verdict::Confirm, constructor, offset};
            }
        }
        for (const auto &request : flaggedRequests) {
            if (request.constructor == constructor) {
                if (length - offset < 8) {
                    return {Verdict::Reject, constructor, offset};
                }
                if ((readUint32(data + offset + 4) & request.confirmationFlags) != 0) {
                    return {Verdict::Confirm, constructor, offset};
                }
                break;
            }
        }
        return {Verdict::Allow, constructor, offset};
    }
    return {Verdict::Reject, 0, offset};
}

std::vector<Detail> describe(const uint8_t *data, uint32_t length, uint32_t constructor) {
    std::vector<Detail> details;
    if (data == nullptr) {
        return details;
    }
    Reader reader(data, length);
    if (reader.u32() != constructor) {
        return details;
    }
    switch (constructor) {
        case sendStarsForm: {
            details.push_back({DetailKind::Form, reader.i64(), {}});
            if (!reader.ok()) {
                break;
            }
            uint8_t digest[SHA256_DIGEST_LENGTH];
            SHA256(data + 12, length - 12, digest);
            const char *hex = "0123456789ABCDEF";
            std::string hash;
            hash.reserve(SHA256_DIGEST_LENGTH * 2);
            for (uint8_t byte : digest) {
                hash.push_back(hex[byte >> 4]);
                hash.push_back(hex[byte & 15]);
            }
            details.push_back({DetailKind::InvoiceHash, 0, std::move(hash)});
            addInvoice(reader, details);
            break;
        }
        case sendPaymentForm:
            reader.skip(4);
            reader.skip(8);
            addInvoice(reader, details);
            break;
        case transferStarGift:
            addSavedGift(reader, details);
            addPeer(reader, details, DetailKind::Recipient);
            break;
        case updateStarGiftPrice:
            addSavedGift(reader, details);
            addAmount(reader, details);
            break;
        case convertStarGift:
        case deleteStarGift:
            addSavedGift(reader, details);
            break;
        case upgradeStarGift:
        case getStarGiftWithdrawalUrl:
        case withdrawStarGift:
            reader.skip(4);
            addSavedGift(reader, details);
            break;
        case craftStarGift: {
            if (reader.u32() != vectorConstructor) {
                break;
            }
            uint32_t count = reader.u32();
            for (uint32_t i = 0; i < count && i < maxDescribedGifts && reader.ok(); i++) {
                addSavedGift(reader, details);
            }
            break;
        }
        case sendPaidReaction: {
            reader.skip(4);
            addPeer(reader, details, DetailKind::Recipient);
            reader.skip(4);
            uint32_t count = reader.u32();
            if (reader.ok()) {
                details.push_back({DetailKind::Stars, count, {}});
            }
            break;
        }
        case sendStarGiftOffer: {
            reader.skip(4);
            addPeer(reader, details, DetailKind::Recipient);
            std::string slug = reader.bytes();
            if (!reader.ok()) {
                break;
            }
            addGiftSlug(details, std::move(slug));
            addAmount(reader, details);
            break;
        }
        case sendMessage:
        case sendMedia:
        case sendMultiMedia:
        case sendInlineBotResult:
            reader.skip(4);
            addPeer(reader, details, DetailKind::Recipient);
            break;
        case forwardMessages: {
            int64_t ignored;
            reader.skip(4);
            readPeer(reader, ignored);
            reader.skipVector(4);
            reader.skipVector(8);
            addPeer(reader, details, DetailKind::Recipient);
            break;
        }
        case editCreator:
            addChannel(reader, details);
            addUser(reader, details, DetailKind::User);
            break;
        case editAdmin:
            reader.skip(4);
            addChannel(reader, details);
            addUser(reader, details, DetailKind::User);
            break;
        case deleteChannel:
        case updateChannelUsername:
        case toggleChannelUsername:
            addChannel(reader, details);
            break;
        case updateConnectedBot: {
            uint32_t flags = reader.u32();
            if ((flags & (1u << 0)) != 0) {
                reader.skip(8);
            }
            addUser(reader, details, DetailKind::Bot);
            break;
        }
        case sendTransfer: {
            uint32_t flags = reader.u32();
            reader.bytes();
            if ((flags & (1u << 0)) != 0) {
                reader.bytes();
            }
            addUser(reader, details, DetailKind::Recipient);
            break;
        }
        case getStarsRevenueWithdrawalUrl: {
            uint32_t flags = reader.u32();
            addPeer(reader, details, DetailKind::Chat);
            if ((flags & (1u << 1)) != 0) {
                int64_t amount = reader.i64();
                if (reader.ok()) {
                    details.push_back({(flags & (1u << 0)) != 0 ? DetailKind::Grams : DetailKind::Stars, amount, {}});
                }
            }
            break;
        }
        default:
            break;
    }
    if (!reader.ok()) {
        details.clear();
    }
    return details;
}

std::vector<uint32_t> protectedConstructors() {
    std::vector<uint32_t> constructors;
    for (const auto &request : confirmRequired) {
        constructors.push_back(request.constructor);
    }
    for (const auto &request : flaggedRequests) {
        constructors.push_back(request.constructor);
    }
    return constructors;
}

const char *methodName(uint32_t constructor) {
    for (const auto &request : confirmRequired) {
        if (request.constructor == constructor) {
            return request.name;
        }
    }
    for (const auto &request : flaggedRequests) {
        if (request.constructor == constructor) {
            return request.name;
        }
    }
    return nullptr;
}

}
