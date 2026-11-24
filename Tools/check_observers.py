#!/usr/bin/env python3
"""
Registration symmetry checker for NotificationCenter observers and friends.

Telegram registers observers with NotificationCenter.addObserver(observer, id) and is
expected to unregister the exact same (observer, id) pair on the exact same
NotificationCenter instance. Getting any part of that triple wrong leaks the observer -
usually a whole fragment or view tree - for the lifetime of the process.

Rather than counting `addObserver` against `removeObserver` per file, this script pairs
registrations up on all three axes:

  observer   resolved per class scope, so `this` inside an anonymous class is never
             confused with `this` of the outer class,
  id         the NotificationCenter.* constant,
  instance   global instance vs. account instance, including the account expression.

Self-removing anonymous delegates and `instance`-style self references are understood
and not reported.

The same pairing runs over the other registration APIs that leak the same way -
NotificationCenter.ObserversGroup, addDelegate/removeDelegate registries,
AnimatedEmojiDrawable.addView/removeView (its cache is static, so a missing removeView
pins the whole view tree) and x.attach()/x.detach(). Those compare aliases rather than
exact text, so `field = local` assignments, casts, array subscripts and two locals of
the same type are recognised as one slot.

Finding types:

  never-removed           an id is registered and never unregistered
  add-in-teardown         addObserver sits in onDestroy/dismiss/... - a removeObserver typo
  instance-mismatch       registered on the global center, unregistered on an account one
  account-mismatch        registered and unregistered for different accounts
  lifecycle-mismatch      registered from a lifecycle entry point, released nowhere near one
  group-never-released    createObserversGroup() without removeAllObservers()
  *-never-released        addDelegate/addView/attach with no matching release call
  *-indirect-release      released only by handing ownership over (x.destroy()), so the
                          pairing depends on a method this file cannot see
  inverted-guard          `if (x) return; x = false;` - the early return makes the
                          unregistering below it unreachable
  orphan-registration     addObserver(id) whose didReceivedNotification never looks at id
  orphan-handler          didReceivedNotification handles an id nobody registers for
  never-added             removeObserver() for an id this class never registers (dead code)

Findings are ranked by confidence; --all shows the suppressed ones, which are mostly
singletons and static initialisers whose registrations are permanent by design.
"""

import argparse
import json
import os
import re
import sys
from collections import defaultdict, namedtuple

# ---------------------------------------------------------------------------- lexing

def strip_noise(text, kotlin):
    """Blank out comments and literals, preserving offsets and line numbers."""
    out = list(text)
    n = len(text)
    i = 0

    def blank(start, end):
        for k in range(start, min(end, n)):
            if out[k] != "\n":
                out[k] = " "

    while i < n:
        c = text[i]
        if c == "/" and i + 1 < n and text[i + 1] == "/":
            j = text.find("\n", i)
            j = n if j < 0 else j
            blank(i, j)
            i = j
        elif c == "/" and i + 1 < n and text[i + 1] == "*":
            j = text.find("*/", i + 2)
            j = n if j < 0 else j + 2
            blank(i, j)
            i = j
        elif c == '"' and kotlin and text.startswith('"""', i):
            j = text.find('"""', i + 3)
            j = n if j < 0 else j + 3
            blank(i, j)
            i = j
        elif c in "\"'":
            j = i + 1
            while j < n:
                if text[j] == "\\":
                    j += 2
                    continue
                if text[j] == c or text[j] == "\n":
                    break
                j += 1
            blank(i, j + 1)
            i = j + 1
        else:
            i += 1
    return "".join(out)


# ---------------------------------------------------------------------------- scopes

CLASS_DECL_RE = re.compile(r"\b(class|interface|enum|record)\s+([A-Za-z_]\w*)")
# `object Foo` is a Kotlin declaration; in Java `object` is just a common variable name
# that would otherwise turn `object instanceof Foo` into a class called `instanceof`
KT_CLASS_DECL_RE = re.compile(
    r"\b(class|interface|enum|record|object)\s+([A-Za-z_]\w*)")
ANON_CLASS_RE = re.compile(r"\bnew\s+([\w.]+)\s*(?:<[^;{]*>)?\s*\([^;{]*\)\s*$")
KT_ANON_RE = re.compile(r"\bobject\s*:\s*([\w.]+)[^;{]*$")
KT_COMPANION_RE = re.compile(r"\bcompanion\s+object\s*$")
METHOD_RE = re.compile(
    r"([A-Za-z_]\w*)\s*\((?:[^()]|\([^()]*\))*\)\s*"
    r"(?::\s*[\w.<>?,\[\]\s]+)?(?:throws\s+[\w.,\s]+)?\s*$"
)
EXTENDS_RE = re.compile(r"\b(?:extends|implements|:)\s+([^{]*)$")
NON_METHOD_NAMES = {
    "if", "while", "for", "switch", "catch", "synchronized", "do", "else",
    "try", "return", "new", "when", "run", "let", "also", "apply", "with",
}


class Scope:
    __slots__ = ("start", "end", "kind", "name", "parent", "extends", "uid")

    def __init__(self, start, kind, name, parent, extends, uid):
        self.start = start
        self.end = 1 << 60
        self.kind = kind          # 'file' | 'class' | 'method' | 'block'
        self.name = name
        self.parent = parent
        self.extends = extends
        self.uid = uid


