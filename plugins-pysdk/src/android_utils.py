# This is the source code of the exteraGram plugins SDK.
#
# We do not and cannot prevent the use of our code,
# but be respectful and credit the original author.
#
# Copyright @exteraGram, 2022-2026
#
# Licensed under the GNU GPL v. 3 or later, with the additional terms
# in ADDITIONAL_TERMS.md at the root of the repository.

import _sdk_version
_sdk_version.Z.c()

import traceback
from typing import Any

from android.view import View
from com.android.dx import TypeId
from com.chaquo.python import PyObject
from com.exteragram.messenger.plugins.utils import ClassProxy as CP
from com.exteragram.messenger.utils import AppUtils
from java import dynamic_proxy, jarray
from extera_utils.classes import Base, java_subclass, jclassbuilder, jfield, jsetmethod
from java.lang import Runnable, Object
from java.lang.reflect import Modifier
from log_utils import dump, _split_for_logcat
from extera_utils.lifecycle import ManagedCallback, bind, bind_task
from org.telegram.messenger import AndroidUtilities


_PYOBJECT_TYPE = TypeId.get(PyObject.getClass())
_NO_TYPES = jarray(TypeId)([])
_RUN_PYTHON_RUNNABLE = TypeId.get(CP.getClass()).getMethod(
    TypeId.VOID,
    "runPythonRunnable",
    _PYOBJECT_TYPE,
)


class OnClickListener(dynamic_proxy(View.OnClickListener)):
    def __init__(self, fn: callable):
        super().__init__()
        self._fn = bind(fn)

    def onClick(self, _view):
        try:
            self._fn(_view)
        except Exception as e:
            log(f"Error in onClick listener: {e}\n{traceback.format_exc()}")


class OnLongClickListener(dynamic_proxy(View.OnLongClickListener)):
    def __init__(self, fn: callable):
        super().__init__()
        self._fn = bind(fn)

    def onLongClick(self, _view):
        try:
            result = self._fn(_view)
            return True if result is None else bool(result)
        except Exception as e:
            log(f"Error in onLongClick listener: {e}\n{traceback.format_exc()}")
            return False


def run_on_ui_thread(func: callable, delay=0):
    callback = bind_task(func, once=True)
    runnable = R(callback)
    try:
        AndroidUtilities.runOnUIThread(runnable, delay)
        callback.set_cancel(lambda: AndroidUtilities.cancelRunOnUIThread(runnable))
    except BaseException:
        callback.cancel()
        raise
    return callback


_LEGACY_LOG_CHUNK_BYTES = 3800


def log(data: Any):
    if isinstance(data, (str, int, float, bool)) or data is None:
        text = str(data)
    else:
        text = dump(data)
    for chunk in _split_for_logcat(text, _LEGACY_LOG_CHUNK_BYTES):
        AppUtils.log(chunk)


def copy_to_clipboard(text: str):
    if AndroidUtilities.addToClipboard(text):
        from ui.bulletin import BulletinHelper
        BulletinHelper.show_copied_to_clipboard()


@java_subclass(Object, Runnable)
class _R(Base):
    _fn = jfield(
        PyObject.getClass(),
        modifiers=Modifier.PUBLIC | Modifier.VOLATILE,
        methods=[jsetmethod("setRunnableCallback")],
    )

    @jclassbuilder()
    @staticmethod
    def _build_run(dex, proxy_type, _super_type, _interfaces):
        method_id = proxy_type.getMethod(TypeId.VOID, "run", _NO_TYPES)
        code = dex.declare(method_id, Modifier.PUBLIC | Modifier.FINAL)
        this_ref = code.getThis(proxy_type)
        callback = code.newLocal(_PYOBJECT_TYPE)

        code.iget(
            proxy_type.getField(_PYOBJECT_TYPE, "_fn"),
            callback,
            this_ref,
        )
        code.invokeStatic(_RUN_PYTHON_RUNNABLE, None, callback)
        code.returnVoid()


def _void_callback(fn):
    def callback():
        fn()

    return callback


def R(fn):
    if not callable(fn):
        raise TypeError("fn must be callable")

    peer = _R.new_instance()
    java_instance = peer.java
    interceptor = type(peer)._managed_interceptor()

    try:
        java_instance.setRunnableCallback(_void_callback(fn if isinstance(fn, ManagedCallback) else bind_task(fn)))
    finally:
        if not interceptor.detach_peer(java_instance, peer):
            raise RuntimeError("Failed to detach Runnable peer")

    return java_instance
