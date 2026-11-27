# Agent Guide for exteraGram

This file is the canonical repository-wide instruction set for coding agents working in this checkout.
`REVIEW.md` holds the mechanical style rules every review must check. `.agents/shared/` holds the
on-demand references (build and verification, settings, localization, plugins SDK, upstream edits),
and `.agents/skills/` holds the multi-step workflows: `settings`, `l10n` and `review`. `CLAUDE.md`
points here, and `.claude/commands/` exposes the skills as slash commands. Read this file first, then
only the references the current change needs.

## The project

exteraGram is a third-party Telegram client for Android built on the official Telegram for Android
sources (DrKLO/Telegram). It adds Python plugins with their own SDK, Material You theming, app icon
packs, and a long list of interface and chat options the official client does not have.

Two source worlds live side by side, and every rule below depends on which one you are editing:

- **Upstream code** — `org.telegram.messenger`, `org.telegram.ui`, `org.telegram.tgnet`,
  `org.telegram.utils`, `org.telegram.SQLite`, `org.telegram.PhoneFormat`, most of `jni/`,
  `TMessagesProj_Modules/media`, `TMessagesProj/src/main/res/values/strings.xml`. Treat it as an
  external codebase that is rebased on new Telegram releases.
- **Fork code** — `com.exteragram.messenger.**`, `buildSrc/`, `TMessagesProj/gradle/`,
  `TMessagesProj/build.gradle`, `plugins-pysdk/`, `Tools/`, `TMessagesProj/appicons/`,
  `TMessagesProj/src/main/res/values/extera.xml`. This code is ours: edit it freely, following the
  conventions it already has.

The main module is `:TMessagesProj`. `:TMessagesProj_Modules:media3:*` is a submodule (Media3) and
`:baselineprofile` produces Macrobenchmark baseline profiles. `:jlatexmath` is a submodule too.

## Load-bearing details that must not be "cleaned up"

These exist for licensing, attribution, security or update-integrity reasons. Never change them
unless the user explicitly asks for that exact change:

- The exteraGram source-file notice (`This is the source code of exteraGram for Android.` … in Java,
  Kotlin, XML and `#` form in Python). Keep it on every new fork file and keep it on existing ones.
- `ADDITIONAL_TERMS.md`: modified builds must keep a visible "based on exteraGram" notice with a link
  to <https://github.com/exteraSquad/exteraGram> where the app shows its name, version or legal
  notices. In the app that is `AppUtils.getVersionText()` (the `based on @exteraGram` line) and the
  signature/package check next to it (`EXPECTED_SIGNATURE`, `EXPECTED_PACKAGE_NAME`). Do not remove,
  weaken or "fix" that check.
- `exteraAppName` and the other brand strings; the ones that are brand text carry
  `translatable="false"`.
- `SdkSignature` — the pinned P-256 public key that authenticates over-the-air plugin SDK updates.
  Rotating it is a coordinated release action, never a drive-by edit.
- The Gradle signing configuration and `API_KEYS` handling. Keys are git-ignored and never committed.

## Tasks, scope and permissions

- A request the user makes in conversation **is** the task. There is no external task queue, board or
  task repository for this project: do not invent task ids, work records, branches or status files.
- Do the smallest correct change that fully does what was asked. `.agents/shared/keep-it-simple.md`
  is the rule, not an aspiration.
- Do not add unrequested features, settings, refactors, renames, format passes, dependency upgrades or
  version bumps. Do not "fix" unrelated code you happen to read.
- Prefer editing fork code. Touch upstream files only where the behavior genuinely has to change
  there, keep the patch minimal, and see "Upstream versus fork code" below.
- Do not run `git push`, rewrite published history, create tags or bump `APP_VERSION_CODE` /
  `APP_VERSION_NAME` unless asked.
- Do not modify submodule content, `.gitmodules`, signing material, update channels or release
  artifacts unless asked.
- If the request is ambiguous in a way that changes the design, ask one focused question rather than
  guessing. If it is merely large, state a short plan and proceed.