def build_scopes(text, kotlin):
    """Brace-walk the file, returning the scope tree plus an ordered opening index."""
    root = Scope(0, "file", "<file>", None, "", 0)
    stack = [root]
    all_scopes = [root]
    starts = []
    last_break = 0
    uid = 0
    n = len(text)
    i = 0
    while i < n:
        c = text[i]
        if c == "{":
            header = text[last_break:i]
            uid += 1
            kind, name, extends = "block", "", ""
            decl = (KT_CLASS_DECL_RE if kotlin else CLASS_DECL_RE).search(header)
            anon = ANON_CLASS_RE.search(header)
            kt_anon = KT_ANON_RE.search(header) if kotlin else None
            if decl and not header.rstrip().endswith("->"):
                kind, name = "class", decl.group(2)
                em = EXTENDS_RE.search(header[decl.end():])
                extends = em.group(1) if em else ""
            elif anon:
                kind = "class"
                name = "<anon:%s>" % anon.group(1)
                extends = anon.group(1)
            elif kt_anon:
                kind = "class"
                name = "<anon:%s>" % kt_anon.group(1)
                extends = kt_anon.group(1)
            elif kotlin and KT_COMPANION_RE.search(header):
                kind, name = "class", "Companion"
            elif header.strip() == "static":
                kind, name = "static-init", "<clinit>"
            else:
                meth = METHOD_RE.search(header)
                if meth and meth.group(1) not in NON_METHOD_NAMES:
                    kind, name = "method", meth.group(1)
            sc = Scope(i, kind, name, stack[-1], extends, uid)
            stack.append(sc)
            all_scopes.append(sc)
            starts.append((i, sc))
            last_break = i + 1
        elif c == "}":
            if len(stack) > 1:
                stack.pop().end = i
            last_break = i + 1
        elif c == ";":
            last_break = i + 1
        i += 1
    return root, all_scopes, starts


def scope_at(starts, all_scopes, offset):
    best = all_scopes[0]
    for start, sc in starts:
        if start > offset:
            break
        if sc.end >= offset and sc.start >= best.start:
            best = sc
    return best


def enclosing(scope, kind):
    while scope is not None:
        if scope.kind == kind:
            return scope
        scope = scope.parent
    return None


def named_class(scope):
    """Nearest enclosing class that is not anonymous - anonymous bodies belong to it."""
    s = enclosing(scope, "class")
    while s is not None and s.name.startswith("<anon:"):
        s = enclosing(s.parent, "class")
    return s


def class_label(scope):
    names = []
    s = enclosing(scope, "class")
    while s is not None:
        names.append(s.name)
        s = enclosing(s.parent, "class")
    return ".".join(reversed(names)) or "<file>"


# ------------------------------------------------------------------- call extraction

# Registration APIs that must be called in pairs. `by_arg` says whether the thing being
# registered is the first argument (addDelegate(x)) or the receiver itself (x.attach()).
# `receiver_types` restricts a generic name to receivers of a known type.
# `add_arg`/`remove_arg` is the index of the registered subject in the argument list, or
# None when the receiver itself is the subject (`x.attach()`).
PairedApi = namedtuple(
    "PairedApi",
    "add remove family add_arg remove_arg receiver_types skip_receivers")
PAIRED_APIS = (
    # Bulletin stores its delegate on the fragment/view it decorates rather than in a
    # registry, so an unpaired Bulletin.addDelegate cannot outlive its owner.
    PairedApi("addDelegate", "removeDelegate", "delegate", 0, 0, None, {"Bulletin"}),
    # AnimatedEmojiDrawable lives in a static cache and holds its views strongly, so a
    # missing removeView pins the whole view tree. addView is also ViewGroup.addView,
    # hence the type restriction.
    # (addViewListening is deliberately absent: it wires the view's own attach-state
    # listener, so it removes itself.)
    PairedApi("addView", "removeView", "emoji-view", 0, 0,
              {"AnimatedEmojiDrawable", "SwapAnimatedEmojiDrawable"}, None),
    PairedApi("addParentView", "removeParentView", "parent-view", 0, 0, None, None),
    # AnimatedEmojiSpan.update(cacheType, view, ...) registers the view with the same
    # static drawable cache; release(view, spans) is the only way back out.
    PairedApi("update", "release", "emoji-spans", 1, 0, {"AnimatedEmojiSpan"}, None),
    # x.attach() keeps an ImageReceiver attached and its bitmaps resident
    PairedApi("attach", "detach", "attach", None, None, None, None),
)
PAIRED_BY_NAME = {}
for _api in PAIRED_APIS:
    PAIRED_BY_NAME.setdefault(_api.add, []).append((_api, "add"))
    PAIRED_BY_NAME.setdefault(_api.remove, []).append((_api, "remove"))

# `x.attach(...)` is also released when ownership of x is handed over wholesale
OWNERSHIP_RELEASE = ("destroy", "release", "recycle")
CALL_RE = re.compile(
    r"\b(addObserver|removeObserver|createObserversGroup|removeAllObservers|%s)\s*\("
    % "|".join(sorted({n for api in PAIRED_APIS for n in (api.add, api.remove)}
                      | set(OWNERSHIP_RELEASE)))
)
SUBSCRIPT_RE = re.compile(r"\[[^\[\]]*\]")
NC_CONST_RE = re.compile(r"\bNotificationCenter\s*\.\s*([a-z]\w*)\b")
NC_DECL_RE = re.compile(r"static\s+final\s+int\s+(\w+)\s*=\s*totalEvents\+\+")
DISPATCHES_ON_ID_RE = re.compile(r"\bid\s*==|==\s*id\b|switch\s*\(\s*id\s*\)")
# only ids the handler actually dispatches on; a NotificationCenter.* that merely gets
# posted from inside the body is not "handled"
HANDLED_ID_RE = re.compile(
    r"\bid\s*[!=]=\s*NotificationCenter\s*\.\s*(\w+)"
    r"|NotificationCenter\s*\.\s*(\w+)\s*[!=]=\s*id\b"
    r"|\bcase\s+NotificationCenter\s*\.\s*(\w+)\s*:")
HANDLER = "didReceivedNotification"


def load_notification_ids(root):
    """The real NotificationCenter.* event names, so `getInstance` is not mistaken
    for one when scanning a didReceivedNotification body."""
    for dirpath, _dirs, filenames in os.walk(root):
        if "NotificationCenter.java" in filenames:
            with open(os.path.join(dirpath, "NotificationCenter.java"),
                      encoding="utf-8") as f:
                return set(NC_DECL_RE.findall(f.read()))
    return set()


