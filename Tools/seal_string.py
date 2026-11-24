#!/usr/bin/env python3

import argparse
import base64
import getpass
import hashlib
import os

KEY = b"exteraGram/SealedString/v1"
SALT_LENGTH = 8


def keystream(salt, length):
    stream = b""
    counter = 0
    while len(stream) < length:
        stream += hashlib.sha256(KEY + salt + bytes([counter])).digest()
        counter += 1
    return stream[:length]


def seal(value):
    data = value.encode("utf-8")
    if len(data) > 32 * 256:
        raise SystemExit("value is too long")
    salt = os.urandom(SALT_LENGTH)
    body = bytes(a ^ b for a, b in zip(data, keystream(salt, len(data))))
    return base64.urlsafe_b64encode(salt + body).rstrip(b"=").decode("ascii")


def unseal(sealed):
    data = base64.urlsafe_b64decode(sealed + "=" * (-len(sealed) % 4))
    salt, body = data[:SALT_LENGTH], data[SALT_LENGTH:]
    return bytes(a ^ b for a, b in zip(body, keystream(salt, len(body)))).decode("utf-8")


def main():
    parser = argparse.ArgumentParser(
        description="Seal a string for SealedString.open() so it is not stored in the sources as plain text."
    )
    parser.add_argument("--open", metavar="SEALED", help="print the plain value of a sealed string")
    args = parser.parse_args()

    if args.open:
        print(unseal(args.open))
    else:
        print(seal(getpass.getpass("value: ")))


if __name__ == "__main__":
    main()
