# This is the source code of the exteraGram plugins SDK.
#
# We do not and cannot prevent the use of our code,
# but be respectful and credit the original author.
#
# Copyright @exteraGram, 2022-2026
#
# Licensed under the GNU GPL v. 3 or later, with the additional terms
# in ADDITIONAL_TERMS.md at the root of the repository.

from _sdk_version import X

import copy
import gc
import inspect
import os
import sys
import threading
import time
import traceback
import weakref
from typing import Optional

from com.exteragram.messenger.plugins.utils import ClassProxy as CP  # type: ignore
from java import dynamic_proxy, jarray, jclass, jvoid  # type: ignore

from java.lang import Boolean, Byte, Character, Double, Float, Integer, Long, Short, System, Void, Object  # type: ignore
from java.lang.ref import WeakReference  # type: ignore
from java.lang.reflect import Modifier  # type: ignore
from java.util import ArrayList, Arrays  # type: ignore
from org.telegram.messenger import Utilities  # type: ignore

JavaClass = type(jclass("java.lang.Object"))
_UNSET = object()
_PYTHON_PEER_FIELD_NAME = "__extera_internal_python_peer__"
_PLUGIN_MODULE_PREFIX = "ElyxPlugins."

_interceptors = []
_RETIRED = Object()
_peer_lock = threading.Lock()
_scan_lock = threading.Lock()
_SCAN_INTERVAL = 1.0
_last_scan = 0.0
_plugins_dirs = []
_module_owners = {}


_PyObjectArray = jarray(jclass("com.chaquo.python.PyObject"))
_ObjectArray = jarray(Object)
_field_set = jclass("java.lang.reflect.Field").getClass().getMethod("set", Object.getClass(), Object.getClass())


def _set_peer_field(instance, peer):
    peers = _PyObjectArray([peer])
    args = _ObjectArray([instance, None])
    System.arraycopy(peers, 0, args, 1, 1)
    _field_set.invoke(instance.getClass().getField(_PYTHON_PEER_FIELD_NAME), args)


def _java_of(peer):
    d = peer.__dict__
    java_instance = d.get("_jthis")
    if java_instance is None:
        weak = d.get("_jweak")
        if weak is not None:
            java_instance = weak.get()
            if java_instance is not None:
                d["_jthis"] = java_instance
    return java_instance


def _count_base_refs():
    holder = [object()]
    obj = holder[0]
    return sys.getrefcount(obj)


_BASE_REFS = _count_base_refs()


def _scan_peers(force=False):
    global _last_scan
    now = time.monotonic()
    if not force and now - _last_scan < _SCAN_INTERVAL:
        return
    if not _scan_lock.acquire(False):
        return
    try:
        _last_scan = now
        for interceptor in tuple(_interceptors):
            refs = interceptor.instances.valuerefs()
            if not refs and interceptor.pyclass is None:
                _interceptors.remove(interceptor)
                continue
            for ref in refs:
                peer = ref()
                if peer is not None:
                    d = peer.__dict__
                    if (d.get("_jthis") is not None and d.get("_jweak") is not None
                            and sys.getrefcount(peer) <= _BASE_REFS):
                        del d["_jthis"]
                peer = None
    finally:
        _scan_lock.release()


def _on_gc(phase, info):
    if phase == "stop" and _interceptors:
        _scan_peers(force=info.get("generation") == 2)


gc.callbacks.append(_on_gc)


def collect_peers():
    _scan_peers(force=True)


def _plugins_dir():
    if not _plugins_dirs:
        try:
            from com.exteragram.messenger.plugins import PluginsController  # type: ignore
            path = PluginsController.getInstance().getPluginsDir().getAbsolutePath()
            _plugins_dirs.append(os.path.realpath(str(path)))
        except Exception:
            return None
    return _plugins_dirs[0]


def _plugin_id_of_module(module_name):
    if not isinstance(module_name, str):
        return None
    if module_name.startswith(_PLUGIN_MODULE_PREFIX):
        return module_name[len(_PLUGIN_MODULE_PREFIX):].split(".", 1)[0] or None
    if "." in module_name:
        return None
    path = getattr(sys.modules.get(module_name), "__file__", None)
    if not isinstance(path, str):
        return None
    key = (module_name, path)
    if key not in _module_owners:
        plugins_dir = _plugins_dir()
        if plugins_dir is None:
            return None
        real_path = os.path.realpath(path)
        is_plugin = (os.path.dirname(real_path) == plugins_dir
                     and os.path.basename(real_path) == module_name + ".py")
        _module_owners[key] = module_name if is_plugin else None
    return _module_owners[key]


def _calling_plugin_id():
    try:
        frame = sys._getframe(0)
    except ValueError:
        return None
    while frame is not None:
        plugin_id = _plugin_id_of_module(frame.f_globals.get("__name__"))
        if plugin_id is not None:
            return plugin_id
        frame = frame.f_back
    return None


def release_plugin(plugin_id=None):
    count = 0
    for interceptor in tuple(_interceptors):
        for ref in interceptor.instances.valuerefs():
            peer = ref()
            if peer is not None:
                owner = peer.__dict__.get("_jowner")
                if owner is not None and (plugin_id is None or owner == plugin_id):
                    if peer.jrelease():
                        count += 1
            peer = None
    return count

_PRIMITIVE_TYPES = {
    "void": Void.TYPE,
    "boolean": Boolean.TYPE,
    "byte": Byte.TYPE,
    "char": Character.TYPE,
    "short": Short.TYPE,
    "int": Integer.TYPE,
    "long": Long.TYPE,
    "float": Float.TYPE,
    "double": Double.TYPE,
}

_MODIFIER_FLAGS = {
    "public": Modifier.PUBLIC,
    "protected": Modifier.PROTECTED,
    "private": Modifier.PRIVATE,
    "static": Modifier.STATIC,
    "final": Modifier.FINAL,
    "abstract": Modifier.ABSTRACT,
}


def _copy_default(value):
    if value is _UNSET:
        return _UNSET
    if callable(value):
        return value()
    try:
        return copy.deepcopy(value)
    except Exception:
        return value


def _normalize_java_type(type_ref):
    if type_ref is None:
        return None
    if isinstance(type_ref, JavaClass):
        return type_ref.getClass()
    if isinstance(type_ref, str):
        primitive = _PRIMITIVE_TYPES.get(type_ref.strip().lower())
        return primitive if primitive is not None else jclass(type_ref).getClass()
    return type_ref


