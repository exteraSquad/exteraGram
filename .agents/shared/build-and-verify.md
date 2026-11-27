# Build and Verify

The practical loop for changing exteraGram: what to run, in what order, what each failure means.
`AGENTS.md` states the policy; this file is the reference an agent comes back to.

## Environment

| Tool | Version in this checkout | Where it comes from |
| --- | --- | --- |
| Gradle | 9.4.1 | `gradle/wrapper/gradle-wrapper.properties` |
| Android Gradle Plugin | 9.1.1 | root `build.gradle` buildscript classpath |
| Kotlin | 2.3.20 | root `build.gradle` + `kotlin-stdlib` dependency |
| JDK | 17 | `compileOptions` / `jvmTarget` |
| `compileSdkVersion` | 37 | `TMessagesProj/build.gradle` |
| `targetSdkVersion` | 36 | `TMessagesProj/build.gradle` |
| `minSdkVersion` | 24 | `TMessagesProj/build.gradle` |
| build-tools | 36.0.0 | `TMessagesProj/build.gradle` |
| NDK | 29.0.14206865 | `TMessagesProj/build.gradle` |
| CMake | 4.0.0+ | `externalNativeBuild` |
| Python | 3.11 | Chaquopy + `plugins-pysdk` |

Gradle daemon settings come from `gradle.properties`: 8 GB heap, `MaxMetaspaceSize=1024m`,
`org.gradle.parallel=true`, `org.gradle.caching=true`.

## Required inputs that are not in git

- `API_KEYS` in the repository root:

  ```
  APP_ID = 123456
  APP_HASH = "…"
  MAPS_V2_API = …

  SIGNING_KEY_PASSWORD = …
  SIGNING_KEY_ALIAS = …
  SIGNING_KEY_STORE_PASSWORD = …
  ```

- `TMessagesProj/config/exteraGram.jks`, the keystore named by those values. Debug and release both use
  it.
- `local.properties` (optional) for `python.build` or an SDK path.
- Checked-out submodules: `git submodule update --init --recursive`.

When one of these is missing, the build cannot succeed. Say which input is missing and stop; do not
generate placeholder keys, do not add a debug keystore, do not disable signing.

## Task naming

`assemble<AppType><Abi><BuildType>`, for example:

| Goal | Task |
| --- | --- |
| Normal development build | `:TMessagesProj:assembleFullArm64Debug` |
| Lite checks | `:TMessagesProj:assembleLiteArm64Debug` |
| Parity of both flavors in one run (universal ARM, beta) | `assembleLiteAndFullBetaDebug` |
| Release artifacts (R8 on) | `assembleLiteAndFullAfatRelease` |
| Single universal release | `:TMessagesProj:assembleFullAfatRelease` |
| Beta-named debug | `:TMessagesProj:assembleFullBetaDebug` |

`full` has no `x86`/`x64` variant; `beforeVariants` disables it. `beta` exists for debug-type builds
only. `armv7` and `x86` builds are rarely needed; run them only when the change is ABI-specific.

Output: `TMessagesProj/build/outputs/apk/exteraGram-<appType>-<abi>-<yyyyMMdd>.apk`.
Version code: `APP_VERSION_CODE * 10 + abiIndex` (`armv7`=1, `x86`=2, `arm64`=5, `x64`=6,
universal=9).

## What a build really does

1. Merges the resource roots `TMessagesProj/src/main/res`, `res-appicons`, `res-solar`, `res-remix` and
   `res-tabs` (all under `src/main`, from the `sourceSets` block). `res-appicons` content is produced by
   `:TMessagesProj:generateAppIcons` ahead of time, not during a normal build.
2. For `full`: runs `plugins-pysdk/tools/build_sdk.py` (needs Python 3.11) and downloads the plugin
   analyzer (`Tools/download_analyzer.py`, cached under `Tools/temp`).
3. Configures and compiles `TMessagesProj/jni` with CMake for the requested ABI, including tgnet,
   tgcalls, WebRTC, tdlib, ffmpeg, libvpx and the rest of `third_party` from prebuilt libraries and
   submodules.
4. Compiles Kotlin and Java for `main` + the flavor source set, runs KSP (Room), then packages,
   minifies (release) and signs.

That is why the first build of a checkout takes a long time and why a full rebuild is not a debugging
step. Never start several builds of this checkout at once, and never run `clean` as a reflex.

## The loop

1. Change the code.
2. Compile the smallest variant that can prove it:
   - Kotlin/Java in `main` or `full` → `:TMessagesProj:assembleFullArm64Debug`;
   - anything plugin-facing or inside `src/lite` → add `:TMessagesProj:assembleLiteArm64Debug`, or use
     `assembleLiteAndFullBetaDebug`;
   - native → the same variant build, which links the `.so` for that ABI.
3. Run the host checks that apply (below).
4. If the change is visual or behavioral and a device is available, install and look at it.
5. Report exactly what ran and what it printed. "It compiles" is one claim; "it works" is another.

## Host checks

### NotificationCenter observers and delegates