ALL_NC_IDS = set()

# cheap pre-filter: a file with none of these cannot produce a finding
TRIGGER_TOKENS = ("addObserver", "createObserversGroup", "return;") + tuple(
    api.add for api in PAIRED_APIS)

# `if (attached) return; attached = false;` - the guard returns exactly when the
# assignment would do something, so the body below it is dead and the observer never
# gets unregistered. The mirror image, `if (!x) return; x = true;`, is the same bug.
GUARD_RE = re.compile(
    r"\bif\s*\(\s*(!?)\s*([A-Za-z_][\w.]*)\s*\)\s*\{?\s*return\s*;\s*\}?\s*"
    r"([A-Za-z_][\w.]*)\s*=\s*(true|false)\s*;"
)
IDENT_CHARS = set(
    "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_$"
)


def read_args(text, open_paren):
    """Return (raw argument text, index past ')') for a balanced '(' at open_paren."""
    depth = 0
    i = open_paren
    n = len(text)
    while i < n:
        c = text[i]
        if c in "([{":
            depth += 1
        elif c in ")]}":
            depth -= 1
            if depth == 0:
                return text[open_paren + 1:i], i + 1
        i += 1
    return "", n


def split_args(raw):
    args, depth, cur = [], 0, []
    for c in raw:
        if c in "([{":
            depth += 1
        elif c in ")]}":
            depth -= 1
        if c == "," and depth == 0:
            args.append("".join(cur).strip())
            cur = []
        else:
            cur.append(c)
    tail = "".join(cur).strip()
    if tail:
        args.append(tail)
    return args


def read_receiver(text, name_start):
    """Walk backwards over a dotted call chain and return its source text."""
    i = name_start - 1
    while i >= 0 and text[i].isspace():
        i -= 1
    if i < 0 or text[i] != ".":
        return ""
    end = i
    i -= 1
    while True:
        while i >= 0 and text[i].isspace():
            i -= 1
        if i >= 0 and text[i] == ")":
            depth = 0
            while i >= 0:
                if text[i] in ")]":
                    depth += 1
                elif text[i] in "([":
                    depth -= 1
                    if depth == 0:
                        i -= 1
                        break
                i -= 1
            continue
        if i >= 0 and text[i] in IDENT_CHARS:
            while i >= 0 and text[i] in IDENT_CHARS:
                i -= 1
            while i >= 0 and text[i].isspace():
                i -= 1
            if i >= 0 and text[i] == ".":
                i -= 1
                continue
        break
    return norm(text[i + 1:end])


def norm(s):
    return re.sub(r"\s+", " ", s).strip()


Paired = namedtuple("Paired", "side line expr recv cls_label method_scope category "
                              "api recv_aliases subject_aliases")


def aliases(expr, ident_types, assignments):
    """
    Every name that can denote the same registration slot: the expression itself, the
    other half of a `field = local` assignment, and the declared type, so
    `DrawingText other` and `DrawingText drawingText` are recognised as one family.
    """
    base = normalise_target(expr)
    if not base:
        return {""}
    out = {base}
    for lhs, rhs in assignments:
        if lhs == base:
            out.add(normalise_target(rhs))
        elif normalise_target(rhs) == base:
            out.add(lhs)
    for name in list(out):
        head = name.split(".")[0].split("[")[0]
        if head in ident_types:
            out.add("type:" + ident_types[head])
        if "." in name:
            # `botButton.animatedEmojiDrawable` and `button.animatedEmojiDrawable`
            # reach the same field through different holders
            out.add("field:" + name.rsplit(".", 1)[1])
    return out


def normalise_target(expr):
    """`this.foo[i]` and `Outer.this.foo[a]` both denote the same registration slot."""
    e = norm(expr)
    if e == "null":
        return ""
    e = re.sub(r"\.\s*(?:get|valueAt|keyAt)\s*\([^()]*\)\s*$", "", e)
    for _ in range(3):
        stripped = e
        if stripped.startswith("(") and stripped.endswith(")"):
            inner = stripped[1:-1].strip()
            if balanced(inner):
                stripped = inner
        stripped = re.sub(r"^\((?:final\s+)?[\w.<>\[\]]+\)\s*", "", stripped)
        stripped = re.sub(r"^(?:[A-Za-z_]\w*\s*\.\s*)?this\s*\.\s*", "", stripped)
        stripped = re.sub(r"^[A-Za-z_]\w*\s*\.\s*this$", "this", stripped)
        if stripped == e:
            break
        e = stripped
    return SUBSCRIPT_RE.sub("[]", e)


def balanced(s):
    depth = 0
    for c in s:
        if c in "([":
            depth += 1
        elif c in ")]":
            depth -= 1
            if depth < 0:
                return False
    return depth == 0


GLOBAL_RE = re.compile(r"getGlobalInstance\s*\(\s*\)\s*$")
INSTANCE_RE = re.compile(r"getInstance\s*\(([^()]*)\)\s*$")
FRAGMENT_NC_RE = re.compile(r"getNotificationCenter\s*\(\s*\)\s*$")


def classify_receiver(recv):
    """-> (kind, account expression); kind is global | account | var | implicit."""
    r = norm(recv)
    if not r:
        return "implicit", ""
    if GLOBAL_RE.search(r):
        return "global", ""
    if FRAGMENT_NC_RE.search(r):
        # BaseFragment / BaseController: the account instance for currentAccount
        return "account", "currentAccount"
    m = INSTANCE_RE.search(r)
    if m:
        return "account", strip_assignment(norm(m.group(1)))
    return "var", r


ASSIGN_EXPR_RE = re.compile(r"^([A-Za-z_]\w*)\s*=[^=]")


def strip_assignment(expr):
    """`getInstance(lastAccount = UserConfig.selectedAccount)` tracks `lastAccount`."""
    m = ASSIGN_EXPR_RE.match(expr)
    return m.group(1) if m else expr