## Repository map

| Path | What lives there | How to treat it |
| --- | --- | --- |
| `TMessagesProj/src/main/java/org/telegram/**` | Upstream client: controllers, UI, networking, storage | Upstream; minimal marked patches |
| `TMessagesProj/src/main/java/com/exteragram/messenger/**` | Fork features: preferences, AI, plugins glue, adblock, player, feed, pill stack, UI helpers, utilities | Fork-owned |
| `TMessagesProj/src/full/java/**` | `com.exteragram.messenger.plugins.**` — real plugin engine, hooking, analyzer, Xposed bridge, Python engine | Full flavor only |
| `TMessagesProj/src/lite/java/**` | Stub counterparts of the plugin API (same class and method names) | Lite flavor only; signature parity with `full` |
| `TMessagesProj/src/main/res/values/extera.xml` | Every fork-owned string, English source for Crowdin | Fork-owned; do not move strings into `strings.xml` |
| `TMessagesProj/src/main/res/values-*/extera.xml` | Translations synced from Crowdin | Machine-synced; see localization rules |
| `TMessagesProj/src/main/res/` | Upstream resources; `res-appicons`, `res-solar`, `res-remix`, `res-tabs` are fork resource roots | `res-appicons` content is generated; see "Generated files" |
| `TMessagesProj/appicons/` | Source of truth for launcher icons (`icons.json`, per-icon artwork) | Edit here, then regenerate |
| `TMessagesProj/jni/` | Native build: `CMakeLists.txt`, JNI wrappers, `extera/`, wallet, twidget, prisma, third-party | C++17; submodules are third-party |
| `plugins-pysdk/` | Python plugins SDK: runtime modules, `elyxcore`, tests, build and signing tools | Fork-owned; versioned independently |
| `buildSrc/` | Gradle plugins and tasks: build plugin, string generation, app icons, localization utils | Fork-owned Kotlin/Groovy |
| `baselineprofile/` | Macrobenchmark generator and startup benchmarks | Needs a device |
| `Tools/` | Host scripts: inline math tests, observer checker, Crowdin helpers, analyzer download, battery measurement, local Maven repo | Fork-owned tooling |
| `.github/` | Issue templates only; there is no CI workflow | — |

Useful anchors: the settings UI starts at `com/exteragram/messenger/preferences/` with
`BasePreferencesActivity` and `utils/SettingsRegistry.java`; global preferences are the delegated
properties in `com/exteragram/messenger/ExteraConfig.kt`; the fork's strings are resolved through
`LocaleController`, not Android resources.

## Upstream versus fork code

New behavior belongs in fork code whenever it can live there. Integrate by calling one small fork
entry point from the upstream file (`ExteraConfig.getX()`, a fork helper, a hook interface) instead of
rewriting upstream logic in place.

Rules for every upstream edit:

- Keep it as small as the behavior allows: one guard, one call, one corrected condition. No
  reformatting, no import reordering, no drive-by cleanups in the same file.
- Mark it. The established marker is a trailing comment in the modified block:
  `// exteraGram: <short reason>` (`// extera: …` also exists; prefer `exteraGram`). It is how the
  next rebase finds our deltas.
- Preserve upstream comments and code style in that file: upstream Java uses tabs, its own naming and
  older idioms. Do not modernize surrounding lines.
- If the same behavior is better expressed as a hook, a plugin API or a `com.exteragram` helper,
  prefer that over another upstream delta.
- When a Telegram-authored method gains a fork parameter or branch, document nothing extra: the marker
  comment is the record.

See `.agents/shared/upstream-edits.md` for the integration patterns that already exist.

## Build

### Prerequisites

- JDK 17 (source and target are Java 17).
- Android SDK with `compileSdkVersion 37`, `targetSdkVersion 36`, `minSdkVersion 24`, build-tools
  `36.0.0`; NDK `29.0.14206865`; CMake 4.0.0+.
