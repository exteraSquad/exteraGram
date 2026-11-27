# Code Review Style Guide

Mechanical style and formatting rules for exteraGram. A review of a change in this repository checks
every item here and either fixes it or reports it with the file and the rule.

These rules are deliberately narrow: they are the things a reader can decide without knowing the
feature. Design, scope and behavior rules live in `AGENTS.md` and
`.agents/shared/keep-it-simple.md`.

## File header

Every new fork-owned source file starts with the exteraGram notice. Copy the block from a neighbouring
file in the same language; do not invent a variant.

```java
/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/
```

- Python files use the same text with `#` comments.
- Resource XML files that carry fork content (`values/extera.xml`) use the XML comment form ending at
  `Copyright @exteraGram, 2022-2026`.
- Never change the copyright years, the wording or the license line of an existing header as part of an
  unrelated change.
- Never add a header to an upstream `org.telegram` file; those keep Telegram's copyright.

## Upstream markers

An edit inside an upstream file carries a marker comment in the modified block:

```java
// BAD - a silent upstream delta, lost at the next rebase:
if (Build.VERSION.SDK_INT >= 31) {
    applyBlur(view);
}

// GOOD:
// exteraGram: system blur instead of the fallback gradient
if (Build.VERSION.SDK_INT >= 31) {
    applyBlur(view);
}
```