ID_RE = re.compile(r"^NotificationCenter\s*\.\s*([A-Za-z_]\w*)$")


def classify_id(expr):
    """-> (display name, True only for a literal NotificationCenter.* constant)."""
    e = norm(expr)
    m = ID_RE.match(e)
    if m:
        return m.group(1), True
    return e, False


THIS_RE = re.compile(r"^(?:([A-Za-z_]\w*)\s*\.\s*)?this$")
BARE_IDENT_RE = re.compile(r"^[A-Za-z_]\w*$")


def resolve_this_scope(outer_name, scope):
    s = enclosing(scope, "class")
    if outer_name:
        while s is not None and s.name != outer_name:
            s = enclosing(s.parent, "class")
    return s


def classify_observer(expr, scope, kotlin, self_fields):
    """
    Resolve an observer expression to an identity that is stable within the file.

    Returns (key, resolved class scope or None). A bare identifier declared with the
    type of its own enclosing class (the `private static VoIPPiPView instance` idiom)
    resolves to that class, so it pairs with `removeObserver(this, ...)`.
    """
    e = norm(expr)
    m = THIS_RE.match(e)
    if m:
        s = resolve_this_scope(m.group(1), scope)
        if s is None:
            return e, None
        return "this#%d" % s.uid, s
    if kotlin and e.startswith("this@"):
        s = resolve_this_scope(e[5:], scope)
        return ("this#%d" % s.uid, s) if s else (e, None)
    if BARE_IDENT_RE.match(e):
        cls = enclosing(scope, "class")
        while cls is not None:
            if e in self_fields.get(cls.name, ()):  # field typed as its own class
                return "this#%d" % cls.uid, cls
            cls = enclosing(cls.parent, "class")
    return e, None


def collect_self_fields(text):
    """(class name -> idents declared with that type, ident -> its declared type)."""
    by_type = defaultdict(set)
    by_ident = {}
    for m in re.finditer(r"\b([A-Z]\w*)\s+([a-zA-Z_]\w*)\s*[;=)]", text):
        by_type[m.group(1)].add(m.group(2))
        by_ident.setdefault(m.group(2), m.group(1))
    # a collection is aliased to its element type, so `items.get(i).detach()` and
    # `Item item; item.attach()` are recognised as the same family
    for m in re.finditer(r"\b[A-Z]\w*\s*<\s*([A-Z]\w*)[^<>]*>\s+([a-zA-Z_]\w*)", text):
        by_type[m.group(1)].add(m.group(2))
        by_ident.setdefault(m.group(2), m.group(1))
    for m in re.finditer(r"\b(?:val|var)\s+([a-zA-Z_]\w*)\s*:\s*([A-Z]\w*)", text):
        by_type[m.group(2)].add(m.group(1))
        by_ident.setdefault(m.group(1), m.group(2))
    return by_type, by_ident


# ------------------------------------------------------------------ class categories

UI_HINTS = (
    "BaseFragment", "View", "Layout", "Cell", "BottomSheet", "Dialog", "Sheet",
    "Adapter", "Drawable", "Activity", "Alert", "Popup", "Window", "Bulletin",
    "Renderer", "Container", "Page", "Preview", "Button", "Recorder", "Screen",
)
SINGLETON_RE = re.compile(
    r"\bstatic\s+(?:volatile\s+|final\s+)*[\w.<>]+(?:\[\])?\s+"
    r"(?:INSTANCE|Instance|instance)\b"
    r"|\bstatic\s+[\w.<>]+\s+getInstance\s*\("
)
SINGLETON_BASES = ("BaseController", "AccountInstance")


def class_category(text, scope, cache):
    """'ui', 'singleton', 'static' (static initialiser), 'anon', or 'other'."""
    if enclosing(scope, "static-init") is not None:
        return "singleton"
    s = enclosing(scope, "class")
    seen_anon = False
    while s is not None:
        if s.name.startswith("<anon:"):
            seen_anon = True
        cached = cache.get(s.uid)
        if cached is None:
            blob = s.name + " " + s.extends
            if any(h in blob for h in UI_HINTS):
                cached = "ui"
            elif (any(b in s.extends for b in SINGLETON_BASES)
                  or SINGLETON_RE.search(text[s.start:s.end])):
                cached = "singleton"
            else:
                cached = ""
            cache[s.uid] = cached
        if cached:
            return cached
        s = enclosing(s.parent, "class")
    return "anon" if seen_anon else "other"


# ------------------------------------------------------------------------- scan file

# names that can only mean "tear down"; an addObserver here is a typo
TEARDOWN_RE = re.compile(
    r"^(onFragmentDestroy|onDestroy|onDestroyView|onViewDestroyed|onDetachedFromWindow"
    r"|onViewDetachedFromWindow|dismiss|dismissInternal|onDismiss|onDismissed"
    r"|release|destroy|finishInternal|unregister|recycle|onDetach)$"
)
# names that plausibly host a removal, including the hide-is-teardown idiom
RELEASE_RE = re.compile(
    r"^(onPause|onStop|onFinish|onFragmentPause|cleanup|clear|detach|close|onClose"
    r"|hide|onHide|onRemove|setVisibility|onDataSetDestroyed)$"
)


class Site:
    __slots__ = ("kind", "line", "observer", "observer_src", "observer_scope",
                 "id", "id_static", "recv_kind", "account", "method", "method_scope",
                 "anon_scope", "cls", "cls_label", "category")


def line_of(text, offset):
    return text.count("\n", 0, offset) + 1


SKIP_FILES = {"NotificationCenter.java"}
# vendored third-party sources; their lifecycles are not ours to audit
SKIP_DIRS = (
    os.path.join("java", "androidx"), os.path.join("java", "com", "google"),
    os.path.join("java", "com", "android"), os.path.join("java", "com", "carrotsearch"),
    os.path.join("java", "org", "webrtc"), os.path.join("java", "me", "vkryl"),
)


