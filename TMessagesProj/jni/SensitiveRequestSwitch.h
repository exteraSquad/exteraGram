#pragma once

#include <cstdint>
#include <string>

namespace SensitiveRequestSwitch {

constexpr int64_t disableDelayMs = 6ll * 60 * 60 * 1000;

bool init(const std::string &directory);

bool isDisabled();

int64_t disableAt();

int64_t scheduleDisable();

void enforce();

}