def _normalize_arg_types(arg_types, *, allow_none=False):
    if arg_types is None:
        return None if allow_none else []
    if not isinstance(arg_types, (list, tuple)):
        arg_types = [arg_types]
    return [_normalize_java_type(arg_type) for arg_type in arg_types]


def _parse_modifiers(modifiers):
    if modifiers is None:
        return Modifier.PUBLIC
    if isinstance(modifiers, int):
        return modifiers

    result = 0
    normalized = str(modifiers).replace("|", " ").replace(",", " ").split()
    for token in normalized:
        flag = _MODIFIER_FLAGS.get(token.lower())
        if flag is None:
            raise ValueError(f"Unknown modifier: {token}")
        result |= flag
    return result or Modifier.PUBLIC


def _parse_argument_specs(arguments=None, *, arg_types=None, allow_none=False):
    if arguments is not None and arg_types is not None:
        raise ValueError("Use either arguments or arg_types, not both")

    if arguments is None:
        normalized_types = _normalize_arg_types(arg_types, allow_none=allow_none)
        return None, normalized_types

    if not isinstance(arguments, (list, tuple)):
        arguments = [arguments]

    argument_names = []
    argument_types = []

    for index, argument in enumerate(arguments):
        if isinstance(argument, (list, tuple)):
            if len(argument) != 2:
                raise ValueError("Argument tuples must be in the form (name, type)")
            argument_name, argument_type = argument
        else:
            argument_name, argument_type = None, argument

        if argument_name is None:
            argument_names.append(f"arg{index}")
        else:
            argument_names.append(str(argument_name))

        argument_types.append(_normalize_java_type(argument_type))

    return argument_names, argument_types


def _get_java_field_value(java_instance, field_name):
    if java_instance is None:
        return _UNSET

    try:
        field = java_instance.getClass().getField(field_name)
        field.setAccessible(True)
        return field.get(java_instance)
    except Exception:
        return _UNSET


def _set_java_field_value(java_instance, field_name, value):
    if java_instance is None:
        return False

    try:
        field = java_instance.getClass().getField(field_name)
        field.setAccessible(True)
        field.set(java_instance, value)
        return True
    except Exception:
        return False


def _find_class_attr(cls, name):
    for current in cls.__mro__:
        if name in current.__dict__:
            return current.__dict__[name]
    return None


def _unwrap_callable_attr(attr):
    return attr.__func__ if isinstance(attr, (classmethod, staticmethod)) else attr


def _bind_attr(instance, attr):
    if not hasattr(attr, "__get__"):
        return attr
    if isinstance(instance, type):
        if isinstance(attr, classmethod):
            return attr.__get__(None, instance)
        return attr.__get__(instance, instance)
    return attr.__get__(instance, type(instance))


def _callable_parameters(fn):
    signature = inspect.signature(_unwrap_callable_attr(fn))
    parameters = list(signature.parameters.values())
    if parameters and parameters[0].name in ("self", "cls"):
        parameters = parameters[1:]
    return signature, parameters


def _call_with_supported_args(fn, *args):
    signature = inspect.signature(_unwrap_callable_attr(fn))
    parameters = list(signature.parameters.values())

    if any(parameter.kind == inspect.Parameter.VAR_POSITIONAL for parameter in parameters):
        return fn(*args)

    positional = [
        parameter for parameter in parameters
        if parameter.kind in (inspect.Parameter.POSITIONAL_ONLY, inspect.Parameter.POSITIONAL_OR_KEYWORD)
    ]
    return fn(*args[:len(positional)])


def _infer_arg_types(fn):
    _, parameters = _callable_parameters(fn)

    arg_types = []
    for parameter in parameters:
        if parameter.annotation is inspect.Signature.empty:
            raise ValueError(f"Method '{_unwrap_callable_attr(fn).__name__}' requires arg_types or annotations")
        arg_types.append(_normalize_java_type(parameter.annotation))
    return arg_types


def _infer_method_signature(fn):
    signature, _ = _callable_parameters(fn)
    arg_types = _infer_arg_types(fn)
    return_annotation = signature.return_annotation
    return_type = jvoid if return_annotation is inspect.Signature.empty else _normalize_java_type(return_annotation)
    return return_type, arg_types


def _type_name(type_ref):
    normalized = _normalize_java_type(type_ref)
    if normalized is None:
        return None
    if hasattr(normalized, "getName"):
        return normalized.getName()
    return str(normalized)


def _build_signature(name, arg_types):
    if arg_types is None:
        return name
    return ":".join([name] + [_type_name(arg_type) for arg_type in arg_types])


def _split_signature(raw_name):
    parts = (raw_name or "").split(":")
    return parts[0], tuple(parts[1:])


def _call_super(instance, simple_name, args):
    try:
        return getattr(instance, f"SUPER_{simple_name}")(*args)
    except Exception:
        return None


def _normalize_constructor_result(updated_args):
    if updated_args is None:
        return None
    if isinstance(updated_args, (list, tuple)):
        return list(updated_args)
    return [updated_args]


def _normalize_proxy_custom_name(name):
    if name is None:
        return None

    normalized = str(name).strip()
    if not normalized:
        return None

    result = []
    for char in normalized:
        if char.isalnum() or char == "_":
            result.append(char)
        else:
            result.append("_")
    return "".join(result)


class _MethodMetadata:
    def __init__(self, kind, return_type, arg_types, modifiers, throws=None, java_name=None, implementation="python", code=None, arg_names=None):
        self.kind = kind
        self.return_type = return_type
        self.arg_types = arg_types
        self.modifiers = modifiers
        self.throws = throws
        self.java_name = java_name
        self.implementation = implementation
        self.code = code
        self.arg_names = arg_names

    def resolve(self, fn, python_name):
        return_type = self.return_type
        arg_types = self.arg_types
        arg_names = list(self.arg_names) if self.arg_names is not None else None

        if self.kind == "method" and self.implementation == "python" and (return_type is None or arg_types is None):
            inferred_return_type, inferred_arg_types = _infer_method_signature(fn)
            if return_type is None:
                return_type = inferred_return_type
            if arg_types is None:
                arg_types = inferred_arg_types
        elif self.implementation == "mvel":
            if self.kind == "method":
                if return_type is None:
                    return_type = jvoid
                if arg_types is None:
                    arg_types = []
                    if arg_names is not None and len(arg_names) != 0:
                        raise ValueError(f"MVEL method '{python_name}' defines argument names without argument types")
            elif arg_names is not None and arg_types is not None and len(arg_names) != len(arg_types):
                raise ValueError(f"MVEL method '{python_name}' must have the same number of argument names and argument types")

        return _ResolvedMethodMetadata(
            kind=self.kind,
            python_name=python_name,
            java_name=self.java_name or python_name,
            return_type=_normalize_java_type(return_type) if return_type is not None else None,
            arg_types=_normalize_arg_types(arg_types, allow_none=self.kind == "override"),
            modifiers=_parse_modifiers(self.modifiers),
            throws=self.throws,
            implementation=self.implementation,
            code=self.code,
            arg_names=arg_names,
        )


