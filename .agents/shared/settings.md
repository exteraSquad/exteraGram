# Settings, Preferences and Settings Search

Everything a user can toggle lives in one system. This file is the checklist for adding, changing or
removing an option without breaking storage, backup or settings search.

## The pieces

| Piece | File |
| --- | --- |
| Storage delegates and the global config | `TMessagesProj/src/main/java/com/exteragram/messenger/ExteraConfig.kt` |
| Screen base class and row builders | `.../preferences/BasePreferencesActivity.kt` |
| Concrete screens | `.../preferences/**` (`GeneralPreferencesActivity`, `appearance/…`, `chats/…`, `OtherPreferencesActivity`, …) |
| Settings search, link aliases, new-feature badges | `.../preferences/utils/SettingsRegistry.java` |
| Backup export/import and its validation | `.../backup/PreferencesUtils.java` |
| Deeplink entry point | `.../utils/IntentsController.kt` (`exteraSettings` → `s`) |
| Text | `TMessagesProj/src/main/res/values/extera.xml` |

## Adding a setting

### 1. Storage

Add a delegated property to `ExteraConfig.kt`, under the `// Section` comment that matches the area, in
the order the user meets it in the UI:

```kotlin
// Chats
var stickerTimeMode by EnumPref(StickerTimeMode.DEFAULT)
```

Available delegates: `BooleanPref`, `IntegerPref`, `FloatPref`, `LongPref`, `StringPref`,
`NullableStringPref`, `StringSetPref`, `EnumPref`, `SanitizedIntegerPref`. The property name **is** the
`SharedPreferences` key, and every delegate registers itself for backup automatically through
`registeredKeys`.

- Use `EnumPref` with a `PrefEnum` enum (`override val prefId: Int`) instead of a magic integer. The
  stored value is the `prefId`, so existing values survive reordering.
- Use `SanitizedIntegerPref` or a `FloatPref` plus a getter when the value has a range; clamp on read
  and write, and add a bounds entry (see step 6).
- Derived values are read-only properties next to the delegate, e.g.
  `val springAnimations get() = transitionAnimation == TransitionAnimation.SPRING`. Do not add a second
  preference that can disagree with the first.
- A boolean that must never be backed up (safe mode, analysis, debug monitors) is registered in
  `PreferencesUtils.excludedExteraKeys`.
- Do not read `SharedPreferences` from feature code. If there is no delegate yet, add one.

### 2. Screen and row

Add the row to the matching screen:

1. Add an entry to the screen's item enum; ids are `ordinal + 1`:
   ```kotlin
   enum class GeneralItem {
       SHOW_TRANSLATE_BUTTON,
       …
   }
   ```
2. Add the row in `fillItems(items, adapter)` with `uCheck`, `uButton`, `UItem.asSlideView`,
   `UItem.asHeader`, `UItem.asShadow`, in visual order, grouped under headers.
3. Handle it in `onClick(item, view, position, x, y)` by resolving the enum from `item.id - 1`.
4. For a plain boolean use `toggleBooleanSettingAndRefresh(item) { enabled -> … }`, which flips the
   stored value and refreshes the row and adapter.
5. For a choice use `showListDialog(item, options, title, currentValue) { index -> … }`.
6. When the change needs the current screens rebuilt, call `parentLayout.rebuildFragments(0)`; when it
   only needs the list refreshed, `listView.adapter?.update(true)`.
7. When the change only takes effect after a restart, show `showRestartBulletin()` instead of pretending
   otherwise.

`uCheck` takes `subtext` for an example or explanation, `checkBoxIcon` for a lock or premium marker,
`multiline`, `icon` and `linkAlias`. `uButton` takes `value` (the current state shown on the right),
`subtext`, `red`, `accent`, `showDivider`, `prioritizeTitleOverValue` and `linkAlias`.

### 3. Text

Add the row title, the value labels and any shadow/description text to
`TMessagesProj/src/main/res/values/extera.xml` with PascalCase keys, and read them with the imported
`getString` (from `LocaleController`). Never hardcode the text on the row.

### 4. Search and linking

