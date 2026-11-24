#!/usr/bin/env bash

# This is the source code of the exteraGram plugins SDK.
#
# We do not and cannot prevent the use of our code,
# but be respectful and credit the original author.
#
# Copyright @exteraGram, 2022-2026
#
# Licensed under the GNU GPL v. 3 or later, with the additional terms
# in ADDITIONAL_TERMS.md at the root of the repository.

# Usage: sign-sdk.sh <archive> <version> <abi> <channel>
# Key:   SDK_SIGNING_KEY (PEM contents) or SDK_SIGNING_KEY_FILE (path to the PEM).

set -euo pipefail

log() {
    echo "sign-sdk: $*" >&2
}

fail() {
    log "$*"
    exit 1
}

[[ $# -eq 4 ]] || fail "usage: sign-sdk.sh <archive> <version> <abi> <channel>"

archive="$1"
version="$2"
abi="$3"
channel="$4"

[[ -f "$archive" ]] || fail "archive does not exist: $archive"
[[ "$channel" == "stable" || "$channel" == "beta" ]] || fail "channel must be stable or beta"

expected="${version}|0"
[[ "$channel" == "beta" ]] && expected="${version}|1"
packed="$(unzip -p "$archive" v.txt 2>/dev/null || true)"
[[ "$packed" == "$expected" ]] || fail "archive is built as '${packed}', but is being signed as '${expected}'"

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
digest="$(openssl dgst -sha256 "$archive" | awk '{print $NF}')"
payload="extera-pysdk|${version}|${abi}|${digest}"
signature="$(
    ARTIFACT_SIGNING_KEY="${SDK_SIGNING_KEY:-}" ARTIFACT_SIGNING_KEY_FILE="${SDK_SIGNING_KEY_FILE:-}" ARTIFACT_PUBLIC_KEY= \
        bash "$script_dir/sign-artifact.sh" "$payload"
)"

printf '%s\n' "$signature" > "$archive.sig"

cat > "$archive.message.txt" <<EOF
python_sdk_${channel}
version = ${version}
abi = ${abi}
signature = ${signature}
can_not_skip = false
EOF

log "signed $(basename "$archive") ($version, $abi, $channel)"
printf '%s\n' "$signature"