def scan_file(path):
    try:
        with open(path, encoding="utf-8") as f:
            raw = f.read()
    except (OSError, UnicodeDecodeError) as exc:
        print("Failed to read %s: %s" % (path, exc), file=sys.stderr)
        return None
    if not any(tok in raw for tok in TRIGGER_TOKENS):
        return None

    kotlin = path.endswith(".kt")
    text = strip_noise(raw, kotlin)
    _root, all_scopes, starts = build_scopes(text, kotlin)
    self_fields, ident_types = collect_self_fields(text)

    assignments = {(m.group(1), m.group(2))
                   for m in re.finditer(
                       r"\b([A-Za-z_]\w*)\s*=\s*([A-Za-z_][\w.]*)\s*[;,)]", text)}
    sites = []
    category_cache = {}
    paired = defaultdict(list)
    owned = defaultdict(list)
    created_groups = []
    group_adds = []
    group_released = []
    groups = defaultdict(lambda: {"adds": [], "released": [], "created": None})

    for m in CALL_RE.finditer(text):
        call = m.group(1)
        args_raw, _ = read_args(text, m.end() - 1)
        args = split_args(args_raw)
        recv = read_receiver(text, m.start())
        sc = scope_at(starts, all_scopes, m.start())
        line = line_of(text, m.start())

        if call in ("addObserver", "removeObserver") and len(args) >= 2:
            s = Site()
            s.kind = "add" if call == "addObserver" else "remove"
            s.line = line
            s.observer_src = norm(args[0])
            s.observer, s.observer_scope = classify_observer(args[0], sc, kotlin,
                                                             self_fields)
            s.id, s.id_static = classify_id(args[1])
            s.recv_kind, s.account = classify_receiver(recv)
            s.method_scope = enclosing(sc, "method")
            owner_class = enclosing(sc, "class")
            s.anon_scope = (owner_class
                            if owner_class is not None
                            and owner_class.name.startswith("<anon:") else None)
            s.cls = 0 if s.observer_scope is not None else (
                owner_class.uid if owner_class else 0)
            s.method = s.method_scope.name if s.method_scope else "<init>"
            s.cls_label = class_label(sc)
            s.category = class_category(text, sc, category_cache)
            sites.append(s)
        if call in OWNERSHIP_RELEASE:
            base = normalise_target(recv)
            if base and not base[0].isupper():
                owner = named_class(sc)
                owned[(owner.uid if owner else 0)].append(
                    (base, aliases(base, ident_types, assignments), call, line))
        if call in PAIRED_BY_NAME:
            base = normalise_target(recv)
            for api, side in PAIRED_BY_NAME[call]:
                if api.skip_receivers and base in api.skip_receivers:
                    continue
                # the type gate exists to keep ViewGroup.addView out; applying it to
                # the remove side too would silently drop the matching removeView
                if side == "add" and api.receiver_types and not any(
                        base in self_fields.get(t, ()) or t.lower() in base.lower()
                        for t in api.receiver_types):
                    continue
                arg_index = api.add_arg if side == "add" else api.remove_arg
                if arg_index is not None:
                    if len(args) <= arg_index or not base:
                        continue
                    subject = normalise_target(args[arg_index])
                elif not base or base[0].isupper():
                    # `VerticalPositionAutoAnimator.attach(view)` is a static factory,
                    # not one half of an instance pair
                    continue
                else:
                    subject = normalise_target(args[0]) if args else ""
                    if subject == "null":
                        # `params.attach(null)` is how this shape spells "release"
                        side, subject = "remove", ""
                owner = named_class(sc)
                # a class registering instances of itself is publishing an API - its
                # own detach() is the consumer's job to call, not this file's
                if (owner is not None and side == "add"
                        and ident_types.get(base) == owner.name):
                    continue
                paired[(owner.uid if owner else 0, api.family)].append(Paired(
                    side, line, "%s.%s(%s)" % (base, call, subject), base,
                    class_label(sc), enclosing(sc, "method"),
                    class_category(text, sc, category_cache), api,
                    aliases(base, ident_types, assignments),
                    aliases(subject, ident_types, assignments)))
        if call == "createObserversGroup":
            var = assigned_var(text, m.start()) or ("<inline:%d>" % line)
            obs_scope = classify_observer(args[0], sc, kotlin, self_fields)[1] \
                if args else None
            owner = named_class(sc)
            created_groups.append((owner.uid if owner else 0, var, line, norm(recv),
                                   norm(args[0]) if args else "", obs_scope))
            for gl, gid in chained_group_ids(text, m.end() - 1):
                group_adds.append((owner.uid if owner else 0, var, gl, gid))
        elif call == "removeAllObservers":
            owner = named_class(sc)
            group_released.append((owner.uid if owner else 0, norm(recv), line))

    # `groupVar.add(NotificationCenter.x)` statements outside the creation chain
    for _uid, var, _line, _recv, _obs, _scope in created_groups:
        if var.startswith("<inline:"):
            continue
        for gm in re.finditer(r"\b%s\s*\.\s*add\s*\(" % re.escape(var), text):
            args_raw, _ = read_args(text, gm.end() - 1)
            gid, _static = classify_id(args_raw)
            owner = named_class(scope_at(starts, all_scopes, gm.start()))
            uid = owner.uid if owner else 0
            group_adds.append((uid, var, line_of(text, gm.start()), gid))
            # `observersGroup.add(a).add(b)` - the links after the first are not
            # prefixed with the variable name
            for gl, chained in chained_group_ids(text, gm.end() - 1):
                group_adds.append((uid, var, gl, chained))

    # Two classes in one file can both have a field called `observersGroup`, so a group
    # is identified by its owning class as well as its name.
    by_var = defaultdict(list)
    for uid, var, line, recv, obs, scope in created_groups:
        groups[(uid, var)]["created"] = (line, recv, obs, scope)
        by_var[var].append((uid, var))

    def group_key(uid, var):
        if (uid, var) in groups:
            return (uid, var)
        candidates = by_var.get(var, ())
        return candidates[0] if len(candidates) == 1 else None

    for uid, var, line, gid in group_adds:
        key = group_key(uid, var)
        if key is not None and (line, gid) not in groups[key]["adds"]:
            groups[key]["adds"].append((line, gid))
    for uid, var, line in group_released:
        key = group_key(uid, var)
        if key is not None:
            groups[key]["released"].append(line)

    handlers = {}
    for sc in all_scopes:
        if sc.kind != "method" or sc.name != HANDLER:
            continue
        owner = enclosing(sc.parent, "class")
        if owner is None:
            continue
        body = text[sc.start:sc.end]
        # a body that hands the notification on to someone else, or that ignores `id`
        # and reacts to everything it is subscribed to, tells us nothing
        forwards = HANDLER in body or not DISPATCHES_ON_ID_RE.search(body)
        handlers[owner.uid] = {
            "ids": {g for match in HANDLED_ID_RE.findall(body) for g in match if g},
            "forwards": forwards,
            "line": line_of(text, sc.start),
            "label": class_label(sc),
            "extends": owner.extends,
            "name": owner.name,
        }
    handlers_by_name = {h["name"]: h for h in handlers.values()}

    guards = []
    for m in GUARD_RE.finditer(text):
        negated, guarded, assigned, value = m.groups()
        if guarded != assigned:
            continue
        if (negated == "" and value == "false") or (negated == "!" and value == "true"):
            sc = scope_at(starts, all_scopes, m.start())
            meth = enclosing(sc, "method")
            guards.append((line_of(text, m.start()), guarded, negated, value,
                           meth.name if meth else "<init>", class_label(sc)))

    return {"path": path, "sites": sites, "groups": groups, "guards": guards,
            "handlers": handlers, "handlers_by_name": handlers_by_name,
            "paired": paired, "owned": owned, "assignments": assignments}