class _ResolvedMethodMetadata:
    def __init__(self, *, kind, python_name, java_name, return_type, arg_types, modifiers, throws=None, implementation="python", code=None, arg_names=None):
        self.kind = kind
        self.python_name = python_name
        self.java_name = java_name
        self.return_type = return_type
        self.arg_types = arg_types
        self.modifiers = modifiers
        self.throws = throws
        self.implementation = implementation
        self.code = code
        self.arg_names = arg_names
        self.signature = _build_signature(java_name, arg_types) if arg_types is not None else None


class _ConstructorMetadata:
    def __init__(self, arg_types=None, *, phase="post"):
        self.arg_types = arg_types
        self.phase = phase

    def resolve(self, fn, python_name):
        arg_types = self.arg_types
        if arg_types is None:
            try:
                arg_types = _infer_arg_types(fn)
            except ValueError:
                arg_types = None

        return _ResolvedConstructorMetadata(
            python_name=python_name,
            arg_types=_normalize_arg_types(arg_types, allow_none=True),
            phase=self.phase,
        )


class _ResolvedConstructorMetadata:
    def __init__(self, *, python_name, arg_types, phase):
        self.python_name = python_name
        self.arg_types = arg_types
        self.phase = phase
        self.signature = _build_signature("<constructor>", arg_types) if arg_types is not None else None


class _ClassBuildMetadata:
    def resolve(self, python_name):
        return _ResolvedClassBuildMetadata(python_name=python_name)


class _ResolvedClassBuildMetadata:
    def __init__(self, *, python_name):
        self.python_name = python_name


class _FieldMetadata:
    def __init__(self, field_type, default=_UNSET, modifiers="public", methods=None):
        self.field_type = field_type
        self.default = default
        self.modifiers = modifiers
        self.methods = methods

    def resolve(self, python_name):
        return _ResolvedFieldMetadata(
            python_name=python_name,
            field_type=_normalize_java_type(self.field_type),
            default=self.default,
            modifiers=_parse_modifiers(self.modifiers),
            methods=_resolve_field_accessor_methods(self.methods, python_name, self.field_type),
        )


class _ResolvedFieldMetadata:
    def __init__(self, *, python_name, field_type, default, modifiers, methods):
        self.python_name = python_name
        self.field_type = field_type
        self.default = default
        self.modifiers = modifiers
        self.methods = methods


class _FieldAccessorMetadata:
    def __init__(self, kind, name=None, modifiers="public"):
        self.kind = kind
        self.name = name
        self.modifiers = modifiers

    def resolve(self, field_name, field_type):
        return _ResolvedFieldAccessorMetadata(
            kind=self.kind,
            name=self.name or _default_field_accessor_name(field_name, self.kind, field_type),
            modifiers=_parse_modifiers(self.modifiers),
        )


class _ResolvedFieldAccessorMetadata:
    def __init__(self, *, kind, name, modifiers):
        self.kind = kind
        self.name = name
        self.modifiers = modifiers


class JField:
    def __init__(self, field_type, *, default=_UNSET, modifiers="public", methods=None):
        self.__jfield__ = _FieldMetadata(field_type, default=default, modifiers=modifiers, methods=methods)
        self.name = None
        self.storage_name = None

    def __set_name__(self, owner, name):
        self.name = name
        self.storage_name = f"__jfield_value_{name}"

    def __get__(self, instance, owner=None):
        if instance is None:
            return self

        java_instance = _java_of(instance)
        if java_instance is not None:
            java_value = _get_java_field_value(java_instance, self.name)
            if java_value is not _UNSET:
                instance.__dict__[self.storage_name] = java_value
                return java_value

        if self.storage_name in instance.__dict__:
            return instance.__dict__[self.storage_name]

        value = _copy_default(self.__jfield__.default)
        if value is _UNSET:
            return None
        instance.__dict__[self.storage_name] = value
        return value

    def __set__(self, instance, value):
        instance.__dict__[self.storage_name] = value

        java_instance = _java_of(instance)
        if java_instance is not None and _set_java_field_value(java_instance, self.name, value):
            return


class JMvelMethod:
    def __init__(self, metadata):
        self.__jproxy_method__ = metadata
        self.python_name = None

    def __set_name__(self, owner, name):
        self.python_name = name

    def __get__(self, instance, owner=None):
        if instance is None:
            return self

        java_instance = _java_of(instance)
        if java_instance is None:
            raise RuntimeError("Java instance is not attached yet")

        metadata = self.__jproxy_method__
        return getattr(java_instance, metadata.java_name or self.python_name)


def _field_accessor_title(name):
    return "".join(part[:1].upper() + part[1:] for part in str(name).split("_") if part)


def _default_field_accessor_name(field_name, kind, field_type):
    title = _field_accessor_title(field_name)
    field_type_name = _type_name(field_type)

    if kind == "get":
        if field_type_name in ("boolean", "java.lang.Boolean"):
            return f"is{title}"
        return f"get{title}"
    if kind == "set":
        return f"set{title}"
    raise ValueError(f"Unknown field accessor kind: {kind}")


def _resolve_field_accessor_methods(methods, field_name, field_type):
    if methods is None:
        return []
    if not isinstance(methods, (list, tuple)):
        methods = [methods]

    resolved = []
    for method in methods:
        if not isinstance(method, _FieldAccessorMetadata):
            raise TypeError("Field methods must be created with jgetmethod(...) or jsetmethod(...)")
        resolved.append(method.resolve(field_name, field_type))
    return resolved


