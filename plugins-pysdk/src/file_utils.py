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

from extera_utils.classes import java_subclass, Base, jfield, jclassbuilder, jsetmethod
from java.util.concurrent import ConcurrentHashMap
from com.android.dx import TypeId, Label, Comparison, BinaryOp
from com.chaquo.python import PyObject
import itertools
import secrets
from contextlib import suppress
from dataclasses import dataclass, field
from enum import Enum
from java import jarray, jclass
from java.lang.reflect import Modifier, Method
from _sdk_version import X
from android.graphics.drawable import Drawable
from java.io import File
from org.telegram.ui.Cells import ChatMessageCell
from org.telegram.ui.Components import MediaActionDrawable

import os
import traceback
from typing import List, Optional, Callable
from org.telegram.messenger import FileLoader, MessageObject, AndroidUtilities
from com.exteragram.messenger.plugins import PluginsController
from android_utils import log
from android.app import Activity
from org.telegram.ui.ActionBar import Theme, BaseFragment
from java.lang import Integer, Object


def get_plugins_dir() -> str:
    plugins_dir = PluginsController.getInstance().pluginsDir
    return plugins_dir.getAbsolutePath()

def get_cache_dir() -> str:
    return FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE).getAbsolutePath()

def get_files_dir() -> str:
    return FileLoader.getDirectory(FileLoader.MEDIA_DIR_FILES).getAbsolutePath()

def get_images_dir() -> str:
    return FileLoader.getDirectory(FileLoader.MEDIA_DIR_IMAGE).getAbsolutePath()

def get_videos_dir() -> str:
    return FileLoader.getDirectory(FileLoader.MEDIA_DIR_VIDEO).getAbsolutePath()

def get_audios_dir() -> str:
    return FileLoader.getDirectory(FileLoader.MEDIA_DIR_AUDIO).getAbsolutePath()

def get_documents_dir() -> str:
    return FileLoader.getDirectory(FileLoader.MEDIA_DIR_DOCUMENT).getAbsolutePath()

def read_file(file_path: str) -> Optional[str]:
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            return f.read()
    except Exception as e:
        log(f"Error reading file {file_path}: {e}\n{traceback.format_exc()}")

def read_file_bytes(file_path: str) -> Optional[bytes]:
    try:
        with open(file_path, 'rb') as f:
            return f.read()
    except Exception as e:
        log(f"Error reading file {file_path}: {e}\n{traceback.format_exc()}")

def write_file(file_path: str, content: str):
    try:
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(content)
    except Exception as e:
        log(f"Error writing to file {file_path}: {e}\n{traceback.format_exc()}")

def write_file_bytes(file_path: str, content: bytes):
    try:
        with open(file_path, 'wb') as f:
            f.write(content)
    except Exception as e:
        log(f"Error writing to file {file_path}: {e}\n{traceback.format_exc()}")

def delete_file(file_path: str) -> bool:
    if os.path.exists(file_path):
        try:
            os.remove(file_path)
            return True
        except OSError as e:
            log(f"Error deleting file {file_path}: {e}\n{traceback.format_exc()}")
            return False
    return False

def ensure_dir_exists(dir_path: str):
    try:
        os.makedirs(dir_path, exist_ok=True)
    except OSError as e:
        log(f"Error creating directory {dir_path}: {e}\n{traceback.format_exc()}")

def list_dir(
    path: str,
    recursive: bool = False,
    include_files: bool = True,
    include_dirs: bool = False,
    extensions: Optional[List[str]] = None
) -> List[str]:
    results = []
    if not os.path.isdir(path):
        log(f"Path is not a directory or does not exist: {path}")
        return results

    if recursive:
        for root, dirs, files in os.walk(path):
            if include_dirs:
                for d in dirs:
                    results.append(os.path.join(root, d))
            if include_files:
                for f in files:
                    if not extensions or any(f.endswith(ext) for ext in extensions):
                        results.append(os.path.join(root, f))
    else:
        for item in os.listdir(path):
            full_path = os.path.join(path, item)
            is_dir = os.path.isdir(full_path)
            if (is_dir and include_dirs) or (not is_dir and include_files):
                if not is_dir and extensions and not any(item.endswith(ext) for ext in extensions):
                    continue
                results.append(full_path)
    return results


class staticproperty:
    def __init__(self, fget):
        self.fget = fget.__get__(None, object) if isinstance(fget, staticmethod) else fget

    def __get__(self, obj, owner):
        return self.fget()