ASSIGN_RE = re.compile(r"([A-Za-z_]\w*)\s*=\s*[\w.<>()\s]*$")


def assigned_var(text, call_start):
    """Find `foo = <chain>.createObserversGroup(...)` and return `foo`."""
    seg = text[max(0, call_start - 400):call_start]
    cut = max(seg.rfind(";"), seg.rfind("{"), seg.rfind("}"))
    m = ASSIGN_RE.search(seg[cut + 1:])
    return m.group(1) if m else None


def chained_group_ids(text, open_paren):
    """Collect `.add(NotificationCenter.x)` links chained onto createObserversGroup()."""
    _, i = read_args(text, open_paren)
    out = []
    n = len(text)
    add_call = re.compile(r"add\s*\(")
    while True:
        j = i
        while j < n and text[j].isspace():
            j += 1
        if j >= n or text[j] != ".":
            break
        j += 1
        while j < n and text[j].isspace():
            j += 1
        m = add_call.match(text, j)
        if not m:
            break
        args_raw, i = read_args(text, m.end() - 1)
        gid, _static = classify_id(args_raw)
        out.append((line_of(text, j), gid))
    return out


# -------------------------------------------------------------------------- analysis

LIFECYCLE_ENTRY = {
    "onFragmentCreate", "onAttachedToWindow", "onViewAttachedToWindow",
    "onResume", "onStart", "onCreate", "show",
}

SEVERITY_ORDER = {"high": 0, "medium": 1, "low": 2, "info": 3}


def self_removed(add_site, removes):
    """
    True when an anonymous delegate declared inside the same member as the add
    unregisters itself - the standard one-shot listener idiom. Removes that pass the
    incoming `id` parameter straight back through count too.
    """
    container = add_site.method_scope
    for r in removes:
        k = r.observer_scope if (r.observer_scope is not None
                                 and r.observer_scope.name.startswith("<anon:")
                                 ) else r.anon_scope
        if k is None:
            continue
        if r.id_static and r.id != add_site.id:
            continue
        if container is None or (container.start <= k.start and k.end <= container.end):
            return True
    return False