- Gradle 9.4.1 through the wrapper; Android Gradle Plugin 9.1.1.
- Python 3.11 for the `full` flavor (`python3.11`, `py -3.11` on Windows, or `python.build` in
  `local.properties` / `BUILD_PYTHON` in the environment).
- `API_KEYS` in the repository root with `APP_ID`, `APP_HASH`, `MAPS_V2_API` and the three signing
  values.
- A keystore at `TMessagesProj/config/exteraGram.jks`. Both debug and release builds are signed with
  it, so even a debug build needs it.
- Git submodules checked out: `git submodule update --init --recursive`. `settings.gradle` throws a
  clear error when `TMessagesProj_Modules/media` or `TMessagesProj/lib/jlatexmath` is missing.

`API_KEYS` and the keystore are git-ignored. When they are absent, report that the checkout cannot
build and let the user provide them. Never generate substitutes, never commit keys, never print
secret values into logs or commit messages.

### Flavors and ABIs

Two flavor dimensions:

- `appType`: `full` (Python runtime, plugins, method hooking) and `lite` (no Python, no plugins, no
  hooking).
- `minApi`: `armv7`, `arm64`, `x86`, `x64`, `afat` (universal `armeabi-v7a` + `arm64-v8a`), `beta`
  (universal ARM, beta naming).

`full` is built for `armv7` and `arm64` only — `androidComponents.beforeVariants` disables `full` for
`x86`/`x64`. `beta` exists for debug-type builds only.

Variant task names follow `assemble<AppType><Abi><BuildType>`:

```bash
./gradlew :TMessagesProj:assembleFullArm64Debug      # normal development build
./gradlew :TMessagesProj:assembleLiteArm64Debug      # lite half of a parity check
./gradlew :TMessagesProj:assembleFullAfatRelease     # universal ARM release
./gradlew :TMessagesProj:assembleFullBetaDebug       # beta-named universal debug
```

On Windows use `gradlew.bat`. The APK lands in `TMessagesProj/build/outputs/apk/` and is named
`exteraGram-<appType>-<abi>-<yyyyMMdd>.apk`; the version code is `APP_VERSION_CODE * 10 + abiIndex`.

Notes that cost time or produce misleading failures:

- Debug builds have `debuggable false` and are signed with the release keystore. There is no `adb
  run-as` and no JDWP attach; diagnose with logs.
- `full` builds compile `plugins-pysdk` with Python and download the prebuilt plugin analyzer from
  GitHub on the first build. The analyzer is cached in `Tools/temp`, so later builds work offline.
- `org.gradle.jvmargs` gives the daemon 8 GB and enables parallel builds. A failed build with an OOM
  or a wedged daemon is an environment problem, not a code problem; retry once, then report.
- Release builds enable R8 minification and resource shrinking. A release-only crash usually means a
  missing keep rule — see "Reflection, JNI and keep rules".
- Never start several builds of this checkout at once.

### Commands that are not builds

```bash
./gradlew :TMessagesProj:generateAppIcons     # regenerates res-appicons + manifest + registry
```

Icon generation needs `rsvg-convert`, `cwebp` and `dwebp` on the machine (or
`-Pappicons.rsvg-convert=…` style overrides). Regular builds never run it; commit its output.

## Verification

There is no full test suite to list: `:TMessagesProj` has no unit, instrumentation or Robolectric
tests. Verification is a compile, plus the host checks below, plus on-device observation when the
change is visual or behavioral.

| Change | Proof to run |
| --- | --- |
| Kotlin/Java in `main` or `full` | `:TMessagesProj:assembleFullArm64Debug` |
| Anything touching the plugin API, `src/full` ↔ `src/lite` | the same, plus `:TMessagesProj:assembleLiteArm64Debug` |
| Native code under `jni/` | the same variant build; it compiles and links the `.so` files |
| Inline math, currency rates, units, time zones (`com.exteragram.messenger.inline.*`) | `bash Tools/inline/run_tests.sh` |
| `plugins-pysdk/**` | `python3.11 -m unittest discover -s plugins-pysdk/tests` |
| `NotificationCenter` observers, delegates, `attach`/`detach` pairing | `python3 Tools/check_observers.py` (add `--filter <path>` for one area, `--all` for lower severities) |
| Resource strings or translations | a build, then check the key in the app's language |
| Release-only behavior | ask before running a release build; never ship a conclusion inferred from debug alone |

