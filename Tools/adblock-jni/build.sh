#!/usr/bin/env bash
set -euo pipefail

: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to the Android NDK directory}"

cargo build --release --target aarch64-linux-android
echo "arm64 done"

cargo build --release --target armv7-linux-androideabi
echo "armv7 done"

cargo build --release --target i686-linux-android
echo "i686 done"

cargo build --release --target x86_64-linux-android
echo "x86_64 done"

mkdir -p jniLibs/arm64-v8a
mkdir -p jniLibs/armeabi-v7a
mkdir -p jniLibs/x86
mkdir -p jniLibs/x86_64

cp target/aarch64-linux-android/release/libetgadblock.so jniLibs/arm64-v8a/libetgadblock.so
cp target/armv7-linux-androideabi/release/libetgadblock.so jniLibs/armeabi-v7a/libetgadblock.so
cp target/i686-linux-android/release/libetgadblock.so jniLibs/x86/libetgadblock.so
cp target/x86_64-linux-android/release/libetgadblock.so jniLibs/x86_64/libetgadblock.so

echo "All targets built successfully"
