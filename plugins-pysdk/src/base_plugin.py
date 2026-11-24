# This is the source code of the exteraGram plugins SDK.
#
# We do not and cannot prevent the use of our code,
# but be respectful and credit the original author.
#
# Copyright @exteraGram, 2022-2026
#
# Licensed under the GNU GPL v. 3 or later, with the additional terms
# in ADDITIONAL_TERMS.md at the root of the repository.

import functools
import inspect
import _sdk_version

import traceback
from abc import ABC, abstractmethod
from dataclasses import dataclass
from de.robv.android.xposed import XposedBridge, XC_MethodHook
from enum import Enum

from extera_utils import lifecycle
from file_utils import FilesController
from intents import IntentsManager
from java.lang import Class
from java.lang.reflect import Member
from java.util import ArrayList
from typing import Any, List, final, Optional, Callable, Dict, Union, TYPE_CHECKING

if TYPE_CHECKING:
    from client_utils import AccountClient

import plugin_settings
from android_utils import log
from log_utils import Logger, _plugin_logger, _reset_plugin_logger
from com.exteragram.messenger.plugins import PluginsController, PluginsConstants
from com.exteragram.messenger.plugins.hooks import HookFilter as JavaHookFilter
from com.exteragram.messenger.plugins.xposed import PyMethodHook, PyMethodReplacement


@dataclass
class PluginMetadata:
    id: str
    name: str
    description: str
    author: str
    version: str
    icon: str
    min_version: str
    requirements: List[str]


class HookStrategy(Enum):
    CANCEL = PluginsConstants.Strategy.CANCEL
    MODIFY = PluginsConstants.Strategy.MODIFY
    DEFAULT = PluginsConstants.Strategy.DEFAULT
    MODIFY_FINAL = PluginsConstants.Strategy.MODIFY_FINAL


class IntentHookType(Enum):
    BEFORE = 0
    AFTER = 1


@dataclass
class HookResult:
    strategy: HookStrategy = HookStrategy.DEFAULT
    request: Any = None
    response: Any = None
    update: Any = None
    updates: Any = None
    error: Any = None
    params: Any = None


class PluginError(Exception):
    plugin_id: Optional[str]

    def __init__(self, message: str, plugin_id: Optional[str] = None):
        super().__init__(message)
        self.plugin_id = plugin_id


class XposedHook(ABC):
    pass


class MethodReplacement(XposedHook):
    @abstractmethod
    def replace_hooked_method(self, param: XC_MethodHook.MethodHookParam) -> Any:
        pass


class MethodHook(XposedHook):
    def before_hooked_method(self, param: XC_MethodHook.MethodHookParam):
        pass

    def after_hooked_method(self, param: XC_MethodHook.MethodHookParam):
        pass


_PARTIAL_PRIMITIVES = None
_PARTIAL_API = None


def _partial_primitives():
    global _PARTIAL_PRIMITIVES
    if _PARTIAL_PRIMITIVES is None:
        from java import jboolean, jbyte, jchar, jdouble, jfloat, jint, jlong, jshort
        _PARTIAL_PRIMITIVES = {
            "boolean": jboolean, "byte": jbyte, "char": jchar, "short": jshort,
            "int": jint, "long": jlong, "float": jfloat, "double": jdouble,
        }
    return _PARTIAL_PRIMITIVES


def _to_java_value(value: Any, java_type: Any) -> Any:
    if value is None or java_type is None:
        return value
    primitive = java_type.isPrimitive()
    name = java_type.getName()
    boxed = {
        "java.lang.Boolean": "boolean", "java.lang.Byte": "byte", "java.lang.Character": "char",
        "java.lang.Short": "short", "java.lang.Integer": "int", "java.lang.Long": "long",
        "java.lang.Float": "float", "java.lang.Double": "double",
    }.get(name)
    if not primitive and boxed is None:
        return value
    wrapper = _partial_primitives().get(name if primitive else boxed)
    if wrapper is None or not isinstance(value, (bool, int, float, str)):
        return value
    return wrapper(value)


def _partial_api():
    global _PARTIAL_API
    if _PARTIAL_API is not None:
        return _PARTIAL_API
    try:
        from com.exteragram.hook import ExteraHook, HookTarget, InstructionMatcher, Interceptor
        from java import dynamic_proxy, jarray
        from java.lang import Object as JavaObject
    except ImportError as e:
        raise PluginError(f"Partial hooks need a newer exteraGram: {e}")
    if not hasattr(PluginsController.getInstance(), "addPartialHook"):
        raise PluginError("Partial hooks need a newer exteraGram: PluginsController.addPartialHook is missing")

    class _Interceptor(dynamic_proxy(Interceptor)):
        def __init__(self, plugin, callback, param_types, result_type):
            super().__init__()
            self._plugin = plugin
            self._callback = callback
            self._param_types = param_types
            self._result_type = result_type

        def intercept(self, chain):
            scope = self._plugin._lifecycle
            if scope is not None and scope.stop_event.is_set():
                return chain.proceed()
            wrapped = PartialChain(chain, self._param_types)
            try:
                result = self._callback(wrapped)
            except Exception:
                if not wrapped.threw:
                    log(f"[{self._plugin.id}: Partial hook error] {traceback.format_exc()}")
                raise
            return _to_java_value(result, self._result_type)

    _PARTIAL_API = {
        "ExteraHook": ExteraHook,
        "HookTarget": HookTarget,
        "InstructionMatcher": InstructionMatcher,
        "Interceptor": _Interceptor,
        "jarray": jarray,
        "Object": JavaObject,
    }
    return _PARTIAL_API


