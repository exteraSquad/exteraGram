# This is the source code of the exteraGram plugins SDK.
#
# We do not and cannot prevent the use of our code,
# but be respectful and credit the original author.
#
# Copyright @exteraGram, 2022-2026
#
# Licensed under the GNU GPL v. 3 or later, with the additional terms
# in ADDITIONAL_TERMS.md at the root of the repository.

import io
import traceback
from typing import Callable, Optional, Any, Union

from PIL import Image
from org.mvel2 import MVEL  # noqa

from java.lang import Class
from client_utils import run_on_queue
from java import dynamic_proxy  # noqa
from android_utils import log as logcat
from java.util import HashMap


_generated_classes: dict[tuple[str, str, bool, Any], type] = {}

def gen(java_class: Union[Class, type[Class]], method_name: str, return_value: bool = False, default_value=None):
    """
    Generate class implements an interface

    Args:
        java_class: java-interface
        method_name (str): name of method for override it
        return_value (bool, default is False): return your result
        default_value (optional, default is None): return default_value if your func raised exception
    """
    if default_value and not return_value:
        raise ValueError("provide default_value only if return_value is True")
    NewType = _generated_classes.get((java_class.getClass().toString(), method_name, return_value, default_value), None)
    if NewType:
        return NewType

    def _run(instance, *java_args):
        try:
            value = instance._fn(*java_args, *instance._args, **instance._kwargs)  # noqa
            if return_value:
                return value
        except Exception:  # noqa
            logcat("[ERROR] [ElyxCore] " + traceback.format_exc())
            if return_value:
                return default_value

    def __init__(self, fn, *args, **kwargs):
        self._fn = fn
        self._args = args
        self._kwargs = kwargs
        super(type(self), self).__init__()

    NewType = type('GeneratedProxyClass_' + java_class.__name__, (dynamic_proxy(java_class),), {
        '__init__': __init__,
        method_name: _run,
    })
    _generated_classes[(java_class.getClass().toString(), method_name, return_value, default_value)] = NewType
    return NewType


def gen2(java_class, return_value: bool = False, **methods):
    def _run(method: Callable):
        def _fn(instance, *java_args):
            try:
                value = method(*java_args, *instance._args, **instance._kwargs)  # noqa
                if return_value:
                    return value
            except Exception:  # noqa
                logcat("[ERROR] [ElyxCore]" + traceback.format_exc())

        return _fn

    def __init__(self, *args, **kwargs):
        self._args = args
        self._kwargs = kwargs
        super(type(self), self).__init__()

    return type('GeneratedProxyClass_' + java_class.__name__, (dynamic_proxy(java_class),), {
        '__init__': __init__,
        **{
            method_name: _run(method)
            for method_name, method in methods.items()
        },
    })


_mvel_compiled = {}

def mvel_execute(script: str, data: Optional[dict | HashMap], to_type=None, java_instance=None):
    """
    Execute MVEL script

    Args:
        script (str): MVEL script
        data (dict | HashMap, optional): dictionary with keys and values for script
        to_type (JavaClass, optional): convert result automatically to custom JavaClass
        java_instance (optional): instance of java class for use `this` in script
    """
    compiled = _mvel_compiled.get(script, None)
    if not compiled:
        compiled = MVEL.compileExpression(script)
        _mvel_compiled[script] = compiled

    if isinstance(data, dict):
        hashmap = HashMap()
        for k, v in data.items():
            hashmap.put(k, v)
    elif isinstance(data, HashMap):
        hashmap = data
    else:
        raise ValueError("data must be an instance of dict or java.util.HashMap")

    args = []
    if java_instance:
        args.append(java_instance)
    args.append(hashmap)
    if to_type:
        args.append(to_type)

    return MVEL.executeExpression(compiled, *args)


class LazyDict(dict):
    def __setattr__(self, key, value):
        self.__setitem__(key, value)

    def __getattr__(self, item):
        return self.__getitem__(item)


def safe_run_on_queue(fn):
    def _fn():
        try:
            fn()
        except Exception:  # noqa
            logcat("[ERROR] [Elyx:RunOnQueue] " + traceback.format_exc())

    run_on_queue(_fn)


def safe_func(fn):
    def _fn(*args, **kwargs):
        try:
            # stack = " | ".join([ff.filename + ":" + str(ff.lineno) + " in " + ff.function for ff in inspect.stack()])
            # logcat(f"[INFO] [Elyx:SafeFunc] {fn.__name__}({args=}, {kwargs=}) called {stack}")
            result = fn(*args, **kwargs)
            # logcat(f"[INFO] [Elyx:SafeFunc] {fn.__name__}({args=}, {kwargs=}) returned {result} successfully")
            return result
        except Exception:  # noqa
            logcat(f"[ERROR] [Elyx:SafeFunc] {fn.__name__}({args=}, {kwargs=}) failed" + traceback.format_exc())

    return _fn


def hash_map(dict_):
    hash_map_ = HashMap()
    for k, v in dict_.items():
        hash_map_.put(k, v)
    return hash_map_


def resize_image(image: bytes, width: int, height: int) -> bytes:
    img = Image.open(io.BytesIO(image))
    resized_image = img.resize((width, height), Image.Resampling.LANCZOS)

    output = io.BytesIO()
    resized_image.save(output, format="PNG")
    return output.getvalue()