```bash
python3 Tools/check_observers.py                       # default severity: medium and above
python3 Tools/check_observers.py --filter Ui/Cells     # one area
python3 Tools/check_observers.py --all                 # include info-level findings
python3 Tools/check_observers.py --json                # machine-readable
```

The script pairs `addObserver`/`removeObserver` by observer, notification id and `NotificationCenter`
instance, and covers `ObserversGroup`, `addDelegate`/`removeDelegate` registries,
`AnimatedEmojiDrawable.addView/removeView` and `attach`/`detach` pairs. It scans
`TMessagesProj/src` (main plus `full` and `lite`) by default. Run it when a change touches any of those
APIs; a new finding in the changed area is a bug in the change.

### Inline math, rates, units and time zones

```bash
bash Tools/inline/run_tests.sh
```

It compiles `com.exteragram.messenger.inline.{math,rates,units,time,docs}` with the Kotlin compiler
already in the Gradle cache and runs `Tools/inline/MathExpressionTests.kt` on the host. It needs bash,
a previous Gradle build (to populate `~/.gradle/caches`) and no device. Use it for any change to inline
expression evaluation or formatting.

### Plugins SDK

```bash
python3.11 -m unittest discover -s plugins-pysdk/tests
```

Host-only tests with stubbed Java packages (`plugins-pysdk/tests/stubs.py`). No Android, no device.

### Icon and locale generation

```bash
./gradlew :TMessagesProj:generateAppIcons
```

Needs `rsvg-convert`, `cwebp`, `dwebp`. Output is committed: `src/main/res-appicons/**`,
`src/main/AndroidManifest-appicons.xml`, `.../appicons/GeneratedAppIcons.java`. Never hand-edit those.

## On-device

```bash
adb devices
adb install -r TMessagesProj/build/outputs/apk/exteraGram-full-arm64-<date>.apk
adb logcat -s tmessages
```

- Debug builds are `debuggable false` and signed with the release key: no `adb run-as`, no JDWP, no
  `am start` with a debugger. Work from logs and from what the screen shows.
- App-level logs go through `FileLog` (logcat tag `tmessages`) and `AppUtils.log` (tag is the caller
  class name). Fork diagnostics are prefixed `[Extera]`.
- Crash triage: `adb logcat -b crash`, then the app's `tdata`/log directory if the user provides it.
- Behavior that depends on server state, account state or plugins should be described as observed under
  specific conditions, not generalized.

## Baseline profiles and benchmarks

`:baselineprofile` is an `com.android.test` module with `BaselineProfileGenerator` and
`StartupBenchmarks`. It is a Macrobenchmark test that needs a connected device, a profileable build and
`useConnectedDevices = true`; `Tools/merge_baseline_profiles.py` merges the per-test profiles into
`startup-prof.txt` and `baseline-prof.txt`.

The results land in `TMessagesProj/src/<variant>/generated/baselineProfiles/baseline-prof.txt` and are
committed. `TMessagesProj/src/main/baselineProfiles/*.txt` (for example `emoji-pack.txt`) holds extra
hand-written rules. This is performance work: run it only when the user asks, and never present it as
a functional test.

## Failure modes and what they mean

| Output | Meaning | Action |
| --- | --- | --- |
| `Submodule … is not checked out` from `settings.gradle` | fresh clone without submodules | `git submodule update --init --recursive` |
| `APP_ID` / `APP_HASH` null or empty in a generated `BuildConfig` | `API_KEYS` missing or malformed | ask the user for keys; do not stub them |
| Signing task fails on a missing `.jks` | keystore absent | ask the user; do not switch to a debug keystore |
| `Could not find com.exteragram.hook:…` | `Tools/maven` artifact missing or repository removed | restore the repository entry; do not fetch from an ad-hoc URL |
| `Unresolved reference` in `lite` for a plugin class | full-only code reached `main` or lite lost its stub | fix the source-set boundary |
| `Chaquopy … python` failure on `full` | Python 3.11 not on PATH and not configured | set `python.build`/`BUILD_PYTHON` or report |
| Analyzer download error | offline machine without `Tools/temp` | report; do not vendor the binary |
| `OutOfMemoryError`, daemon hangs | JVM/daemon state | retry once; then report with the failing task and the daemon log |
| Compile passes, release crashes | missing R8 keep rule | add the narrowest keep rule next to the closest analogue |
| `Execution failed for task ':TMessagesProj:…'` with native errors | CMake/NDK/ABI mismatch | check the ABI, the NDK version and whether the change edited a submodule |

When a build fails, read the first error, not the last. Gradle repeats the summary at the end.

## Things not to do while verifying

- Do not run `clean`, delete `build/`, `TMessagesProj/.cxx/` or the Gradle cache to "fix" a failure
  without a stated reason; it costs a long rebuild and usually changes nothing.
- Do not switch ABI or flavor to dodge a failure that only happens in the original one.
- Do not disable a lint task, a signing config or a variant gate to get past an error.
- Do not commit `API_KEYS`, `*.jks`, `local.properties`, build outputs or generated intermediates.