class _ManagedClassBuildHook(dynamic_proxy(CP.DexMakerHook)):
    def __init__(self, pyclass):
        super().__init__()
        self.pyclass = pyclass

    def apply(self, dex_maker, proxy_type, super_type, interfaces):
        try:
            for hook_name in self.pyclass._class_build_hooks():
                attr = _find_class_attr(self.pyclass, hook_name)
                if attr is None:
                    continue
                bound = _bind_attr(self.pyclass, attr)
                _call_with_supported_args(bound, dex_maker, proxy_type, super_type, interfaces)
        except Exception:
            from android_utils import log
            log(f"Error in class build hook {self.pyclass.__name__}: {traceback.format_exc()}")
            raise


class ClassInterceptor(dynamic_proxy(Utilities.Callback3Return)):
    def __init__(self, fn=None, pyclass=None, **kwargs):
        if fn is None and pyclass is None:
            raise ValueError("fn or pyclass must be provided")

        super().__init__()

        self.fn = fn
        self.pyclass = pyclass
        self.kwargs = kwargs

    def run(self, instance, method_name, args):
        try:
            raw_name = method_name or ""
            simple_name, _ = _split_signature(raw_name)

            if simple_name == "<constructor>":
                if instance is None:
                    name = "<on-pre-init>" if self.fn else "on_pre_init"
                else:
                    name = "<on-post-init>" if self.fn else "on_post_init"
            else:
                name = simple_name

            try:
                if self.fn:
                    return self.fn(instance, raw_name, *(args or ()), **self.kwargs)

                method = getattr(self.pyclass, raw_name, None)
                if method:
                    return method(instance, *(args or ()), **self.kwargs)

                method = getattr(self.pyclass, name, None)
                if method:
                    return method(instance, *(args or ()), **self.kwargs)
                if method := getattr(self.pyclass, "jintercept", None):
                    return method(instance, raw_name, *(args or ()), **self.kwargs)
            except Exception:
                from android_utils import log
                log(f"Error in {(self.pyclass if self.pyclass else self.fn).__class__.__name__} ({raw_name or name}): {traceback.format_exc()}")

            return getattr(instance, f"SUPER_{name}")(*(args or ())) if simple_name != "<constructor>" and instance is not None else None
        except Exception:
            from android_utils import log
            log(f"Error in {(self.pyclass if self.pyclass else self.fn).__class__.__name__} ({method_name}): {traceback.format_exc()}")
            return None


def _collect_method_metadata(pyclass):
    methods = {}
    for current in reversed(pyclass.__mro__):
        for name, attr in current.__dict__.items():
            source = _unwrap_callable_attr(attr) if callable(_unwrap_callable_attr(attr)) else attr
            metadata = getattr(source, "__jproxy_method__", None)
            if metadata is not None:
                methods[name] = metadata.resolve(_unwrap_callable_attr(attr) if callable(_unwrap_callable_attr(attr)) else None, name)
    return methods


def _collect_constructor_metadata(pyclass):
    constructors = {}
    for current in reversed(pyclass.__mro__):
        for name, attr in current.__dict__.items():
            metadata = getattr(_unwrap_callable_attr(attr), "__jproxy_constructor__", None)
            if metadata is not None:
                constructors[name] = metadata.resolve(_unwrap_callable_attr(attr), name)
    return constructors


def _collect_class_build_metadata(pyclass):
    hooks = []
    for current in reversed(pyclass.__mro__):
        for name, attr in current.__dict__.items():
            source = _unwrap_callable_attr(attr) if callable(_unwrap_callable_attr(attr)) else attr
            metadata = getattr(source, "__jclass_build_hook__", None)
            if metadata is not None:
                hooks.append(metadata.resolve(name))
    return hooks


def _collect_field_metadata(pyclass):
    fields = {}
    for current in reversed(pyclass.__mro__):
        for name, attr in current.__dict__.items():
            metadata = getattr(attr, "__jfield__", None)
            if metadata is not None:
                fields[name] = metadata.resolve(name)
    return fields


def _make_super_bridge(java_name):
    def bridge(self, *args):
        java_instance = _java_of(self)
        if java_instance is None:
            raise RuntimeError("Java instance is not attached yet")
        return getattr(java_instance, f"SUPER_{java_name}")(*args)

    return bridge


def _install_super_bridge(pyclass):
    previous_bridge = getattr(pyclass, "__jsuper_bridge_base__", None)
    bases = tuple(base for base in pyclass.__bases__ if base is not previous_bridge)

    bridge_attrs = {}
    for method in _collect_method_metadata(pyclass).values():
        if method.kind == "override" and method.implementation == "python":
            bridge_attrs[method.python_name] = _make_super_bridge(method.java_name)

    if not bridge_attrs:
        pyclass.__bases__ = bases
        pyclass.__jsuper_bridge_base__ = None
        return

    bridge_base = type(f"{pyclass.__name__}JavaSuper", (object,), bridge_attrs)
    pyclass.__bases__ = (bridge_base,) + bases
    pyclass.__jsuper_bridge_base__ = bridge_base


def _to_java_list(items):
    if not items:
        return None
    return Arrays.asList(list(items))


def _build_method_dispatch(pyclass):
    dispatch = {"exact": {}, "wildcard": {}}
    for method in pyclass._resolved_jmethods().values():
        if method.implementation != "python":
            continue
        if method.signature is None:
            dispatch["wildcard"][method.java_name] = method.python_name
        else:
            dispatch["exact"][method.signature] = method.python_name
    return dispatch


def _build_constructor_dispatch(pyclass):
    dispatch = {
        "pre": {"exact": {}, "wildcard": None},
        "post": {"exact": {}, "wildcard": None},
    }

    for constructor in pyclass._resolved_jconstructors().values():
        bucket = dispatch[constructor.phase]
        if constructor.signature is None:
            bucket["wildcard"] = constructor.python_name
        else:
            bucket["exact"][constructor.signature] = constructor.python_name

    return dispatch


def _build_constructor_signature(arg_types):
    return _build_signature("<constructor>", list(arg_types or ()))


def _unwrap_java_value(value):
    java_value = getattr(value, "java", _UNSET)
    return java_value if java_value not in (_UNSET, None) else value


