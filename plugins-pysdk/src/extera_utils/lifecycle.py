# This is the source code of the exteraGram plugins SDK.
#
# We do not and cannot prevent the use of our code,
# but be respectful and credit the original author.
#
# Copyright @exteraGram, 2022-2026
#
# Licensed under the GNU GPL v. 3 or later, with the additional terms
# in ADDITIONAL_TERMS.md at the root of the repository.

import contextlib
import contextvars
import inspect
import sys
import threading
import traceback
import weakref

from extera_utils.get_caller import ELYX_PLUGIN_NAMESPACE, _get_callable_location, _plugin_id_from_location


_scopes = {}
_lock = threading.RLock()
_current = contextvars.ContextVar("plugin_lifecycle", default=None)
_cleanup_scope = contextvars.ContextVar("plugin_cleanup", default=None)
_cleanup_requests = contextvars.ContextVar("plugin_cleanup_requests", default=None)


def _report_error():
    sys.stderr.write(traceback.format_exc())


class PluginScope:
    def __init__(self, plugin_id):
        self.plugin_id = plugin_id
        self.stop_event = threading.Event()
        self._callbacks = weakref.WeakSet()
        self._cleanups = set()
        self._lock = threading.RLock()

    def close(self):
        with self._lock:
            self.stop_event.set()
            callbacks = tuple(self._callbacks)
            self._callbacks.clear()
            self._cleanups.clear()
        for callback in callbacks:
            callback.cancel()

    def register_cleanup(self, cleanup):
        callback = ManagedCallback(None, self)
        with self._lock:
            if not self.stop_event.is_set():
                self._cleanups.add(callback)
        def run_cleanup():
            with cleanup_context(self):
                cleanup()
        callback.set_cancel(run_cleanup)
        return callback


class ManagedCallback:
    def __init__(self, fn, scope=None, once=False, allow_stopped=False, cleanup_task=False):
        self.scope = scope
        self._fn = fn
        self._cleanup_task = cleanup_task
        self._allow_stopped = allow_stopped
        self._once = once or allow_stopped
        self._cancel = None
        self._finished = False
        self._cancelled = False
        self._lock = threading.RLock()
        if scope is not None and not allow_stopped:
            with scope._lock:
                if scope.stop_event.is_set():
                    self._fn = None
                    self._cancelled = True
                else:
                    scope._callbacks.add(self)

    @property
    def cancelled(self):
        with self._lock:
            return self._cancelled or (not self._allow_stopped and self.scope is not None and self.scope.stop_event.is_set())

    def set_cancel(self, cancel):
        with self._lock:
            cancelled = self._cancelled
            if not cancelled and not self._finished:
                self._cancel = cancel
        if cancelled:
            token = _current.set(self.scope)
            try:
                cancel()
            except BaseException:
                _report_error()
            finally:
                _current.reset(token)

    def _detach(self):
        if self.scope is not None:
            with self.scope._lock:
                self.scope._callbacks.discard(self)
                self.scope._cleanups.discard(self)

    def cancel(self):
        with self._lock:
            self._cancelled = True
            self._fn = None
            cancel, self._cancel = self._cancel, None
        self._detach()
        if cancel is not None:
            token = _current.set(self.scope)
            try:
                cancel()
            except BaseException:
                _report_error()
            finally:
                _current.reset(token)

    def __call__(self, *args, **kwargs):
        with self._lock:
            if self._cancelled or self._finished or (not self._allow_stopped and self.scope is not None and self.scope.stop_event.is_set()):
                return None
            fn = self._fn
            if self._once:
                self._finished = True
                self._fn = None
                self._cancel = None
        if self._once:
            self._detach()
        if fn is None:
            return None
        token = _current.set(self.scope)
        cleanup_token = _cleanup_scope.set(None)
        request_token = _cleanup_requests.set(self.scope if self._cleanup_task else None)
        try:
            return fn(*args, **kwargs)
        finally:
            _cleanup_requests.reset(request_token)
            _cleanup_scope.reset(cleanup_token)
            _current.reset(token)


def begin(plugin_id):
    stop(plugin_id)
    with _lock:
        _scopes[plugin_id] = PluginScope(plugin_id)


