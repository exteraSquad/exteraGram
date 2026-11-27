# Keep It Simple

**DON'T OVERENGINEER.** This is the owner's rule for every change, review and fix in this repository,
in every area of the client and its tooling.

The best solution is the simplest one that fully does what was asked. It works well and fast on the
normal path — the thing a user of a working app does nearly every time. It never reaches a
never-happen outcome. Everything a design adds beyond that has to earn its place or come out: code,
layers, options, generality, guarantees, checks, tests, steps. When two designs both work, choose the
simpler one, even if it is a little less optimal in rare cases.

## What overengineering looks like

- **Scope.** Doing more than asked: extra options or settings, side cleanups, support for callers or
  inputs nobody has, a new preference screen for a value that has one correct answer.
- **Structure.** Abstraction before a second user: an interface with one implementation, a manager,
  registry or framework around one call, a state machine over a linear flow, a new
  `com.exteragram.messenger` package where an existing helper fits.
- **Generality.** Solving a general problem when the specific one was asked. Plugin points, hook
  points, configuration and extension hooks "for later".
- **Defensiveness.** Checks for states the code cannot reach, fallbacks for fallbacks, re-validating
  trusted internal data at every layer.
- **Guarantees.** Promising more in rare cases than anyone needs (see "Rare cases heal themselves").
- **Reinvention.** A new mechanism where the client already has one that fits — another settings
  screen instead of `BasePreferencesActivity`, another list instead of `UItem`/`UniversalAdapter`,
  another loader instead of `ImageLoader`/`FileLoader`.
- **Process.** Tests, fixtures, evidence, tooling, phases or documents beyond what proves the result.

## Never-happen outcomes

These are the only reasons to go beyond the simple design:

- an ANR, a crash, an OOM, undefined behavior, a frozen UI;
- a logout, or loss of the user's session, authorization or passcode;
- irreversible loss or corruption of user data: messages, drafts, settings, saved files, wallet keys,
  money;
- a security or privacy failure, such as data reaching the wrong account or chat, a secret written to
  a log or a backup, or unvalidated outside input trusted;
- wrong data presented as right, such as a message shown in the wrong chat, a chat row under the wrong
  title, a cached item that does not match what it claims to be, an amount in the wrong currency;
- a state that never recovers by itself: stuck until the user clears data, or crashing on every
  launch;
- a slower or worse normal path.

## Rare cases heal themselves

Rare cases include:

- an interrupted operation (a crash, a cancel, a logout, a process death, a rotation);
- concurrent or reentrant callbacks from `NotificationCenter`, coroutines, plugin code or the network;
- no network, a slow network, a server error, a flood wait;
- unusual input, a malformed plugin, an unreadable file;
- a timing no user produces on purpose.

A rare case only has to end up correct again by the next use, the next retry, the next restart, or the
user repeating the action. It does not need the best possible outcome. Prefer these shapes:

- **Fail and start over** rather than recover or resume. An upload whose connection dropped starts
  again.
- **Throw away what is unfinished** rather than journal it. A half-applied draft is dropped on the next
  open.
- **Reset rather than preserve** a rare intermediate state. A rotation during an animation jumps to the
  end state.
- **Join or serialize** concurrent requests rather than coordinate them. Two callers wanting the same
  file share one `FileLoader` request; an occasional duplicate request or an ignored late callback is
  fine.
- **Retry once or not at all** rather than build a retry state machine.
- **Keep state on the main thread, or on the plugin queue.** Only blocking or CPU-heavy work goes to
  the background and returns a result. A weak reference drops late results.
- **Persist only what the normal path needs** after a restart. Counters, guards and intermediate
  progress stay in memory, unless losing them reaches a never-happen outcome.
- **Reject unusual input cleanly** rather than interpret every variant of it.

## Match the shipped precedent

The closest shipped feature of this client sets the level of structure and guarantees. When it handles
something simply, so does the new code. Going past it needs a never-happen reason, stated once. A change
several times the size of its analogue is overengineered until shown otherwise.

## Simple, not sloppy

Simplicity never excuses:

- a broken or slow normal path;
- a never-happen outcome;
- duplicating code that a shared helper already covers;
- ignoring the repository conventions in `AGENTS.md` and `REVIEW.md`;
- a hack the next change has to work around.

The goal is the smallest correct change in the codebase's existing shape. A shortcut that makes code
harder to read or change is not simple.

## Applying it here

- **Planning a change:** write the smallest design that does it, and name the shipped analogue
  (which preference screen, which cell, which controller). For every new file, class, option or
  abstraction, name what requires it. State which never-happen outcomes the change can reach and how
  it prevents them.
- **Writing code:** put new behavior in fork code, call it from upstream with one marked line, reuse the
  existing settings row or cell type, and skip the option nobody asked for.
- **Reviewing:** blocking means a broken or slower normal path, a reachable never-happen outcome, or a
  failing requested scenario. A rare-case finding that reaches no never-happen outcome and heals by
  itself is not blocking; list it as a known limitation. Needless machinery the change added is a valid
  finding, and its fix is deletion.
- **Fixing:** repair with the smallest change, and remove a mechanism rather than add one. When one
  mechanism keeps producing rare-case defects, replace it with a simpler shape instead of layering a
  check on top.