def _python_arg_match_score(expected_type, value):
    expected_type = _normalize_java_type(expected_type)
    if expected_type is None:
        return 0

    if value is None:
        try:
            return -1 if expected_type.isPrimitive() else 1
        except Exception:
            return 1

    raw_value = _unwrap_java_value(value)
    type_name = _type_name(expected_type)

    if type_name == "boolean":
        return 10 if isinstance(raw_value, bool) else -1

    if type_name in ("byte", "short", "int", "long"):
        return 9 if isinstance(raw_value, int) and not isinstance(raw_value, bool) else -1

    if type_name in ("float", "double"):
        return 8 if isinstance(raw_value, (int, float)) and not isinstance(raw_value, bool) else -1

    if type_name == "char":
        return 9 if isinstance(raw_value, str) and len(raw_value) == 1 else -1

    if type_name == "java.lang.String":
        return 10 if isinstance(raw_value, str) else -1

    if type_name == "java.lang.Object":
        return 1

    if hasattr(expected_type, "isInstance"):
        try:
            if expected_type.isInstance(raw_value):
                return 7
        except Exception:
            pass

    raw_class = getattr(raw_value, "getClass", None)
    if raw_class is not None and hasattr(expected_type, "isAssignableFrom"):
        try:
            if expected_type.isAssignableFrom(raw_class()):
                return 6
        except Exception:
            pass

    return -1


def _select_method_overload(candidates, args):
    exact_matches = []
    wildcard_matches = []

    for method in candidates:
        if method.arg_types is None:
            wildcard_matches.append(method)
            continue

        if len(method.arg_types) != len(args):
            continue

        total_score = 0
        for expected_type, value in zip(method.arg_types, args):
            score = _python_arg_match_score(expected_type, value)
            if score < 0:
                total_score = None
                break
            total_score += score

        if total_score is not None:
            exact_matches.append((total_score, method))

    if exact_matches:
        exact_matches.sort(key=lambda item: item[0], reverse=True)
        return exact_matches[0][1]

    if wildcard_matches:
        return wildcard_matches[0]

    return None


def _build_method_specs(pyclass):
    specs = ArrayList()
    for method in pyclass._resolved_jmethods().values():
        argument_names = None
        if method.arg_names:
            argument_names = ArrayList()
            for argument_name in method.arg_names:
                argument_names.add(argument_name)

        specs.add(
            CP.ProxyMethodSpec(
                method.java_name,
                method.return_type if method.kind == "method" else None,
                method.arg_types,
                method.modifiers,
                method.kind == "override",
                method.implementation,
                method.code,
                argument_names,
            )
        )
    return specs if specs.size() else None


def _build_field_specs(pyclass):
    specs = ArrayList()
    for field in pyclass._resolved_jfields().values():
        accessor_specs = None
        if field.methods:
            accessor_specs = ArrayList()
            for method in field.methods:
                accessor_specs.add(CP.FieldMethodSpec(method.name, method.modifiers, method.kind == "get"))

        specs.add(CP.FieldSpec(field.python_name, field.field_type, field.modifiers, accessor_specs))
    return specs if specs.size() else None


def _parse_override_spec(signature, *, name=None, arg_types=None):
    if signature:
        if len(signature) == 1:
            if isinstance(signature[0], (list, tuple)):
                arg_types = signature[0]
            else:
                name = signature[0]
        elif len(signature) == 2:
            name, arg_types = signature
        else:
            raise TypeError("Override accepts (), (arg_types) or (name, arg_types)")
    return name, arg_types


class _ManagedClassInterceptor(dynamic_proxy(Utilities.Callback3Return)):
    def __init__(self, pyclass):
        super().__init__()
        self._pyclass_ref = weakref.ref(pyclass)
        self.instances = weakref.WeakValueDictionary()
        self.pending_init_args = []

    @property
    def pyclass(self):
        return self._pyclass_ref()

    def run(self, instance, method_name, args):
        try:
            raw_name = method_name or ""
            simple_name, _ = _split_signature(raw_name)
            arguments = list(args or ())

            if simple_name == "<constructor>":
                return self._handle_constructor(instance, raw_name, arguments)

            peer = self._get_or_create_peer(instance)
            if peer is None:
                return _call_super(instance, simple_name, arguments)
            return self._dispatch(peer, instance, raw_name, simple_name, arguments)
        except Exception:
            from android_utils import log
            pyclass = self.pyclass
            log(f"Error in managed proxy {getattr(pyclass, '__name__', None)} ({method_name}): {traceback.format_exc()}")
            return None

    def _handle_constructor(self, instance, raw_name, args):
        pyclass = self.pyclass
        if pyclass is None:
            return None

        if instance is None:
            matched, updated_args = pyclass._call_constructor_handler("pre", raw_name, args, target=pyclass)
            if matched:
                return _normalize_constructor_result(updated_args)

            pre_init_attr = _find_class_attr(pyclass, "on_pre_init")
            if pre_init_attr in (None, Base.__dict__["on_pre_init"]):
                return None

            pre_init = _bind_attr(pyclass, pre_init_attr)
            return _normalize_constructor_result(pre_init(*args))

        peer = self._get_or_create_peer(instance)
        if peer is not None and not peer.__dict__.get("_jinitialized", False):
            self._initialize_peer(peer, raw_name, args)
        return None

    @staticmethod
    def _read_peer(instance):
        try:
            return getattr(instance, _PYTHON_PEER_FIELD_NAME)
        except Exception:
            return None

    def _get_or_create_peer(self, instance):
        peer = self._read_peer(instance)
        if peer is None:
            with _peer_lock:
                peer = self._read_peer(instance)
                if peer is None:
                    return self._create_peer(instance)
        if not isinstance(peer, Base):
            return None
        if peer.__dict__.get("_jthis") is None:
            peer.__dict__["_jthis"] = instance
        return peer

    def _create_peer(self, instance):
        pyclass = self.pyclass
        if pyclass is None:
            return None

        owner = getattr(pyclass, "__jowner__", None)
        if owner is None and getattr(pyclass, "__jowner_from_caller__", False):
            owner = _calling_plugin_id()

        peer = pyclass.__new__(pyclass)
        object.__setattr__(peer, "_jthis", instance)
        object.__setattr__(peer, "_jweak", WeakReference(instance))
        object.__setattr__(peer, "_jinitialized", False)
        object.__setattr__(peer, "_jowner", owner)
        _set_peer_field(instance, peer)
        self.instances[id(peer)] = peer
        return peer

    def get_peer(self, instance):
        peer = self._read_peer(instance)
        if not isinstance(peer, Base):
            return None
        if peer.__dict__.get("_jthis") is None:
            peer.__dict__["_jthis"] = instance
        return peer

    def attach_peer(self, instance, *, raw_name=None, args=None):
        peer = self._get_or_create_peer(instance)
        if peer is None:
            raise RuntimeError("Java instance has no Python peer")
        if not peer.__dict__.get("_jinitialized", False):
            self._initialize_peer(peer, raw_name, list(args or ()))
        return peer

    def detach_peer(self, instance, peer):
        if instance is None or peer is None:
            return False
        if self._read_peer(instance) is not peer:
            return False

        try:
            setattr(instance, _PYTHON_PEER_FIELD_NAME, None)
        except Exception:
            pass
        self.instances.pop(id(peer), None)
        peer.__dict__.pop("_jthis", None)
        peer.__dict__.pop("_jweak", None)
        return True

    def push_pending_init_args(self, init_args):
        token = object()
        self.pending_init_args.append((token, list(init_args or ())))
        return token

    def cancel_pending_init_args(self, token):
        for index in range(len(self.pending_init_args) - 1, -1, -1):
            pending_token, _ = self.pending_init_args[index]
            if pending_token is token:
                self.pending_init_args.pop(index)
                return

    def _consume_pending_init_args(self, fallback_args):
        if self.pending_init_args:
            _, init_args = self.pending_init_args.pop()
            return list(init_args)
        return list(fallback_args or ())

    def _initialize_peer(self, peer, raw_name, args, *, init_args=_UNSET):
        for field in type(peer)._resolved_jfields().values():
            value = _copy_default(field.default)
            if value is not _UNSET:
                setattr(peer, field.python_name, value)

        python_init_args = self._consume_pending_init_args(args) if init_args is _UNSET else list(init_args or ())

        init = _find_class_attr(type(peer), "__init__")
        if init not in (None, Base.__dict__["__init__"], object.__init__):
            init(peer, *python_init_args)

        constructor_name = type(peer)._resolve_constructor_handler("post", raw_name)
        if constructor_name not in (None, "__init__"):
            attr = _find_class_attr(type(peer), constructor_name)
            _bind_attr(peer, attr)(*args)

        post_init = getattr(peer, "on_post_init", None)
        if post_init is not None and _find_class_attr(type(peer), "on_post_init") is not Base.__dict__["on_post_init"]:
            post_init(*args)

        object.__setattr__(peer, "_jinitialized", True)

    def _dispatch(self, peer, instance, raw_name, simple_name, args):
        handler_name = type(peer)._resolve_method_handler(raw_name, simple_name)
        if handler_name is not None:
            attr = _find_class_attr(type(peer), handler_name)
            bound = _bind_attr(peer, attr)
            return bound(*args)

        intercept = _find_class_attr(type(peer), "jintercept")
        if intercept is not None:
            return _bind_attr(peer, intercept)(raw_name, *args)

        return _call_super(instance, simple_name, args)


