# This is the source code of the exteraGram plugins SDK.
#
# We do not and cannot prevent the use of our code,
# but be respectful and credit the original author.
#
# Copyright @exteraGram, 2022-2026
#
# Licensed under the GNU GPL v. 3 or later, with the additional terms
# in ADDITIONAL_TERMS.md at the root of the repository.

from __future__ import annotations

import importlib.abc
import importlib.machinery
import sys
import types
from pathlib import Path

SRC = Path(__file__).resolve().parents[1] / "src"
JAVA_ROOTS = {"android", "androidx", "com", "org", "de", "java", "dalvik", "kotlin", "kotlinx"}


class Stub:
    def __init__(self, name: str = "stub"):
        self._name = name

    def __getattr__(self, name: str):
        if name == "__name__":
            return self._name.rsplit(".", 1)[-1]
        if name.startswith("__") and name.endswith("__"):
            raise AttributeError(name)
        return Stub(f"{self._name}.{name}")

    def __call__(self, *args, **kwargs):
        if self._name.endswith(("dynamic_proxy", "static_proxy")):
            return type("Proxy", (object,), {})
        if len(args) == 1 and callable(args[0]) and not kwargs and not isinstance(args[0], Stub):
            return args[0]
        return Stub(f"{self._name}()")

    def __mro_entries__(self, bases):
        return (object,)

    def __getitem__(self, key):
        return Stub(f"{self._name}[]")

    def __iter__(self):
        return iter(())

    def __or__(self, other):
        return self

    __ror__ = __or__

    def __repr__(self):
        return f"<Stub {self._name}>"

    def __str__(self):
        return "/nonexistent/stub"

    def __fspath__(self):
        return "/nonexistent/stub"


class StubModule(types.ModuleType):
    def __getattr__(self, name: str):
        if name.startswith("__") and name.endswith("__"):
            raise AttributeError(name)
        return Stub(f"{self.__name__}.{name}")


class StubFinder(importlib.abc.MetaPathFinder, importlib.abc.Loader):
    def __init__(self, names: set[str]):
        self.names = names

    def find_spec(self, fullname, path, target=None):
        if fullname.split(".")[0] in JAVA_ROOTS or fullname in self.names:
            return importlib.machinery.ModuleSpec(fullname, self, is_package=True)
        return None

    def create_module(self, spec):
        module = StubModule(spec.name)
        module.__path__ = []
        return module

    def exec_module(self, module):
        pass


def install(*sdk_stubs: str) -> None:
    if str(SRC) not in sys.path:
        sys.path.insert(0, str(SRC))
    sys.meta_path.insert(0, StubFinder(set(sdk_stubs)))
