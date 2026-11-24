#!/bin/bash

# Does not use :TMessagesProj:generate<Variant>BaselineProfile on purpose: that task runs
# connectedAndroidTest, which uninstalls the tested app when it finishes and wipes the signed-in
# account with it. Driving the instrumentation directly keeps the app and its data in place.
#
# Usage:
#   ./generate_baseline_profiles.sh [--install|--run] [Variant...]
#
# Split the run with --install / --run when features have to be enabled on the device between
# installing the build and profiling it.

set -euo pipefail

cd "$(dirname "$0")/.."

export JAVA_HOME="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home}"

APP_ID="com.exteragram.messenger"
TEST_ID="com.exteragram.messenger.baselineprofile"
RUNNER="androidx.test.runner.AndroidJUnitRunner"
DEVICE_OUT="/sdcard/Android/media/$TEST_ID"

do_install=true
do_run=true
case "${1:-}" in
    --install) do_run=false; shift ;;
    --run) do_install=false; shift ;;
esac

variants=("$@")
if [ ${#variants[@]} -eq 0 ]; then
    variants=(FullAfat LiteAfat)
fi

if [ "$(adb devices | awk '$2 == "device"' | wc -l)" -eq 0 ]; then
    echo "No connected device: baseline profiles can only be generated on a real device." >&2
    exit 1
fi

if $do_run && ! adb shell pm path "$APP_ID" > /dev/null 2>&1; then
    echo "$APP_ID is not installed. Install and sign in first, otherwise the journey only" >&2
    echo "reaches the login screen and the profile is worthless." >&2
    exit 1
fi

workdir="$(mktemp -d)"
trap 'rm -rf "$workdir"' EXIT

for variant in "${variants[@]}"; do
    flavor="$(echo "${variant:0:1}" | tr '[:upper:]' '[:lower:]')${variant:1}"

    case "$variant" in
        *Beta) consumer="Debug" ;;
        *) consumer="Release" ;;
    esac
    profile_dir="TMessagesProj/src/${flavor}${consumer}/generated/baselineProfiles"

    if $do_install; then
        echo "==> Building $variant"
        ./gradlew ":TMessagesProj:assemble${variant}NonMinifiedRelease" \
                  ":baselineprofile:assemble${variant}NonMinifiedRelease"

        app_apk="$(find "TMessagesProj/build/outputs/apk/$flavor/nonMinifiedRelease" -name '*.apk' | head -1)"
        test_apk="$(find "baselineprofile/build/outputs/apk/$flavor/nonMinifiedRelease" -name '*.apk' | head -1)"

        if [ -z "$app_apk" ] || [ -z "$test_apk" ]; then
            echo "Could not find the built APKs for $variant." >&2
            exit 1
        fi

        echo "==> Installing $variant (keeping app data)"
        adb install -r -t "$app_apk" > /dev/null
        adb install -r -t "$test_apk" > /dev/null
    fi

    if ! $do_run; then
        echo "==> $variant installed. Enable whatever the profile should cover, then rerun with --run."
        continue
    fi

    echo "==> Running the journey on device"
    adb shell rm -rf "$DEVICE_OUT" > /dev/null 2>&1 || true
    adb shell am instrument -w \
        -e targetAppId "$APP_ID" \
        -e class "$TEST_ID.BaselineProfileGenerator" \
        "$TEST_ID/$RUNNER" 2>&1 | tee "$workdir/instrument.log"

    if ! grep -q "^OK (" "$workdir/instrument.log"; then
        echo "Instrumentation did not report success for $variant." >&2
        exit 1
    fi

    echo "==> Collecting profiles"
    rm -rf "$workdir/pull"
    mkdir -p "$workdir/pull"
    adb pull "$DEVICE_OUT" "$workdir/pull" > /dev/null

    mkdir -p "$profile_dir"
    python3 Tools/merge_baseline_profiles.py "$workdir/pull" "$profile_dir"

    echo "==> $variant done: $profile_dir"
done

if $do_run; then
    adb uninstall "$TEST_ID" > /dev/null 2>&1 || true
    echo "All profiles generated successfully."
fi