def _parse_method_spec(signature, *, return_type=None, arg_types=None, name=None):
    if signature:
        if len(signature) == 1:
            if isinstance(signature[0], (list, tuple)):
                arg_types = signature[0]
            else:
                return_type = signature[0]
        elif len(signature) == 2:
            return_type, arg_types = signature
        elif len(signature) == 3:
            name, return_type, arg_types = signature
        else:
            raise TypeError("Method accepts (return_type), (return_type, arg_types) or (name, return_type, arg_types)")
    return name, return_type, arg_types


def _parse_constructor_spec(signature, *, arg_types=None):
    if signature:
        if len(signature) != 1:
            raise TypeError("Constructor decorators accept () or (arg_types)")
        arg_types = signature[0]
    return arg_types


def class_proxy(clazz, *, fn: Optional[callable] = None, interceptor=None, methods=None, constructors=None, custom_name=None, **kwargs):
    if not isinstance(clazz, JavaClass):
        raise ValueError("clazz must be a JavaClass type")

    if fn is None and interceptor is None:
        raise ValueError("fn or interceptor must be provided")

    if isinstance(interceptor, type) and issubclass(interceptor, Base):
        bound = interceptor.bind(clazz, methods=methods, constructors=constructors, custom_name=custom_name)
        return bound.java_class()

    if isinstance(interceptor, Base):
        raise ValueError("Managed proxy classes should be passed as a class, not an instance")

    if X.BUILD_VERSION >= 66690:
        return CP.createProxyClass(
            clazz,
            ClassInterceptor(fn, interceptor, **kwargs),
            _to_java_list(methods),
            _to_java_list(constructors),
            _normalize_proxy_custom_name(custom_name),
        )
    else:
        return CP.createProxyClass(
            clazz,
            ClassInterceptor(fn, interceptor, **kwargs),
            _to_java_list(methods),
            _to_java_list(constructors),
        )


