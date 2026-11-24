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
if _sdk_version.X.BUILD_VERSION < 66999:
    raise Exception(f"Intents Manager works only on app which version >= 66999 (12.6.4). Current version: {_sdk_version.X.BUILD_VERSION}")

import inspect
import re
import secrets
import traceback
from contextlib import suppress
from dataclasses import dataclass, field
from typing import Callable, Optional
from urllib.parse import parse_qsl

from extera_utils.classes import java_subclass, Base, jfield, jclassbuilder, jsetmethod
from com.android.dx import TypeId, Label, Comparison, BinaryOp
from com.chaquo.python import PyObject
from java import jarray, jclass
from java.lang.reflect import Modifier
from java.lang import Object, String, Integer, Boolean
from java.util import ArrayList
from java.util.concurrent import ConcurrentHashMap
from android.content import Intent
from android.net import Uri

from android_utils import log


_PATH_ARG_RE = re.compile(r"\{([A-Za-z_][A-Za-z0-9_]*)}")


class IntentsManager:
    _AS = ["tg", "https", "http"]

    __handlers: dict[str, "IntentsManager._HandlerInfo"] = {}
    __hooks: list = []
    __jc = None
    __jfilter_cls = None

    @dataclass
    class HandlerInfo:
        callback: Callable
        scheme: Optional[str] = field(default=None)
        host: Optional[str] = field(default=None)
        path: Optional[str] = field(default=None)
        required_path_args_names: Optional[list[str]] = field(default=None)
        priority: Optional[int] = field(default=0)
        action: Optional[str] = field(default=None)
        whitelist_flags: Optional[list[int]] = field(default=None)
        blacklist_flags: Optional[list[int]] = field(default=None)
        type: Optional[str] = field(default=None)
        categories: Optional[list[str]] = field(default=None)

    @dataclass
    class _HandlerInfo:
        handler_id: str
        callback: Callable = field(compare=False, repr=False)
        hook_type: str
        priority: int = 0
        scheme: Optional[str] = None
        host: Optional[str] = None
        path: Optional[str] = None
        path_regex: Optional[str] = None
        required_path_args_names: tuple[str, ...] = field(default_factory=tuple)
        action: Optional[str] = None
        whitelist_flags: tuple[int, ...] = field(default_factory=tuple)
        blacklist_flags: tuple[int, ...] = field(default_factory=tuple)
        type: Optional[str] = None
        categories: tuple[str, ...] = field(default_factory=tuple)

    @dataclass
    class HandlerHandle:
        handler_id: str

        def unhandle(self):
            IntentsManager.unhandle(self.handler_id)

    class HandlerNotRegistered(Exception):
        def __init__(self, handler_id: str):
            super().__init__(f"Handler '{handler_id}' is not registered.")

    @staticmethod
    def parse(url: str) -> dict[str, str]:
        s1 = url.split("://", 1)
        if "://" not in url or len(s1) == 1:
            raise ValueError(f"Invalid uri: {url}")

        s2 = s1[1].split("/", 1)
        return {
            "scheme": s1[0],
            "host": s2[0],
        } | (
            {
                "path": s2[1]
            } if len(s2) > 1 else {}
        ) | (
            {
                "query": s1[1].split("?", 1)[1]
            } if "?" in s1[1] else {}
        )

    @classmethod
    def __normalize_path(cls, path: Optional[str]) -> Optional[str]:
        if path is None:
            return None

        path = str(path).strip()
        if not path:
            return None

        if not path.startswith("/"):
            path = "/" + path
        return path

    @classmethod
    def __build_path_regex(cls, path: Optional[str]) -> tuple[Optional[str], tuple[str, ...]]:
        if path is None:
            return None, ()

        result = []
        names = []
        last_index = 0
        for match in _PATH_ARG_RE.finditer(path):
            result.append(re.escape(path[last_index:match.start()]))
            result.append(r"[^/]+")
            names.append(match.group(1))
            last_index = match.end()

        result.append(re.escape(path[last_index:]))
        return "^" + "".join(result) + "$", tuple(names)

    @classmethod
    def __normalize_required_path_args_names(
        cls,
        required_path_args_names: Optional[list[str]],
    ) -> tuple[str, ...]:
        if required_path_args_names is None:
            return ()

        normalized = []
        for name in required_path_args_names:
            current = str(name).strip()
            if not current:
                continue
            if current not in normalized:
                normalized.append(current)

        return tuple(normalized)

    @classmethod
    def __normalize_flags(cls, flags: Optional[list[int]]) -> tuple[int, ...]:
        if flags is None:
            return ()

        normalized = []
        for flag in flags:
            current = int(flag)
            if current not in normalized:
                normalized.append(current)
        return tuple(normalized)

    @classmethod
    def __normalize_categories(cls, categories: Optional[list[str]]) -> tuple[str, ...]:
        if categories is None:
            return ()

        normalized = []
        for category in categories:
            current = str(category).strip()
            if current and current not in normalized:
                normalized.append(current)
        return tuple(normalized)

    @classmethod
    def __make_handler_info(
        cls,
        callback: Callable,
        *,
        hook_type: str,
        scheme: Optional[str] = None,
        host: Optional[str] = None,
        path: Optional[str] = None,
        required_path_args_names: Optional[list[str]] = None,
        action: Optional[str] = None,
        priority: Optional[int] = 0,
        whitelist_flags: Optional[list[int]] = None,
        blacklist_flags: Optional[list[int]] = None,
        type: Optional[str] = None,
        categories: Optional[list[str]] = None,
    ) -> "IntentsManager._HandlerInfo":
        normalized_path = cls.__normalize_path(path)
        path_regex, _ = cls.__build_path_regex(normalized_path)
        required_names = cls.__normalize_required_path_args_names(required_path_args_names)
        normalized_whitelist_flags = cls.__normalize_flags(whitelist_flags)
        normalized_blacklist_flags = cls.__normalize_flags(blacklist_flags)
        normalized_categories = cls.__normalize_categories(categories)

        return cls._HandlerInfo(
            handler_id=secrets.token_hex(16),
            callback=callback,
            hook_type=hook_type,
            priority=0 if priority is None else int(priority),
            scheme=None if scheme is None else str(scheme).strip().lower(),
            host=None if host is None else str(host).strip().lower(),
            path=normalized_path,
            path_regex=path_regex,
            required_path_args_names=required_names,
            action=None if action is None else str(action).strip(),
            whitelist_flags=normalized_whitelist_flags,
            blacklist_flags=normalized_blacklist_flags,
            type=None if type is None else str(type).strip(),
            categories=normalized_categories,
        )

    @classmethod
    def __extract_query_args(cls, data: Optional[Uri]) -> dict[str, str]:
        if data is None:
            return {}

        try:
            query = data.getQuery()
        except Exception:
            query = None

        if not query:
            return {}

        result = {}
        for key, value in parse_qsl(str(query), keep_blank_values=True):
            if key:
                result[key] = value
        return result

    @classmethod
    def __extract_path_args(cls, path_template: Optional[str], actual_path: Optional[str]) -> dict[str, str]:
        if not path_template or not actual_path:
            return {}

        template_parts = [part for part in path_template.split("/") if part]
        actual_parts = [part for part in str(actual_path).split("/") if part]
        if len(template_parts) != len(actual_parts):
            return {}

        result = {}
        for template_part, actual_part in zip(template_parts, actual_parts):
            match = _PATH_ARG_RE.fullmatch(template_part)
            if match:
                result[match.group(1)] = actual_part
            elif template_part != actual_part:
                return {}
        return result

    @classmethod
    def __build_callback_context(cls, info: "IntentsManager._HandlerInfo", intent: Intent) -> tuple[dict, list]:
        data = None
        with suppress(Exception):
            data = intent.getData()

        scheme = None
        host = None
        path = None
        with suppress(Exception):
            if data is not None:
                scheme = data.getScheme()
                host = data.getHost()
                path = data.getPath()

        action = None
        with suppress(Exception):
            action = intent.getAction()

        flags = 0
        with suppress(Exception):
            flags = int(intent.getFlags())

        type_value = None
        with suppress(Exception):
            type_value = intent.getType()

        categories = set()
        with suppress(Exception):
            raw_categories = intent.getCategories()
            if raw_categories is not None:
                categories = {str(category) for category in raw_categories if category is not None}

        query_args = cls.__extract_query_args(data)
        path_args = cls.__extract_path_args(info.path, path)

        named_context = {
            "intent": intent,
            "scheme": scheme,
            "host": host,
            "path": path,
            "query_args": query_args,
            "action": action,
            "flags": flags,
            "type": type_value,
            "categories": categories,
        }
        dynamic_context = {**path_args, **query_args}
        standard_args = [intent, scheme, host, path, query_args, action, flags, type_value, categories]
        return (dynamic_context | named_context), standard_args

    @classmethod
    def __invoke_handler_callback(cls, info: "IntentsManager._HandlerInfo", intent: Intent):
        callback = info.callback
        available, standard_args = cls.__build_callback_context(info, intent)
        signature = inspect.signature(callback)

        args = []
        kwargs = {}
        consumed_standard = set()
        bound_names = set()
        has_var_args = False
        has_var_kwargs = False

        for parameter in signature.parameters.values():
            if parameter.kind == inspect.Parameter.VAR_POSITIONAL:
                has_var_args = True
                continue
            if parameter.kind == inspect.Parameter.VAR_KEYWORD:
                has_var_kwargs = True
                continue

            if parameter.name not in available:
                continue

            value = available[parameter.name]
            bound_names.add(parameter.name)
            if parameter.name in ("intent", "scheme", "host", "path", "query_args", "action", "flags", "type", "categories"):
                consumed_standard.add(parameter.name)

            if parameter.kind in (
                inspect.Parameter.POSITIONAL_ONLY,
                inspect.Parameter.POSITIONAL_OR_KEYWORD,
            ):
                args.append(value)
            elif parameter.kind == inspect.Parameter.KEYWORD_ONLY:
                kwargs[parameter.name] = value

        if has_var_args:
            for name, value in zip(
                ("intent", "scheme", "host", "path", "query_args", "action", "flags", "type", "categories"),
                standard_args,
            ):
                if name not in consumed_standard:
                    args.append(value)

        if has_var_kwargs:
            for key, value in available.items():
                if key not in kwargs and key not in bound_names:
                    kwargs[key] = value

        return callback(*args, **kwargs)

    @classmethod
    def __dispatch_matched_ids(cls, matched_ids, hook_type: str, intent: Intent):
        try:
            ordered = sorted(
                [
                    cls.__handlers[handler_id]
                    for handler_id in list(matched_ids.toArray())
                    if handler_id in cls.__handlers and cls.__handlers[handler_id].hook_type == hook_type
                ],
                key=lambda info: info.priority,
            )

            for info in ordered:
                result = cls.__invoke_handler_callback(info, intent)
                if hook_type == "before" and result is True:
                    return True
        except Exception:
            log(f"Failed to dispatch intent handlers: {traceback.format_exc()}")
        return False

    @classmethod
    def __ghc(cls):
        if cls.__jc is not None:
            return cls.__jc

        from de.robv.android.xposed import XC_MethodHook

        pyobject_type = TypeId.get(PyObject.getClass())
        object_array_type = TypeId.get(jarray(Object))
        string_array_type = TypeId.get(jarray(String))
        int_box_type = TypeId.get(Integer.getClass())
        int_array_type = TypeId.get(jarray(Integer))
        bool_box_type = TypeId.get(Boolean.getClass())
        method_param_type = TypeId.get(jclass("de.robv.android.xposed.XC_MethodHook$MethodHookParam"))
        string_type = TypeId.STRING
        bool_type = TypeId.BOOLEAN
        intent_type = TypeId.get(Intent.getClass())
        uri_type = TypeId.get(Uri.getClass())
        map_type = TypeId.get(ConcurrentHashMap.getClass())
        array_list_type = TypeId.get(ArrayList.getClass())
        set_type = TypeId.get(jclass("java.util.Set"))
        iterator_type = TypeId.get(jclass("java.util.Iterator"))
        entry_type = TypeId.get(jclass("java.util.Map$Entry"))

        param_args_field = method_param_type.getField(object_array_type, "args")
        param_set_result = method_param_type.getMethod(TypeId.VOID, "setResult", TypeId.OBJECT)
        pyobject_call = pyobject_type.getMethod(pyobject_type, "call", object_array_type)
        pyobject_to_boolean = pyobject_type.getMethod(bool_type, "toBoolean")
        string_equals = string_type.getMethod(bool_type, "equals", TypeId.OBJECT)
        string_matches = string_type.getMethod(bool_type, "matches", string_type)
        intent_get_action = intent_type.getMethod(string_type, "getAction")
        intent_get_data = intent_type.getMethod(uri_type, "getData")
        intent_get_flags = intent_type.getMethod(TypeId.INT, "getFlags")
        intent_get_type = intent_type.getMethod(string_type, "getType")
        intent_get_categories = intent_type.getMethod(set_type, "getCategories")
        uri_get_scheme = uri_type.getMethod(string_type, "getScheme")
        uri_get_host = uri_type.getMethod(string_type, "getHost")
        uri_get_path = uri_type.getMethod(string_type, "getPath")
        uri_get_query_parameter = uri_type.getMethod(string_type, "getQueryParameter", string_type)
        map_entry_set = map_type.getMethod(set_type, "entrySet")
        set_iterator = set_type.getMethod(iterator_type, "iterator")
        iterator_has_next = iterator_type.getMethod(bool_type, "hasNext")
        iterator_next = iterator_type.getMethod(TypeId.OBJECT, "next")
        entry_get_key = entry_type.getMethod(TypeId.OBJECT, "getKey")
        entry_get_value = entry_type.getMethod(TypeId.OBJECT, "getValue")
        array_list_ctor = array_list_type.getConstructor()
        array_list_add = array_list_type.getMethod(bool_type, "add", TypeId.OBJECT)
        array_list_size = array_list_type.getMethod(TypeId.INT, "size")
        set_contains = set_type.getMethod(bool_type, "contains", TypeId.OBJECT)
        int_int_value = int_box_type.getMethod(TypeId.INT, "intValue")
        bool_value_of = bool_box_type.getMethod(bool_box_type, "valueOf", bool_type)

        def _py_dispatch(matched_ids, hook_type, intent):
            return cls.__dispatch_matched_ids(matched_ids, hook_type, intent)

        @java_subclass(Object, custom_name="IntentFilterRecord")
        class _JIntentFilter(Base):
            hookType = jfield(String.getClass())
            scheme = jfield(String.getClass())
            host = jfield(String.getClass())
            path = jfield(String.getClass())
            pathRegex = jfield(String.getClass())
            action = jfield(String.getClass())
            requiredPathArgNames = jfield(jarray(String))
            whitelistFlags = jfield(jarray(Integer))
            blacklistFlags = jfield(jarray(Integer))
            type = jfield(String.getClass())
            categories = jfield(jarray(String))

        filter_type = TypeId.get(_JIntentFilter.java_class())
        filter_hook_type_field = filter_type.getField(string_type, "hookType")
        filter_scheme_field = filter_type.getField(string_type, "scheme")
        filter_host_field = filter_type.getField(string_type, "host")
        filter_path_field = filter_type.getField(string_type, "path")
        filter_path_regex_field = filter_type.getField(string_type, "pathRegex")
        filter_action_field = filter_type.getField(string_type, "action")
        filter_required_path_args_names_field = filter_type.getField(string_array_type, "requiredPathArgNames")
        filter_whitelist_flags_field = filter_type.getField(int_array_type, "whitelistFlags")
        filter_blacklist_flags_field = filter_type.getField(int_array_type, "blacklistFlags")
        filter_type_field = filter_type.getField(string_type, "type")
        filter_categories_field = filter_type.getField(string_array_type, "categories")

        @java_subclass(XC_MethodHook, custom_name="IntentHandleHook")
        class _JIntentHook(Base):
            filters = jfield(ConcurrentHashMap.getClass())
            _ex = jfield(PyObject.getClass(), default=None, methods=[jsetmethod("setEx")])

            @staticmethod
            def _emit_optional_string_check(
                code,
                filter_field,
                filter_ref,
                current_filter_value,
                actual_value,
                equals_result,
                skip_check,
                fail_label,
                source_ref,
                getter_method,
            ):
                code.iget(filter_field, current_filter_value, filter_ref)
                code.compareZ(Comparison.EQ, skip_check, current_filter_value)
                code.compareZ(Comparison.EQ, fail_label, source_ref)
                code.invokeVirtual(getter_method, actual_value, source_ref)
                code.compareZ(Comparison.EQ, fail_label, actual_value)
                code.invokeVirtual(string_equals, equals_result, current_filter_value, actual_value)
                code.compareZ(Comparison.EQ, fail_label, equals_result)
                code.mark(skip_check)

            @staticmethod
            def _emit_optional_path_check(
                code,
                filter_ref,
                path_regex_ref,
                current_filter_value,
                actual_path,
                equals_result,
                skip_check,
                fail_label,
                data_ref,
            ):
                code.iget(filter_path_regex_field, current_filter_value, filter_ref)
                code.compareZ(Comparison.EQ, skip_check, current_filter_value)
                code.compareZ(Comparison.EQ, fail_label, data_ref)
                code.invokeVirtual(uri_get_path, actual_path, data_ref)
                code.compareZ(Comparison.EQ, fail_label, actual_path)
                code.iget(filter_path_field, path_regex_ref, filter_ref)
                code.invokeVirtual(string_matches, equals_result, actual_path, current_filter_value)
                code.compareZ(Comparison.EQ, fail_label, equals_result)
                code.mark(skip_check)

            @staticmethod
            def _emit_required_query_args_check(
                code,
                filter_ref,
                required_names_ref,
                query_args_count,
                query_args_idx,
                query_arg_name,
                query_arg_value,
                data_ref,
                fail_label,
                loop_label,
                after_loop_label,
                next_idx,
                one,
            ):
                code.iget(filter_required_path_args_names_field, required_names_ref, filter_ref)
                code.compareZ(Comparison.EQ, after_loop_label, required_names_ref)
                code.compareZ(Comparison.EQ, fail_label, data_ref)
                code.arrayLength(query_args_count, required_names_ref)
                code.compare(Comparison.EQ, after_loop_label, query_args_count, query_args_idx)
                code.mark(loop_label)
                code.compare(Comparison.EQ, after_loop_label, query_args_idx, query_args_count)
                code.aget(query_arg_name, required_names_ref, query_args_idx)
                code.compareZ(Comparison.EQ, fail_label, query_arg_name)
                code.invokeVirtual(uri_get_query_parameter, query_arg_value, data_ref, query_arg_name)
                code.compareZ(Comparison.EQ, fail_label, query_arg_value)
                code.op(BinaryOp.ADD, next_idx, query_args_idx, one)
                code.move(query_args_idx, next_idx)
                code.jump(loop_label)
                code.mark(after_loop_label)

            @staticmethod
            def _emit_required_categories_check(
                code,
                filter_ref,
                required_categories_ref,
                categories_count,
                categories_idx,
                category_name,
                categories_set_ref,
                contains_result,
                fail_label,
                loop_label,
                after_loop_label,
                next_idx,
                one,
                zero,
            ):
                code.iget(filter_categories_field, required_categories_ref, filter_ref)
                code.compareZ(Comparison.EQ, after_loop_label, required_categories_ref)
                code.arrayLength(categories_count, required_categories_ref)
                code.compare(Comparison.EQ, after_loop_label, categories_count, zero)
                code.compareZ(Comparison.EQ, fail_label, categories_set_ref)
                code.mark(loop_label)
                code.compare(Comparison.EQ, after_loop_label, categories_idx, categories_count)
                code.aget(category_name, required_categories_ref, categories_idx)
                code.compareZ(Comparison.EQ, fail_label, category_name)
                code.invokeInterface(set_contains, contains_result, categories_set_ref, category_name)
                code.compareZ(Comparison.EQ, fail_label, contains_result)
                code.op(BinaryOp.ADD, next_idx, categories_idx, one)
                code.move(categories_idx, next_idx)
                code.jump(loop_label)
                code.mark(after_loop_label)

            @staticmethod
            def _emit_flags_check(
                code,
                flags_field,
                filter_ref,
                flags_array_ref,
                flags_count,
                flags_idx,
                flag_obj,
                flag_box,
                flag_int,
                masked_flags,
                intent_flags,
                fail_label,
                loop_label,
                after_loop_label,
                next_idx,
                one,
                zero,
                require_present,
            ):
                code.iget(flags_field, flags_array_ref, filter_ref)
                code.compareZ(Comparison.EQ, after_loop_label, flags_array_ref)
                code.arrayLength(flags_count, flags_array_ref)
                code.compare(Comparison.EQ, after_loop_label, flags_count, zero)
                code.mark(loop_label)
                code.compare(Comparison.EQ, after_loop_label, flags_idx, flags_count)
                code.aget(flag_obj, flags_array_ref, flags_idx)
                code.compareZ(Comparison.EQ, fail_label, flag_obj)
                code.cast(flag_box, flag_obj)
                code.invokeVirtual(int_int_value, flag_int, flag_box)
                code.op(BinaryOp.AND, masked_flags, intent_flags, flag_int)
                if require_present:
                    code.compare(Comparison.NE, fail_label, masked_flags, flag_int)
                else:
                    code.compare(Comparison.NE, fail_label, masked_flags, zero)
                code.op(BinaryOp.ADD, next_idx, flags_idx, one)
                code.move(flags_idx, next_idx)
                code.jump(loop_label)
                code.mark(after_loop_label)

            @staticmethod
            def _gen_dispatch_matches(dex, proxy_type):
                method_id = proxy_type.getMethod(
                    TypeId.BOOLEAN,
                    "dispatchMatches",
                    jarray(TypeId)([method_param_type, string_type]),
                )
                code = dex.declare(method_id, Modifier.PUBLIC)

                this_ref = code.getThis(proxy_type)
                param_ref = code.getParameter(0, method_param_type)
                hook_type_ref = code.getParameter(1, string_type)
                filters_ref = code.newLocal(map_type)
                ex_ref = code.newLocal(pyobject_type)
                args_ref = code.newLocal(object_array_type)
                args_size = code.newLocal(TypeId.INT)
                zero = code.newLocal(TypeId.INT)
                idx = code.newLocal(TypeId.INT)
                intent_obj = code.newLocal(TypeId.OBJECT)
                is_intent = code.newLocal(bool_type)
                intent_ref = code.newLocal(intent_type)
                action_ref = code.newLocal(string_type)
                intent_flags = code.newLocal(TypeId.INT)
                intent_type_ref = code.newLocal(string_type)
                intent_categories_ref = code.newLocal(set_type)
                data_ref = code.newLocal(uri_type)
                matched_ids = code.newLocal(array_list_type)
                added = code.newLocal(bool_type)
                entry_set_ref = code.newLocal(set_type)
                iterator_ref = code.newLocal(iterator_type)
                has_next = code.newLocal(bool_type)
                entry_obj = code.newLocal(TypeId.OBJECT)
                entry_ref = code.newLocal(entry_type)
                id_obj = code.newLocal(TypeId.OBJECT)
                filter_obj = code.newLocal(TypeId.OBJECT)
                id_ref = code.newLocal(string_type)
                filter_ref = code.newLocal(filter_type)
                filter_hook_type = code.newLocal(string_type)
                filter_action = code.newLocal(string_type)
                filter_scheme = code.newLocal(string_type)
                filter_host = code.newLocal(string_type)
                filter_path = code.newLocal(string_type)
                filter_path_regex = code.newLocal(string_type)
                filter_required_path_args_names = code.newLocal(string_array_type)
                filter_whitelist_flags = code.newLocal(int_array_type)
                filter_blacklist_flags = code.newLocal(int_array_type)
                filter_intent_type = code.newLocal(string_type)
                filter_categories = code.newLocal(string_array_type)
                query_args_count = code.newLocal(TypeId.INT)
                query_args_idx = code.newLocal(TypeId.INT)
                next_query_args_idx = code.newLocal(TypeId.INT)
                categories_count = code.newLocal(TypeId.INT)
                categories_idx = code.newLocal(TypeId.INT)
                next_categories_idx = code.newLocal(TypeId.INT)
                flags_count = code.newLocal(TypeId.INT)
                flags_idx = code.newLocal(TypeId.INT)
                next_flags_idx = code.newLocal(TypeId.INT)
                one = code.newLocal(TypeId.INT)
                flag_obj = code.newLocal(TypeId.OBJECT)
                flag_box = code.newLocal(int_box_type)
                flag_int = code.newLocal(TypeId.INT)
                masked_flags = code.newLocal(TypeId.INT)
                query_arg_name = code.newLocal(string_type)
                query_arg_value = code.newLocal(string_type)
                category_name = code.newLocal(string_type)
                actual_value = code.newLocal(string_type)
                actual_path = code.newLocal(string_type)
                equals_result = code.newLocal(bool_type)
                py_result = code.newLocal(pyobject_type)
                stop_requested = code.newLocal(bool_type)
                call_args = code.newLocal(object_array_type)
                call_size = code.newLocal(TypeId.INT)
                end = Label()
                loop = Label()
                after_loop = Label()
                next_filter = Label()
                skip_action = Label()
                skip_scheme = Label()
                skip_host = Label()
                skip_type = Label()
                skip_path = Label()
                query_args_loop = Label()
                after_query_args_loop = Label()
                categories_loop = Label()
                after_categories_loop = Label()
                whitelist_flags_loop = Label()
                after_whitelist_flags_loop = Label()
                blacklist_flags_loop = Label()
                after_blacklist_flags_loop = Label()

                code.loadConstant(zero, 0)
                code.loadConstant(one, 1)
                code.loadConstant(stop_requested, False)
                code.iget(proxy_type.getField(pyobject_type, "_ex"), ex_ref, this_ref)
                code.compareZ(Comparison.EQ, end, ex_ref)
                code.iget(proxy_type.getField(map_type, "filters"), filters_ref, this_ref)
                code.compareZ(Comparison.EQ, end, filters_ref)
                code.iget(param_args_field, args_ref, param_ref)
                code.compareZ(Comparison.EQ, end, args_ref)
                code.arrayLength(args_size, args_ref)
                code.compare(Comparison.EQ, end, args_size, zero)
                code.aget(intent_obj, args_ref, zero)
                code.compareZ(Comparison.EQ, end, intent_obj)
                code.instanceOfType(is_intent, intent_obj, intent_type)
                code.compareZ(Comparison.EQ, end, is_intent)
                code.cast(intent_ref, intent_obj)
                code.invokeVirtual(intent_get_action, action_ref, intent_ref)
                code.invokeVirtual(intent_get_flags, intent_flags, intent_ref)
                code.invokeVirtual(intent_get_type, intent_type_ref, intent_ref)
                code.invokeVirtual(intent_get_categories, intent_categories_ref, intent_ref)
                code.invokeVirtual(intent_get_data, data_ref, intent_ref)
                code.newInstance(matched_ids, array_list_ctor)
                code.invokeVirtual(map_entry_set, entry_set_ref, filters_ref)
                code.invokeInterface(set_iterator, iterator_ref, entry_set_ref)

                code.mark(loop)
                code.invokeInterface(iterator_has_next, has_next, iterator_ref)
                code.compareZ(Comparison.EQ, after_loop, has_next)
                code.invokeInterface(iterator_next, entry_obj, iterator_ref)
                code.cast(entry_ref, entry_obj)
                code.invokeInterface(entry_get_key, id_obj, entry_ref)
                code.cast(id_ref, id_obj)
                code.invokeInterface(entry_get_value, filter_obj, entry_ref)
                code.compareZ(Comparison.EQ, next_filter, filter_obj)
                code.cast(filter_ref, filter_obj)
                code.iget(filter_required_path_args_names_field, filter_required_path_args_names, filter_ref)
                code.iget(filter_hook_type_field, filter_hook_type, filter_ref)
                code.invokeVirtual(string_equals, equals_result, hook_type_ref, filter_hook_type)
                code.compareZ(Comparison.EQ, next_filter, equals_result)

                code.iget(filter_action_field, filter_action, filter_ref)
                code.compareZ(Comparison.EQ, skip_action, filter_action)
                code.compareZ(Comparison.EQ, next_filter, action_ref)
                code.invokeVirtual(string_equals, equals_result, filter_action, action_ref)
                code.compareZ(Comparison.EQ, next_filter, equals_result)
                code.mark(skip_action)

                _JIntentHook._emit_optional_string_check(
                    code,
                    filter_scheme_field,
                    filter_ref,
                    filter_scheme,
                    actual_value,
                    equals_result,
                    skip_scheme,
                    next_filter,
                    data_ref,
                    uri_get_scheme,
                )
                _JIntentHook._emit_optional_string_check(
                    code,
                    filter_host_field,
                    filter_ref,
                    filter_host,
                    actual_value,
                    equals_result,
                    skip_host,
                    next_filter,
                    data_ref,
                    uri_get_host,
                )
                _JIntentHook._emit_optional_string_check(
                    code,
                    filter_type_field,
                    filter_ref,
                    filter_intent_type,
                    actual_value,
                    equals_result,
                    skip_type,
                    next_filter,
                    intent_ref,
                    intent_get_type,
                )
                _JIntentHook._emit_optional_path_check(
                    code,
                    filter_ref,
                    filter_path,
                    filter_path_regex,
                    actual_path,
                    equals_result,
                    skip_path,
                    next_filter,
                    data_ref,
                )
                code.loadConstant(query_args_idx, 0)
                _JIntentHook._emit_required_query_args_check(
                    code,
                    filter_ref,
                    filter_required_path_args_names,
                    query_args_count,
                    query_args_idx,
                    query_arg_name,
                    query_arg_value,
                    data_ref,
                    next_filter,
                    query_args_loop,
                    after_query_args_loop,
                    next_query_args_idx,
                    one,
                )
                code.loadConstant(categories_idx, 0)
                _JIntentHook._emit_required_categories_check(
                    code,
                    filter_ref,
                    filter_categories,
                    categories_count,
                    categories_idx,
                    category_name,
                    intent_categories_ref,
                    equals_result,
                    next_filter,
                    categories_loop,
                    after_categories_loop,
                    next_categories_idx,
                    one,
                    zero,
                )
                code.loadConstant(flags_idx, 0)
                _JIntentHook._emit_flags_check(
                    code,
                    filter_whitelist_flags_field,
                    filter_ref,
                    filter_whitelist_flags,
                    flags_count,
                    flags_idx,
                    flag_obj,
                    flag_box,
                    flag_int,
                    masked_flags,
                    intent_flags,
                    next_filter,
                    whitelist_flags_loop,
                    after_whitelist_flags_loop,
                    next_flags_idx,
                    one,
                    zero,
                    True,
                )
                code.loadConstant(flags_idx, 0)
                _JIntentHook._emit_flags_check(
                    code,
                    filter_blacklist_flags_field,
                    filter_ref,
                    filter_blacklist_flags,
                    flags_count,
                    flags_idx,
                    flag_obj,
                    flag_box,
                    flag_int,
                    masked_flags,
                    intent_flags,
                    next_filter,
                    blacklist_flags_loop,
                    after_blacklist_flags_loop,
                    next_flags_idx,
                    one,
                    zero,
                    False,
                )

                code.invokeVirtual(array_list_add, added, matched_ids, id_ref)
                code.mark(next_filter)
                code.jump(loop)

                code.mark(after_loop)
                code.invokeVirtual(array_list_size, args_size, matched_ids)
                code.compare(Comparison.EQ, end, args_size, zero)
                code.loadConstant(call_size, 3)
                code.newArray(call_args, call_size)
                code.loadConstant(idx, 0)
                code.aput(call_args, idx, matched_ids)
                code.loadConstant(idx, 1)
                code.aput(call_args, idx, hook_type_ref)
                code.loadConstant(idx, 2)
                code.aput(call_args, idx, intent_ref)
                code.invokeVirtual(pyobject_call, py_result, ex_ref, call_args)
                code.invokeVirtual(pyobject_to_boolean, stop_requested, py_result)
                code.mark(end)
                code.returnValue(stop_requested)

            @jclassbuilder()
            @staticmethod
            def create_methods(dex, proxy_type, _, __):
                _JIntentHook._gen_dispatch_matches(dex, proxy_type)

                dispatch_matches = proxy_type.getMethod(
                    TypeId.BOOLEAN,
                    "dispatchMatches",
                    jarray(TypeId)([method_param_type, string_type]),
                )

                before_hooked_method = proxy_type.getMethod(
                    TypeId.VOID,
                    "beforeHookedMethod",
                    jarray(TypeId)([method_param_type]),
                )
                code = dex.declare(before_hooked_method, Modifier.PUBLIC)
                this_ref = code.getThis(proxy_type)
                param_ref = code.getParameter(0, method_param_type)
                before_name = code.newLocal(string_type)
                stop_requested = code.newLocal(bool_type)
                true_obj = code.newLocal(bool_box_type)
                before_end = Label()
                code.loadConstant(before_name, "before")
                code.invokeVirtual(dispatch_matches, stop_requested, this_ref, param_ref, before_name)
                code.compareZ(Comparison.EQ, before_end, stop_requested)
                code.invokeStatic(bool_value_of, true_obj, stop_requested)
                code.invokeVirtual(param_set_result, None, param_ref, true_obj)
                code.mark(before_end)
                code.returnVoid()

                after_hooked_method = proxy_type.getMethod(
                    TypeId.VOID,
                    "afterHookedMethod",
                    jarray(TypeId)([method_param_type]),
                )
                code = dex.declare(after_hooked_method, Modifier.PUBLIC)
                this_ref = code.getThis(proxy_type)
                param_ref = code.getParameter(0, method_param_type)
                after_name = code.newLocal(string_type)
                ignored_stop = code.newLocal(bool_type)
                code.loadConstant(after_name, "after")
                code.invokeVirtual(dispatch_matches, ignored_stop, this_ref, param_ref, after_name)
                code.returnVoid()

        cls.__jfilter_cls = _JIntentFilter
        cls.__jc = _JIntentHook.new_instance()
        cls.__jc.filters = ConcurrentHashMap()
        cls.__jc.setEx(_py_dispatch)
        return cls.__jc

    @classmethod
    def __ih(cls):
        if cls.__hooks:
            return

        from de.robv.android.xposed import XposedBridge
        from com.exteragram.messenger.utils import IntentsController

        hook = cls.__ghc()
        cls.__hooks.append(
            XposedBridge.hookMethod(
                IntentsController.getClass().getDeclaredMethod("handleIntent", Intent.getClass()),
                hook.java,
            )
        )

    @classmethod
    def __install_handler(cls, info: "IntentsManager._HandlerInfo") -> "IntentsManager.HandlerHandle":
        cls.__ih()
        cls.__handlers[info.handler_id] = info

        filter_obj = cls.__jfilter_cls.new_instance()
        filter_obj.hookType = info.hook_type
        filter_obj.scheme = info.scheme
        filter_obj.host = info.host
        filter_obj.path = info.path
        filter_obj.pathRegex = info.path_regex
        filter_obj.action = info.action
        filter_obj.requiredPathArgNames = jarray(String)(list(info.required_path_args_names))
        filter_obj.whitelistFlags = jarray(Integer)([Integer.valueOf(int(flag)) for flag in info.whitelist_flags])
        filter_obj.blacklistFlags = jarray(Integer)([Integer.valueOf(int(flag)) for flag in info.blacklist_flags])
        filter_obj.type = info.type
        filter_obj.categories = jarray(String)(list(info.categories))
        cls.__ghc().filters.put(info.handler_id, filter_obj.java)
        return cls.HandlerHandle(info.handler_id)

    @classmethod
    def new_global_before_handler(
        cls, info: HandlerInfo
    ) -> "IntentsManager.HandlerHandle":
        return cls.__install_handler(
            cls.__make_handler_info(
                info.callback,
                hook_type="before",
                scheme=info.scheme,
                host=info.host,
                path=info.path,
                required_path_args_names=info.required_path_args_names,
                priority=info.priority,
                action=info.action,
                whitelist_flags=info.whitelist_flags,
                blacklist_flags=info.blacklist_flags,
                type=info.type,
                categories=info.categories,
            )
        )

    @classmethod
    def new_global_after_handler(
        cls, info: HandlerInfo
    ) -> "IntentsManager.HandlerHandle":
        return cls.__install_handler(
            cls.__make_handler_info(
                info.callback,
                hook_type="after",
                scheme=info.scheme,
                host=info.host,
                path=info.path,
                required_path_args_names=info.required_path_args_names,
                priority=info.priority,
                action=info.action,
                whitelist_flags=info.whitelist_flags,
                blacklist_flags=info.blacklist_flags,
                type=info.type,
                categories=info.categories,
            )
        )

    @classmethod
    def unhandle(cls, handler_id: str):
        if handler_id not in cls.__handlers:
            raise cls.HandlerNotRegistered(handler_id)

        cls.__handlers.pop(handler_id, None)
        if cls.__jc is not None:
            with suppress(Exception):
                cls.__jc.filters.remove(handler_id)

    @classmethod
    def _stop(cls):
        if cls.__hooks:
            for hook in cls.__hooks:
                with suppress(Exception):
                    hook.unhook()
        cls.__hooks.clear()
        cls.__handlers.clear()
        if cls.__jc is not None:
            with suppress(Exception):
                cls.__jc.filters.clear()