class PartialChain:
    """One interception of a partial hook.

    `this_object` is the receiver of the hooked call or the object that owns the field,
    `caller` is the `this` of the method that contains the hooked place (None in static methods),
    `args` are the call arguments, `[value]` for field writes and instruction hooks.
    `proceed()` runs the original instruction; `proceed(*args)` runs it with new arguments.
    """

    __slots__ = ("java", "threw", "_param_types")

    def __init__(self, chain, param_types):
        self.java = chain
        self.threw = False
        self._param_types = param_types

    @property
    def this_object(self) -> Any:
        return self.java.getThisObject()

    @property
    def caller(self) -> Any:
        return self.java.getCaller()

    @property
    def args(self) -> List[Any]:
        return list(self.java.getArgs())

    def arg(self, index: int) -> Any:
        return self.java.arg(index)

    def proceed(self, *args) -> Any:
        if not args:
            return self.java.proceed()
        if len(args) != len(self._param_types):
            raise TypeError(f"proceed() takes {len(self._param_types)} arguments, got {len(args)}")
        api = _partial_api()
        values = [_to_java_value(value, java_type) for value, java_type in zip(args, self._param_types)]
        return self.java.proceed(api["jarray"](api["Object"])(values))

    def throw_to_caller(self, throwable):
        self.threw = True
        self.java.throwToCaller(throwable)


def fn_hook_filters(field_name: str):
    def decorator(func):
        func.__hook_filters__ = lambda self: [
            (filt if isinstance(filt, HookFilterData) else filt.filter_data).to_java_filter()
            for filt in (getattr(self, field_name, []) or [])
        ]
        return func

    return decorator


def _while_plugin_active(method):
    @functools.wraps(method)
    def wrapped(self, *args, **kwargs):
        scope = self._lifecycle
        if scope is None:
            return method(self, *args, **kwargs)
        with scope._lock:
            if scope.stop_event.is_set():
                return None
            return method(self, *args, **kwargs)
    return wrapped


class BaseHook(MethodHook):
    def __init__(
            self,
            plugin: Optional["BasePlugin"] = None, *,
            before: Optional[Callable] = None,
            after: Optional[Callable] = None,
            before_filters: Optional[List[Any]] = None,
            after_filters: Optional[List[Any]] = None,
    ):
        self.plugin = plugin
        self.before = before
        self.after = after
        self.before_filters = before_filters
        self.after_filters = after_filters

    @fn_hook_filters("before_filters")
    def before_hooked_method(self, param):
        if self.before:
            try:
                self.before(param)
            except Exception:
                plugin_id = f"{self.plugin.id}: " if self.plugin else ""
                log(f"[{plugin_id}Hook error] {traceback.format_exc()}")

    @fn_hook_filters("after_filters")
    def after_hooked_method(self, param):
        if self.after:
            try:
                self.after(param)
            except Exception:
                plugin_id = f"{self.plugin.id}: " if self.plugin else ""
                log(f"[{plugin_id}Hook error] {traceback.format_exc()}")


def _is_method_hook_phase_enabled(hook, method_name: str) -> bool:
    callback = getattr(hook, method_name, None)
    if not callable(callback):
        return False

    implementation = getattr(callback, "__func__", callback)
    return implementation is not getattr(MethodHook, method_name)


try:
    _py_method_hook_constructor_signatures = {
        tuple(parameter_type.getName() for parameter_type in constructor.getParameterTypes())
        for constructor in PyMethodHook.getClass().getConstructors()
    }
    _PY_METHOD_HOOK_SUPPORTS_PHASE_HINTS = {
        (
            "java.lang.String",
            "com.chaquo.python.PyObject",
            "boolean",
            "boolean"
        ),
        (
            "java.lang.String",
            "com.chaquo.python.PyObject",
            "int",
            "boolean",
            "boolean"
        )
    }.issubset(_py_method_hook_constructor_signatures)
except Exception:
    _PY_METHOD_HOOK_SUPPORTS_PHASE_HINTS = False


class AppEvent(Enum):
    START = PluginsConstants.APP_START
    STOP = PluginsConstants.APP_STOP
    PAUSE = PluginsConstants.APP_PAUSE
    RESUME = PluginsConstants.APP_RESUME