class JHelper:
    @staticmethod
    def Override(*signature, arg_types=None, modifiers="public", throws=None, name=None):
        name, arg_types = _parse_override_spec(signature, name=name, arg_types=arg_types)

        def decorator(fn):
            _unwrap_callable_attr(fn).__jproxy_method__ = _MethodMetadata("override", None, arg_types, modifiers, throws=throws, java_name=name)
            return fn

        return decorator

    @staticmethod
    def Overload(name, arg_types, *, modifiers="public", throws=None):
        return JHelper.Override(name, arg_types, modifiers=modifiers, throws=throws)

    @staticmethod
    def Method(*signature, return_type=None, arg_types=None, modifiers="public", throws=None, name=None):
        name, return_type, arg_types = _parse_method_spec(signature, return_type=return_type, arg_types=arg_types, name=name)

        def decorator(fn):
            _unwrap_callable_attr(fn).__jproxy_method__ = _MethodMetadata("method", return_type, arg_types, modifiers, throws=throws, java_name=name)
            return fn

        return decorator

    @staticmethod
    def MVELMethod(*, code, return_type=None, arguments=None, arg_types=None, modifiers="public", throws=None, name=None, override=False):
        argument_names, resolved_arg_types = _parse_argument_specs(arguments, arg_types=arg_types, allow_none=override)
        return JMvelMethod(
            _MethodMetadata(
                "override" if override else "method",
                return_type,
                resolved_arg_types,
                modifiers,
                throws=throws,
                java_name=name,
                implementation="mvel",
                code=code,
                arg_names=argument_names,
            )
        )

    @staticmethod
    def MVELOverride(*signature, code, arguments=None, arg_types=None, modifiers="public", throws=None, name=None):
        name, signature_arg_types = _parse_override_spec(signature, name=name, arg_types=arg_types)
        if arguments is not None and signature_arg_types is not None:
            raise ValueError("Use either arguments or arg_types for MVEL overrides, not both")

        resolved_arguments = arguments
        if resolved_arguments is None and signature_arg_types is not None:
            resolved_arguments = signature_arg_types

        return JHelper.MVELMethod(
            code=code,
            return_type=None,
            arguments=resolved_arguments,
            modifiers=modifiers,
            throws=throws,
            name=name,
            override=True,
        )

    @staticmethod
    def Constructor(*signature, arg_types=None):
        arg_types = _parse_constructor_spec(signature, arg_types=arg_types)

        def decorator(fn):
            _unwrap_callable_attr(fn).__jproxy_constructor__ = _ConstructorMetadata(arg_types, phase="post")
            return fn

        return decorator

    @staticmethod
    def PreConstructor(*signature, arg_types=None):
        arg_types = _parse_constructor_spec(signature, arg_types=arg_types)

        def decorator(fn):
            _unwrap_callable_attr(fn).__jproxy_constructor__ = _ConstructorMetadata(arg_types, phase="pre")
            return fn

        return decorator

    @staticmethod
    def Field(field_type, *, default=_UNSET, modifiers="public", methods=None):
        return JField(field_type, default=default, modifiers=modifiers, methods=methods)

    @staticmethod
    def GetMethod(name=None, *, modifiers="public"):
        return _FieldAccessorMetadata("get", name=name, modifiers=modifiers)

    @staticmethod
    def SetMethod(name=None, *, modifiers="public"):
        return _FieldAccessorMetadata("set", name=name, modifiers=modifiers)

    @staticmethod
    def ClassBuilder():
        def decorator(fn):
            _unwrap_callable_attr(fn).__jclass_build_hook__ = _ClassBuildMetadata()
            return fn

        return decorator