- Every row a user might look for gets a `linkAlias`, unique across the app. The alias is what
  registers the row in settings search and makes it linkable as
  `https://t.me/exteraSettings?s=<alias>` (handled in `IntentsController`); plugin settings use
  `?p=<pluginId>&s=<alias>`.
- A new screen is registered in `SettingsRegistry.categoriesIcons` with an icon, otherwise settings
  search cannot index its rows.
- The alias is also the stable deep-link contract: do not rename one without a compatibility path.

### 5. New-feature badge

If the option should be highlighted as new, add its alias to `SettingsRegistry.newFeatures`. The badge
appears when the alias is present there and disappears permanently once the user has seen the row for a
day (`markAsNewFeature`), or immediately if the user dismisses it. Removing an alias from that list
later is normal cleanup.

### 6. Backup export/import

`PreferencesUtils.isExpectedValue` decides whether a key is exported **and** imported. Its behavior by
type:

| Delegate | Default behavior | What a new key needs |
| --- | --- | --- |
| `BooleanPref` | accepted | nothing |
| `StringPref`, `NullableStringPref` | accepted when non-empty and no other rule rejects it | a case in `isExpectedString` only if the value has a format to enforce |
| `IntegerPref`, `EnumPref` | **rejected** unless the key has a range in `isExpectedInteger` | add the key to that `switch` with its bounds |
| `FloatPref` | **rejected** unless the key has a range in `isExpectedFloat` | add the key to that `switch` with its bounds |
| `LongPref` | **rejected** always | add handling, or store it as `Int`/`String` |
| `StringSetPref` | **rejected** unless it is `pinnedPlugins` | extend `isExpectedStringSet` |

So: a boolean or a plain string option round-trips with no extra work; a numeric or set option that a
user would expect to survive a backup needs a validation entry. Forgetting it is silent — the option is
simply absent from the exported file and is never restored.

Keys that are not delegates (JSON blobs, legacy keys, other configs) need a `BackupItem` in
`extraExteraKeys` (or the matching per-config array) to be included at all. Secrets and
security-relevant toggles go into `excludedExteraKeys`.

### 7. Migrations

Renaming or repurposing a key means adding a `migrate*` function next to the existing ones and calling
it from `ExteraConfig.loadConfig()`:

```kotlin
private fun migrateStickerTimeMode() {
    if (!preferences.contains("hideStickerTime")) return
    if (preferences.getBoolean("hideStickerTime", false))
        stickerTimeMode = StickerTimeMode.HIDDEN
    editor.remove("hideStickerTime").apply()
}
```

Rules: check the old key, write the new one, remove the old one, and keep the function idempotent. The
migration list in `loadConfig()` runs inside the config lock; do not do IO there.

## Removing a setting

1. Remove the row and the enum entry. Ids are `ordinal + 1`, so removing or inserting an entry in the
   middle renumbers every later row on that screen. Nothing is persisted by id (aliases, strings and the
   new-feature list are keyed by name), so renumbering is safe — but append new entries at the end when
   practical, and re-check every `onClick` branch after a removal.
2. Remove the delegate, the string, the alias from `newFeatures`, the backup validation entry and the
   `categoriesIcons`/screen registration if the screen goes away.
3. Add a migration that removes the orphaned key (`editor.remove("oldKey").apply()`) so old installs do
   not carry it forever.

## Changing the shape of a value

- Integer → enum: keep the numeric value, map it in `EnumPref`'s `prefId`, and add a migration only if
  the old numbers do not match the new ids.
- Boolean → three-way enum: migrate the old boolean, as `migrateStickerTimeMode` does.
- String → structured value: validate on read, fall back to the default, never crash on garbage.

## Review checklist for a settings change

- [ ] delegate exists, is named after the key, and sits under the right section
- [ ] row uses the screen's enum id and is dispatched by id, not by text
- [ ] `linkAlias` present and unique
- [ ] strings in `extera.xml`, read through `LocaleController`
- [ ] new numeric/float/set key added to `PreferencesUtils` validation
- [ ] migration added for a renamed or removed key
- [ ] `SettingsRegistry.newFeatures` updated if the feature is new
- [ ] a screen added to `categoriesIcons` if a screen was added
- [ ] `assembleFullArm64Debug` passes and the screen was opened once
