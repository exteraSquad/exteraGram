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

import builtins
import contextvars
import importlib
import importlib.abc
import importlib.util
import marshal
import sys
import types
from pathlib import Path
from types import ModuleType
from typing import Any, Optional

import elyxcore as elyx_api
from elyxcore._plugin import ElyxPlugin, _read_wheel_roots
from elyxcore.assets import load_data_file
from elyxcore.utils import LazyDict

PLUGIN_NAMESPACE = "ElyxPlugins"
PUBLIC_MODULE = "elyx"
_DATA_SUFFIXES = frozenset((".json", ".yaml", ".yml", ".txt"))
_EXTERNAL_ROOTS = frozenset((
    "android", "androidx", "base_plugin", "client_utils", "com", "de",
    "elyx", "hook_utils", "importlib", "java", "org", "ui",
))
_MISSING = object()


def _patch_code_filename(code_obj, filename: str):
    constants = tuple(
        _patch_code_filename(value, filename)
        if isinstance(value, types.CodeType)
        else value
        for value in code_obj.co_consts
    )
    return code_obj.replace(co_filename=filename, co_consts=constants)


class _DataModule(ModuleType):
    def __getitem__(self, key):
        return self._data[key]

    def get(self, key, default=None):
        return self._data.get(key, default)

    def __contains__(self, key):
        return key in self._data

    def __str__(self):
        return str(self._data)


class _ElyxEnvironment(ModuleType):
    """Shared module whose plugin-specific values are resolved from the caller."""

    def __init__(self, owner: "ElyxImporter"):
        super().__init__(PUBLIC_MODULE)
        self.__dict__["_owner"] = owner
        self.__dict__.update(owner.public_environment)
        self.__dict__["__all__"] = elyx_api.__all__
        self.__dict__["__doc__"] = elyx_api.__doc__

    def __getattr__(self, name):
        plugin = self._owner.get_caller_plugin()
        if plugin is not None:
            environment = plugin.get_environment_vars()
            if name in environment:
                return environment[name]
        raise AttributeError(name)

    def import_module(self, name: str, package: Optional[str] = None):
        return self._owner.import_module(name, package)