class Base:
    __jbaseclass__ = None
    __jinterfaces__ = ()
    __jproxy_class__ = None
    __jproxy_interceptor__ = None
    __jproxy_custom_name__ = None
    __jlegacy_methods__ = ()
    __jlegacy_constructors__ = ()
    __jsuper_bridge_base__ = None
    __jresolved_methods__ = None
    __jresolved_constructors__ = None
    __jresolved_fields__ = None
    __jmethod_dispatch__ = None
    __jconstructor_dispatch__ = None
    __jclass_build_hooks_cache__ = None
    __jowner__ = None
    __jowner_from_caller__ = False

    def __init__(self, *args, **kwargs):
        pass

    @classmethod
    def on_pre_init(cls, *args):
        return None

    def on_post_init(self, *args):
        pass

    @property
    def java(self):
        return _java_of(self)

    @property
    def this(self):
        return self.java

    def __getattr__(self, item):
        if item == "_jthis":
            return _java_of(self)

        managed = self._managed_method(item)
        if managed is not None:
            return managed

        java_instance = _java_of(self)
        if java_instance is None:
            raise AttributeError(item)
        return getattr(java_instance, item)

    def jrelease(self):
        d = self.__dict__
        if d.get("_jthis") is None and d.get("_jweak") is None:
            return False

        try:
            self.on_release()
        except Exception:
            from android_utils import log
            log(f"Error in {type(self).__name__}.on_release: {traceback.format_exc()}")

        java_instance = _java_of(self)
        if java_instance is not None:
            try:
                if getattr(java_instance, _PYTHON_PEER_FIELD_NAME) is self:
                    setattr(java_instance, _PYTHON_PEER_FIELD_NAME, _RETIRED)
            except Exception:
                pass
        d.pop("_jthis", None)
        d.pop("_jweak", None)

        interceptor = type(self).__jproxy_interceptor__
        if interceptor is not None:
            interceptor.instances.pop(id(self), None)
        return True

    def on_release(self):
        pass

    def _managed_method(self, name):
        candidates = [
            method
            for method in type(self)._resolved_jmethods().values()
            if method.java_name == name and method.implementation == "python"
        ]
        if not candidates:
            return None

        def dispatcher(*args):
            selected = _select_method_overload(candidates, args)
            if selected is None:
                java_instance = _java_of(self)
                if java_instance is None:
                    raise AttributeError(name)
                return getattr(java_instance, name)(*args)

            attr = _find_class_attr(type(self), selected.python_name)
            return _bind_attr(self, attr)(*args)

        return dispatcher

    @classmethod
    def _resolved_jmethods(cls):
        if cls.__jresolved_methods__ is None:
            cls.__jresolved_methods__ = _collect_method_metadata(cls)
        return cls.__jresolved_methods__

    @classmethod
    def _resolved_jconstructors(cls):
        if cls.__jresolved_constructors__ is None:
            cls.__jresolved_constructors__ = _collect_constructor_metadata(cls)
        return cls.__jresolved_constructors__

    @classmethod
    def _resolved_jfields(cls):
        if cls.__jresolved_fields__ is None:
            cls.__jresolved_fields__ = _collect_field_metadata(cls)
        return cls.__jresolved_fields__

    @classmethod
    def _method_dispatch(cls):
        if cls.__jmethod_dispatch__ is None:
            cls.__jmethod_dispatch__ = _build_method_dispatch(cls)
        return cls.__jmethod_dispatch__

    @classmethod
    def _constructor_dispatch(cls):
        if cls.__jconstructor_dispatch__ is None:
            cls.__jconstructor_dispatch__ = _build_constructor_dispatch(cls)
        return cls.__jconstructor_dispatch__

    @classmethod
    def _class_build_hooks(cls):
        if cls.__jclass_build_hooks_cache__ is None:
            cls.__jclass_build_hooks_cache__ = [hook.python_name for hook in _collect_class_build_metadata(cls)]
        return cls.__jclass_build_hooks_cache__

    @classmethod
    def _resolve_method_handler(cls, raw_name, simple_name):
        dispatch = cls._method_dispatch()
        return (
            dispatch["exact"].get(raw_name)
            or dispatch["wildcard"].get(simple_name)
            or (simple_name if _find_class_attr(cls, simple_name) is not None else None)
        )

    @classmethod
    def _resolve_constructor_handler(cls, phase, raw_name):
        dispatch = cls._constructor_dispatch()[phase]
        return dispatch["exact"].get(raw_name) or dispatch["wildcard"]

    @classmethod
    def _call_constructor_handler(cls, phase, raw_name, args, *, target):
        handler_name = cls._resolve_constructor_handler(phase, raw_name)
        if handler_name is None:
            return False, None

        attr = _find_class_attr(cls, handler_name)
        return True, _bind_attr(target, attr)(*args)

    @classmethod
    def extends(cls, java_class, *interfaces, methods=None, constructors=None, custom_name=None):
        def decorator(pyclass):
            if not isinstance(java_class, JavaClass):
                raise ValueError("java_class must be a JavaClass type")
            for interface in interfaces:
                if not isinstance(interface, JavaClass):
                    raise ValueError("interfaces must be JavaClass types")
            if not issubclass(pyclass, Base):
                raise TypeError("Managed proxies must inherit from Base")

            pyclass.__jbaseclass__ = java_class
            pyclass.__jinterfaces__ = tuple(interfaces or ())
            pyclass.__jlegacy_methods__ = tuple(methods or ())
            pyclass.__jlegacy_constructors__ = tuple(constructors or ())
            pyclass.__jproxy_custom_name__ = _normalize_proxy_custom_name(custom_name) or pyclass.__name__
            pyclass.__jproxy_class__ = None
            pyclass.__jproxy_interceptor__ = None
            pyclass.__jresolved_methods__ = None
            pyclass.__jresolved_constructors__ = None
            pyclass.__jresolved_fields__ = None
            pyclass.__jmethod_dispatch__ = None
            pyclass.__jconstructor_dispatch__ = None
            pyclass.__jclass_build_hooks_cache__ = None
            pyclass.__jowner__ = _plugin_id_of_module(pyclass.__module__)
            _install_super_bridge(pyclass)
            return pyclass

        return decorator

    @classmethod
    def bind(cls, java_class, *interfaces, methods=None, constructors=None, custom_name=None):
        return cls.extends(
            java_class,
            *interfaces,
            methods=methods,
            constructors=constructors,
            custom_name=custom_name,
        )(cls)

    @classmethod
    def java_class(cls):
        if cls.__jbaseclass__ is None:
            raise ValueError(f"{cls.__name__} is not bound to a Java class")

        if cls.__jproxy_class__ is not None:
            return cls.__jproxy_class__

        method_specs = _build_method_specs(cls)
        field_specs = _build_field_specs(cls)
        if field_specs is None:
            field_specs = ArrayList()
        field_specs.add(CP.FieldSpec(_PYTHON_PEER_FIELD_NAME, jclass("java.lang.Object"), Modifier.PUBLIC))
        interceptor = _ManagedClassInterceptor(cls)
        class_build_hook = _ManagedClassBuildHook(cls) if cls._class_build_hooks() else None

        args = [
            cls.__jbaseclass__,
            interceptor,
            class_build_hook,
            _to_java_list(cls.__jinterfaces__),
            _to_java_list(cls.__jlegacy_methods__),
            _to_java_list(cls.__jlegacy_constructors__),
            method_specs,
            field_specs,
        ]
        if X.BUILD_VERSION >= 66690:
            args.append(_normalize_proxy_custom_name(cls.__jproxy_custom_name__) or cls.__name__)

        cls.__jproxy_class__ = CP.createProxyClass(*args)
        cls.__jproxy_interceptor__ = interceptor
        _interceptors.append(interceptor)
        return cls.__jproxy_class__

    @classmethod
    def _managed_interceptor(cls):
        cls.java_class()
        interceptor = cls.__jproxy_interceptor__
        if interceptor is None:
            raise RuntimeError(f"{cls.__name__} does not have an active managed interceptor")
        return interceptor

    @classmethod
    def from_java(cls, instance, *, init_args=None):
        if instance is None:
            return None
        interceptor = cls._managed_interceptor()
        peer = interceptor._get_or_create_peer(instance)
        if peer is not None and not peer.__dict__.get("_jinitialized", False):
            interceptor._initialize_peer(peer, None, [], init_args=init_args)
        return peer

    @classmethod
    def _instantiate_java(cls, *args):
        proxy_class = cls.java_class()
        if not args:
            return proxy_class.newInstance(), _build_constructor_signature(())

        last_error = None
        for constructor in proxy_class.getConstructors():
            parameter_types = constructor.getParameterTypes()
            if len(parameter_types) != len(args):
                continue

            constructor_signature = _build_constructor_signature(parameter_types)

            for invocation in (
                lambda: constructor.newInstance(*args),
                lambda: constructor.newInstance(args),
                lambda: constructor.newInstance(list(args)),
            ):
                try:
                    return invocation(), constructor_signature
                except Exception as exc:
                    from android_utils import log
                    log(f"{exc}")
                    last_error = exc

        error_suffix = f": {last_error}" if last_error is not None else ""
        raise TypeError(f"No matching constructor found for {cls.__name__} with {len(args)} arguments{error_suffix}")

    @classmethod
    def new_java_instance(cls, *args):
        java_instance, _ = cls._instantiate_java(*args)
        return java_instance

    @classmethod
    def new_instance(cls, *args, init_args=None):
        interceptor = cls._managed_interceptor()
        init_token = interceptor.push_pending_init_args(init_args) if init_args is not None else None

        try:
            java_instance, constructor_signature = cls._instantiate_java(*args)
        except Exception:
            if init_token is not None:
                interceptor.cancel_pending_init_args(init_token)
            raise

        return interceptor.attach_peer(java_instance, raw_name=constructor_signature, args=args)


joverride = JHelper.Override
joverload = JHelper.Overload
jmethod = JHelper.Method
jfield = JHelper.Field
jconstructor = JHelper.Constructor
jpreconstructor = JHelper.PreConstructor
jgetmethod = JHelper.GetMethod
jsetmethod = JHelper.SetMethod
jmvelmethod = JHelper.MVELMethod
jmveloverride = JHelper.MVELOverride
jMVELmethod = JHelper.MVELMethod
jMVELoverride = JHelper.MVELOverride
jclassbuilder = JHelper.ClassBuilder
java_subclass = Base.extends


@java_subclass(Object)
class PyObj(Base):
    obj = jfield("com.chaquo.python.PyObject")

    @classmethod
    def create(cls, obj):
        instance = cls.new_java_instance()
        instance.obj = obj
        return instance
