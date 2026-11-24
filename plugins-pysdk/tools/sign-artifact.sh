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

set -euo pipefail

[[ $# -eq 1 ]] || { echo "usage: sign-artifact.sh <payload>" >&2; exit 1; }

temporary="$(mktemp -d)"
trap 'rm -rf "$temporary"' EXIT

if [[ -n "${ARTIFACT_SIGNING_KEY_FILE:-}" ]]; then
    key="$ARTIFACT_SIGNING_KEY_FILE"
elif [[ -n "${ARTIFACT_SIGNING_KEY:-}" ]]; then
    key="$temporary/key.pem"
    (umask 077; printf '%s\n' "$ARTIFACT_SIGNING_KEY" > "$key")
else
    echo "Set ARTIFACT_SIGNING_KEY or ARTIFACT_SIGNING_KEY_FILE" >&2
    exit 1
fi

openssl ec -in "$key" -pubout -out "$temporary/public.pem" 2>/dev/null
curve="$(openssl ec -pubin -in "$temporary/public.pem" -text -noout 2>/dev/null)"
if [[ "$curve" != *"ASN1 OID: prime256v1"* ]]; then
    echo "The signing key must use the P-256 curve" >&2
    exit 1
fi
public_key="$(openssl pkey -pubin -in "$temporary/public.pem" -outform DER | openssl base64 -A)"
if [[ -n "${ARTIFACT_PUBLIC_KEY:-}" && "$public_key" != "$ARTIFACT_PUBLIC_KEY" ]]; then
    echo "The signing key does not match the configured public key" >&2
    exit 1
fi

printf '%s' "$1" | openssl dgst -sha256 -sign "$key" -out "$temporary/signature"
printf '%s' "$1" | openssl dgst -sha256 -verify "$temporary/public.pem" -signature "$temporary/signature" >/dev/null
printf 'Public signing key: %s\n' "$public_key" >&2
openssl base64 -A -in "$temporary/signature"
printf '\n'