# noinspection PyNestedDecorators
class FilesController:
    __h: list = []
    __config: dict[str, "FilesController.FileInfo"] = {}
    __m: dict[str, str] = {}
    __icon_id = itertools.count(start=101)
    __jc = None

    @staticproperty
    @staticmethod
    def DIRECT_FILE_ICONS() -> bool:
        if X.BUILD_VERSION is None:
            return False
        registry_methods = ("registerFileIcon", "unregisterFileIcon", "clearFileIcons")
        return all(
            hasattr(PluginsController, method_name)
            for method_name in registry_methods
        )

    @staticproperty
    @staticmethod
    def SUPPORT_ICONS() -> bool:
        return X.BUILD_VERSION is not None and X.BUILD_VERSION >= 66690

    class Place(Enum):
        UNKNOWN = 1
        ChatActivity = 2
        FilteredSearchView = 3
        SharedMediaLayout = 4
        SearchDownloadsContainer = 5
        ChannelAdminLogActivity = 6

    @dataclass
    class FileInfo:
        ext: str
        on_click: Callable[["FilesController.OnClickArgs"], None] = field(compare=False, repr=False)
        whitelist_places: list["FilesController.Place"] = field(default_factory=list, kw_only=True)
        blacklist_places: list["FilesController.Place"] = field(default_factory=list, kw_only=True)
        get_icon: Optional[Callable[[], Drawable]] = field(default=None, compare=False, repr=False, kw_only=True)
        _icid: int = field(default=-1, init=False, repr=False, compare=False)

        def __post_init__(self):
            if self.whitelist_places and self.blacklist_places:
                raise ValueError("Cannot use whitelist_places with blacklist_places together")

            if self.get_icon and not FilesController.SUPPORT_ICONS:
                raise ValueError("Custom file icons are not supported by this app version")

    @dataclass
    class OnClickArgs:
        place: "FilesController.Place"
        file: File = field(compare=False, repr=False)
        file_name: str
        message: MessageObject = field(compare=False, repr=False)
        activity: Activity = field(compare=False, repr=False)
        parent_fragment: Optional[BaseFragment] = field(default=None, compare=False, repr=False)

    class ExtensionAlreadyRegistered(Exception):
        def __init__(self, ext: str):
            super().__init__(f"The file extension '{ext}' is already in use.")

    class ExtensionNotRegistered(Exception):
        def __init__(self, ext: str):
            super().__init__(f"The file extension '{ext}' is not registered.")

    class SecretInvalid(Exception):
        def __init__(self, ext: str, secret: str):
            super().__init__(f"Provided secret '{secret}' for extension '{ext}' is invalid.")

    @classmethod
    def __ghc(cls):
        if cls.__jc is not None:
            return cls.__jc

        pyobject_type = TypeId.get(PyObject.getClass())
        object_array_type = TypeId.get(jarray(Object))
        method_param_type = TypeId.get(jclass("de.robv.android.xposed.XC_MethodHook$MethodHookParam"))
        string_type = TypeId.STRING
        class_type = TypeId.get(jclass("java.lang.Class"))
        field_type = TypeId.get(jclass("java.lang.reflect.Field"))
        bool_box_type = TypeId.get(jclass("java.lang.Boolean"))
        int_box_type = TypeId.get(jclass("java.lang.Integer"))
        thread_type = TypeId.get(jclass("java.lang.Thread"))
        stack_element_type = TypeId.get(jclass("java.lang.StackTraceElement"))
        stack_array_type = TypeId.get(jarray(jclass("java.lang.StackTraceElement")))
        message_type = TypeId.get(jclass("org.telegram.tgnet.TLRPC$Message"))
        document_type = TypeId.get(jclass("org.telegram.tgnet.TLRPC$Document"))
        file_type = TypeId.get(File.getClass())
        activity_type = TypeId.get(Activity.getClass())
        base_fragment_type = TypeId.get(BaseFragment.getClass())
        message_object_type = TypeId.get(MessageObject.getClass())
        file_loader_type = TypeId.get(FileLoader.getClass())
        map_type = TypeId.get(ConcurrentHashMap.getClass())
        drawable_type = TypeId.get(Drawable.getClass())
        user_config_type = TypeId.get(jclass("org.telegram.messenger.UserConfig"))

        pyobject_call_method = pyobject_type.getMethod(pyobject_type, "call", object_array_type)
        pyobject_to_boolean = pyobject_type.getMethod(TypeId.BOOLEAN, "toBoolean")
        thread_current_thread = thread_type.getMethod(thread_type, "currentThread")
        thread_get_stack_trace = thread_type.getMethod(stack_array_type, "getStackTrace")
        stack_element_get_class_name = stack_element_type.getMethod(string_type, "getClassName")
        stack_element_get_method_name = stack_element_type.getMethod(string_type, "getMethodName")
        string_equals = string_type.getMethod(TypeId.BOOLEAN, "equals", TypeId.OBJECT)
        string_index_of = string_type.getMethod(TypeId.INT, "indexOf", string_type)
        string_last_index_of = string_type.getMethod(TypeId.INT, "lastIndexOf", string_type)
        string_substring = string_type.getMethod(string_type, "substring", TypeId.INT)
        string_substring_range = string_type.getMethod(string_type, "substring", TypeId.INT, TypeId.INT)
        string_to_lower = string_type.getMethod(string_type, "toLowerCase")
        string_is_empty = string_type.getMethod(TypeId.BOOLEAN, "isEmpty")
        object_get_class = TypeId.OBJECT.getMethod(class_type, "getClass")
        class_get_declared_field = class_type.getMethod(field_type, "getDeclaredField", string_type)
        field_set_accessible = field_type.getMethod(TypeId.VOID, "setAccessible", TypeId.BOOLEAN)
        field_get = field_type.getMethod(TypeId.OBJECT, "get", TypeId.OBJECT)
        file_ctor = file_type.getConstructor(string_type)
        file_exists = file_type.getMethod(TypeId.BOOLEAN, "exists")
        message_get_document = message_object_type.getMethod(document_type, "getDocument")
        message_get_document_name = message_object_type.getMethod(string_type, "getDocumentName")
        message_owner_field = message_object_type.getField(message_type, "messageOwner")
        message_attach_path_field = message_type.getField(string_type, "attachPath")
        file_loader_get_instance = file_loader_type.getMethod(file_loader_type, "getInstance", TypeId.INT)
        file_loader_get_path_to_message = file_loader_type.getMethod(file_type, "getPathToMessage", message_type)
        selected_account_field = user_config_type.getField(TypeId.INT, "selectedAccount")
        int_value_of = int_box_type.getMethod(int_box_type, "valueOf", TypeId.INT)
        int_int_value = int_box_type.getMethod(TypeId.INT, "intValue")
        bool_value_of = bool_box_type.getMethod(bool_box_type, "valueOf", TypeId.BOOLEAN)
        bool_boolean_value = bool_box_type.getMethod(TypeId.BOOLEAN, "booleanValue")
        param_args_field = method_param_type.getField(object_array_type, "args")
        param_this_object_field = method_param_type.getField(TypeId.OBJECT, "thisObject")
        param_get_result = method_param_type.getMethod(TypeId.OBJECT, "getResult")
        param_set_result = method_param_type.getMethod(TypeId.VOID, "setResult", TypeId.OBJECT)
        map_put = map_type.getMethod(TypeId.OBJECT, "put", TypeId.OBJECT, TypeId.OBJECT)
        map_remove = map_type.getMethod(TypeId.OBJECT, "remove", TypeId.OBJECT)
        map_get = map_type.getMethod(TypeId.OBJECT, "get", TypeId.OBJECT)
        map_contains_key = map_type.getMethod(TypeId.BOOLEAN, "containsKey", TypeId.OBJECT)

        @java_subclass(Object, custom_name="FilesController")
        class _JFilesController(Base):
            active_icons_ids = jfield(ConcurrentHashMap.getClass())
            icons = jfield(ConcurrentHashMap.getClass())
            _ex = jfield(PyObject.getClass(), default=None, methods=[jsetmethod("setEx")])

            @staticmethod
            def _box_int(code, src, dst):
                code.invokeStatic(int_value_of, dst, src)

            @staticmethod
            def _box_bool(code, src, dst):
                code.invokeStatic(bool_value_of, dst, src)

            @staticmethod
            def _inc(code, value, tmp, one):
                code.op(BinaryOp.ADD, tmp, value, one)
                code.move(value, tmp)

            @staticmethod
            def _gen_get_place(dex, proxy_type):
                method_id = proxy_type.getMethod(string_type, "getPlace", jarray(TypeId)([string_type]))
                code = dex.declare(method_id, Modifier.PUBLIC)

                method_name = code.getParameter(0, string_type)
                thread_ref = code.newLocal(thread_type)
                stack_trace = code.newLocal(stack_array_type)
                elem = code.newLocal(stack_element_type)
                idx = code.newLocal(TypeId.INT)
                tmp_idx = code.newLocal(TypeId.INT)
                size = code.newLocal(TypeId.INT)
                zero = code.newLocal(TypeId.INT)
                one = code.newLocal(TypeId.INT)
                next_frame = code.newLocal(TypeId.INT)
                current_class_name = code.newLocal(string_type)
                current_method_name = code.newLocal(string_type)
                class_name = code.newLocal(string_type)
                trimmed_class_name = code.newLocal(string_type)
                class_name_obj = code.newLocal(TypeId.OBJECT)
                method_name_obj = code.newLocal(TypeId.OBJECT)
                lsp_hooker = code.newLocal(string_type)
                dollar = code.newLocal(string_type)
                dot = code.newLocal(string_type)
                null_str = code.newLocal(string_type)
                dollar_idx = code.newLocal(TypeId.INT)
                dot_idx = code.newLocal(TypeId.INT)
                start_idx = code.newLocal(TypeId.INT)
                bool_result = code.newLocal(TypeId.BOOLEAN)

                loop = Label()
                handle_next = Label()
                skip_method_match = Label()
                after_inner_trim = Label()
                no_package = Label()
                end = Label()

                code.loadConstant(zero, 0)
                code.loadConstant(one, 1)
                code.loadConstant(next_frame, 0)
                code.loadConstant(lsp_hooker, "LSPHooker_")
                code.loadConstant(dollar, "$")
                code.loadConstant(dot, ".")
                code.loadConstant(null_str, None)

                code.invokeStatic(thread_current_thread, thread_ref)
                code.invokeVirtual(thread_get_stack_trace, stack_trace, thread_ref)
                code.arrayLength(size, stack_trace)
                code.loadConstant(idx, 0)

                code.mark(loop)
                code.compare(Comparison.GE, end, idx, size)
                code.aget(elem, stack_trace, idx)
                code.compareZ(Comparison.NE, handle_next, next_frame)

                code.invokeVirtual(stack_element_get_class_name, current_class_name, elem)
                code.cast(class_name_obj, current_class_name)
                code.invokeVirtual(string_equals, bool_result, lsp_hooker, class_name_obj)
                code.compareZ(Comparison.EQ, skip_method_match, bool_result)
                code.invokeVirtual(stack_element_get_method_name, current_method_name, elem)
                code.cast(method_name_obj, current_method_name)
                code.invokeVirtual(string_equals, bool_result, method_name, method_name_obj)
                code.compareZ(Comparison.EQ, skip_method_match, bool_result)
                code.loadConstant(next_frame, 1)

                code.mark(skip_method_match)
                _JFilesController._inc(code, idx, tmp_idx, one)
                code.jump(loop)

                code.mark(handle_next)
                code.invokeVirtual(stack_element_get_class_name, class_name, elem)
                code.invokeVirtual(string_index_of, dollar_idx, class_name, dollar)
                code.compare(Comparison.LT, after_inner_trim, dollar_idx, zero)
                code.invokeVirtual(string_substring_range, trimmed_class_name, class_name, zero, dollar_idx)
                code.move(class_name, trimmed_class_name)

                code.mark(after_inner_trim)
                code.invokeVirtual(string_last_index_of, dot_idx, class_name, dot)
                code.compare(Comparison.LT, no_package, dot_idx, zero)
                code.op(BinaryOp.ADD, start_idx, dot_idx, one)
                code.invokeVirtual(string_substring, trimmed_class_name, class_name, start_idx)
                code.returnValue(trimmed_class_name)

                code.mark(no_package)
                code.returnValue(class_name)

                code.mark(end)
                code.returnValue(null_str)

            @staticmethod
            def _gen_add_active_id(dex, proxy_type):
                method_id = proxy_type.getMethod(TypeId.VOID, "addActiveId", jarray(TypeId)([string_type, TypeId.INT]))
                code = dex.declare(method_id, Modifier.PUBLIC)

                this_ref = code.getThis(proxy_type)
                ext = code.getParameter(0, string_type)
                ext_obj = code.newLocal(TypeId.OBJECT)
                icon_id = code.getParameter(1, TypeId.INT)
                active_map = code.newLocal(map_type)
                boxed_id = code.newLocal(int_box_type)
                ignored = code.newLocal(TypeId.OBJECT)
                end = Label()

                code.compareZ(Comparison.EQ, end, ext)
                code.cast(ext_obj, ext)
                code.iget(proxy_type.getField(map_type, "active_icons_ids"), active_map, this_ref)
                _JFilesController._box_int(code, icon_id, boxed_id)
                code.invokeVirtual(map_put, ignored, active_map, ext_obj, boxed_id)
                code.mark(end)
                code.returnVoid()

            @staticmethod
            def _gen_remove_active_id(dex, proxy_type):
                method_id = proxy_type.getMethod(TypeId.VOID, "removeActiveId", jarray(TypeId)([string_type]))
                code = dex.declare(method_id, Modifier.PUBLIC)

                this_ref = code.getThis(proxy_type)
                ext = code.getParameter(0, string_type)
                ext_obj = code.newLocal(TypeId.OBJECT)
                active_map = code.newLocal(map_type)
                icons_map = code.newLocal(map_type)
                removed_obj = code.newLocal(TypeId.OBJECT)
                ignored = code.newLocal(TypeId.OBJECT)
                end = Label()

                code.compareZ(Comparison.EQ, end, ext)
                code.cast(ext_obj, ext)
                code.iget(proxy_type.getField(map_type, "active_icons_ids"), active_map, this_ref)
                code.invokeVirtual(map_remove, removed_obj, active_map, ext_obj)
                code.compareZ(Comparison.EQ, end, removed_obj)
                code.iget(proxy_type.getField(map_type, "icons"), icons_map, this_ref)
                code.invokeVirtual(map_remove, ignored, icons_map, removed_obj)
                code.mark(end)
                code.returnVoid()

            @staticmethod
            def _gen_update_icon(dex, proxy_type):
                method_id = proxy_type.getMethod(TypeId.VOID, "updateIcon", jarray(TypeId)([TypeId.INT, drawable_type]))
                code = dex.declare(method_id, Modifier.PUBLIC)

                this_ref = code.getThis(proxy_type)
                icon_id = code.getParameter(0, TypeId.INT)
                icon = code.getParameter(1, drawable_type)
                icons_map = code.newLocal(map_type)
                boxed_id = code.newLocal(int_box_type)
                ignored = code.newLocal(TypeId.OBJECT)
                end = Label()

                code.compareZ(Comparison.EQ, end, icon)
                code.iget(proxy_type.getField(map_type, "icons"), icons_map, this_ref)
                _JFilesController._box_int(code, icon_id, boxed_id)
                code.invokeVirtual(map_put, ignored, icons_map, boxed_id, icon)
                code.mark(end)
                code.returnVoid()

            @staticmethod
            def _gen_open_for_view(dex, proxy_type):
                method_id = proxy_type.getMethod(TypeId.VOID, "openForView", jarray(TypeId)([method_param_type]))
                code = dex.declare(method_id, Modifier.PUBLIC)

                this_ref = code.getThis(proxy_type)
                param = code.getParameter(0, method_param_type)
                ex_ref = code.newLocal(pyobject_type)
                place = code.newLocal(string_type)
                hook_name = code.newLocal(string_type)
                args = code.newLocal(object_array_type)
                arg0 = code.newLocal(TypeId.OBJECT)
                arg1 = code.newLocal(TypeId.OBJECT)
                message = code.newLocal(message_object_type)
                activity = code.newLocal(activity_type)
                owner = code.newLocal(message_type)
                attach_path = code.newLocal(string_type)
                file_local = code.newLocal(file_type)
                null_file = code.newLocal(file_type)
                exists_result = code.newLocal(TypeId.BOOLEAN)
                empty_result = code.newLocal(TypeId.BOOLEAN)
                account = code.newLocal(TypeId.INT)
                loader = code.newLocal(file_loader_type)
                call_args = code.newLocal(object_array_type)
                py_result = code.newLocal(pyobject_type)
                py_bool = code.newLocal(TypeId.BOOLEAN)
                true_bool = code.newLocal(TypeId.BOOLEAN)
                true_obj = code.newLocal(bool_box_type)
                idx = code.newLocal(TypeId.INT)
                size = code.newLocal(TypeId.INT)
                get_place = proxy_type.getMethod(string_type, "getPlace", jarray(TypeId)([string_type]))

                load_from_loader = Label()
                after_file = Label()
                end = Label()

                code.iget(proxy_type.getField(pyobject_type, "_ex"), ex_ref, this_ref)
                code.compareZ(Comparison.EQ, end, ex_ref)
                code.loadConstant(hook_name, "openForView")
                code.invokeVirtual(get_place, place, this_ref, hook_name)
                code.iget(param_args_field, args, param)
                code.loadConstant(idx, 0)
                code.aget(arg0, args, idx)
                code.cast(message, arg0)
                code.loadConstant(idx, 1)
                code.aget(arg1, args, idx)
                code.cast(activity, arg1)
                code.compareZ(Comparison.EQ, end, message)
                code.iget(message_owner_field, owner, message)
                code.compareZ(Comparison.EQ, end, owner)
                code.loadConstant(null_file, None)
                code.move(file_local, null_file)
                code.iget(message_attach_path_field, attach_path, owner)
                code.compareZ(Comparison.EQ, load_from_loader, attach_path)
                code.invokeVirtual(string_is_empty, empty_result, attach_path)
                code.compareZ(Comparison.NE, load_from_loader, empty_result)
                code.newInstance(file_local, file_ctor, attach_path)
                code.compareZ(Comparison.EQ, load_from_loader, file_local)
                code.invokeVirtual(file_exists, exists_result, file_local)
                code.compareZ(Comparison.NE, after_file, exists_result)

                code.mark(load_from_loader)
                code.sget(selected_account_field, account)
                code.invokeStatic(file_loader_get_instance, loader, account)
                code.invokeVirtual(file_loader_get_path_to_message, file_local, loader, owner)

                code.mark(after_file)
                code.compareZ(Comparison.EQ, end, file_local)
                code.invokeVirtual(file_exists, exists_result, file_local)
                code.compareZ(Comparison.EQ, end, exists_result)
                code.loadConstant(size, 4)
                code.newArray(call_args, size)
                code.loadConstant(idx, 0)
                code.aput(call_args, idx, place)
                code.loadConstant(idx, 1)
                code.aput(call_args, idx, file_local)
                code.loadConstant(idx, 2)
                code.aput(call_args, idx, message)
                code.loadConstant(idx, 3)
                code.aput(call_args, idx, activity)
                code.invokeVirtual(pyobject_call_method, py_result, ex_ref, call_args)
                code.invokeVirtual(pyobject_to_boolean, py_bool, py_result)
                code.compareZ(Comparison.EQ, end, py_bool)
                code.loadConstant(true_bool, True)
                _JFilesController._box_bool(code, true_bool, true_obj)
                code.invokeVirtual(param_set_result, None, param, true_obj)

                code.mark(end)
                code.returnVoid()

            @staticmethod
            def _gen_open_document(dex, proxy_type):
                method_id = proxy_type.getMethod(TypeId.VOID, "openDocument", jarray(TypeId)([method_param_type]))
                code = dex.declare(method_id, Modifier.PUBLIC)

                this_ref = code.getThis(proxy_type)
                param = code.getParameter(0, method_param_type)
                ex_ref = code.newLocal(pyobject_type)
                place = code.newLocal(string_type)
                hook_name = code.newLocal(string_type)
                args = code.newLocal(object_array_type)
                arg0 = code.newLocal(TypeId.OBJECT)
                arg1 = code.newLocal(TypeId.OBJECT)
                arg2 = code.newLocal(TypeId.OBJECT)
                message = code.newLocal(message_object_type)
                activity = code.newLocal(activity_type)
                parent_fragment = code.newLocal(base_fragment_type)
                owner = code.newLocal(message_type)
                document = code.newLocal(document_type)
                attach_path = code.newLocal(string_type)
                file_local = code.newLocal(file_type)
                null_file = code.newLocal(file_type)
                exists_result = code.newLocal(TypeId.BOOLEAN)
                empty_result = code.newLocal(TypeId.BOOLEAN)
                account = code.newLocal(TypeId.INT)
                loader = code.newLocal(file_loader_type)
                call_args = code.newLocal(object_array_type)
                py_result = code.newLocal(pyobject_type)
                py_bool = code.newLocal(TypeId.BOOLEAN)
                null_obj = code.newLocal(TypeId.OBJECT)
                idx = code.newLocal(TypeId.INT)
                size = code.newLocal(TypeId.INT)
                get_place = proxy_type.getMethod(string_type, "getPlace", jarray(TypeId)([string_type]))

                load_from_loader = Label()
                after_file = Label()
                end = Label()

                code.iget(proxy_type.getField(pyobject_type, "_ex"), ex_ref, this_ref)
                code.compareZ(Comparison.EQ, end, ex_ref)
                code.loadConstant(hook_name, "openDocument")
                code.invokeVirtual(get_place, place, this_ref, hook_name)
                code.iget(param_args_field, args, param)
                code.loadConstant(idx, 0)
                code.aget(arg0, args, idx)
                code.cast(message, arg0)
                code.loadConstant(idx, 1)
                code.aget(arg1, args, idx)
                code.cast(activity, arg1)
                code.loadConstant(idx, 2)
                code.aget(arg2, args, idx)
                code.cast(parent_fragment, arg2)
                code.compareZ(Comparison.EQ, end, message)
                code.invokeVirtual(message_get_document, document, message)
                code.compareZ(Comparison.EQ, end, document)
                code.iget(message_owner_field, owner, message)
                code.compareZ(Comparison.EQ, end, owner)
                code.loadConstant(null_file, None)
                code.move(file_local, null_file)
                code.iget(message_attach_path_field, attach_path, owner)
                code.compareZ(Comparison.EQ, load_from_loader, attach_path)
                code.invokeVirtual(string_is_empty, empty_result, attach_path)
                code.compareZ(Comparison.NE, load_from_loader, empty_result)
                code.newInstance(file_local, file_ctor, attach_path)
                code.compareZ(Comparison.EQ, load_from_loader, file_local)
                code.invokeVirtual(file_exists, exists_result, file_local)
                code.compareZ(Comparison.NE, after_file, exists_result)

                code.mark(load_from_loader)
                code.sget(selected_account_field, account)
                code.invokeStatic(file_loader_get_instance, loader, account)
                code.invokeVirtual(file_loader_get_path_to_message, file_local, loader, owner)

                code.mark(after_file)
                code.compareZ(Comparison.EQ, end, file_local)
                code.invokeVirtual(file_exists, exists_result, file_local)
                code.compareZ(Comparison.EQ, end, exists_result)
                code.loadConstant(size, 5)
                code.newArray(call_args, size)
                code.loadConstant(idx, 0)
                code.aput(call_args, idx, place)
                code.loadConstant(idx, 1)
                code.aput(call_args, idx, file_local)
                code.loadConstant(idx, 2)
                code.aput(call_args, idx, message)
                code.loadConstant(idx, 3)
                code.aput(call_args, idx, activity)
                code.loadConstant(idx, 4)
                code.aput(call_args, idx, parent_fragment)
                code.invokeVirtual(pyobject_call_method, py_result, ex_ref, call_args)
                code.invokeVirtual(pyobject_to_boolean, py_bool, py_result)
                code.compareZ(Comparison.EQ, end, py_bool)
                code.loadConstant(null_obj, None)
                code.invokeVirtual(param_set_result, None, param, null_obj)

                code.mark(end)
                code.returnVoid()

            @staticmethod
            def _gen_get_file_type(dex, proxy_type):
                method_id = proxy_type.getMethod(TypeId.VOID, "getFileType", jarray(TypeId)([method_param_type]))
                code = dex.declare(method_id, Modifier.PUBLIC)

                this_ref = code.getThis(proxy_type)
                param = code.getParameter(0, method_param_type)
                result_obj = code.newLocal(TypeId.OBJECT)
                is_int = code.newLocal(TypeId.BOOLEAN)
                result_int_box = code.newLocal(int_box_type)
                result_int = code.newLocal(TypeId.INT)
                expected = code.newLocal(TypeId.INT)
                this_obj = code.newLocal(TypeId.OBJECT)
                this_class = code.newLocal(class_type)
                current_field = code.newLocal(field_type)
                field_name = code.newLocal(string_type)
                accessible = code.newLocal(TypeId.BOOLEAN)
                message_obj_raw = code.newLocal(TypeId.OBJECT)
                message_obj = code.newLocal(message_object_type)
                document = code.newLocal(document_type)
                doc_name = code.newLocal(string_type)
                empty_result = code.newLocal(TypeId.BOOLEAN)
                dot = code.newLocal(string_type)
                dot_idx = code.newLocal(TypeId.INT)
                zero = code.newLocal(TypeId.INT)
                one = code.newLocal(TypeId.INT)
                start_idx = code.newLocal(TypeId.INT)
                ext = code.newLocal(string_type)
                ext_tail = code.newLocal(string_type)
                ext_lower = code.newLocal(string_type)
                ext_obj = code.newLocal(TypeId.OBJECT)
                active_map = code.newLocal(map_type)
                has_icon = code.newLocal(TypeId.BOOLEAN)
                icon_value = code.newLocal(TypeId.OBJECT)

                no_dot = Label()
                have_ext = Label()
                end = Label()

                code.invokeVirtual(param_get_result, result_obj, param)
                code.instanceOfType(is_int, result_obj, int_box_type)
                code.compareZ(Comparison.EQ, end, is_int)
                code.cast(result_int_box, result_obj)
                code.invokeVirtual(int_int_value, result_int, result_int_box)
                code.loadConstant(expected, 5)
                code.compare(Comparison.NE, end, result_int, expected)
                code.iget(param_this_object_field, this_obj, param)
                code.compareZ(Comparison.EQ, end, this_obj)
                code.invokeVirtual(object_get_class, this_class, this_obj)
                code.loadConstant(field_name, "currentMessageObject")
                code.invokeVirtual(class_get_declared_field, current_field, this_class, field_name)
                code.loadConstant(accessible, True)
                code.invokeVirtual(field_set_accessible, None, current_field, accessible)
                code.invokeVirtual(field_get, message_obj_raw, current_field, this_obj)
                code.compareZ(Comparison.EQ, end, message_obj_raw)
                code.cast(message_obj, message_obj_raw)
                code.invokeVirtual(message_get_document, document, message_obj)
                code.compareZ(Comparison.EQ, end, document)
                code.invokeVirtual(message_get_document_name, doc_name, message_obj)
                code.compareZ(Comparison.EQ, end, doc_name)
                code.invokeVirtual(string_is_empty, empty_result, doc_name)
                code.compareZ(Comparison.NE, end, empty_result)
                code.loadConstant(zero, 0)
                code.loadConstant(one, 1)
                code.loadConstant(dot, ".")
                code.invokeVirtual(string_last_index_of, dot_idx, doc_name, dot)
                code.compare(Comparison.LT, no_dot, dot_idx, zero)
                code.op(BinaryOp.ADD, start_idx, dot_idx, one)
                code.invokeVirtual(string_substring, ext_tail, doc_name, start_idx)
                code.move(ext, ext_tail)
                code.jump(have_ext)

                code.mark(no_dot)
                code.move(ext, doc_name)

                code.mark(have_ext)
                code.invokeVirtual(string_to_lower, ext_lower, ext)
                code.cast(ext_obj, ext_lower)
                code.iget(proxy_type.getField(map_type, "active_icons_ids"), active_map, this_ref)
                code.invokeVirtual(map_contains_key, has_icon, active_map, ext_obj)
                code.compareZ(Comparison.EQ, end, has_icon)
                code.invokeVirtual(map_get, icon_value, active_map, ext_obj)
                code.invokeVirtual(param_set_result, None, param, icon_value)

                code.mark(end)
                code.returnVoid()

            @staticmethod
            def _gen_get_file_icon(dex, proxy_type):
                method_id = proxy_type.getMethod(TypeId.VOID, "getFileIcon", jarray(TypeId)([method_param_type]))
                code = dex.declare(method_id, Modifier.PUBLIC)
                this_ref = code.getThis(proxy_type)
                param = code.getParameter(0, method_param_type)
                code.invokeVirtual(proxy_type.getMethod(TypeId.VOID, "getFileType", jarray(TypeId)([method_param_type])), None, this_ref, param)
                code.returnVoid()

            @staticmethod
            def _gen_get_custom_icon_drawable(dex, proxy_type):
                method_id = proxy_type.getMethod(TypeId.VOID, "getCustomIconDrawable", jarray(TypeId)([method_param_type]))
                code = dex.declare(method_id, Modifier.PUBLIC)

                this_ref = code.getThis(proxy_type)
                param = code.getParameter(0, method_param_type)
                result_obj = code.newLocal(TypeId.OBJECT)
                args = code.newLocal(object_array_type)
                idx = code.newLocal(TypeId.INT)
                icon_obj = code.newLocal(TypeId.OBJECT)
                icons_map = code.newLocal(map_type)
                has_icon = code.newLocal(TypeId.BOOLEAN)
                icon_value = code.newLocal(TypeId.OBJECT)
                end = Label()

                code.invokeVirtual(param_get_result, result_obj, param)
                code.compareZ(Comparison.NE, end, result_obj)
                code.iget(param_args_field, args, param)
                code.loadConstant(idx, 0)
                code.aget(icon_obj, args, idx)
                code.compareZ(Comparison.EQ, end, icon_obj)
                code.iget(proxy_type.getField(map_type, "icons"), icons_map, this_ref)
                code.invokeVirtual(map_contains_key, has_icon, icons_map, icon_obj)
                code.compareZ(Comparison.EQ, end, has_icon)
                code.invokeVirtual(map_get, icon_value, icons_map, icon_obj)
                code.invokeVirtual(param_set_result, None, param, icon_value)

                code.mark(end)
                code.returnVoid()

            @staticmethod
            def _gen_is_custom_file_icon(dex, proxy_type):
                method_id = proxy_type.getMethod(TypeId.VOID, "isCustomFileIcon", jarray(TypeId)([method_param_type]))
                code = dex.declare(method_id, Modifier.PUBLIC)

                this_ref = code.getThis(proxy_type)
                param = code.getParameter(0, method_param_type)
                result_obj = code.newLocal(TypeId.OBJECT)
                is_bool = code.newLocal(TypeId.BOOLEAN)
                bool_box = code.newLocal(bool_box_type)
                bool_value = code.newLocal(TypeId.BOOLEAN)
                args = code.newLocal(object_array_type)
                idx = code.newLocal(TypeId.INT)
                icon_obj = code.newLocal(TypeId.OBJECT)
                icons_map = code.newLocal(map_type)
                has_icon = code.newLocal(TypeId.BOOLEAN)
                true_bool = code.newLocal(TypeId.BOOLEAN)
                true_obj = code.newLocal(bool_box_type)
                end = Label()

                code.invokeVirtual(param_get_result, result_obj, param)
                code.instanceOfType(is_bool, result_obj, bool_box_type)
                code.compareZ(Comparison.EQ, end, is_bool)
                code.cast(bool_box, result_obj)
                code.invokeVirtual(bool_boolean_value, bool_value, bool_box)
                code.compareZ(Comparison.NE, end, bool_value)
                code.iget(param_args_field, args, param)
                code.loadConstant(idx, 0)
                code.aget(icon_obj, args, idx)
                code.compareZ(Comparison.EQ, end, icon_obj)
                code.iget(proxy_type.getField(map_type, "icons"), icons_map, this_ref)
                code.invokeVirtual(map_contains_key, has_icon, icons_map, icon_obj)
                code.compareZ(Comparison.EQ, end, has_icon)
                code.loadConstant(true_bool, True)
                _JFilesController._box_bool(code, true_bool, true_obj)
                code.invokeVirtual(param_set_result, None, param, true_obj)

                code.mark(end)
                code.returnVoid()

            @jclassbuilder()
            @staticmethod
            def create_methods(dex, proxy_type, _, __):
                _JFilesController._gen_get_place(dex, proxy_type)
                _JFilesController._gen_open_for_view(dex, proxy_type)
                _JFilesController._gen_open_document(dex, proxy_type)

                if FilesController.SUPPORT_ICONS and not FilesController.DIRECT_FILE_ICONS:
                    _JFilesController._gen_add_active_id(dex, proxy_type)
                    _JFilesController._gen_remove_active_id(dex, proxy_type)
                    _JFilesController._gen_update_icon(dex, proxy_type)
                    _JFilesController._gen_get_file_type(dex, proxy_type)
                    _JFilesController._gen_get_file_icon(dex, proxy_type)
                    _JFilesController._gen_get_custom_icon_drawable(dex, proxy_type)
                    _JFilesController._gen_is_custom_file_icon(dex, proxy_type)

        cls.__jc = _JFilesController.new_instance()
        if cls.SUPPORT_ICONS and not cls.DIRECT_FILE_ICONS:
            cls.__jc.active_icons_ids = ConcurrentHashMap()
            cls.__jc.icons = ConcurrentHashMap()
        return cls.__jc

    @classmethod
    def __ih(cls):
        if cls.__h:
            return

        from de.robv.android.xposed import XposedBridge, XC_MethodHook  # noqa
        from java.lang import Boolean
        jhooks = cls.__ghc()

        def _ex(place, file, message, activity, parent_fragment=None):
            try:
                place_enum = FilesController.Place.UNKNOWN
                if place:
                    with suppress(KeyError):
                        place_enum = FilesController.Place[str(place)]

                file_name = message.getFileName() if message else ""
                if file_name is None:
                    file_name = ""
                ext = file_name.rsplit(".", 1)[-1].lower() if "." in file_name else file_name.lower()

                if (fi := cls.__config.get(ext, None)) and (
                        fi.whitelist_places and place_enum in fi.whitelist_places
                        or fi.blacklist_places and place_enum not in fi.blacklist_places
                        or not fi.whitelist_places and not fi.blacklist_places
                ):
                    fi.on_click(FilesController.OnClickArgs(
                        place=place_enum,
                        file=file,
                        file_name=file_name,
                        message=message,
                        activity=activity,
                        parent_fragment=parent_fragment
                    ))
                    return True
            except Exception:
                log(traceback.format_exc())
            return False

        jhooks.setEx(_ex)
        methods = {
            m.getName(): m
            for m in jhooks.getClass().getDeclaredMethods()
        }

        method_type = TypeId.get(Method.getClass())
        method_param_type = TypeId.get(XC_MethodHook.MethodHookParam.getClass())
        object_array_type = TypeId.get(jarray(Object))
        string_type = TypeId.STRING
        int_box_type = TypeId.get(jclass("java.lang.Integer"))
        bool_box_type = TypeId.get(jclass("java.lang.Boolean"))
        method_get_name = method_type.getMethod(string_type, "getName")
        method_invoke = method_type.getMethod(TypeId.OBJECT, "invoke", TypeId.OBJECT, object_array_type)
        param_get_result = method_param_type.getMethod(TypeId.OBJECT, "getResult")
        param_args_field = method_param_type.getField(object_array_type, "args")
        string_equals = string_type.getMethod(TypeId.BOOLEAN, "equals", TypeId.OBJECT)
        int_int_value = int_box_type.getMethod(TypeId.INT, "intValue")
        bool_boolean_value = bool_box_type.getMethod(TypeId.BOOLEAN, "booleanValue")

        def _gen_invoke(code, method_ref, controller_ref, param_ref, invoke_args, idx, size, ignored):
            code.loadConstant(size, 1)
            code.newArray(invoke_args, size)
            code.loadConstant(idx, 0)
            code.aput(invoke_args, idx, param_ref)
            code.invokeVirtual(method_invoke, ignored, method_ref, controller_ref, invoke_args)

        @java_subclass(XC_MethodHook)
        class _BeforeHook(Base):
            method: Method = jfield(Method.getClass(), default=None, methods=[jsetmethod("setMethod")])
            controller = jfield(Object.getClass(), default=None, methods=[jsetmethod("setController")])

            @jclassbuilder()
            @staticmethod
            def create_methods(dex, proxy_type, _, __):
                method_id = proxy_type.getMethod(TypeId.VOID, "beforeHookedMethod", jarray(TypeId)([method_param_type]))
                code = dex.declare(method_id, Modifier.PUBLIC)

                this_ref = code.getThis(proxy_type)
                param_ref = code.getParameter(0, method_param_type)
                method_ref = code.newLocal(method_type)
                controller_ref = code.newLocal(TypeId.OBJECT)
                invoke_args = code.newLocal(object_array_type)
                idx = code.newLocal(TypeId.INT)
                size = code.newLocal(TypeId.INT)
                ignored = code.newLocal(TypeId.OBJECT)
                end = Label()

                code.iget(proxy_type.getField(method_type, "method"), method_ref, this_ref)
                code.compareZ(Comparison.EQ, end, method_ref)
                code.iget(proxy_type.getField(TypeId.OBJECT, "controller"), controller_ref, this_ref)
                code.compareZ(Comparison.EQ, end, controller_ref)
                _gen_invoke(code, method_ref, controller_ref, param_ref, invoke_args, idx, size, ignored)
                code.mark(end)
                code.returnVoid()

        hook = _BeforeHook.new_java_instance()
        hook.setController(jhooks.java)
        hook.setMethod(methods["openForView"])
        cls.__h.append(
            XposedBridge.hookMethod(
                AndroidUtilities.getClass().getDeclaredMethod("openForView", MessageObject.getClass(), Activity.getClass(), Theme.ResourcesProvider.getClass(), Boolean.TYPE),
                hook
            )
        )
        hook = _BeforeHook.new_java_instance()
        hook.setController(jhooks.java)
        hook.setMethod(methods["openDocument"])
        cls.__h.append(
            XposedBridge.hookMethod(
                AndroidUtilities.getClass().getDeclaredMethod("openDocument", MessageObject.getClass(), Activity.getClass(), BaseFragment.getClass()),
                hook
            )
        )

        if FilesController.SUPPORT_ICONS and not FilesController.DIRECT_FILE_ICONS:
            @java_subclass(XC_MethodHook)
            class _AfterHook(Base):
                method: Method = jfield(Method.getClass(), default=None, methods=[jsetmethod("setMethod")])
                controller = jfield(Object.getClass(), default=None, methods=[jsetmethod("setController")])

                @jclassbuilder()
                @staticmethod
                def create_methods(dex, proxy_type, _, __):
                    method_id = proxy_type.getMethod(TypeId.VOID, "afterHookedMethod", jarray(TypeId)([method_param_type]))
                    code = dex.declare(method_id, Modifier.PUBLIC)

                    this_ref = code.getThis(proxy_type)
                    param_ref = code.getParameter(0, method_param_type)
                    method_ref = code.newLocal(method_type)
                    controller_ref = code.newLocal(TypeId.OBJECT)
                    result_obj = code.newLocal(TypeId.OBJECT)
                    method_name = code.newLocal(string_type)
                    method_name_obj = code.newLocal(TypeId.OBJECT)
                    get_file_icon_name = code.newLocal(string_type)
                    get_custom_icon_drawable_name = code.newLocal(string_type)
                    is_custom_file_icon_name = code.newLocal(string_type)
                    is_target = code.newLocal(TypeId.BOOLEAN)
                    args = code.newLocal(object_array_type)
                    idx = code.newLocal(TypeId.INT)
                    invoke_size = code.newLocal(TypeId.INT)
                    arg0_obj = code.newLocal(TypeId.OBJECT)
                    arg0_int_box = code.newLocal(int_box_type)
                    arg0_int = code.newLocal(TypeId.INT)
                    result_int_box = code.newLocal(int_box_type)
                    result_int = code.newLocal(TypeId.INT)
                    result_bool_box = code.newLocal(bool_box_type)
                    result_bool = code.newLocal(TypeId.BOOLEAN)
                    hundred = code.newLocal(TypeId.INT)
                    five = code.newLocal(TypeId.INT)
                    zero = code.newLocal(TypeId.INT)
                    invoke_args = code.newLocal(object_array_type)
                    ignored = code.newLocal(TypeId.OBJECT)
                    end = Label()
                    check_custom_drawable = Label()
                    check_is_custom = Label()
                    check_icon_arg = Label()

                    code.iget(proxy_type.getField(method_type, "method"), method_ref, this_ref)
                    code.compareZ(Comparison.EQ, end, method_ref)
                    code.iget(proxy_type.getField(TypeId.OBJECT, "controller"), controller_ref, this_ref)
                    code.compareZ(Comparison.EQ, end, controller_ref)
                    code.invokeVirtual(param_get_result, result_obj, param_ref)
                    code.invokeVirtual(method_get_name, method_name, method_ref)
                    code.cast(method_name_obj, method_name)
                    code.loadConstant(get_file_icon_name, "getFileIcon")
                    code.loadConstant(get_custom_icon_drawable_name, "getCustomIconDrawable")
                    code.loadConstant(is_custom_file_icon_name, "isCustomFileIcon")
                    code.loadConstant(hundred, 100)
                    code.loadConstant(five, 5)
                    code.loadConstant(zero, 0)

                    code.invokeVirtual(string_equals, is_target, get_file_icon_name, method_name_obj)
                    code.compareZ(Comparison.EQ, check_custom_drawable, is_target)
                    code.instanceOfType(is_target, result_obj, int_box_type)
                    code.compareZ(Comparison.EQ, end, is_target)
                    code.cast(result_int_box, result_obj)
                    code.invokeVirtual(int_int_value, result_int, result_int_box)
                    code.compare(Comparison.NE, end, result_int, five)
                    _gen_invoke(code, method_ref, controller_ref, param_ref, invoke_args, idx, invoke_size, ignored)
                    code.jump(end)

                    code.mark(check_custom_drawable)
                    code.invokeVirtual(string_equals, is_target, get_custom_icon_drawable_name, method_name_obj)
                    code.compareZ(Comparison.EQ, check_is_custom, is_target)
                    code.compareZ(Comparison.NE, end, result_obj)
                    code.jump(check_icon_arg)

                    code.mark(check_is_custom)
                    code.invokeVirtual(string_equals, is_target, is_custom_file_icon_name, method_name_obj)
                    code.compareZ(Comparison.EQ, end, is_target)
                    code.instanceOfType(is_target, result_obj, bool_box_type)
                    code.compareZ(Comparison.EQ, end, is_target)
                    code.cast(result_bool_box, result_obj)
                    code.invokeVirtual(bool_boolean_value, result_bool, result_bool_box)
                    code.compareZ(Comparison.NE, end, result_bool)

                    code.mark(check_icon_arg)
                    code.iget(param_args_field, args, param_ref)
                    code.loadConstant(idx, 0)
                    code.aget(arg0_obj, args, idx)
                    code.compareZ(Comparison.EQ, end, arg0_obj)
                    code.instanceOfType(is_target, arg0_obj, int_box_type)
                    code.compareZ(Comparison.EQ, end, is_target)
                    code.cast(arg0_int_box, arg0_obj)
                    code.invokeVirtual(int_int_value, arg0_int, arg0_int_box)
                    code.compare(Comparison.LE, end, arg0_int, hundred)
                    _gen_invoke(code, method_ref, controller_ref, param_ref, invoke_args, idx, invoke_size, ignored)

                    code.mark(end)
                    code.returnVoid()

            cls.__h.append(
                XposedBridge.hookMethod(
                    MediaActionDrawable.getClass().getDeclaredMethod("getCustomIconDrawable", Integer.TYPE),
                    _AfterHook.new_java_instance().JIR.setController(jhooks.java).setMethod(methods["getCustomIconDrawable"])
                )
            )
            cls.__h.append(
                XposedBridge.hookMethod(
                    ChatMessageCell.getClass().getDeclaredMethod("getFileIcon"),
                    _AfterHook.new_java_instance().JIR.setController(jhooks.java).setMethod(methods["getFileIcon"])
                )
            )
            cls.__h.append(
                XposedBridge.hookMethod(
                    MediaActionDrawable.getClass().getDeclaredMethod("isCustomFileIcon", Integer.TYPE),
                    _AfterHook.new_java_instance().JIR.setController(jhooks.java).setMethod(methods["isCustomFileIcon"])
                )
            )

    @classmethod
    def register(cls, file_info: FileInfo) -> str:
        file_info.ext = file_info.ext.lower()
        if file_info.ext in cls.__config:
            raise FilesController.ExtensionAlreadyRegistered(file_info.ext)

        cls.__ih()

        secret = secrets.token_hex(16)
        cls.__m[file_info.ext] = secret
        cls.__config[file_info.ext] = file_info

        if cls.SUPPORT_ICONS and file_info.get_icon:
            icon = file_info.get_icon()
            if icon:
                if cls.DIRECT_FILE_ICONS:
                    file_info._icid = PluginsController.registerFileIcon(file_info.ext, icon)
                else:
                    file_info._icid = next(cls.__icon_id)
                    controller = cls.__ghc()
                    controller.addActiveId(file_info.ext, file_info._icid)
                    controller.updateIcon(file_info._icid, icon)

        return secret

    @classmethod
    def unregister(cls, ext: str, secret: str):
        ext = ext.lower()
        if ext not in cls.__config:
            raise FilesController.ExtensionNotRegistered(ext)

        if cls.__m[ext] != secret:
            raise FilesController.SecretInvalid(ext, secret)

        cls.__config.pop(ext)
        cls.__m.pop(ext)
        if cls.SUPPORT_ICONS:
            if cls.DIRECT_FILE_ICONS:
                PluginsController.unregisterFileIcon(ext)
            else:
                cls.__ghc().removeActiveId(ext)

    @classmethod
    def _stop(cls):
        if cls.__h:
            for hook in cls.__h:
                with suppress(Exception):
                    hook.unhook()
        cls.__h.clear()
        cls.__config.clear()
        cls.__m.clear()
        if cls.DIRECT_FILE_ICONS:
            with suppress(Exception):
                PluginsController.clearFileIcons()
        if cls.__jc is not None:
            if cls.SUPPORT_ICONS and not cls.DIRECT_FILE_ICONS:
                with suppress(Exception):
                    cls.__jc.active_icons_ids.clear()
                with suppress(Exception):
                    cls.__jc.icons.clear()
            with suppress(Exception):
                cls.__jc._ex = None
        cls.__jc = None
