---
name: settings
description: Add, change, rename or remove a user-facing option in exteraGram end to end - delegate in ExteraConfig, row in a preference screen, link alias for settings search and deeplinks, new-feature badge, extera.xml strings, backup validation and key migration. Use when the user asks for a new setting, toggle, slider, list choice or preference screen, or asks to rename, move or remove an existing option.
---

# Add or Change a Setting

Repository-wide rules are in `AGENTS.md`; mechanical style is in `REVIEW.md`; the full subsystem
reference is `.agents/shared/settings.md`. Read the reference before editing — this skill is the flow,
that file is the contract.

## 1. Establish what the option means

Write down, in one or two lines:

- the stored value and its type (boolean, enum, number with a range, string, set);
- where the user meets it (which existing screen: General, Appearance, Chats, Other, a feature screen);
- what changes when it changes, and whether the UI must rebuild, restart, or reload a resource;
- whether it should be searchable and linkable, and whether it is a "new" feature.

If the request only needs one correct value, it is not a setting. Do not add an option nobody asked
for.

## 2. Storage

Add the delegate in `ExteraConfig.kt` under the matching `// Section`, at the end of that section:

- boolean → `BooleanPref`
- fixed list → an enum implementing `PrefEnum` plus `EnumPref`
- bounded number → `IntegerPref`/`FloatPref` plus clamping in a getter, or `SanitizedIntegerPref`
- text → `StringPref` / `NullableStringPref`
- set → `StringSetPref`

Reuse an existing enum or value type when one already expresses the choices. Derived state is a
computed property, never a second stored key.

## 3. Row

In the screen's `fillItems(items, adapter)`:

- add the enum entry (ids are `ordinal + 1`) and the row in display order;
- choose `uCheck`, `uButton`, `UItem.asSlideView`, `UItem.asHeader`, `UItem.asShadow`;
- pass a unique `linkAlias`;
- pass a `subtext` where an example or an explanation helps.

In `onClick(item, view, position, x, y)` dispatch on the enum resolved from the item id:

- boolean → `toggleBooleanSettingAndRefresh(item) { … }`;
- choice → `showListDialog(item, options, title, currentValue) { index -> … }`;
- text/number → the existing dialog pattern on the screen;
- restart needed → `showRestartBulletin()`;
- UI rebuild needed → `parentLayout.rebuildFragments(0)`.

Never dispatch on the visible title, and never read `SharedPreferences` directly.

## 4. Strings

Add every new title, value label and shadow text to `TMessagesProj/src/main/res/values/extera.xml`
(PascalCase keys), and read it with the imported `getString` from `LocaleController`. No hardcoded
text, no `Context.getString`, no string in `values/strings.xml`.

## 5. Search, links and the new badge

- The `linkAlias` registers the row in settings search and makes it reachable as
  `https://t.me/exteraSettings?s=<alias>`.
- A new screen must be added to `SettingsRegistry.categoriesIcons` with an icon, or search cannot index
  it.
- A new feature adds its alias to `SettingsRegistry.newFeatures` for the badge.

## 6. Backup and migration

- Check `PreferencesUtils`: booleans and strings round-trip by default; **integers, floats, longs and
  string sets do not** unless `isExpectedInteger` / `isExpectedFloat` / `isExpectedStringSet` handle the
  key. Add the bounds for a new numeric option.
- Non-delegate keys need a `BackupItem` in `extraExteraKeys` or the matching array.
- Secrets and security toggles go to `excludedExteraKeys`.
- A renamed or removed key needs a `migrate*` function called from `ExteraConfig.loadConfig()`: read the
  old key, write the new one, remove the old one, stay idempotent.

## 7. Verify

1. `:TMessagesProj:assembleFullArm64Debug` passes.
2. Open the screen and toggle the option.
3. Find it in settings search by its title, and open it through the link alias if one exists.
4. If the option is backed up, export settings, reset the value, import, and confirm it returns.
5. Report what was verified and on which build; if no device was available, say so.

## Do not

- add a second preference for state that can be derived;
- renumber a screen's enum entries in the middle without checking every dispatch and alias;
- silently drop an option from backup by forgetting the validation entry;
- rename a key without a migration;
- add a setting that is only needed to work around a bug you introduced.