def stop(plugin_id):
    with _lock:
        scope = _scopes.get(plugin_id)
    if scope is not None:
        scope.close()


def stop_all():
    with _lock:
        scopes = tuple(_scopes.values())
    for scope in scopes:
        scope.stop_event.set()
    for scope in scopes:
        scope.close()


def _scope_from_globals(namespace):
    scope = namespace.get("__extera_plugin_scope__")
    if isinstance(scope, PluginScope):
        return scope
    module_name = getattr(namespace.get("__spec__"), "name", None) or namespace.get("__name__", "")
    plugin_id = _plugin_id_from_location(namespace.get("__file__", ""), module_name)
    with _lock:
        scope = _scopes.get(plugin_id)
    if scope is not None:
        module = sys.modules.get(module_name)
        if module is None or vars(module) is not namespace:
            scope = PluginScope(plugin_id)
            scope.close()
        namespace["__extera_plugin_scope__"] = scope
    return scope


def _is_plugin_module(module_name):
    parts = module_name.split(".")
    if len(parts) > 1:
        return parts[0] == ELYX_PLUGIN_NAMESPACE
    with _lock:
        return module_name in _scopes


def _owned_scope(fn):
    if fn is None:
        return None
    if inspect.isclass(fn):
        module_name = getattr(fn, "__module__", None) or ""
    else:
        location = _get_callable_location(fn)
        module_name = (location[3] if location is not None else None) or getattr(fn, "__module__", None) or ""
    if not module_name or not _is_plugin_module(module_name):
        return None
    module = sys.modules.get(module_name)
    if module is None:
        return None
    return _scope_from_globals(vars(module))


def capture_scope(fn=None):
    scope = _current.get()
    if scope is not None:
        owned = _owned_scope(fn)
        if owned is not None and owned.plugin_id != scope.plugin_id:
            return owned
        return scope
    if inspect.isclass(fn):
        for member in vars(fn).values():
            member = getattr(member, "__func__", member)
            namespace = getattr(member, "__globals__", None)
            if namespace is not None:
                scope = _scope_from_globals(namespace)
                if scope is not None:
                    return scope
    target = getattr(fn, "__func__", fn)
    namespace = getattr(target, "__globals__", None)
    if namespace is not None:
        scope = _scope_from_globals(namespace)
        if scope is not None:
            return scope
    module = sys.modules.get(getattr(fn, "__module__", ""))
    if module is not None:
        scope = _scope_from_globals(vars(module))
        if scope is not None:
            return scope
    location = _get_callable_location(fn)
    if location is not None:
        module = sys.modules.get(location[3])
        if module is not None:
            scope = _scope_from_globals(vars(module))
            if scope is not None:
                return scope
    try:
        frame = sys._getframe(0)
    except ValueError:
        return None
    try:
        while frame is not None:
            scope = _scope_from_globals(frame.f_globals)
            if scope is not None:
                return scope
            frame = frame.f_back
    finally:
        del frame
    return None


def bind(fn, *, once=False):
    return ManagedCallback(fn, capture_scope(fn), once)


@contextlib.contextmanager
def plugin_context(plugin_id):
    with _lock:
        scope = _scopes.get(plugin_id)
    if scope is None:
        yield
        return
    token = _current.set(scope)
    try:
        yield
    finally:
        _current.reset(token)


@contextlib.contextmanager
def cleanup_context(scope):
    token = _current.set(scope)
    cleanup_token = _cleanup_scope.set(scope)
    request_token = _cleanup_requests.set(scope)
    try:
        yield
    finally:
        _cleanup_requests.reset(request_token)
        _cleanup_scope.reset(cleanup_token)
        _current.reset(token)


def bind_task(fn, *, once=False, scope=None):
    if scope is None:
        scope = capture_scope(fn)
    allow_stopped = scope is not None and _cleanup_scope.get() is scope
    return ManagedCallback(fn, scope, once, allow_stopped=allow_stopped, cleanup_task=allow_stopped)


def bind_request(fn):
    scope = capture_scope(fn)
    allow_stopped = scope is not None and _cleanup_requests.get() is scope
    return ManagedCallback(fn, scope, once=True, allow_stopped=allow_stopped)