On-device loop, when a device or emulator is available:

```bash
adb install -r TMessagesProj/build/outputs/apk/exteraGram-full-arm64-<date>.apk
adb logcat -s tmessages          # FileLog and app logs
```

`:baselineprofile` holds Macrobenchmark generators (`BaselineProfileGenerator`, `StartupBenchmarks`).
They are not app tests, they need a device, and they are run only when the user asks for profile work.

Report honestly what was and was not verified. "Compiles" is not "works"; a visual change that was
never seen on a device must be reported as unverified.

## Code conventions

### Language and layout

- Fork code is mixed: most feature code is Java, while `ExteraConfig.kt` and its preference delegates,
  several preference screens and helpers, plugin models and parts of the newer UI are Kotlin. Match the
  area you are working in: a new file in a Java area is Java, a new file next to Kotlin is Kotlin. Never
  port a file between the two languages as part of an unrelated change, and never split one feature
  across both languages without a reason.
- Fork Java/Kotlin files use 4-space indentation, LF line endings and no trailing whitespace.
- Upstream Java files use tabs and an older style. Match the file, do not reformat it.
- Import grouping used across fork code: `android*`, `androidx*`, `com.exteragram*`, third-party,
  `org.telegram*`, `java*`, `kotlin*`, separated by blank lines. Follow the file you are in; do not
  re-sort imports across a file.
- Kotlin uses expression bodies for small getters, trailing commas in multi-line declarations, and
  `when` for dispatch. Multi-line call arguments go one per line.
- Java uses `var` for locals in fork code and arrow `switch` expressions; the codebase targets Java 17.
- Public Kotlin API used from Java carries `@JvmOverloads` where defaults matter, and is kept
  nullability-correct — plugin and Python code bind to these signatures by name.

### Naming

- Fork packages live under `com.exteragram.messenger.<area>`. Reuse an existing area before creating a
  new one; a new package needs a reason.
- Preference properties in `ExteraConfig.kt` are camelCase Kotlin delegates, grouped by the existing
  `// Section` comments. Renaming one is a data migration, not a rename — see the settings reference.
- `ExteraConfig` is a Kotlin file annotated `@file:JvmName("ExteraConfig")`; top-level properties and
  functions become static members. Java reads `ExteraConfig.getLogging()`, Kotlin reads the property.
  Keep that Java-visible surface stable.
- Resource strings are PascalCase keys in `values/extera.xml`, matching Telegram's legacy resource
  naming (`R.string.CustomSavePath`). Fork drawables that cannot collide with upstream names use an
  `extera_` prefix (`extera_outline`, `extera_watermark`).
- Cell and view classes follow the existing Telegram names: `…Cell`, `…View`, `…Layout`, `…BottomSheet`,
  `…Activity`, `…Fragment`. Pick the closest shipped one as the template.

### Comments

- Add a comment only when it carries a reason the code cannot: a non-obvious constraint, a workaround
  with a reference, an upstream marker, or a section label inside a long preference file. Do not narrate
  what the next line does.
- Never cite line numbers in comments or documents. Name the function, member or type instead.
- Do not delete existing comments unless your change makes them wrong. Move a useful comment with the
  code it explains.
- Debug scaffolding, temporary instrumentation and "TODO: remove" blocks do not belong in a commit.

### Logging and errors

- Use `FileLog` (`FileLog.e`, `FileLog.d`, tag `tmessages`) or `AppUtils.log` for fork code; never
  `System.out`, and never `android.util.Log` directly in app code.
- Prefix fork diagnostics that a user or developer will grep with `[Extera]`, as
  `SettingsRegistry` does.
