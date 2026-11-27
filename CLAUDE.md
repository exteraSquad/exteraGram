# Claude Code Pointer

Read `AGENTS.md` and treat it as the canonical repository-wide instructions. Read `REVIEW.md` before
reviewing or committing anything.

- On-demand references live in `.agents/shared/`: `build-and-verify.md`, `settings.md`,
  `localization.md`, `plugins-sdk.md`, `upstream-edits.md`, `keep-it-simple.md`.
- Workflows live in `.agents/skills/`: `settings`, `l10n`, `review`, also exposed as
  `/settings`, `/l10n`, `/review` through `.claude/commands/`.
- Load a skill when the request matches it, and read the matching reference before editing the files it
  covers.