class MenuItemType(Enum):
    MESSAGE_CONTEXT_MENU = PluginsConstants.MenuItemTypes.MESSAGE_CONTEXT_MENU
    DRAWER_MENU = PluginsConstants.MenuItemTypes.DRAWER_MENU
    MAIN_MENU = PluginsConstants.MenuItemTypes.MAIN_MENU
    CHAT_ACTION_MENU = PluginsConstants.MenuItemTypes.CHAT_ACTION_MENU
    PROFILE_ACTION_MENU = PluginsConstants.MenuItemTypes.PROFILE_ACTION_MENU


@dataclass
class MenuItemData:
    menu_type: MenuItemType
    text: str
    on_click: Callable[[Dict[str, Any]], None]
    item_id: Optional[str] = None
    icon: Optional[str] = None
    subtext: Optional[str] = None
    condition: Optional[str] = None
    priority: int = 0
    on_long_click: Optional[Callable[[Dict[str, Any]], None]] = None


@dataclass
class HookFilterData:
    filter_type: str
    arg_index: Optional[int] = None
    or_filters: Optional[Any] = None
    mvel_expression: Optional[str] = None
    instance_of: Optional[Any] = None
    object: Any = None

    def to_java_filter(self) -> JavaHookFilter:
        if not self.filter_type:
            return None

        filter = JavaHookFilter(self.filter_type)
        if self.arg_index is not None and isinstance(self.arg_index, int):
            filter.argIndex = self.arg_index
        if self.or_filters is not None and isinstance(self.or_filters, ArrayList):
            filter.orFilters = self.or_filters
        if self.mvel_expression is not None and isinstance(self.mvel_expression, str):
            filter.mvelExpression = self.mvel_expression
        if self.instance_of is not None:
            filter.instanceOf = self.instance_of
        if self.object is not None:
            filter.object = self.object
        return filter


class HookFilter(Enum):
    RESULT_IS_NULL = PluginsConstants.HookFilterTypes.RESULT_IS_NULL
    RESULT_IS_TRUE = PluginsConstants.HookFilterTypes.RESULT_IS_TRUE
    RESULT_IS_FALSE = PluginsConstants.HookFilterTypes.RESULT_IS_FALSE
    RESULT_NOT_NULL = PluginsConstants.HookFilterTypes.RESULT_NOT_NULL

    @property
    def filter_data(self):
        return HookFilterData(filter_type=self.value)

    @staticmethod
    def ResultIsInstanceOf(clazz):
        return HookFilterData(
            filter_type=PluginsConstants.HookFilterTypes.RESULT_IS_INSTANCE_OF,
            instance_of=clazz
        )

    @staticmethod
    def ResultEqual(value):
        return HookFilterData(
            filter_type=PluginsConstants.HookFilterTypes.RESULT_EQUAL,
            object=value
        )

    @staticmethod
    def ResultNotEqual(value):
        return HookFilterData(
            filter_type=PluginsConstants.HookFilterTypes.RESULT_NOT_EQUAL,
            object=value
        )

    @staticmethod
    def ArgumentIsNull(index: int):
        return HookFilterData(
            filter_type=PluginsConstants.HookFilterTypes.ARGUMENT_IS_NULL,
            arg_index=index
        )

    @staticmethod
    def ArgumentIsTrue(index: int):
        return HookFilterData(
            filter_type=PluginsConstants.HookFilterTypes.ARGUMENT_IS_TRUE,
            arg_index=index
        )

    @staticmethod
    def ArgumentIsFalse(index: int):
        return HookFilterData(
            filter_type=PluginsConstants.HookFilterTypes.ARGUMENT_IS_FALSE,
            arg_index=index
        )

    @staticmethod
    def ArgumentNotNull(index: int):
        return HookFilterData(
            filter_type=PluginsConstants.HookFilterTypes.ARGUMENT_NOT_NULL,
            arg_index=index
        )

    @staticmethod
    def ArgumentIsInstanceOf(index: int, clazz):
        return HookFilterData(
            filter_type=PluginsConstants.HookFilterTypes.ARGUMENT_IS_INSTANCE_OF,
            arg_index=index,
            instance_of=clazz
        )

    @staticmethod
    def ArgumentEqual(index: int, value):
        return HookFilterData(
            filter_type=PluginsConstants.HookFilterTypes.ARGUMENT_EQUAL,
            arg_index=index,
            object=value
        )

    @staticmethod
    def ArgumentNotEqual(index: int, value):
        return HookFilterData(
            filter_type=PluginsConstants.HookFilterTypes.ARGUMENT_NOT_EQUAL,
            arg_index=index,
            object=value
        )

    @staticmethod
    def Condition(condition: str, object: Any = None):
        return HookFilterData(
            filter_type=PluginsConstants.HookFilterTypes.CONDITION,
            mvel_expression=condition,
            object=object
        )

    @staticmethod
    def Or(*filters):
        if not filters:
            return None

        arr = ArrayList(len(filters))
        for item in filters:
            if not isinstance(item, (HookFilter, HookFilterData)):
                continue
            arr.add((item if isinstance(item, HookFilterData) else item.filter_data).to_java_filter())
        
        return HookFilterData(
            filter_type=PluginsConstants.HookFilterTypes.OR,
            or_filters=arr
        )