- Do not log per frame, per keystroke or per chat row. Guard verbose logging with
  `BuildVars.LOGS_ENABLED` when it is not always wanted.
- Catch at boundaries (IO, reflection, plugin code, optional platform APIs) and log the exception.
  Never swallow an exception into an empty block silently; the established shape is
  `catch (Exception e) { FileLog.e(e); }` plus a defined fallback.

### Threading and lifecycle

- UI work on the main thread: `AndroidUtilities.runOnUIThread`, `view.post`, or a coroutine on
  `Dispatchers.Main`. Fork background work uses `backgroundScope()` from
  `com.exteragram.messenger.utils.Extensions` (SupervisorJob + IO + exception logging).
- Plugin work runs on the plugins queue: `PluginsController.runOnPluginsQueue` (see the full source).
  Never touch plugin state from the UI thread without going through the controller.
- Cancel on teardown: notifications observers, delegates, receivers, sockets, `Disposable` objects and
  request callbacks registered by a fragment, cell or controller must be released in its cleanup path.
  `Tools/check_observers.py` exists because leaks of this kind are common here.
- Do not hold a `Context`, `Activity`, `View` or `Fragment` in a static field. Application-level state
  goes to `ApplicationLoader.applicationContext`.

## Full and lite flavors

This is the most common way to break the build, so it has explicit rules:

- Code that imports `com.chaquo.python`, `de.robv.android.xposed`, `com.exteragram.hook.*`,
  `lsplant`, `shadowhook` or otherwise needs Python/hooking lives in `TMessagesProj/src/full` and only
  there.
- `TMessagesProj/src/lite` carries stub counterparts of the plugin API with the **same class names and
  method signatures** (`PluginsController`, `Plugin`, `PluginsConstants`, `PluginsHooks`,
  `SettingItem`, `TextSetting`, `MenuContextBuilder`, plugin activities and components). Changing a
  plugin-facing signature means changing both copies in the same commit, and the Python side if the
  engine contract moved.
- `main` may reference the plugin API, because both flavors provide it. It must never reference a
  full-only class directly: use `BuildConfig.EXTERA_HOOK_EMBEDDED`, `BuildVars.IS_LITE_VERSION`,
  `ExteraConfig.pluginsEngine`, or reflection for classes that only exist in `full`.
- Lite must compile and run with no plugin functionality beyond the "not supported" bulletins.
  A lite build is the proof.

## Settings and preferences

Every user-facing option goes through the fork preference system; a raw `SharedPreferences` read in a
feature is a code smell. The short version:

1. Add a delegated property in `ExteraConfig.kt` under the matching `// Section`, using
   `BooleanPref` / `IntegerPref` / `FloatPref` / `LongPref` / `StringPref` / `NullableStringPref` /
   `StringSetPref` / `EnumPref` / `SanitizedIntegerPref`. The property name is the storage key.
2. Add the row to the right preference activity (`preferences/` — General, Appearance, Chats, Other,
   …). Use `uCheck` / `uButton` / `UItem.asSlideView` / `UItem.asHeader` / `UItem.asShadow` from
   `BasePreferencesActivity`, give the row a stable id from the screen's item enum, and handle it in
   `onClick`.
3. Give the row a `linkAlias`. That alias is what makes the setting searchable and linkable
   (`https://t.me/exteraSettings?s=<alias>`); a row without one is not in settings search.
4. Add its text to `values/extera.xml` and read it with `LocaleController.getString(R.string.Key)`.
5. If the setting should be flagged as new, add the alias to `SettingsRegistry.newFeatures`.
6. If it must survive settings export/import, check the backup validation rules — booleans and strings
   are accepted by default, numbers and string sets are not. Details in
   `.agents/shared/settings.md`.
7. Renaming or removing a key requires a migration function in `ExteraConfig.loadConfig()`, in the
   style of the existing `migrate*` functions.

New preference screens: extend `BasePreferencesActivity`, implement `getTitle()` and
`fillItems(items, adapter)`, and register the class in `SettingsRegistry.categoriesIcons` with an icon
so settings search can index it.