- Use `// exteraGram: <reason>`, not a bare `// extera` (older ones may stay as they are).
- Keep the reason about behavior, not about the edit ("apply the user's corner radius", not "added
  call").
- Do not add a marker to fork-owned files; their whole content is fork code.

## Indentation and whitespace

- Fork Java, Kotlin, Gradle, Python and XML use 4 spaces. Upstream Java files use tabs. Match the file
  you are editing, never convert one to the other.
- No tab characters in Kotlin, XML, Gradle, Python or Markdown.
- No trailing whitespace. Exactly one newline at end of file.
- At most one blank line in a row.
- Do not re-indent or re-wrap lines you are not changing.

## Line endings and encoding

- LF everywhere, UTF-8 without BOM.
- Never save a file from a tool that silently switches it to CRLF or adds a BOM. If a file arrives
  mixed, normalize it to the repository convention rather than leaving it mixed.

## Strings

No user-visible text is hardcoded in Java, Kotlin or XML layouts.

```kotlin
// BAD - untranslatable, and invisible to Crowdin:
title = "Custom save path"

// GOOD:
title = getString(R.string.CustomSavePath)
```

- Fork strings go to `TMessagesProj/src/main/res/values/extera.xml` with a PascalCase key. Never add
  fork strings to `values/strings.xml`; that file is upstream and Crowdin does not see it.
- Read them through `LocaleController.getString(R.string.Key)` (usually imported as `getString`). A raw
  `context.getString(...)` bypasses the language pack the app loads from assets.
- Keys are stable. Renaming one requires updating every use, the translations, and any settings link
  alias that pointed at it.
- Brand names and untranslatable literals carry `translatable="false"`.
- No `<plurals>` in `extera.xml`; counted text uses `LocaleController.formatPluralString`.
- Format placeholders stay exactly as the existing string has them (`%1$s`, `%1$d`); never reorder
  placeholders in a translated string.

## Dimensions

Density-independent values come from `dp`, never from a raw pixel constant.

```kotlin
// BAD - ignores density and the user's display size:
view.layoutParams.height = 48
setPadding(16, 8, 16, 8)

// GOOD:
view.layoutParams.height = dp(48f)
setPadding(dp(16f), dp(8f), dp(16f), dp(8f))
```

- Use the existing helpers: `AndroidUtilities.dp(float)`, `LayoutHelper.createLinear/createFrame`, and
  the `dp`-valued floats those helpers take.
- Distances that scale with the user's setting (avatar radius, corner radius, sticker size) go through
  the fork helpers in `ExteraConfig` rather than a fresh multiplication.
- A raw number is acceptable only for a value that is genuinely not a dimension (counts, indices, bit
  flags, version comparisons).

## Colors

UI colors come from the theme.

```java
// BAD - breaks in dark mode, in custom themes and in night mode:
paint.setColor(0xFF3390EC);

// GOOD:
paint.setColor(Theme.getColor(Theme.key_chats_actionBackground, resourceProvider));
```

- A custom view that draws themed content implements `Theme.Colorable` and updates its paints in
  `updateColors()`; it does not resolve colors in `onDraw`.
- `getThemedColor(...)` / `Theme.getColor(key, resourcesProvider)` is the normal form; the bare
  `Theme.getColor(key)` only where no provider exists.
- `Color.parseColor` and `Color.rgb` are acceptable only for colors that come from data (a user theme,
  a plugin-provided palette, a rendered image), never for chrome.

## Logging

- `FileLog.d/e` for app code, `AppUtils.log` when a caller-attributed tag helps. Never `System.out`,
  never `android.util.Log` in app code.
- No logging inside `onDraw`, `onMeasure`, `onLayout`, `bindViewHolder`, or a per-message/per-frame
  callback.
- New diagnostic prefixes start with `[Extera]`, matching `SettingsRegistry` and the plugin logs.
- Log the exception: `catch (Exception e) { FileLog.e(e); }`.

```kotlin
// BAD - swallows a real failure with no trace:
try {
    register()
} catch (_: Exception) {
}

// GOOD:
try {
    register()
} catch (e: Exception) {
    FileLog.e(e)
}
```

An empty catch is allowed only where the comment states why the failure is expected and harmless.

## Kotlin

- Expression body for a single expression:

```kotlin
// BAD:
fun title(): String {
    return getString(R.string.General)
}

// GOOD:
fun title(): String = getString(R.string.General)
```

- Trailing comma in a multi-line parameter or argument list, as the codebase does.
- One argument per line when a call does not fit on one line. Do not pair arguments up.

```kotlin
// BAD - inconsistent line lengths, noisy diffs:
items.add(uCheck(GeneralItem.IN_APP_VIBRATION, getString(R.string.InAppVibration), inAppVibration,
    linkAlias = "inAppVibration"))

// GOOD:
items.add(uCheck(
    GeneralItem.IN_APP_VIBRATION,
    getString(R.string.InAppVibration),
    inAppVibration,
    linkAlias = "inAppVibration"
))
```

- `when` over an enum is exhaustive; do not add an `else` that silently swallows a new constant.
- No `!!` on a value that a platform API or a nullable field can produce. Use `?.let`, `?: return`, or
  an explicit early return with a log.
- No new wildcard imports in a new file; existing wildcard imports in a file may stay.
- `@JvmOverloads` on public functions with default parameters that Java calls (the settings and plugin
  APIs do this).
- Keep new declarations as private/internal as the use allows. Widening visibility is an API decision,
  not a review fix.
- `@Suppress` stays narrow and carries the reason when it is not obvious (`UNCHECKED_CAST` in a typed
  preference delegate is the accepted example).

## Java

- `var` for locals in fork code; an explicit type where it documents the shape of an ambiguous call.
- Arrow `switch` with exhaustive cases; a `switch` over a string returns a value rather than falling
  through.
- Annotate nullability on public Java API that Kotlin or plugin code calls: `@Nullable` / `@NonNull`.
- No raw types (`List`, `Map`) in new signatures; no unchecked casts without a `@Suppress` on the
  smallest scope.
- No `public` fields added to classes that already use accessors. `PreferencesUtils.BackupItem` and
  similar legacy shapes may stay.
- Initialize a field whose default carries meaning: a boxed `Boolean`/`Integer`, a nullable holder that
  the code branches on, or an array of nullable elements. Plain `int`/`boolean` fields already default
  safely and do not need `= 0` noise.

## Imports and grouping

Fork files group imports as `android*`, `androidx*`, `com.exteragram*`, third-party, `org.telegram*`,
`java*`, `kotlin*`, with one blank line between groups, alphabetized inside a group. Follow the file you
are editing; do not re-sort a whole file to fix one import. Unused imports are always removed.

## Resources and naming

- String keys: PascalCase (`CustomSavePath`, `RestartRequired`).
- Layout, drawable and id names: lowercase snake_case, as in the rest of the repository.
- A fork drawable that could collide with a future upstream name gets the `extera_` prefix
  (`extera_outline`, `extera_watermark`).
- Do not rename an existing resource without updating every reference and every translation; renaming a
  string key drops its translations.

## Views and drawing

- Branch on a view's own state with `getVisibility() == View.VISIBLE`, not `isShown()`. `isShown()` is
  false while the parent is detached or animating, so layout logic written against it silently breaks
  during transitions.
- No allocation in `onDraw` / `onMeasure` / `onBindViewHolder`: no `new Paint()`, no
  `new RectF()`, no `String.format` per frame. Cache the object in a field and reuse it.
- Reuse the shipped cells and components (`UItem`, `UniversalAdapter`, `…Cell`, `LayoutHelper`) instead
  of hand-building a parallel layout.
- A view that reacts to theme changes implements `Theme.Colorable` or relies on the existing
  `updateColors` chain; it does not read `Theme.getColor` in a listener that only fires once.
- Recycler adapters report specific changes (`notifyItemChanged`, `notifyItemRangeInserted`); a new
  `notifyDataSetChanged` needs a reason.

## Settings rows

- A settings row has a stable integer id from the screen's item enum and is dispatched by that id in
  `onClick`. Never dispatch on the visible text.
- A row the user should be able to find in settings search has a `linkAlias`, and the alias is unique
  across the app.
- Toggling a boolean uses `toggleBooleanSettingAndRefresh(item) { … }` so the row and the adapter stay
  in sync.
- A preference is read through its `ExteraConfig` delegate, not from `SharedPreferences` directly.
- A new numeric setting is added to `PreferencesUtils`' validation switches, or it will not survive
  settings export/import.

## Full and lite

- A change to a plugin-facing class in `src/full` has the same signature change in `src/lite`.
- `main` gains no import of a full-only class (`com.chaquo.python`, `de.robv.android.xposed`,
  `com.exteragram.hook.*`).
- A lite build must not gain a new `BuildConfig` reference that only exists in the full flavor.

## Native code

- A new source file is added to the right target in `TMessagesProj/jni/CMakeLists.txt`; no Gradle-side
  source lists.
- No edits inside `jni/third_party`, `jni/td`, `jni/tgcalls` or any other submodule.
- New JNI entry points follow the file's existing style (`Java_…` static export or `RegisterNatives`)
  and have a matching keep rule if R8 could strip the Java side.
- C++ follows the file: 4 spaces or the file's existing tabs; no `using namespace` in headers.

## Gradle

- Dependencies stay literal strings sorted by group, as the file does; no version catalog is used in
  this repository.
- No new repository entries; local artifacts belong in `Tools/maven`.
- A flavor- or ABI-specific dependency uses the existing `fullImplementation` / `liteImplementation`
  shape, not a runtime check.
- A build script change does not also reformat unrelated blocks.

## Python (plugins SDK and Tools)

- 4 spaces, `snake_case` functions, `PascalCase` classes, `UPPER_CASE` constants.
- Keep the `#` license header on new SDK modules and tests.
- Public SDK names are API: adding is fine, renaming or removing needs a version bump and a migration.
- Tests live in `plugins-pysdk/tests` and use `unittest` with the existing `stubs` Java stub installer.

## Commits touched by a review

- The subject describes the change, in English, and the diff contains nothing unrelated.
- No generated files, keys, build output or submodule changes are mixed in.
- The commit message carries no attribution trailer and no tool markers.

## Before approving

- `:TMessagesProj:assembleFullArm64Debug` passes for any change that touches code.
- A lite variant was also built when the plugin API, `src/full` or `src/lite` changed.
- The host checks that apply were run and their output reported: `Tools/check_observers.py`,
  `Tools/inline/run_tests.sh`, `plugins-pysdk` tests.
- Anything visual is either observed on a device or reported as unverified.
