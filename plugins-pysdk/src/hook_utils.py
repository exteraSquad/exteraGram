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

from typing import Any, Optional
import traceback

from java import jclass

from android_utils import log

JavaClass = type(jclass("java.lang.Object"))
JavaObject = jclass("java.lang.Object")
_JavaLangClass = jclass("java.lang.Class")


def find_class(class_name: str) -> Optional[JavaClass]:
    if not isinstance(class_name, str):
        return None
    try:
        return jclass(class_name)
    except Exception as e:
        log(f"Error finding class '{class_name}': {e}\n{traceback.format_exc()}")
        return None


def get_private_field(obj: JavaObject, field_name: str) -> Optional[Any]:
    if not isinstance(obj, JavaObject) or not isinstance(field_name, str):
        return None

    try:
        clazz = obj.getClass()
        field = None
        current_class = clazz
        while current_class is not None:
            try:
                field = current_class.getDeclaredField(field_name)
                break
            except Exception:
                current_class = current_class.getSuperclass()

        if field is None:
            return None

        field.setAccessible(True)
        return field.get(obj)
    except Exception as e:
        log(f"Error accessing field '{field_name}' on {obj}: {e}\n{traceback.format_exc()}")
        return None


def set_private_field(obj: JavaObject, field_name: str, new_value: Any) -> bool:
    if not isinstance(obj, JavaObject) or not isinstance(field_name, str):
        return False

    try:
        clazz = obj.getClass()
        field = None
        current_class = clazz
        while current_class is not None:
            try:
                field = current_class.getDeclaredField(field_name)
                break
            except Exception:
                current_class = current_class.getSuperclass()

        if field is None:
            return False

        field.setAccessible(True)
        field.set(obj, new_value)
        return True
    except Exception as e:
        log(f"Error setting field '{field_name}' on {obj}: {e}\n{traceback.format_exc()}")
        return False


def _java_lang_class(clazz: Any) -> Optional[Any]:
    if isinstance(clazz, JavaClass):
        return clazz.getClass()
    if isinstance(clazz, _JavaLangClass):
        return clazz
    return None


_PRIMITIVE_BOXES = {
    "boolean": "java.lang.Boolean", "byte": "java.lang.Byte", "char": "java.lang.Character",
    "short": "java.lang.Short", "int": "java.lang.Integer", "long": "java.lang.Long",
    "float": "java.lang.Float", "double": "java.lang.Double", "void": "java.lang.Void",
}


def _class_for_name(name: str) -> Any:
    base = name.strip()
    dimensions = 0
    while base.endswith("[]"):
        base = base[:-2].rstrip()
        dimensions += 1
    if base in _PRIMITIVE_BOXES:
        java_class = jclass(_PRIMITIVE_BOXES[base]).TYPE
    else:
        loader = jclass("org.telegram.messenger.ApplicationLoader").applicationContext.getClassLoader()
        java_class = _JavaLangClass.forName(base, False, loader)
    array = jclass("java.lang.reflect.Array")
    for _ in range(dimensions):
        java_class = array.newInstance(java_class, 0).getClass()
    return java_class


def java_class(clazz: Any) -> Optional[Any]:
    """Returns the `java.lang.Class` for a class name, a `find_class(...)` result or a `Class`.

    Names may be primitive (`"int"`), arrays (`"boolean[]"`, `"[Z"`) or binary names of nested
    classes (`"org.telegram.tgnet.TLRPC$User"`). Returns None if the class cannot be found.
    """
    if isinstance(clazz, str):
        try:
            return _class_for_name(clazz)
        except Exception as e:
            log(f"Error finding class '{clazz}': {e}")
            return None
    return _java_lang_class(clazz)


def _parameter_classes(param_types) -> Optional[list]:
    classes = []
    for param_type in param_types:
        resolved = java_class(param_type)
        if resolved is None:
            return None
        classes.append(resolved)
    return classes


def get_method(clazz: Any, method_name: str, *param_types: Any) -> Optional[Any]:
    """Finds a method declared in `clazz` or its superclasses and makes it accessible.

    `clazz` and every parameter type accept anything `java_class` accepts.
    """
    owner = java_class(clazz)
    params = _parameter_classes(param_types)
    if owner is None or params is None:
        return None
    current = owner
    while current is not None:
        try:
            method = current.getDeclaredMethod(method_name, *params)
            method.setAccessible(True)
            return method
        except Exception:
            current = current.getSuperclass()
    log(f"Method {method_name}({', '.join(p.getName() for p in params)}) not found in {owner.getName()}")
    return None


def get_constructor(clazz: Any, *param_types: Any) -> Optional[Any]:
    """Finds a constructor of `clazz` and makes it accessible."""
    owner = java_class(clazz)
    params = _parameter_classes(param_types)
    if owner is None or params is None:
        return None
    try:
        constructor = owner.getDeclaredConstructor(*params)
        constructor.setAccessible(True)
        return constructor
    except Exception:
        log(f"Constructor ({', '.join(p.getName() for p in params)}) not found in {owner.getName()}")
        return None


def get_field(clazz: Any, field_name: str) -> Optional[Any]:
    """Finds a field declared in `clazz` or its superclasses and makes it accessible."""
    owner = java_class(clazz)
    if owner is None:
        return None
    field = _find_declared_field(owner, field_name)
    if field is None:
        log(f"Field '{field_name}' not found in {owner.getName()}")
        return None
    field.setAccessible(True)
    return field


def _find_declared_field(java_class: Any, field_name: str) -> Optional[Any]:
    while java_class is not None:
        try:
            return java_class.getDeclaredField(field_name)
        except Exception:
            java_class = java_class.getSuperclass()
    return None


def get_static_private_field(clazz: JavaClass, field_name: str) -> Optional[Any]:
    java_class = _java_lang_class(clazz)
    if java_class is None or not isinstance(field_name, str):
        return None

    try:
        field = _find_declared_field(java_class, field_name)
        if field is None:
            return None

        field.setAccessible(True)
        return field.get(None)
    except Exception as e:
        log(f"Error accessing static field '{field_name}' from {java_class.getName()}: {e}\n{traceback.format_exc()}")
        return None


def set_static_private_field(clazz: JavaClass, field_name: str, new_value: Any) -> bool:
    java_class = _java_lang_class(clazz)
    if java_class is None or not isinstance(field_name, str):
        return False

    try:
        field = _find_declared_field(java_class, field_name)
        if field is None:
            log(f"Field '{field_name}' not found in class hierarchy of {java_class.getName()}")
            return False

        field.setAccessible(True)
        field.set(None, new_value)
        return True
    except Exception as e:
        log(f"Error setting static field '{field_name}' on {java_class.getName()}: {e}\n{traceback.format_exc()}")
        return False