def hook_filters(*filters):
    def decorator(func):
        func.__hook_filters__ = [
            (filter if isinstance(filter, HookFilterData) else filter.filter_data).to_java_filter()
            for filter in (filters[0] if len(filters) == 1 and isinstance(filters[0], list) else filters)
        ]
        return func
    return decorator


_HOOK_ACCOUNT_ARG = {
    "pre_request_hook": 1,
    "post_request_hook": 1,
    "on_update_hook": 1,
    "on_updates_hook": 1,
    "on_send_message_hook": 0,
}


def _account_scoped_hook(fn: Callable, account_index: int) -> Callable:
    @functools.wraps(fn)
    def wrapper(self, *args, **kwargs):
        from client_utils import account_scope

        if len(args) > account_index:
            account = args[account_index]
        else:
            account = kwargs.get("account")

        with account_scope(account):
            return fn(self, *args, **kwargs)

    wrapper.__extera_account_scoped__ = True
    return wrapper


class BasePlugin:
    def __new__(cls):
        _sdk_version.Z.c()
        return super().__new__(cls)

    def __init_subclass__(cls, **kwargs):
        super().__init_subclass__(**kwargs)

        for name, account_index in _HOOK_ACCOUNT_ARG.items():
            fn = cls.__dict__.get(name)
            if fn is None or getattr(fn, "__extera_account_scoped__", False):
                continue
            try:
                setattr(cls, name, _account_scoped_hook(fn, account_index))
            except Exception as e:
                log(f"Could not bind {cls.__name__}.{name} to its account: {e}")

    @final
    @classmethod
    def _findPluginClass(cls, module):
        for _, obj in inspect.getmembers(module):
            if inspect.isclass(obj):
                if issubclass(obj, cls) and obj is not cls:
                    return obj
                
                ancestors = [_cls.__name__ for _cls in inspect.getmro(obj)]
                if cls.__name__ in ancestors and obj.__name__ != cls.__name__:
                    return obj
        return None

    __fh = {}  # file hooks
    __ih = {}  # intent handlers
    @final
    @classmethod
    def _cleanup(cls, plugin_id):
        for (ext, secret) in cls.__fh.pop(plugin_id, []):
            try:
                FilesController.unregister(ext, secret)
            except BaseException:
                log(traceback.format_exc())

        for h in cls.__ih.pop(plugin_id, []):
            try:
                h.unhandle()
            except BaseException:
                log(traceback.format_exc())

        _reset_plugin_logger(plugin_id)

    _begin_lifecycle = staticmethod(lifecycle.begin)
    _stop_lifecycle = staticmethod(lifecycle.stop)
    _stop_all_lifecycles = staticmethod(lifecycle.stop_all)

    def __init__(self):
        _sdk_version.Z.c()
        
        self._lifecycle = lifecycle.capture_scope(type(self))
        self.id: str = ""
        self.name: str = ""
        self.description: str = ""
        self.author: str = ""
        self.min_version: str = ""
        self.version: str = ""
        self.requirements: List[str] = []
        self.icon: Optional[str] = None
        self.error_message: Optional[str] = None
        self.enabled: bool = False
        self.initialized: bool = False

    @property
    def stop_event(self):
        return self._lifecycle.stop_event if self._lifecycle is not None else None

    def register_cleanup(self, cleanup):
        if self._lifecycle is None:
            raise RuntimeError("Plugin lifecycle is not initialized")
        return self._lifecycle.register_cleanup(cleanup)

    def on_plugin_load(self):
        _sdk_version.Z.c()

    def _invoke_plugin_unload(self):
        with lifecycle.cleanup_context(self._lifecycle):
            self.on_plugin_unload()

    def on_plugin_unload(self):
        pass

    def create_settings(self) -> List[Any]:
        _sdk_version.Z.c()
        return []

    def on_app_event(self, event_type: AppEvent):
        _sdk_version.Z.c()

    def pre_request_hook(self, request_name: str, account: int, request: Any) -> HookResult:
        return HookResult()

    def post_request_hook(self, request_name: str, account: int, response: Any, error: Any) -> HookResult:
        return HookResult()

    def on_update_hook(self, update_name: str, account: int, update: Any) -> HookResult:
        return HookResult()

    def on_updates_hook(self, container_name: str, account: int, updates: Any) -> HookResult:
        return HookResult()

    def on_send_message_hook(self, account: int, params: Any) -> HookResult:
        return HookResult()

    @final
    @_while_plugin_active
    def add_hook(self, name: str, match_substring: bool = False, priority: int = 0):
        if not self.id or not name or self.error_message:
            return
        
        _sdk_version.Z.c()
        
        PluginsController.getInstance().addEventHook(self.id, name, match_substring, priority)

    @final
    @_while_plugin_active
    def add_on_send_message_hook(self, priority: int = 0):
        self.add_hook(PluginsConstants.SEND_MESSAGE_HOOK, False, priority)

    @final
    def remove_hook(self, name: str):
        _sdk_version.Z.c()
        
        if not self.id or not name:
            return
        PluginsController.getInstance().removeEventHook(self.id, name)

    @final
    @_while_plugin_active
    def add_file_hook(self, file_info: FilesController.FileInfo):
        if not file_info or not isinstance(file_info, FilesController.FileInfo):
            raise ValueError("You must provide a file info (instance of FilesController.FileInfo)")

        _sdk_version.Z.c()

        secret = FilesController.register(file_info)
        BasePlugin.__fh[self.id] = BasePlugin.__fh.get(self.id, []) + [(file_info.ext, secret)]
        return secret

    @final
    def remove_file_hook(self, ext, secret):
        if not ext or not secret:
            raise ValueError("You must provide a file extension and secret for unregister")

        FilesController.unregister(ext, secret)
        self.__fh[self.id] = [
            i
            for i in BasePlugin.__fh.get(self.id, [])
            if i[0] != ext
        ]

    @final
    @_while_plugin_active
    def add_intent_hook(self, info: IntentsManager.HandlerInfo, type: IntentHookType):
        if not info or not isinstance(info, IntentsManager.HandlerInfo):
            raise ValueError("You must provide a handler info (instance of IntentsManager.HandlerInfo)")
        if not type or not isinstance(type, IntentHookType):
            raise ValueError("You must provide a handler type (instance of IntentsManager.HandlerType)")

        if type == IntentHookType.BEFORE:
            h = IntentsManager.new_global_before_handler(info)
        else:
            h = IntentsManager.new_global_after_handler(info)

        self.__ih[self.id] = self.__ih.get(self.id, []) + [h]
        return h

    @final
    def remove_intent_hook(self, handler_id: Union[IntentsManager.HandlerHandle, str]):
        if not handler_id or not isinstance(handler_id, (IntentsManager.HandlerHandle, str)):
            raise ValueError("You must provide a handler info (instance of IntentsManager.HandlerHandle)")

        if isinstance(handler_id, str):
            IntentsManager.unhandle(handler_id)
            self.__ih[self.id] = [h for h in self.__ih.get(self.id, []) if h.handler_id == handler_id]
        else:
            handler_id.unhandle()
            self.__ih[self.id] = [h for h in self.__ih.get(self.id, []) if h.handler_id == handler_id.handler_id]

    @final
    def get_setting(self, key: str, default: Any = None) -> Any:
        if not self.id or not key or self.error_message:
            return default
        _sdk_version.Z.c()
        return plugin_settings.get_setting(self.id, key, default)

    @final
    def set_setting(self, key: str, value: Any, reload_settings: bool = False):
        _sdk_version.Z.c()
        if not self.id or not key or self.error_message:
            return
        plugin_settings.set_setting(self.id, key, value)
        if reload_settings:
            PluginsController.getInstance().loadPluginSettings(self.id)

    @final
    def export_settings(self) -> dict:
        _sdk_version.Z.c()
        if not self.id or self.error_message:
            return {}
        return plugin_settings.get_all_settings(self.id)

    @final
    def import_settings(self, settings: dict, reload_settings: bool = True):
        _sdk_version.Z.c()
        if not self.id or self.error_message:
            return
        plugin_settings.set_all_settings(self.id, settings)
        if reload_settings:
            PluginsController.getInstance().loadPluginSettings(self.id)

    @final
    def __get_java_callback_instance(
            self,
            xposed_hook: Optional[Any] = None,
            priority: Optional[int] = None,
            *,
            before: Optional[Callable] = None,
            after: Optional[Callable] = None,
            before_filters: Optional[List[Any]] = None,
            after_filters: Optional[List[Any]] = None
    ):
        _sdk_version.Z.c()
        
        if xposed_hook is None and before is None and after is None:
            raise TypeError("xposed_hook or before or after cannot be None")

        def cast_to_array_list(value, hook_instance):
            if isinstance(value, Callable):
                value = value(hook_instance)

            if not value:
                return ArrayList()

            arr = ArrayList(len(value))
            for item in value:
                arr.add(item)

            return arr

        def create_method_hook(hook_instance, has_before_hook, has_after_hook):
            legacy_args = [self.id, hook_instance]
            if priority is not None:
                legacy_args.append(priority)

            if _PY_METHOD_HOOK_SUPPORTS_PHASE_HINTS:
                return PyMethodHook(*legacy_args, has_before_hook, has_after_hook)
            return PyMethodHook(*legacy_args)

        java_callback_instance = None

        if xposed_hook:
            if isinstance(xposed_hook, MethodReplacement):
                if priority is not None:
                    java_callback_instance = PyMethodReplacement(self.id, xposed_hook, priority)
                else:
                    java_callback_instance = PyMethodReplacement(self.id, xposed_hook)
            elif isinstance(xposed_hook, MethodHook):
                has_before_hook = _is_method_hook_phase_enabled(
                    xposed_hook,
                    PluginsConstants.Xposed.BEFORE_HOOKED_METHOD
                )
                has_after_hook = _is_method_hook_phase_enabled(
                    xposed_hook,
                    PluginsConstants.Xposed.AFTER_HOOKED_METHOD
                )
                java_callback_instance = create_method_hook(
                    xposed_hook,
                    has_before_hook,
                    has_after_hook
                )

                if has_before_hook and hasattr(method := getattr(xposed_hook, PluginsConstants.Xposed.BEFORE_HOOKED_METHOD, None), PluginsConstants.Xposed.HOOK_FILTERS):
                    java_callback_instance.setBeforeHookedFilters(cast_to_array_list(getattr(method, PluginsConstants.Xposed.HOOK_FILTERS), xposed_hook))
                if has_after_hook and hasattr(method := getattr(xposed_hook, PluginsConstants.Xposed.AFTER_HOOKED_METHOD, None), PluginsConstants.Xposed.HOOK_FILTERS):
                    java_callback_instance.setAfterHookedFilters(cast_to_array_list(getattr(method, PluginsConstants.Xposed.HOOK_FILTERS), xposed_hook))
            elif hasattr(xposed_hook, PluginsConstants.Xposed.REPLACE_HOOKED_METHOD):
                if priority is not None:
                    java_callback_instance = PyMethodReplacement(self.id, xposed_hook, priority)
                else:
                    java_callback_instance = PyMethodReplacement(self.id, xposed_hook)
            elif (
                    callable(getattr(xposed_hook, PluginsConstants.Xposed.BEFORE_HOOKED_METHOD, None))
                    or callable(getattr(xposed_hook, PluginsConstants.Xposed.AFTER_HOOKED_METHOD, None))
            ):
                before_method = getattr(xposed_hook, PluginsConstants.Xposed.BEFORE_HOOKED_METHOD, None)
                after_method = getattr(xposed_hook, PluginsConstants.Xposed.AFTER_HOOKED_METHOD, None)
                has_before_hook = callable(before_method)
                has_after_hook = callable(after_method)
                java_callback_instance = create_method_hook(
                    xposed_hook,
                    has_before_hook,
                    has_after_hook
                )

                if has_before_hook and hasattr(before_method, PluginsConstants.Xposed.HOOK_FILTERS):
                    java_callback_instance.setBeforeHookedFilters(cast_to_array_list(getattr(before_method, PluginsConstants.Xposed.HOOK_FILTERS), xposed_hook))
                if has_after_hook and hasattr(after_method, PluginsConstants.Xposed.HOOK_FILTERS):
                    java_callback_instance.setAfterHookedFilters(cast_to_array_list(getattr(after_method, PluginsConstants.Xposed.HOOK_FILTERS), xposed_hook))
        elif (before or after) and (not before or isinstance(before, Callable)) and (not after or isinstance(after, Callable)):
            base_hook = BaseHook(self, before=before, after=after, before_filters=before_filters, after_filters=after_filters)
            java_callback_instance = create_method_hook(base_hook, bool(before), bool(after))

            if before_filters:
                java_callback_instance.setBeforeHookedFilters(cast_to_array_list(getattr(base_hook.before_hooked_method, PluginsConstants.Xposed.HOOK_FILTERS), base_hook))
            if after_filters:
                java_callback_instance.setAfterHookedFilters(cast_to_array_list(getattr(base_hook.after_hooked_method, PluginsConstants.Xposed.HOOK_FILTERS), base_hook))
        else:
            raise TypeError("xposed_hook or before or after cannot be None")

        return java_callback_instance

    @final
    @_while_plugin_active
    def hook_method(
            self,
            method_or_constructor: Member,
            xposed_hook: Optional[Any] = None,
            priority: Optional[int] = None,
            *,
            before: Optional[Callable] = None,
            after: Optional[Callable] = None,
            before_filters: Optional[List[Any]] = None,
            after_filters: Optional[List[Any]] = None
    ) -> Optional[XC_MethodHook.Unhook]:
        _sdk_version.Z.c()
        
        if xposed_hook is None and before is None and after is None:
            raise TypeError("xposed_hook or before or after cannot be None")

        unhook = None
        try:
            java_callback_instance = self.__get_java_callback_instance(
                xposed_hook, priority, before=before, after=after,
                before_filters=before_filters, after_filters=after_filters
            )
            if java_callback_instance is not None:
                unhook = XposedBridge.hookMethod(method_or_constructor, java_callback_instance)
                PluginsController.getInstance().addXposedHook(self.id, unhook)
                return unhook
            else:
                self.log(f"Could not determine hook type for {type(xposed_hook or before or after).__name__}.")
                return None

        except Exception as e:
            self.log(f"Error while hooking method {method_or_constructor.getName()}: {e}")
            if unhook is not None:
                self.log("An error occurred after the hook was created, attempting to unhook it immediately.")
                unhook.unhook()
            return None

    @final
    @_while_plugin_active
    def hook_all_methods(
            self,
            hook_class: Class,
            method_name: str,
            xposed_hook: Optional[Any] = None,
            priority: Optional[int] = None,
            *,
            before: Optional[Callable] = None,
            after: Optional[Callable] = None,
            before_filters: Optional[List[Any]] = None,
            after_filters: Optional[List[Any]] = None
    ) -> Optional[List[XC_MethodHook.Unhook]]:
        _sdk_version.Z.c()
        
        if xposed_hook is None and before is None and after is None:
            raise TypeError("xposed_hook or before or after cannot be None")

        if method_name is None:
            raise TypeError("method_name cannot be None")

        try:
            java_callback_instance = self.__get_java_callback_instance(
                xposed_hook, priority, before=before, after=after,
                before_filters=before_filters, after_filters=after_filters
            )
            if java_callback_instance is not None:
                unhooks = XposedBridge.hookAllMethods(hook_class, method_name, java_callback_instance)
                if not unhooks:
                    return None

                PluginsController.getInstance().addXposedHooks(self.id, ArrayList(unhooks.stream().toList()))
                return list(unhooks.toArray())
            else:
                self.log(f"Could not determine hook type for {type(xposed_hook or before or after).__name__}.")
                return None
        except Exception as e:
            self.log(f"Error while hooking class methods with name {method_name} for {hook_class.getName()}: {e}")
            return None

    @final
    @_while_plugin_active
    def hook_all_constructors(
            self,
            hook_class: Class,
            xposed_hook: Optional[Any] = None,
            priority: Optional[int] = None,
            *,
            before: Optional[Callable] = None,
            after: Optional[Callable] = None,
            before_filters: Optional[List[Any]] = None,
            after_filters: Optional[List[Any]] = None
    ) -> Optional[List[XC_MethodHook.Unhook]]:
        _sdk_version.Z.c()
        
        if xposed_hook is None and before is None and after is None:
            raise TypeError("xposed_hook or before or after cannot be None")

        try:
            java_callback_instance = self.__get_java_callback_instance(
                xposed_hook, priority, before=before, after=after,
                before_filters=before_filters, after_filters=after_filters
            )
            if java_callback_instance is not None:
                unhooks = XposedBridge.hookAllConstructors(hook_class, java_callback_instance)
                if not unhooks:
                    return None

                PluginsController.getInstance().addXposedHooks(self.id, ArrayList(unhooks.stream().toList()))
                return list(unhooks.toArray())
            else:
                self.log(f"Could not determine hook type for {type(xposed_hook or before or after).__name__}.")
                return None
        except Exception as e:
            self.log(f"Error while hooking class constructors for {hook_class.getName()}: {e}")
            return None

    @final
    def unhook_method(self, unhook: XC_MethodHook.Unhook):
        PluginsController.getInstance().removeXposedHook(self.id, unhook)

    def _partial_targets_missing(self, kind: str, **targets) -> bool:
        missing = [name for name, value in targets.items() if value is None]
        if missing:
            self.log(f"Cannot install a partial {kind} hook: {', '.join(missing)} is None")
        return bool(missing)

    def _install_partial(self, target, callback: Callable, priority: Optional[int],
                         param_types: List[Any], result_type: Any, description: str):
        _sdk_version.Z.c()
        if callback is None:
            raise TypeError("callback cannot be None")
        api = _partial_api()
        try:
            builder = api["ExteraHook"].hook(target)
            if priority is not None:
                builder = builder.priority(priority)
            handle = builder.intercept(api["Interceptor"](self, callback, param_types, result_type))
        except Exception as e:
            self.log(f"Error while installing a partial hook on {description}: {e}")
            return None
        PluginsController.getInstance().addPartialHook(self.id, handle)
        return handle

    @final
    @_while_plugin_active
    def hook_call_site(self, in_method: Member, callee: Member, callback: Callable[[PartialChain], Any],
                       occurrence: int = -1, priority: Optional[int] = None):
        """Intercepts calls to `callee` made from inside `in_method` only.

        `occurrence` picks one call by its order in the bytecode (0-based), -1 means all of them.
        Returns a handle for `unhook_partial`, or None when the hook could not be installed.
        """
        if self._partial_targets_missing("call site", in_method=in_method, callee=callee):
            return None
        target = _partial_api()["HookTarget"].callSite(in_method, callee, occurrence)
        return self._install_partial(target, callback, priority, list(callee.getParameterTypes()),
                                     callee.getReturnType(), f"{callee.getName()} in {in_method.getName()}")

    @final
    @_while_plugin_active
    def hook_field_read(self, in_method: Member, field: Any, callback: Callable[[PartialChain], Any],
                        occurrence: int = -1, priority: Optional[int] = None):
        """Intercepts reads of `field` inside `in_method`; `proceed()` returns the stored value."""
        if self._partial_targets_missing("field read", in_method=in_method, field=field):
            return None
        target = _partial_api()["HookTarget"].fieldRead(in_method, field, occurrence)
        return self._install_partial(target, callback, priority, [], field.getType(),
                                     f"reads of {field.getName()} in {in_method.getName()}")

    @final
    @_while_plugin_active
    def hook_field_write(self, in_method: Member, field: Any, callback: Callable[[PartialChain], Any],
                         occurrence: int = -1, priority: Optional[int] = None):
        """Intercepts writes of `field` inside `in_method`; `proceed(value)` stores a different value,
        not calling `proceed` skips the write."""
        if self._partial_targets_missing("field write", in_method=in_method, field=field):
            return None
        target = _partial_api()["HookTarget"].fieldWrite(in_method, field, occurrence)
        return self._install_partial(target, callback, priority, [field.getType()], None,
                                     f"writes of {field.getName()} in {in_method.getName()}")

    @final
    @_while_plugin_active
    def hook_string_constant(self, in_method: Member, text: str, callback: Callable[[PartialChain], Any],
                             occurrence: int = -1, priority: Optional[int] = None):
        """Replaces the string literal `text` inside `in_method` with what the callback returns."""
        if self._partial_targets_missing("string constant", in_method=in_method):
            return None
        api = _partial_api()
        target = api["HookTarget"].instruction(in_method, api["InstructionMatcher"].constString(text, occurrence))
        string_class = Class.forName("java.lang.String")
        return self._install_partial(target, callback, priority, [string_class], string_class,
                                     f"\"{text}\" in {in_method.getName()}")

    @final
    @_while_plugin_active
    def hook_return_value(self, in_method: Member, callback: Callable[[PartialChain], Any],
                          occurrence: int = -1, priority: Optional[int] = None):
        """Filters the values returned by the return instructions of `in_method`."""
        if self._partial_targets_missing("return value", in_method=in_method):
            return None
        api = _partial_api()
        target = api["HookTarget"].instruction(in_method, api["InstructionMatcher"].returnValue(occurrence))
        return_type = in_method.getReturnType()
        return self._install_partial(target, callback, priority, [return_type], return_type,
                                     f"returns of {in_method.getName()}")

    @final
    def unhook_partial(self, handle):
        PluginsController.getInstance().removePartialHook(self.id, handle)

    @property
    def logger(self) -> Logger:
        custom = self.__dict__.get("_custom_logger")
        if custom is not None:
            return custom
        plugin_id = getattr(self, "id", "")
        if plugin_id:
            return _plugin_logger(plugin_id)
        return Logger(f"plugin.{type(self).__name__}")

    @logger.setter
    def logger(self, value):
        self.__dict__["_custom_logger"] = value

    @final
    def log(self, message: str):
        log(f"[{self.id}] {message}")
        _sdk_version.Z.c()

    @final
    def client(self, account: Optional[int] = None) -> "AccountClient":
        from client_utils import get_client
        return get_client(account)

    @final
    @_while_plugin_active
    def add_menu_item(self, menu_item_data: MenuItemData) -> Optional[str]:
        _sdk_version.Z.c()
        if not self.id or self.error_message or not menu_item_data:
            return None

        try:
            new_item_id = PluginsController.getInstance().addMenuItem(self.id, {
                PluginsConstants.MenuItemProperties.MENU_TYPE: menu_item_data.menu_type.value,
                PluginsConstants.MenuItemProperties.TEXT: menu_item_data.text,
                PluginsConstants.MenuItemProperties.ON_CLICK: menu_item_data.on_click,
                PluginsConstants.MenuItemProperties.ITEM_ID: menu_item_data.item_id,
                PluginsConstants.MenuItemProperties.ICON: menu_item_data.icon,
                PluginsConstants.MenuItemProperties.SUBTEXT: menu_item_data.subtext,
                PluginsConstants.MenuItemProperties.CONDITION: menu_item_data.condition,
                PluginsConstants.MenuItemProperties.PRIORITY: menu_item_data.priority,
                PluginsConstants.MenuItemProperties.ON_LONG_CLICK: menu_item_data.on_long_click,
            })
            return new_item_id if new_item_id else None
        except Exception as e:
            self.log(f"Error adding menu item: {e}")
            return None

    @final
    def remove_menu_item(self, item_id: str) -> bool:
        if not self.id or not item_id or self.error_message:
            return False
        try:
            return PluginsController.getInstance().removeMenuItem(self.id, item_id)
        except Exception as e:
            self.log(f"Error removing menu item {item_id}: {e}")
            return False
