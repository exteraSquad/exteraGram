---
name: review
description: Review a change in exteraGram against the repository rules - mechanical style from REVIEW.md, scope and simplicity from AGENTS.md, full/lite source-set boundaries, keep rules, settings and localization conventions - then run the checks that apply and report findings ordered by severity. Use when the user asks to review a diff, commit, branch or working tree, or to check a change before committing.
---

# Review a Change

Read `AGENTS.md` and `REVIEW.md` first; they are the source of the rules. This skill is the order of
work and the report shape, not a second copy of the rules.

## 1. Scope the review

- Establish exactly what is under review: `git diff` (unstaged), `git diff --cached`, `git diff
  main...HEAD`, or a named commit range. Say which one you used.
- If the range includes generated files (`res-appicons/**`, `AndroidManifest-appicons.xml`,
  `GeneratedAppIcons.java`, `schemas/**`), check that they were produced by the generator, not edited by
  hand, and review the source spec instead of the output.
- If the range includes submodule pointers, keys, build outputs or version bumps, call them out before
  anything else: they usually mean the change carries something it should not.

## 2. Correctness and scope

- Does it do what was asked, and only that? Extra features, options, refactors and format passes are
  findings.
- Does it reach a never-happen outcome (crash, ANR, data loss, wrong data, security) or break the normal
  path? See `.agents/shared/keep-it-simple.md`.
- Does it add machinery the shipped analogue does not have? Name the analogue.
- Are lifecycle, threading and observer registrations paired? If the change touches
  `NotificationCenter`, delegates, `attach`/`detach`, run `python3 Tools/check_observers.py --filter
  <path>`.

## 3. Repository boundaries

- Upstream file edited: is the delta minimal, marked `// exteraGram: …`, unreformatted, and impossible to
  express from fork code?
- Plugin-facing class changed: is the `src/lite` counterpart updated with the same signature, and the
  Kotlin ↔ Python contract (`elyxcore/_plugin_engine.py`) still in step?
- `main` gained an import of a full-only class: blocking.
- New reflective, JNI or by-name class: does it have a narrow keep rule?
- New or renamed preference: delegate, row, alias, string, backup validation, migration — see
  `.agents/shared/settings.md`.
- New or changed string: `extera.xml`, `LocaleController.getString`, no hand-edited translations.

## 4. Mechanical style

Go through `REVIEW.md` item by item against the changed lines. Fix what is trivial and unambiguous;
report the rest. Do not rewrite untouched code to satisfy a rule.

## 5. Build and checks

| Change | Must have been run |
| --- | --- |
| Kotlin/Java | `:TMessagesProj:assembleFullArm64Debug` |
| Plugin API, `src/full`, `src/lite` | plus `:TMessagesProj:assembleLiteArm64Debug` |
| Native | the variant build that links the `.so` |
| Inline math/rates/units/time | `bash Tools/inline/run_tests.sh` |
| Plugins SDK | `python3.11 -m unittest discover -s plugins-pysdk/tests` |
| Observers/delegates | `Tools/check_observers.py` |

If a required build was not run, say so explicitly and treat unverified compilation as a blocking gap
unless the change is documentation-only. If keys or a keystore are missing, report that the checkout
cannot build instead of guessing.

## 6. Report

Order findings by severity and give each one: file, the rule, what is wrong, the concrete fix.

```markdown
## Blocking
- `path/File.kt` — full-only import in `main`… (AGENTS.md, "Full and lite flavors"); move it behind…

## Should fix
- `path/Other.java` — upstream delta without a marker (REVIEW.md, "Upstream markers"); add…

## Nit
- `path/Cell.java` — two blank lines before the closing brace (REVIEW.md, "Indentation and whitespace").

## Verified
- `:TMessagesProj:assembleFullArm64Debug` — pass.
- `Tools/check_observers.py --filter …` — no new findings.
- UI not observed on a device.
```

State what was verified and what was not. Do not claim a visual result you did not see.

## Hard rules

- Do not fix unrelated code, reformat files, or "improve" the change beyond the requested scope.
- Do not run builds, the Crowdin sync or device commands the review does not need.
- Never push, commit, or amend history as part of a review unless the user asks for that.
