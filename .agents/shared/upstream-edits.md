# Editing Upstream Telegram Code

Most of this repository is Telegram's Android client. The fork's value is in `com.exteragram.messenger`
and in a thin layer of changes to that upstream code. This file records the patterns that layer uses,
so a new change joins it instead of inventing a new way to patch Telegram.

## Principle

Prefer, in this order:

1. **Fork-owned code only.** A new helper, a new cell, a new screen under `com.exteragram.messenger`.
2. **One call from upstream into fork code.** The upstream file gains a guard and a call; the behavior
   lives in fork code where it can be tested, changed and reused.
3. **A small in-place patch**, marked with `// exteraGram: <reason>`, when the behavior is genuinely
   inside an upstream algorithm (a condition, a default, a layout constant).

Anything larger — moving an upstream method, renaming an upstream class, splitting an upstream file —
is a fork-wide maintenance decision, not part of a feature.

## Patterns already in the tree

### Config guard

`AndroidUtilities`, `ApplicationLoader`, `SearchEngine` and many UI files call directly into
`ExteraConfig`:

```java
if (!ExteraConfig.getUseSystemFonts()) {
    …
}
```

Java sees `ExteraConfig.getX()` because the Kotlin file is `@file:JvmName("ExteraConfig")` and its
top-level properties produce getters/setters. Kotlin reads the property (`ExteraConfig.useSystemFonts`
style import or a direct reference). A guard is the cheapest integration and survives upstream changes
better than a rewritten method.

### Feature delegation to a fork helper

When the behavior is more than a condition, upstream calls one fork helper and keeps the rest of its
code untouched. This is the dominant pattern in the tree:

- the restyled chat header: `ChatActivity`, `ChatAvatarContainer`, `ActionBar`, `ChannelAdminLogActivity`
  and `BlurredBackgroundProviderImpl` ask `ChatHeaderUiHelper` for avatar size and radius, glass style,
  top fade and panel height instead of computing their own;
- search engines: `SearchEngine` inserts `ExteraConfig.getYandexSearchEngine()` into its list;
- web view menus: `WebActionBar` adds `AdBlockMenuItem`;
- divider and corner behavior: `AndroidUtilities` asks `ExteraConfig.getDividerStyle()`,
  `getAvatarCorners(...)` and friends.

The helper owns the fork's state and preferences; the upstream call site stays a one-liner that a future
Telegram release can keep or re-apply. The header seam is deliberately large because it restyles a whole
region — but a new feature should start with a single call and grow only if the behavior demands it.

### Substitution at a seam

Where upstream already has an extension point (a base class, a `Theme.Colorable`, a `RecyclerView`
adapter, a menu builder), the fork subclasses or plugs into it rather than editing the base:

- a fork Fragment/Cell subclasses the Telegram one;
- a menu item is added through the existing `ItemOptions`/`UItem` API;
- a themed drawable implements the same `Theme` callbacks.

This is the preferred shape for visual features: no upstream delta at all.

### Reflection for full-only code

Upstream-called code must compile in both flavors. When it needs a class that only exists in `full`,
resolve it reflectively at the call site, as `ApplicationLoader.configureEmbeddedExteraHook()` does:

```java
Class.forName("com.exteragram.hook.ExteraHook")
    .getMethod("useEmbeddedRuntime")
    .invoke(null);
```

Guard it with `BuildConfig.EXTERA_HOOK_EMBEDDED`, and fail softly (log and continue) unless the class is
required for the app to start. A reflective call also needs a keep rule.

## The marker comment

Every in-place upstream delta carries:

```java
// exteraGram: <reason the behavior differs>
```

- Put it on the line above the delta, or as the trailing comment of a one-line insertion.
- Make the reason behavioral: `// exteraGram: destroy camera device` tells the next reader what the
  fork needs; `// exteraGram` alone does not.
- Multi-line deltas get one marker at the start, not one per line.
- Do not convert existing bare `// extera` markers, and do not reformat their code.

## Keeping the delta reviewable

- One delta per concern. If a feature needs three upstream edits, they should read as one idea, not as a
  clean-up pass.
- Do not rename upstream identifiers, reorder imports, convert tabs to spaces, or "modernize" adjacent
  code. Every unrelated line becomes a conflict later.
- Do not add a new preference or string inside an upstream file: add them through `ExteraConfig` and
  `extera.xml`.
- Keep `git diff` for an upstream file small enough to paste into a review. If a change to an upstream
  file is hundreds of lines, the design is probably wrong for this repository.

## When the tree moves to a new Telegram release

- A fork delta can disappear legitimately because Telegram implemented the same thing; check the
  neighboring code before restoring it.
- A fork delta can stop applying because the upstream method changed shape. Re-apply the behavior at
  the new seam, keep the marker, and note in the change what moved.
- A new upstream screen or list may need the fork's additions re-applied (search engines, menu items,
  theme callbacks). Grep for the last release's analogues instead of assuming.
- Never resolve a merge conflict by deleting the fork side "to make it compile"; if the behavior no
  longer applies, say so and remove the preference or the UI entry with it.

## What not to edit

- `org.telegram.**` files unrelated to the change.
- `jni/third_party/**`, `jni/td`, `jni/tgcalls`, `jni/tlottie`, `jni/wallet-engine`, `jni/twidget/**`,
  `TMessagesProj/lib/jlatexmath/**`, `TMessagesProj_Modules/media/**` — submodules and vendored code.
- `TMessagesProj/src/main/res/values/strings.xml` — Telegram's strings, translated elsewhere.
- Upstream tests, sample data or comments that merely look odd.

## Review checklist for an upstream edit

- [ ] the smallest possible delta; fork code could not have carried it
- [ ] `// exteraGram:` marker with a behavioral reason
- [ ] no reformatting, no import reordering, no drive-by fixes in the same file
- [ ] compiles in both `full` and `lite` (or is guarded)
- [ ] reflective/full-only access has a `BuildConfig` guard and a keep rule
- [ ] no new string or preference hardcoded in the upstream file