## Localization

- `TMessagesProj/src/main/res/values/extera.xml` is the single source of truth for fork-owned
  strings, in English. Telegram's own strings stay in `values/strings.xml` and are not ours to edit.
- Read fork strings through `LocaleController.getString(R.string.Key)` (or the imported `getString`),
  never through a raw `Context.getString`. The app merges `extera.xml` from assets, including
  Crowdin-delivered languages, into its own language pack.
- `values-*/extera.xml` are produced from Crowdin. Do not hand-edit translations, and do not add a
  language file by hand.
- Keys are PascalCase and stable; changing one breaks translations and setting links. No `<plurals>`
  in `extera.xml`; use `LocaleController.formatPluralString` for counted text.
- Brand text carries `translatable="false"`.
- See `.agents/shared/localization.md` and the `l10n` skill for the sync workflow.

## Plugins SDK

`plugins-pysdk` is shipped inside every `full` build and is also updated over the air, so it is a
published API:

- Python modules under `plugins-pysdk/src/` are imported by plugins under their top-level names. Do not
  rename a module, function or class that plugins can import without a migration path.
- `elyxcore/_plugin_engine.py` implements the Kotlin `PluginsController.PluginsEngine` interface through
  Chaquopy `@joverride`, and no compiler checks either side. A method added to `PluginsEngine`,
  `Plugin` or `PluginInstallParams` must be added there in the same change.
- `src/_sdk_version.py` holds the version twice (`__version__` and the `version` tuple); the build fails
  when they disagree. Raise the version with every SDK change that ships.
- Tests: `python3.11 -m unittest discover -s plugins-pysdk/tests`.
- The SDK signing key is never in this repository. Do not add keys, do not weaken signature checks, do
  not change the pinned public key.
- See `.agents/shared/plugins-sdk.md` for the full contract, packaging and OTA rules.

## Native code

- C++17, built by `TMessagesProj/jni/CMakeLists.txt` per ABI. Add new sources to the correct target
  there, not to Gradle. `Application.mk` carries the old ndk-build compatibility settings.
- STL differs per flavor: `full` links `c++_shared`, `lite` links `c++_static`. Anything that ships a
  prebuilt `.so` must match the flavor it is packaged into.
- Symbols are hidden by default (`-fvisibility=hidden`) and `exports.map` exports only `Java_*`,
  `JNI_OnLoad` and `JNI_OnUnload`. Use `RegisterNatives` or static `Java_…` names; nothing else is
  reachable from Java.
- New public JNI entry points need a keep rule so R8 does not remove or rename the Java side (see
  below).
- `jni/third_party/**`, `jni/td`, `jni/tgcalls`, `jni/tlottie`, `jni/wallet-engine`, `jni/twidget/**`
  and the other submodules are third-party: never edit them, never commit changes inside them.
- Prebuilt binaries live under `jni/prebuild`; do not replace them as a side effect of a code change.

## Reflection, JNI and keep rules

R8 runs only in release builds, so a missing keep rule shows up as a release crash or a `ClassNotFound`
at runtime. When a change adds any of the following, add the matching rule to
`TMessagesProj/proguard-rules.pro` (or the `full`/`lite` variants):

- a class instantiated by name (`Class.forName`, `newInstance`) — plugin settings classes, `Badger`
  implementations, `BasePreferencesActivity` subclasses, `BaseFragment` subclasses;
- a JNI method or a class whose methods native code resolves;
- a model serialized by Gson with field names (or use `@SerializedName`);
- an Xposed/hook target that must keep its name.

The existing rules show the intended shape; copy the nearest one rather than inventing a broad
`-keep class ** { *; }`. Broadening a keep rule to fix one crash hides the next one and grows the
release APK; prefer the narrowest rule that names the class or members.

## Dependencies, submodules and generated files

- Submodules: `TMessagesProj/jni/{td,tgcalls,tlottie,wallet-engine,twidget/*,third_party/*}`,
  `TMessagesProj/lib/jlatexmath`, `TMessagesProj_Modules/media`. They are pinned by commits; changing a
  pin is an explicit, separate request.
