# Localization

exteraGram has two separate translation pipelines, and mixing them up is the most common localization
bug:

- **Telegram strings** — `TMessagesProj/src/main/res/values/strings.xml` and its `values-*`
  translations. They come from Telegram's own translations platform. Not ours; do not add fork strings
  there.
- **Fork strings** — `TMessagesProj/src/main/res/values/extera.xml` and its `values-*` translations.
  Ours, translated on Crowdin (<https://crowdin.com/project/exteralocales>).

## Source file

`TMessagesProj/src/main/res/values/extera.xml`:

```xml
<resources>
    <string name="exteraAppName" translatable="false">exteraGram</string>
    <string name="Preferences">exteraGram Preferences</string>

    <!-- Main Menu -->
    <string name="Links">Links</string>
    <string name="Crowdin">Translate</string>
```

- Keys are PascalCase and match Telegram's legacy resource naming (`R.string.CustomSavePath`), not
  snake_case.
- Keys are a public contract: they appear in `R.string.*` references across Kotlin, Java and the plugin
  SDK. Renaming one silently drops its translations, so renaming needs a deliberate pass over all uses.
- Brand names and other untranslatable literals carry `translatable="false"`; everything else is
  translatable and must be English in the base file.
- Conventional `%1$s`, `%1$d` placeholders. Keep them when editing a string that already has them; the
  translations carry the same placeholders.
- No `<plurals>` in this file. Counted text uses `LocaleController.formatPluralString(...)` with
  Telegram's own plural resources, so a sentence with a count can still live in `extera.xml` if the
  count is passed as a placeholder.
- Keep the section comments (`<!-- Main Menu -->`) when adding a string to an existing area; a new area
  gets a new comment.
- Keep the XML license header at the top.

## Reading a string in code

```kotlin
import org.telegram.messenger.LocaleController.getString

title = getString(R.string.CustomSavePath)
```

- Use `LocaleController.getString(R.string.Key)` — directly or through the imported `getString` — and
  never a raw `Context.getString`.
- Why: `gradle/extera-locales.gradle` copies every `**/extera.xml` into
  `assets/extera_locales/<qualifier>/extera.xml`, and `LocaleController` loads those assets (base plus
  language/region variants) into its language pack at runtime, including languages delivered from
  Crowdin. `Context.getString` reads the compiled resource table and bypasses that pack, so a fork
  string can come back in English or stale.
- Format with count and arguments:
  ```kotlin
  val text = LocaleController.formatString(R.string.SettingLinkToUser, peerName)
  ```
- Reactive text built from `getString` keeps working; rebuild the fragment when the language changes
  (the app recreates the UI on language switch).

## Runtime asset qualifiers

The Gradle sync maps directory names mechanically:

| Directory | Asset |
| --- | --- |
| `values/extera.xml` | `extera_locales/values/extera.xml` |
| `values-ru/extera.xml` | `extera_locales/values-ru/extera.xml` |
| `values-pt-rBR/extera.xml` | `extera_locales/values-pt-rBR/extera.xml` |

`LocaleController` resolves the current locale to `values-<language>` and `values-<language>-r<REGION>`,
with region variants before generic ones. That is why the directory suffixes must follow Android
resource qualifiers, not Crowdin's language codes.

## Crowdin sync

Configuration: `crowdin.yml` at the root.

- Project `494595`; source `/TMessagesProj/src/main/res/values/extera.xml` → `/extera.xml`;
  translations come back to `values-%two_letters_code%/extera.xml`.
- Language mapping: `he` → `iw`, `id` → `in`, `pt-BR` → `pt-rBR`, `zh-CN` → `zh-rCN`.
- `skip_untranslated_strings: true` — a language file only contains what is translated, so the English
  base must stay complete.

`Tools/sync_crowdin.sh` drives the sync:

```bash
CROWDIN_PERSONAL_TOKEN=… bash Tools/sync_crowdin.sh            # full sync
CROWDIN_PERSONAL_TOKEN=… bash Tools/sync_crowdin.sh --dryrun   # report only
```

- The token is read from the environment only. Never write it to a file, a command line, a log or a
  commit.
- The source upload runs only on `main` and only when `values/extera.xml` has no uncommitted changes.
  On a branch, the script skips the upload and only downloads.
- `CROWDIN_MIN_COMPLETION_PERCENT` (default 25) filters out languages below that completion.
- The script runs the Crowdin CLI through `npx --yes @crowdin/cli@4`, so it needs Node/npm and network
  access; the npm cache is redirected to a temp directory.

Helpers next to it:

- `Tools/cleanup_crowdin_translations.py` — cleans invalid resources and malformed escaping in
  downloaded translation files.
- `Tools/collect_crowdin_edits.py` — collects what changed between the downloaded translations and the
  tree, reusing the cleanup parser.

Treat `values-*/extera.xml` as generated: **do not hand-edit a translation**. If a translation is wrong,
it is fixed on Crowdin and re-synced. If a translation breaks a build or a layout, report it instead of
rewriting the file.

## Adding a string

1. Add it to `values/extera.xml` in the right section, English, with a PascalCase key.
2. Reference it from code with `getString(R.string.Key)`.
3. Build at least one variant to catch a missing or duplicated resource. Resource errors are reported by
   `processDebugResources`, not by the Kotlin compiler.
4. Do not create or copy `values-xx/extera.xml`. Crowdin does that.
5. If the string is only a fallback for a language that already has a translation, the base value still
   matters: it is what every untranslated language shows.

## Changing or removing a string

- Changing the English text is fine; Crowdin marks the translation outdated and translators redo it.
- Renaming the key is a migration: update every `R.string` reference, any `linkAlias` that named the
  setting, and expect existing translations for the old key to be dropped by the next sync.
- Removing a key while a translation still references it is fine for the build (`ExtraTranslation` is
  disabled in `lint`), but the next sync cleans it up. Prefer removing the string in the same change as
  its last use.
- Never change the meaning of a key while keeping its name; translators will not see the change.

## Settings search and translations

Setting titles come from these strings, and settings search indexes the rendered title. A row whose
title is missing from `extera.xml` shows a resource error at build time; a row whose title is built from
a hardcoded literal is invisible to every language and to search. Both are review blockers.

The settings search index is rebuilt when the language changes
(`SettingsRegistry.createEntriesIfNeeded`), so a screen must not cache translated text in a field
initialized once — compute it in `fillItems` / `getTitle`.

## Verifying

- Build a variant, switch the app language in the settings, and check the string on the screen and in
  settings search.
- For a Crowdin round trip, run the sync into a scratch branch and inspect the diff; never commit a
  translation you did not download through the tool.
- Report what was verified and in which language; a string that was never seen rendered is unverified.
