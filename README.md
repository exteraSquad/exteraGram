<p align="center">
  <img src="TMessagesProj/src/main/res-appicons/mipmap-xxxhdpi/ic_launcher.webp" width="96" alt="exteraGram">
</p>

<h1 align="center">exteraGram</h1>

<p align="center">
  A third-party Telegram client for Android, built on the
  <a href="https://github.com/DrKLO/Telegram">official sources</a>.
</p>

<p align="center">
  <a href="https://t.me/exteraGram"><img src="https://img.shields.io/badge/Channel-Telegram-blue.svg" alt="Channel"></a>
  <a href="https://t.me/exteraChat"><img src="https://img.shields.io/badge/Chat-Telegram-blue.svg" alt="Chat"></a>
  <a href="https://t.me/exteraReleases"><img src="https://img.shields.io/badge/Download-Telegram-blue.svg" alt="Download"></a>
  <a href="https://crowdin.com/project/exteralocales"><img src="https://img.shields.io/badge/Translate-Crowdin-green.svg" alt="Crowdin"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-orange.svg" alt="License"></a>
</p>

## What it adds

- Plugins written in Python that can hook into the app, with their own settings and an SDK
  ([`plugins-pysdk`](plugins-pysdk/README.md)).
- Material You themes, icon packs and alternative app icons.
- A long list of interface and chat options that the official client does not have.

Releases are published in [@exteraReleases](https://t.me/exteraReleases).

## Editions

| Flavor | What is inside                                              | ABIs                                  |
|--------|-------------------------------------------------------------|---------------------------------------|
| `full` | Everything, including the Python runtime and plugins        | `armeabi-v7a`, `arm64-v8a`            |
| `lite` | The same client without Python, plugins and method hooking  | `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64` |

The second flavor dimension picks the ABI: `armv7`, `arm64`, `x86`, `x64`, or `afat` for a universal
ARM build. `beta` is a universal ARM build with beta naming.

## Building

### Requirements

- Android Studio with support for Android Gradle Plugin 9.1, or just JDK 17 and the command line
- Android SDK 36 and NDK 29.0.14206865
- Python 3.11 for the `full` flavor, on `PATH` as `python3.11` or set as `python.build` in
  `local.properties`

### Steps

1. Clone the repository with its submodules:
   ```bash
   git clone --recursive --shallow-submodules https://github.com/exteraSquad/exteraGram.git
   ```
   If you cloned without `--recursive`:
   ```bash
   git submodule update --init --recursive --depth=1
   ```
2. Create a file named `API_KEYS` in the repository root:
   ```
   APP_ID = 123456
   APP_HASH = "abcdef0123456789abcdef0123456789"
   MAPS_V2_API = your-google-maps-key

   SIGNING_KEY_PASSWORD = key-password
   SIGNING_KEY_ALIAS = key-alias
   SIGNING_KEY_STORE_PASSWORD = keystore-password
   ```
   Get your own `APP_ID` and `APP_HASH` at <https://core.telegram.org/api/obtaining_api_id> and a
   Maps key in the [Google Cloud console](https://console.cloud.google.com/google/maps-apis/credentials).
   Do not ship builds with someone else's keys.
3. [Generate a signing key](https://developer.android.com/studio/publish/app-signing#generate-key)
   and save the keystore as `TMessagesProj/config/exteraGram.jks`. Debug builds are signed with it
   too.
4. Build:
   ```bash
   ./gradlew :TMessagesProj:assembleFullArm64Debug
   ```
   The APK appears in `TMessagesProj/build/outputs/apk/`. Other variants follow the same pattern:
   `assembleLiteArm64Release`, `assembleFullAfatRelease`, `assembleFullBetaDebug`.

A `full` build compiles the plugins SDK from `plugins-pysdk/` and downloads a prebuilt plugin
analyzer from GitHub; the analyzer is cached in `Tools/temp` for offline builds.

## Translations

Telegram's own strings come from the
[official translations](https://translations.telegram.org/en/android/). Strings that exist only in
exteraGram are translated on [Crowdin](https://crowdin.com/project/exteralocales).

## Thanks

- [Telegram for Android](https://github.com/DrKLO/Telegram)
- [Catogram](https://github.com/Catogram/Catogram)
- [Nekogram](https://gitlab.com/Nekogram/Nekogram)
- [OwlGram](https://github.com/OwlGramDev/OwlGram)

## License

[GNU General Public License v3.0 or later](LICENSE), with [additional terms](ADDITIONAL_TERMS.md)
about attribution and the use of the exteraGram name and logo.
