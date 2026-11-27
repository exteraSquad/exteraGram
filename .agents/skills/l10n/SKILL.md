---
name: l10n
description: Work on exteraGram localization - add or edit strings in values/extera.xml, check how a string reaches the UI through LocaleController, pull translations from Crowdin with Tools/sync_crowdin.sh, or diagnose a string that stays in English. Use when the user asks to add, change or fix a translated string, sync or update translations, or investigate a missing or untranslated label.
---

# Localization

Repository-wide rules are in `AGENTS.md`; the subsystem reference is
`.agents/shared/localization.md`. Read it before touching files — the important split is Telegram's own
`strings.xml` (not ours) versus the fork's `extera.xml` (ours, translated on Crowdin).

## Pick the flow

| Request | Flow |
| --- | --- |
| "add a label for this new option" | New string |
| "change this wording" / "this text is wrong" in the base language | Base edit |
| "update translations" / "pull Crowdin" | Sync |
| "the app shows English in Russian" | Diagnose |
| "translate this into X" | Crowdin, not this repository |

A request that fits none, or several, is a question for the user.

## New string

1. Add one `<string name="PascalCaseKey">English text</string>` to
   `TMessagesProj/src/main/res/values/extera.xml`, in the matching `<!-- Section -->`.
2. Use `%1$s` / `%1$d` placeholders if the text has substitutions; keep any existing placeholder order.
3. Reference it as `getString(R.string.PascalCaseKey)` with the imported `LocaleController` helper.
4. Build (`:TMessagesProj:assembleFullArm64Debug`) and open the screen.
5. Report the key and where it is shown; a string that was never seen rendered is unverified.

Brand names and literals that must not be translated carry `translatable="false"`.

## Base edit

1. Change the English text in place, keep the key.
2. Do not edit any `values-*/extera.xml`; those are Crowdin output.
3. Expect the translation to show as outdated on the next sync; that is correct.

## Sync

```bash
CROWDIN_PERSONAL_TOKEN=… bash Tools/sync_crowdin.sh            # upload source on main + download
CROWDIN_PERSONAL_TOKEN=… bash Tools/sync_crowdin.sh --dryrun   # dry run
```

- The token comes from the environment only. Never print it, write it to a file or put it on a command
  line that ends up in history or logs.
- The source upload only happens on `main` and only when `values/extera.xml` is clean; on a branch the
  script only downloads.
- The script needs the Crowdin CLI and network access. If either is missing, report it instead of
  hand-editing translations.
- Review the downloaded diff. `Tools/cleanup_crowdin_translations.py` and
  `Tools/collect_crowdin_edits.py` exist for cleaning and inspecting the result.
- Never add a language directory by hand.

## Diagnose "it stays in English"

Check, in this order:

1. Is the key in `values/extera.xml` and not in `values/strings.xml`?
2. Is it read with `LocaleController.getString(R.string.Key)` and not `context.getString(...)`? Only the
   former sees the language pack built from `assets/extera_locales/**`.
3. Does a `values-<lang>/extera.xml` exist for that language, and does it contain the key? Missing means
   untranslated, which correctly falls back to English.
4. Is the text cached in a field initialized once, instead of computed in `fillItems`/`getTitle`? The
   settings search index and screens are rebuilt on language change.
5. Did the build actually run `syncExteraLocaleAssets` (it is wired into the merge-assets tasks) and
   does the APK contain `extera_locales/values-<lang>/extera.xml`?

Report which of these failed; do not "fix" it by copying a translation into the base file.

## Hard rules

- `values/extera.xml` is English source. Never put another language in it.
- Do not rename a key without updating all references and accepting the translation loss.
- Do not add `<plurals>`; use `LocaleController.formatPluralString`.
- Do not translate inside code paths for Telegram's own strings.
- Do not commit a Crowdin token, a downloaded private key or `values-*/extera.xml` changes you did not
  produce through the sync tool.