- `Tools/maven` is a local Maven repository for artifacts that are not on Maven Central (`hook-core`,
  `lsplant`, `shadowhook`, `chaquopy` runtime). Do not add a remote repository for something that
  belongs there.
- Generated, never hand-edited: `TMessagesProj/src/main/res-appicons/**`,
  `TMessagesProj/src/main/AndroidManifest-appicons.xml`,
  `TMessagesProj/src/main/java/com/exteragram/messenger/appicons/GeneratedAppIcons.java`
  (all from `TMessagesProj/appicons/` via `:TMessagesProj:generateAppIcons`), the Room schema JSON under
  `TMessagesProj/schemas/**` (from entities via KSP), the locale assets built by
  `gradle/extera-locales.gradle`, and the per-variant profiles under
  `TMessagesProj/src/<variant>/generated/baselineProfiles/**` (written by the benchmark run).
  `TMessagesProj/src/main/baselineProfiles/*.txt` is hand-maintained extra profile rules and may be
  edited.
- `gradle.properties` holds `APP_VERSION_CODE`, `APP_VERSION_NAME`, `APP_PACKAGE`. Version bumps are
  release actions; do not touch them to "make a build work".
- Adding a Gradle dependency or bumping one is a decision for the user. Removing a dependency that
  looks unused is the same.

## Commits

- One concise English subject line that says what changed, matching the style of recent history
  (`fix: support fonts with weird bold`, `Added back "Chats" for Title, Translations; …`). A short body
  only when the subject cannot carry it.
- No `Co-Authored-By:` or any tool/assistant attribution, no auto-generated footers, no emoji.
- Do not commit `API_KEYS*`, `*.jks`, `build/`, `.cxx/`, `.gradle/`, `.kotlin/`, `local.properties`,
  APKs, `*.hprof`, or `Tools/temp`.
- Do not mix an unrelated format pass, dependency bump or generated-icon regeneration into a behavior
  commit.
- Do not push. Do not rewrite published history.

## Text files

- UTF-8 without BOM; LF line endings for source, XML, Gradle, Python and Markdown.
- Preserve each file's existing indentation: 4 spaces in fork code and XML, 2 spaces in JSON
  (`icons.json`, schema JSON), tabs in upstream Java.
- Do not reformat a file you are only touching in one place; a large whitespace diff destroys review
  value and the next upstream merge.

## Quick troubleshooting

| Symptom | Cause and action |
| --- | --- |
| `settings.gradle` throws "Submodule … is not checked out" | `git submodule update --init --recursive` |
| Build fails on missing `APP_ID`/`APP_HASH`/signing values | `API_KEYS` or `TMessagesProj/config/exteraGram.jks` missing; user must provide |
| `lite` compile error about plugins, Chaquopy or Xposed | a full-only reference leaked into `main` or lite did not get the matching stub |
| Release-only crash after a change | missing R8 keep rule for a reflective/JNI class |
| String shows in English in every language | string added to `strings.xml`, or read with `Context.getString` instead of `LocaleController` |
| Setting does not appear in settings search | row has no `linkAlias`, or the screen is missing from `SettingsRegistry.categoriesIcons` |
| Imported settings lose a numeric option | key missing from `PreferencesUtils` validation switches |
| Gradle daemon OOM or hangs | environment; retry once, then report with the failing task |
| Analyzer download fails on a full build | offline machine without `Tools/temp` cache; report, do not vendor binaries |

## When something is unclear

- Look for the shipped analogue first: another preference screen, another `…Cell`, another plugin
  entry point. Matching the closest existing feature is the expected answer.
- `.agents/shared/` answers the common "how do I do X here" questions: build and verify, settings,
  localization, plugins SDK, upstream edits.
- If two files disagree, the one that actually runs wins; say which you followed.
- Ask the user when a decision is theirs: releasing, signing, dependency upgrades, dropping an existing
  feature, or changing a translated string.