class ElyxImporter(importlib.abc.MetaPathFinder, importlib.abc.Loader):
    def __init__(self):
        self.public_environment = elyx_api._public_api()
        self._filename_roots: dict[str, Optional[Path]] = {}
        self._path_cache: dict[tuple[str, str], Any] = {}
        self._wheel_roots: dict[str, tuple[Path, ...]] = {}
        self._builtins_by_plugin: dict[str, dict] = {}
        self._active_plugin_id = contextvars.ContextVar(
            "elyx_active_plugin_id",
            default=None,
        )

    @staticmethod
    def _plugins_dir() -> Optional[Path]:
        try:
            from elyxcore._plugin_engine import ElyxEngine
            instance = ElyxEngine.instance
            return Path(instance.plugins_dir_path) if instance is not None else None
        except Exception:
            return None

    def invalidate_caches(self):
        self._filename_roots.clear()
        self._path_cache.clear()
        self._wheel_roots.clear()

    def _plugin_for_root(self, root: Path) -> Optional[ElyxPlugin]:
        return ElyxPlugin.instances_by_id.get(root.name)

    def _root_from_filename(self, filename: str) -> Optional[Path]:
        cached = self._filename_roots.get(filename, _MISSING)
        if cached is not _MISSING:
            return cached

        result = None
        try:
            path = Path(filename).resolve()
            plugins_dir = self._plugins_dir()
            if plugins_dir is not None:
                try:
                    relative = path.relative_to(plugins_dir)
                    if relative.parts:
                        candidate = plugins_dir / relative.parts[0]
                        if candidate.is_dir() and candidate.name != "archives":
                            result = candidate
                except ValueError:
                    pass
        except Exception:
            pass

        self._filename_roots[filename] = result
        return result

    def get_caller_root(self) -> Optional[Path]:
        plugins_dir = self._plugins_dir()
        active_plugin_id = self._active_plugin_id.get()
        if active_plugin_id and plugins_dir is not None:
            root = plugins_dir / active_plugin_id
            if root.is_dir():
                return root

        try:
            frame = sys._getframe()
        except (AttributeError, ValueError):
            return None

        while frame is not None:
            module_name = frame.f_globals.get("__name__", "")
            if module_name.startswith(PLUGIN_NAMESPACE + "."):
                parts = module_name.split(".")
                if len(parts) >= 2:
                    if plugins_dir is not None:
                        root = plugins_dir / parts[1]
                        if root.is_dir():
                            return root

            filename = frame.f_code.co_filename
            if filename and filename not in ("<string>", "<ElyxPluginEnvironment>"):
                root = self._root_from_filename(filename)
                if root is not None:
                    return root
            frame = frame.f_back
        return None

    def get_caller_plugin(self) -> Optional[ElyxPlugin]:
        root = self.get_caller_root()
        return self._plugin_for_root(root) if root is not None else None

    def import_module(self, name: str, package: Optional[str] = None):
        """Import a local module without creating an unqualified sys.modules alias."""
        if name.startswith("."):
            if not package:
                raise TypeError("package is required for a relative import")
            return importlib.import_module(name, package)

        root = self.get_caller_root()
        if root is not None and self._resolve_path(root, name) is not None:
            return importlib.import_module(f"{PLUGIN_NAMESPACE}.{root.name}.{name}")
        return importlib.import_module(name, package)

    def _get_wheel_roots(self, plugin_id: str) -> tuple[Path, ...]:
        cached = self._wheel_roots.get(plugin_id)
        if cached is not None:
            return cached
        roots = _read_wheel_roots(plugin_id)
        self._wheel_roots[plugin_id] = roots
        return roots

    def _resolve_path(self, root: Path, relative_name: str):
        cache_key = (str(root), relative_name)
        cached = self._path_cache.get(cache_key, _MISSING)
        if cached is not _MISSING:
            return cached

        relative_path = Path(*relative_name.split(".")) if relative_name else Path()
        candidates = (root / relative_path,)
        if relative_name:
            candidates += tuple(
                wheel_root / relative_path
                for wheel_root in self._get_wheel_roots(root.name)
            )

        result = None
        for candidate in candidates:
            py_file = candidate.with_suffix(".py")
            pyc_file = candidate.with_suffix(".pyc")
            init_file = candidate / "__init__.py"
            init_pyc = candidate / "__init__.pyc"
            if py_file.is_file():
                result = (py_file, "source", False)
            elif pyc_file.is_file():
                result = (pyc_file, "bytecode", False)
            elif init_file.is_file():
                result = (init_file, "source", True)
            elif init_pyc.is_file():
                result = (init_pyc, "bytecode", True)
            elif candidate.is_dir():
                result = (candidate, "namespace", True)
            else:
                for suffix in _DATA_SUFFIXES:
                    data_file = candidate.with_suffix(suffix)
                    if data_file.is_file():
                        result = (data_file, "data", False)
                        break
            if result is not None:
                break

        self._path_cache[cache_key] = result
        return result

    def _spec(self, fullname: str, root: Path, relative_name: str):
        resolved = self._resolve_path(root, relative_name)
        if resolved is None:
            return None
        origin, kind, is_package = resolved
        spec = importlib.util.spec_from_loader(
            fullname,
            self,
            origin=str(origin),
            is_package=is_package,
        )
        if spec is not None:
            spec.loader_state = {
                "kind": kind,
                "plugin_id": root.name,
                "plugin_root": str(root),
            }
            if is_package:
                package_dir = origin if origin.is_dir() else origin.parent
                spec.submodule_search_locations = [str(package_dir)]
        return spec

    def find_spec(self, fullname: str, path=None, target=None):
        if fullname == PUBLIC_MODULE:
            return importlib.util.spec_from_loader(fullname, self, origin="<ElyxPluginEnvironment>")

        if fullname == PLUGIN_NAMESPACE:
            plugins_dir = self._plugins_dir()
            if plugins_dir is None:
                return None
            spec = importlib.util.spec_from_loader(
                fullname, self, origin=str(plugins_dir), is_package=True
            )
            if spec is not None:
                spec.loader_state = {"kind": "namespace", "plugin_id": None}
                spec.submodule_search_locations = [str(plugins_dir)]
            return spec

        namespace_prefix = PLUGIN_NAMESPACE + "."
        if fullname.startswith(namespace_prefix):
            parts = fullname.split(".")
            if len(parts) < 2:
                return None
            plugins_dir = self._plugins_dir()
            if plugins_dir is None:
                return None
            root = plugins_dir / parts[1]
            if not root.is_dir():
                return None
            return self._spec(fullname, root, ".".join(parts[2:]))

        root_name = fullname.split(".", 1)[0]
        if root_name in _EXTERNAL_ROOTS:
            return None

        return None

    def create_module(self, spec):
        if spec.name == PUBLIC_MODULE:
            return _ElyxEnvironment(self)
        return None

    def _plugin_builtins(self, plugin_id: str) -> dict:
        cached = self._builtins_by_plugin.get(plugin_id)
        if cached is not None:
            return cached

        real_import = builtins.__import__
        values = dict(vars(builtins))

        def scoped_import(name, globals=None, locals=None, fromlist=(), level=0):
            if level != 0 or not name:
                return real_import(name, globals, locals, fromlist, level)

            root_name = name.split(".", 1)[0]
            root = self._plugins_dir()
            plugin_root = root / plugin_id if root is not None else None
            if (
                root_name not in _EXTERNAL_ROOTS
                and plugin_root is not None
                and self._resolve_path(plugin_root, name) is not None
            ):
                qualified = f"{PLUGIN_NAMESPACE}.{plugin_id}.{name}"
                module = importlib.import_module(qualified)
                if fromlist:
                    return module
                top_local = name.split(".", 1)[0]
                return sys.modules[f"{PLUGIN_NAMESPACE}.{plugin_id}.{top_local}"]

            return real_import(name, globals, locals, fromlist, level)

        values["__import__"] = scoped_import
        self._builtins_by_plugin[plugin_id] = values
        return values

    def exec_module(self, module):
        spec = module.__spec__
        state = getattr(spec, "loader_state", None) or {}
        kind = state.get("kind")

        if module.__name__ == PUBLIC_MODULE:
            module.__dict__.update(self.public_environment)
            return
        if kind == "namespace":
            return

        origin = Path(spec.origin)
        plugin_id = state.get("plugin_id")
        if plugin_id:
            module.__dict__["__builtins__"] = self._plugin_builtins(plugin_id)

        active_plugin_token = (
            self._active_plugin_id.set(plugin_id)
            if plugin_id
            else None
        )
        try:
            if kind == "source":
                code = compile(origin.read_text(encoding="utf-8"), str(origin), "exec")
                exec(code, module.__dict__)
            elif kind == "bytecode":
                raw = origin.read_bytes()
                if len(raw) < 16 or raw[:4] != importlib.util.MAGIC_NUMBER:
                    raise ImportError(f"Incompatible bytecode file: {origin}")
                code = marshal.loads(raw[16:])
                code = _patch_code_filename(code, str(origin.with_suffix(".py")))
                exec(code, module.__dict__)
            elif kind == "data":
                if origin.suffix in (".json", ".yaml", ".yml"):
                    data = load_data_file(origin)
                    module.__class__ = _DataModule
                    module._data = data
                    for key, value in data.items():
                        setattr(module, key, LazyDict(value) if isinstance(value, dict) else value)
                else:
                    module.content = origin.read_text(encoding="utf-8")
        except Exception as exc:
            sys.modules.pop(module.__name__, None)
            raise ImportError(f"Failed to load Elyx module {module.__name__} from {origin}") from exc
        finally:
            if active_plugin_token is not None:
                self._active_plugin_id.reset(active_plugin_token)

    def unload_plugin(self, plugin_id: str):
        prefix = f"{PLUGIN_NAMESPACE}.{plugin_id}"
        for name in tuple(sys.modules):
            if name == prefix or name.startswith(prefix + "."):
                sys.modules.pop(name, None)
        self._builtins_by_plugin.pop(plugin_id, None)
        self.invalidate_caches()

    def unload_all(self):
        prefix = PLUGIN_NAMESPACE + "."
        for name in tuple(sys.modules):
            if name == PLUGIN_NAMESPACE or name.startswith(prefix):
                sys.modules.pop(name, None)
        sys.modules.pop(PUBLIC_MODULE, None)
        self._builtins_by_plugin.clear()
        self.invalidate_caches()


importer = ElyxImporter()


def setup():
    sys.modules.pop(PUBLIC_MODULE, None)

    while importer in sys.meta_path:
        sys.meta_path.remove(importer)
    sys.meta_path.insert(0, importer)
    importer.invalidate_caches()


def stop():
    while importer in sys.meta_path:
        sys.meta_path.remove(importer)
    importer.unload_all()