def analyse(result):
    path = result["path"]
    findings = []
    sites = result["sites"]

    by_key = defaultdict(lambda: {"add": [], "remove": []})
    for s in sites:
        by_key[(s.cls, s.observer, s.id)][s.kind].append(s)

    removes_by_id = defaultdict(list)
    dynamic_removes = []
    removes_by_observer = defaultdict(set)
    adds_by_observer = defaultdict(set)
    for s in sites:
        if s.kind == "remove":
            removes_by_id[s.id].append(s)
            if not s.id_static:
                dynamic_removes.append(s)
            removes_by_observer[(s.cls, s.observer)].add(s.id)
        else:
            adds_by_observer[(s.cls, s.observer)].add(s.id)

    for s in sites:
        if s.kind == "add" and TEARDOWN_RE.match(s.method):
            findings.append(make(
                "add-in-teardown", "high", path, s,
                "addObserver(%s) sits in %s() - a teardown method; almost certainly "
                "meant removeObserver" % (s.id, s.method),
                [s.line], []))

    for (_cls, _observer, oid), buckets in sorted(
        by_key.items(), key=lambda kv: (kv[1]["add"] or kv[1]["remove"])[0].line
    ):
        adds, removes = buckets["add"], buckets["remove"]
        if not adds:
            continue
        a0 = adds[0]
        if a0.category == "singleton":
            sev, why = "info", "singleton owner, permanent registration is expected"
        elif a0.category == "ui":
            sev, why = "high", ""
        else:
            sev, why = "medium", ""

        if not removes:
            if self_removed(a0, removes_by_id.get(oid, []) + dynamic_removes):
                continue
            others = removes_by_observer.get((a0.cls, a0.observer), set())
            detail = (" (the same observer does remove %s)" % ", ".join(sorted(others))
                      ) if others else ""
            findings.append(make(
                "never-removed", sev, path, a0,
                "addObserver(%s) in %s() is never paired with removeObserver%s%s"
                % (oid, a0.method, detail, (" - " + why) if why else ""),
                [x.line for x in adds], []))
            continue

        add_scopes = {(x.recv_kind, x.account) for x in adds}
        rem_scopes = {(x.recv_kind, x.account) for x in removes}
        if add_scopes != rem_scopes:
            kinds_a = {k for k, _ in add_scopes}
            kinds_r = {k for k, _ in rem_scopes}
            if kinds_a != kinds_r and "var" not in kinds_a | kinds_r:
                findings.append(make(
                    "instance-mismatch",
                    "high" if a0.category != "singleton" else "info", path, a0,
                    "registered on %s but unregistered on %s"
                    % (fmt_scopes(add_scopes), fmt_scopes(rem_scopes)),
                    [x.line for x in adds], [x.line for x in removes]))
            elif kinds_a == kinds_r == {"account"} and not account_tracked(
                    {a for _k, a in add_scopes}, {a for _k, a in rem_scopes},
                    result["assignments"]):
                findings.append(make(
                    "account-mismatch",
                    "medium" if a0.category != "singleton" else "info", path, a0,
                    "registered for %s but unregistered for %s"
                    % (fmt_scopes(add_scopes), fmt_scopes(rem_scopes)),
                    [x.line for x in adds], [x.line for x in removes]))

        rem_methods = {x.method for x in removes}
        entries = {x.method for x in adds} & LIFECYCLE_ENTRY
        if (entries and a0.category == "ui"
                and not any(TEARDOWN_RE.match(m) or RELEASE_RE.match(m)
                            for m in rem_methods)):
            findings.append(make(
                "lifecycle-mismatch", "medium", path, a0,
                "added in %s() but only removed in %s(), which is not a teardown callback"
                % (", ".join(sorted(entries)), ", ".join(sorted(rem_methods))),
                [x.line for x in adds], [x.line for x in removes]))

    handlers = result["handlers"]
    handlers_by_name = result["handlers_by_name"]

    def handler_for(scope):
        """The didReceivedNotification body that will see this observer's ids."""
        if scope is None:
            return None
        h = handlers.get(scope.uid)
        if h is not None:
            return h
        for base in re.findall(r"[A-Za-z_]\w*", scope.extends or ""):
            h = handlers_by_name.get(base)
            if h is not None:
                return h
        return None

    registered_by_handler = defaultdict(set)
    registrations = [(s.line, s.id, s.observer_scope, s) for s in sites
                     if s.kind == "add" and s.id_static]
    for g in result["groups"].values():
        if not g["created"]:
            continue
        _line, _recv, _obs, obs_scope = g["created"]
        registrations.extend((gl, gid, obs_scope, None) for gl, gid in g["adds"]
                             if gid in ALL_NC_IDS)

    for line, oid, obs_scope, site in registrations:
        h = handler_for(obs_scope)
        if h is None or h["forwards"]:
            continue
        registered_by_handler[id(h)].add(oid)
        if oid in h["ids"]:
            continue
        finding = {
            "type": "orphan-registration", "severity": "medium", "path": path,
            "line": line, "cls": h["label"], "observer": "this", "id": oid,
            "message": "registered for %s but %s.%s() never looks at that id"
                       % (oid, h["label"], HANDLER),
            "adds": [line], "removes": [],
        }
        if site is not None:
            finding["cls"] = site.cls_label
        findings.append(finding)

    for h in handlers.values():
        if h["forwards"]:
            continue
        registered = registered_by_handler.get(id(h), set())
        if not registered:
            continue                      # registered elsewhere, or not by this file
        handled = h["ids"] & ALL_NC_IDS
        for oid in sorted(handled - registered - group_ids_of(result)):
            findings.append({
                "type": "orphan-handler", "severity": "low", "path": path,
                "line": h["line"], "cls": h["label"], "observer": "this", "id": oid,
                "message": "%s() handles %s, which this class never registers for"
                           % (HANDLER, oid),
                "adds": [], "removes": [],
            })

    group_ids = {gid for g in result["groups"].values() for _l, gid in g["adds"]}
    for (cls, observer), removed_ids in sorted(removes_by_observer.items()):
        stray = removed_ids - adds_by_observer.get((cls, observer), set()) - group_ids
        for oid in sorted(stray):
            s = next(x for x in sites
                     if x.kind == "remove" and x.cls == cls
                     and x.observer == observer and x.id == oid)
            # only trust simple observer expressions here; casts and array holders
            # legitimately name an object that was registered under another expression
            if (not s.id_static or s.anon_scope is not None
                    or not (s.observer.startswith("this#")
                            or BARE_IDENT_RE.match(s.observer))):
                continue
            findings.append(make(
                "never-added", "low", path, s,
                "removeObserver(%s) with no matching addObserver in this class"
                % oid, [], [s.line]))

    for line, guarded, negated, value, method, cls_label in result["guards"]:
        findings.append({
            "type": "inverted-guard", "severity": "high", "path": path, "line": line,
            "cls": cls_label, "observer": guarded, "id": method,
            "message": "%s() starts with `if (%s%s) return;` and then sets %s = %s - the "
                       "guard is inverted, so everything below it is unreachable"
                       % (method, negated, guarded, guarded, value),
            "adds": [], "removes": [],
        })

    for (_owner, family), calls in sorted(result["paired"].items(),
                                          key=lambda kv: kv[1][0].line):
        removes = [c for c in calls if c.side == "remove"]
        unpaired = defaultdict(list)
        for add in (c for c in calls if c.side == "add"):
            # `x.attach(view)` pairs with `x.detach()` and `release(null, spans)`, so
            # only compare subjects when both sides actually name one
            if any(add.recv_aliases & r.recv_aliases
                   and (not add.subject_aliases - {""} or not r.subject_aliases - {""}
                        or add.subject_aliases & r.subject_aliases)
                   for r in removes):
                continue
            unpaired[add.expr].append(add)
        handoffs = result["owned"].get(_owner, [])
        for expr, adds in sorted(unpaired.items(), key=lambda kv: kv[1][0].line):
            a0 = adds[0]
            method = a0.method_scope.name if a0.method_scope else "<init>"
            handoff = next((h for h in handoffs if h[0] == a0.recv), None)
            if handoff:
                findings.append({
                    "type": "%s-indirect-release" % family, "severity": "low",
                    "path": path, "line": a0.line, "cls": a0.cls_label,
                    "observer": expr, "id": family,
                    "message": "%s is released only through %s.%s() - check that it "
                               "really calls %s()"
                               % (expr, handoff[0], handoff[2], a0.api.remove),
                    "adds": [c.line for c in adds], "removes": [handoff[3]],
                })
                continue
            findings.append({
                "type": "%s-never-released" % family,
                "severity": "high" if a0.category == "ui" else "medium",
                "path": path, "line": a0.line, "cls": a0.cls_label, "observer": expr,
                "id": family,
                "message": "%s in %s() is never paired with %s()"
                           % (expr, method, a0.api.remove),
                "adds": [c.line for c in adds], "removes": [],
            })

    for (_uid, var), g in sorted(result["groups"].items()):
        if g["created"] and not g["released"]:
            line, _recv, obs, _scope = g["created"]
            findings.append({
                "type": "group-never-released", "severity": "high", "path": path,
                "line": line, "cls": "", "observer": obs,
                "id": ", ".join(gid for _l, gid in g["adds"]) or "?",
                "message": "createObserversGroup() result '%s' never gets "
                           "removeAllObservers()" % var,
                "adds": [l for l, _ in g["adds"]], "removes": [],
            })
    return findings


