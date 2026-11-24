#include "SensitiveRequestSwitch.h"

#include <atomic>
#include <cstdio>
#include <cstring>
#include <ctime>
#include <mutex>
#include <unistd.h>
#include <openssl/crypto.h>
#include <openssl/hmac.h>
#include <openssl/sha.h>

namespace SensitiveRequestSwitch {

namespace {

constexpr uint32_t fileMagic = 0x31535253;
constexpr size_t macSize = SHA256_DIGEST_LENGTH;
constexpr size_t payloadSize = 4 + 8;

constexpr uint8_t maskedSecret[32] = {
        0x2f, 0x14, 0xd1, 0x45, 0xb9, 0x9a, 0x2a, 0x02, 0xe3, 0x7b, 0xc5, 0xee, 0xbd, 0x90, 0x56, 0xc7,
        0xad, 0xba, 0x92, 0x05, 0x26, 0x6e, 0x6d, 0xf7, 0xb8, 0x19, 0x35, 0xd3, 0xdd, 0x0f, 0x5c, 0x76
};
constexpr uint8_t secretMask[32] = {
        0x2d, 0x21, 0xbe, 0xdc, 0x77, 0x83, 0x6f, 0x71, 0x34, 0xc4, 0x73, 0x7d, 0xa8, 0x3a, 0xe1, 0x20,
        0xd1, 0x02, 0x16, 0x49, 0x57, 0xb8, 0xab, 0x94, 0x63, 0xda, 0xb3, 0x66, 0x81, 0x82, 0xc9, 0x0c
};

std::atomic<bool> initialized{false};
std::atomic<int64_t> disableAtMs{0};
std::mutex writeMutex;
std::string statePath;
uint8_t macKey[SHA256_DIGEST_LENGTH];

int64_t nowMs() {
    struct timespec ts{};
    clock_gettime(CLOCK_REALTIME, &ts);
    return (int64_t) ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

void deriveKey(const std::string &path) {
    uint8_t secret[sizeof(maskedSecret)];
    for (size_t i = 0; i < sizeof(secret); i++) {
        secret[i] = maskedSecret[i] ^ secretMask[i];
    }
    SHA256_CTX ctx;
    SHA256_Init(&ctx);
    SHA256_Update(&ctx, secret, sizeof(secret));
    SHA256_Update(&ctx, path.data(), path.size());
    SHA256_Final(macKey, &ctx);
    OPENSSL_cleanse(secret, sizeof(secret));
}

void encodePayload(int64_t value, uint8_t *out) {
    for (int i = 0; i < 4; i++) {
        out[i] = (uint8_t) (fileMagic >> (8 * i));
    }
    for (int i = 0; i < 8; i++) {
        out[4 + i] = (uint8_t) ((uint64_t) value >> (8 * i));
    }
}

void sign(const uint8_t *payload, uint8_t *mac) {
    unsigned int length = 0;
    HMAC(EVP_sha256(), macKey, sizeof(macKey), payload, payloadSize, mac, &length);
}

int64_t readState() {
    FILE *file = fopen(statePath.c_str(), "rb");
    if (file == nullptr) {
        return 0;
    }
    uint8_t data[payloadSize + macSize];
    size_t read = fread(data, 1, sizeof(data), file);
    bool trailing = fgetc(file) != EOF;
    fclose(file);
    if (read != sizeof(data) || trailing) {
        return 0;
    }
    uint8_t expected[macSize];
    sign(data, expected);
    if (CRYPTO_memcmp(expected, data + payloadSize, macSize) != 0) {
        return 0;
    }
    uint32_t magic = 0;
    for (int i = 0; i < 4; i++) {
        magic |= (uint32_t) data[i] << (8 * i);
    }
    if (magic != fileMagic) {
        return 0;
    }
    uint64_t value = 0;
    for (int i = 0; i < 8; i++) {
        value |= (uint64_t) data[4 + i] << (8 * i);
    }
    return (int64_t) value > 0 ? (int64_t) value : 0;
}

bool writeState(int64_t value) {
    if (value == 0) {
        return unlink(statePath.c_str()) == 0 || access(statePath.c_str(), F_OK) != 0;
    }
    uint8_t data[payloadSize + macSize];
    encodePayload(value, data);
    sign(data, data + payloadSize);
    std::string temp = statePath + ".tmp";
    FILE *file = fopen(temp.c_str(), "wb");
    if (file == nullptr) {
        return false;
    }
    bool ok = fwrite(data, 1, sizeof(data), file) == sizeof(data);
    ok &= fflush(file) == 0;
    ok &= fsync(fileno(file)) == 0;
    ok &= fclose(file) == 0;
    if (!ok || rename(temp.c_str(), statePath.c_str()) != 0) {
        unlink(temp.c_str());
        return false;
    }
    return true;
}

}

bool init(const std::string &directory) {
    std::lock_guard<std::mutex> lock(writeMutex);
    if (initialized.load() || directory.empty()) {
        return false;
    }
    statePath = directory + "/sensitive_request_switch";
    deriveKey(statePath);
    int64_t value = readState();
    if (value == 0) {
        writeState(0);
    }
    disableAtMs.store(value);
    initialized.store(true);
    return true;
}

bool isDisabled() {
    int64_t value = disableAtMs.load();
    return value != 0 && nowMs() >= value;
}

int64_t disableAt() {
    return disableAtMs.load();
}

int64_t scheduleDisable() {
    std::lock_guard<std::mutex> lock(writeMutex);
    if (!initialized.load()) {
        return 0;
    }
    int64_t current = disableAtMs.load();
    if (current != 0) {
        return current;
    }
    int64_t value = nowMs() + disableDelayMs;
    if (!writeState(value)) {
        return 0;
    }
    disableAtMs.store(value);
    return value;
}

void enforce() {
    std::lock_guard<std::mutex> lock(writeMutex);
    disableAtMs.store(0);
    if (initialized.load()) {
        writeState(0);
    }
}

}