def account_tracked(add_accounts, remove_accounts, assignments):
    """
    True for the account-swap idiom, where the removal reads a field that remembers
    the account the registration used - `listeningCurrentAccount = account`.
    """
    for a in add_accounts:
        for r in remove_accounts:
            if (r, a) in assignments or (a, r) in assignments:
                return True
    return False


def group_ids_of(result):
    return {gid for g in result["groups"].values() for _l, gid in g["adds"]}


def make(kind, severity, path, site, message, adds, removes):
    return {
        "type": kind, "severity": severity, "path": path, "line": site.line,
        "cls": site.cls_label, "observer": site.observer_src, "id": site.id,
        "message": message, "adds": adds, "removes": removes,
    }


def fmt_scopes(scopes):
    parts = []
    for kind, acc in sorted(scopes):
        if kind == "global":
            parts.append("global instance")
        elif kind == "account":
            parts.append("instance(%s)" % (acc or "?"))
        elif kind == "var":
            parts.append("'%s'" % acc)
        else:
            parts.append("implicit")
    return " + ".join(parts)


# ------------------------------------------------------------------------------ main

def main():
    here = os.path.dirname(os.path.abspath(__file__))
    # covers the main source set plus the full/lite flavour source sets
    default_root = os.path.join(os.path.dirname(here), "TMessagesProj", "src")
    ap = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--root", default=default_root, help="source root to scan")
    ap.add_argument("--filter", help="only scan paths containing this substring")
    ap.add_argument("--severity", default="medium",
                    choices=["high", "medium", "low", "info"],
                    help="minimum severity to report (default: medium)")
    ap.add_argument("--type", action="append", help="only report these finding types")
    ap.add_argument("--all", action="store_true", help="shorthand for --severity info")
    ap.add_argument("--json", action="store_true", help="machine readable output")
    args = ap.parse_args()

    if not os.path.isdir(args.root):
        sys.exit("source root not found: %s" % args.root)
    global ALL_NC_IDS
    ALL_NC_IDS = load_notification_ids(args.root) or load_notification_ids(
        os.path.join(os.path.dirname(here), "TMessagesProj", "src", "main", "java"))
    limit = SEVERITY_ORDER["info" if args.all else args.severity]

    findings = []
    scanned = 0
    for dirpath, _dirs, filenames in os.walk(args.root):
        for fname in sorted(filenames):
            if not fname.endswith((".java", ".kt")) or fname in SKIP_FILES:
                continue
            path = os.path.join(dirpath, fname)
            if any(skip in path for skip in SKIP_DIRS):
                continue
            if args.filter and args.filter not in path:
                continue
            result = scan_file(path)
            if result is None:
                continue
            scanned += 1
            findings.extend(analyse(result))

    findings = [f for f in findings if SEVERITY_ORDER[f["severity"]] <= limit
                and (not args.type or f["type"] in args.type)]
    findings.sort(key=lambda f: (SEVERITY_ORDER[f["severity"]], f["path"], f["line"]))

    if args.json:
        json.dump(findings, sys.stdout, indent=2)
        print()
        return

    root = os.path.abspath(args.root)
    current = None
    for f in findings:
        rel = os.path.relpath(f["path"], root)
        if rel != current:
            current = rel
            print("\n%s" % rel)
        cls = (" [%s]" % f["cls"]) if f["cls"] else ""
        print("  %-6s %-22s %s:%d%s" % (f["severity"].upper(), f["type"],
                                        os.path.basename(f["path"]), f["line"], cls))
        print("         %s" % f["message"])
        if f["adds"]:
            print("         add:    %s" % ", ".join(str(l) for l in f["adds"]))
        if f["removes"]:
            print("         remove: %s" % ", ".join(str(l) for l in f["removes"]))

    counts = defaultdict(int)
    for f in findings:
        counts[f["severity"]] += 1
    print("\n%d files scanned, %d findings (%s)" % (
        scanned, len(findings),
        ", ".join("%s: %d" % (s, counts[s])
                  for s in ["high", "medium", "low", "info"] if counts[s]) or "none"))


if __name__ == "__main__":
    main()
