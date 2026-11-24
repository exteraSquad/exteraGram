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

import hashlib
import json
import os
import re
import shutil
import sys
import traceback
import warnings
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple, Type, Union
from zipfile import ZipFile, ZipInfo

import yaml
from androidx.core.content import ContextCompat
from com.chaquo.python import PyException
from com.exteragram.messenger.plugins import Plugin, PluginsController
from com.exteragram.messenger.plugins.hooks import PluginsHooks
from com.exteragram.messenger.plugins.models import CustomSetting, DividerSetting, EditTextSetting, HeaderSetting, \
    InputSetting, SelectorSetting, SwitchSetting, TextSetting
from com.exteragram.messenger.plugins.pip import PipController
from com.exteragram.messenger.plugins.ui import PluginSettingsActivity, PluginsActivity
from com.exteragram.messenger.plugins.ui.components import InstallPluginBottomSheet, PluginCellDelegate, \
    PluginRequirementsView
from de.robv.android.xposed import XposedBridge
from org.mvel2 import MVEL
from packaging.version import Version

from elyxcore.assets import Assets, load_data_file
from elyxcore.settings import SettingsController
from elyxcore.utils import gen
from android.view import View
from android_utils import log as logcat
from base_plugin import BasePlugin
from java import dynamic_proxy, jarray, jbyte, jclass, jfloat, jint, jlong
from java.lang import Runnable as JavaRunnable
from java.util import Arrays
from org.telegram.messenger import BuildVars, SharedConfig, Utilities

warnings.filterwarnings("ignore", category=SyntaxWarning)

ELYX_ID = "elyxcore"
_debug_logs = False


def dlog(msg: str) -> None:
    if _debug_logs:
        logcat(msg)


OnClickListener = gen(View.OnClickListener, "onClick")
Runnable = gen(JavaRunnable, "run")
Callback = gen(Utilities.Callback, "run")
Callback2 = gen(Utilities.Callback2, "run")
Callback3 = gen(Utilities.Callback3, "run")
CallbackReturn = gen(Utilities.CallbackReturn, "run", True)

from elyxcore.localization import Strings

ALLOWED_METAINFO = [
    "name", "description", "version", "icon", "id", "author",
    "min_version", "app_version", "sdk_version", "requires",
    "requirements", "category",
]
BASE_METAINFO = {
    "description": "Description not provided",
    "version": "1.0.0",
    "icon": "",
    "author": "Unknown author",
    "min_version": "",
    "app_version": "",
    "sdk_version": "",
    "requires": {},
    "requirements": "",
}
ENGINE_ID = "elyx-py"
REFMAP_FILES = ("refmap.yaml", "refmap.yml", "refmap.json")
METAINFO_FILES = ("metainfo.yaml", "metainfo.yml", "metainfo.json")

############### Elyx Plugin PART ################

class ArchiveEncryptionError(Exception):
    """Exception for archive encryption."""


class SecurityError(Exception):
    """Exception for an unsafe path in a bundled wheel."""


class ValidationError(Exception):
    """Exception for invalid plugin structure."""


class InvalidPluginError(Exception):
    """Exception for invalid plugin."""

    @classmethod
    def plugin_class_not_found(cls, main_file_path):
        return cls(
            f"Could not find a class inheriting from BasePlugin in {main_file_path}. Make sure your main file has a plugin class extends BasePlugin.")


class RequiredPlugin(Exception):
    """Exception for required plugin."""

    def __init__(self, plugin_id, required_version: Optional[str] = None, link_to_download: Optional[str] = None,
                 current_version=None):
        self.plugin_id = plugin_id
        self.required_version = required_version
        self.link_to_download = link_to_download
        self.current_version = current_version
        super().__init__(f"Plugin {plugin_id} " + (
            f"version {required_version} or higher " if required_version else "") + "is required." + (
                             f" Current version: {current_version}" if current_version else ""))


# noinspection SpellCheckingInspection
class ElyxPlugin:
    instances: Dict[str, "ElyxPlugin"] = {}
    instances_by_id: Dict[str, "ElyxPlugin"] = {}

    def __new__(cls, dir_path: Optional[str] = None, plzip: Optional[ZipFile] = None, pwd: Optional[str] = None,
                raise_errors: bool = True):
        if not dir_path and not plzip:
            raise ValueError("Either dir_path or plzip must be provided")

        id_ = str(Path(dir_path).absolute()) if dir_path else plzip.filename
        if id_ not in cls.instances:
            cls.instances[id_] = super().__new__(cls)

        return cls.instances[id_]

    @classmethod
    def from_archive(cls, plzip: ZipFile):
        return cls(plzip=plzip)

    @classmethod
    def read_metainfo(cls, plzip: ZipFile) -> Tuple[Dict[str, Any], Dict[str, Dict[str, Any]]]:
        plugin = object.__new__(cls)
        plugin.__refmap = None
        plugin.__metainfo = None
        plugin.__strings = None
        plugin.dir_path = None
        plugin.plzip = plzip
        plugin.is_archive = True
        files = {i.filename: i for i in plzip.infolist()}
        plugin.get_refmap(files)
        return plugin.get_metainfo(files, with_defaults=False), plugin.get_strings(files)

    @staticmethod
    def is_plugin_dir(dir_path) -> bool:
        path = Path(dir_path)
        return any(path.joinpath(name).is_file() for name in REFMAP_FILES + METAINFO_FILES)

    def __init__(self, dir_path: Optional[str] = None, plzip: Optional[ZipFile] = None, pwd: Optional[str] = None,
                 raise_errors: bool = True):
        if not dir_path and not plzip:
            raise ValueError("Either dir_path or plzip must be provided")

        self.__structure: Optional[ElyxPlugin.Structure] = None
        self.__metainfo: Optional[Dict[str, Any]] = None
        self.__strings: Optional[Dict[str, Dict[str, Any]]] = None
        self.__refmap: Optional[Dict[str, Union[Dict[str, str], str]]] = None
        self.__validation_result: Any = None
        self.instance: Optional[BasePlugin] = None
        self.plugin_class: Optional[Type[BasePlugin]] = None
        self.errors: List[Tuple[BaseException, str]] = []

        self.raise_errors = raise_errors
        self.dir_path = dir_path
        self.plzip = plzip
        self.is_archive = plzip is not None

        if self.is_archive and pwd:
            self.plzip.setpassword(pwd.encode())

        self.id = Path(dir_path).name if dir_path else plzip.filename

        self.init()

    @dataclass
    class Structure:
        refmap: Dict[str, Dict[str, str]] = field(default_factory=dict)
        metainfo: Dict[str, Any] = field(default_factory=dict)
        assets: str = ""
        strings: Dict[str, Dict[str, Any]] = field(default_factory=dict)
        main_file_path: str = ""
        wheels_path: str = ""
        validation_result: Any = None

    @property
    def validation_result(self):
        return self.__validation_result

    @property
    def structure(self) -> Structure:
        return self.__structure

    def init(self):
        self.errors = []
        self.plugin_class = None
        self.instance = None
        self.__validation_result = None
        self.__refmap = None
        self.__strings = None
        self.__metainfo = None
        self.__structure = None

        self.load_structure()

    def get_environment_vars(self) -> Dict[str, Any]:
        vars_ = {
            "settings": SettingsController(self.id)
        }
        if self.__refmap is not None:
            vars_["refmap"] = self.__refmap
        if self.__metainfo is not None:
            vars_["metainfo"] = self.__metainfo
        if self.__structure is not None and self.__structure.assets:
            vars_["assets"] = Assets(Path(self.dir_path).joinpath(self.__structure.assets))
        plugin_strings = self.get_strings()
        if plugin_strings:
            vars_["strings"] = Strings(plugin_strings)

        return vars_

    def _raise(self, e, force_raise: bool = False):
        if self.raise_errors:
            logcat(f"[ERROR] [ElyxPlugin] {e}")
            raise e

        try:
            raise e
        except:  # noqa
            error = traceback.format_exc()
            logcat(f"[ERROR] [ElyxPlugin] {e}\n{error}")
            if force_raise:
                raise

            self.errors.append((e, error))

    def get_strings(self, files=None):
        if self.__strings is not None:
            return self.__strings

        if self.is_archive:
            result = {}
            strings_path = self.__refmap.get("strings") if self.__refmap else None
            if strings_path and self.plzip:
                prefix = strings_path.rstrip("/") + "/"
                for entry in self.plzip.infolist():
                    name = entry.filename
                    if not name.startswith(prefix) or entry.is_dir():
                        continue
                    basename = name[len(prefix):]
                    if "/" in basename:
                        continue
                    parts = basename.split(".")[0].split("_")
                    locale = parts[-1] if len(parts) > 1 else "en"
                    if locale not in result:
                        result[locale] = {}
                    try:
                        raw = self.plzip.read(name).decode("utf-8")
                        if basename.endswith(".json"):
                            result[locale].update(json.loads(raw))
                        elif basename.endswith((".yaml", ".yml")):
                            result[locale].update(yaml.safe_load(raw))
                    except Exception:
                        pass
            self.__strings = result
            return result

        result = None

        if self.__refmap:
            fpath: Optional[str] = self.__refmap.get("strings", None)
            if fpath:
                path = Path(self.dir_path).joinpath(fpath)
                if path.exists():
                    if path.is_file():
                        if any(fpath.endswith(ext) for ext in [".json", ".yaml", ".yml"]):
                            result = {
                                "en": {
                                    k.replace("__", ""): v
                                    for k, v in load_data_file(path).items()
                                }
                            }
                    elif path.is_dir():
                        _strings = {}
                        for file_name in path.iterdir():
                            if file_name.is_file():
                                parts = file_name.name.split(".")[0].split("_")
                                if len(parts) > 1:
                                    locale = parts[-1]  # strings_<locale>.<ext>
                                else:
                                    locale = "en"

                                if locale not in _strings:
                                    _strings[locale] = {}

                                if file_name.name.endswith(".json") or file_name.name.endswith(
                                        ".yaml") or file_name.name.endswith(".yml"):
                                    _strings[locale].update(load_data_file(file_name))
                        result = _strings

        if result is None and files:
            for file_name, f in files.items():
                if file_name in ["strings.yaml", "strings.yml", "strings.json"]:
                    result = {"en": load_data_file(f)}
                    break

            if result is None:
                path = Path(self.dir_path).joinpath("strings")
                if path and path.is_dir():
                    _strings = {}
                    for file_name in path.iterdir():
                        if file_name.is_file():
                            parts = file_name.name.split(".")[0].split("_")
                            if len(parts) > 1:
                                locale = parts[-1]  # strings_<locale>.<ext>
                            else:
                                locale = "en"

                            if locale not in _strings:
                                _strings[locale] = {}

                            if file_name.name.endswith(".json") or file_name.name.endswith(
                                    ".yaml") or file_name.name.endswith(".yml"):
                                _strings[locale].update(load_data_file(file_name))

                    result = _strings

        if result is None:
            result = {}

        self.__strings = result
        return result

    def load_structure(self):
        if self.is_archive:
            files = {
                i.filename: i
                for i in self.plzip.infolist()
            }
            if not files:
                raise Exception("Archive is empty")

            _files = self.plzip.infolist()
            file = 0
            while _files[file].is_dir():
                file += 1

            if _files[file].flag_bits & (1 << 0):
                if not self.plzip.pwd:
                    raise ArchiveEncryptionError("Archive is encrypted and password is not provided")

                try:
                    _ = self.plzip.read(_files[file].filename)
                except RuntimeError as e:
                    if "bad password" in str(e).lower():
                        raise ArchiveEncryptionError("Archive is encrypted and password is incorrect")
                    else:
                        return self._raise(e)
                except Exception as e:
                    return self._raise(e)
        else:
            files = {
                i.name: i
                for i in Path(self.dir_path).iterdir()
            }

        refmap = self.get_refmap(files)
        metainfo = self.get_metainfo(files)
        if not metainfo:
            return self._raise(ValidationError("Metainfo not found or must be empty!"))

        try:
            validation_result = self.get_validation_result()
        except Exception as e:
            return self._raise(e)

        if not self.is_archive:
            self.instances_by_id[metainfo["id"]] = self

        if "main" in refmap:
            main_file_path = refmap["main"]
        else:
            main_file_path = "main.py" if (
                                                  not self.is_archive and Path(self.dir_path).joinpath(
                                              "main.py").exists()
                                          ) or (self.is_archive and "main.py" in files) else None

        if "assets" in refmap:
            assets_path = refmap["assets"]
        else:
            assets_path = "assets" if (
                                              not self.is_archive and Path(self.dir_path).joinpath("assets").exists()
                                      ) or (self.is_archive and "assets/" in files) else None

        wheels_path = refmap["wheels"] if "wheels" in refmap else None

        strings_dict = self.get_strings(files)

        self.__structure = self.Structure(
            refmap=refmap,
            metainfo=metainfo,
            assets=assets_path,
            strings=strings_dict,
            main_file_path=main_file_path,
            wheels_path=wheels_path,
            validation_result=validation_result
        )
        self.validate(files)

    def validate(self, files: Dict[str, Union[ZipInfo, Path]]):
        structure = self.__structure
        if not structure.metainfo:
            self._raise(ValidationError("Metainfo not found!"))

        if not structure.main_file_path:
            self._raise(ValidationError("Main file not found!"))
        else:
            if self.is_archive:
                if structure.main_file_path not in files:
                    self._raise(ValidationError(f"Main file with path \"{structure.main_file_path}\" not found!"))
            elif not Path(self.dir_path).joinpath(structure.main_file_path).exists():
                self._raise(ValidationError(f"Main file with path \"{structure.main_file_path}\" not found!"))

        if "assets" in structure.refmap:
            if self.is_archive:
                if structure.assets + "/" not in files:
                    self._raise(ValidationError("Assets directory not found!"))
            elif not Path(self.dir_path).joinpath(structure.assets).exists():
                self._raise(ValidationError("Assets directory not found!"))

        if "strings" in structure.refmap:
            if not self.is_archive and not structure.strings:
                self._raise(ValidationError(f"Strings with path \"{structure.refmap['strings']}\" not found!"))

        if "wheels" in structure.refmap:
            if self.is_archive:
                if structure.wheels_path + "/" not in files:
                    self._raise(ValidationError("Wheels directory not found!"))
            elif not Path(self.dir_path).joinpath(structure.wheels_path).exists():
                self._raise(ValidationError("Wheels directory not found!"))

    def get_file_content(self, f: Optional[Union[ZipInfo, Path]] = None, path: Optional[str] = None) -> str:
        try:
            if path is not None:
                return Path(self.dir_path).joinpath(path).read_text() if not self.is_archive else self.plzip.read(
                    path).decode()

            if isinstance(f, ZipInfo):
                return self.plzip.read(f.filename).decode()
            elif isinstance(f, Path):
                return f.read_text()
        except KeyError:
            raise ValueError(f"File {f or path} not found")

    def get_refmap(self, files: Dict[str, Union[ZipInfo, Path]]) -> Dict[str, Dict[str, str]]:
        if self.__refmap is not None:
            return self.__refmap

        result = {}

        for file_name, f in files.items():
            if file_name in REFMAP_FILES:
                result = load_data_file(f, self.plzip)
                break

        self.__refmap = result
        return result

    def get_metainfo(self, files: Optional[Dict[str, Union[ZipInfo, Path]]] = None, with_defaults: bool = True) -> Dict[str, Any]:
        if self.__metainfo is not None:
            return self.__metainfo

        result: Optional[dict[str, Any]] = None

        if self.__refmap:
            fpath: Optional[str] = self.__refmap.get("metainfo", None)
            if fpath:
                if self.is_archive:
                    if fpath not in files:
                        return {}
                else:
                    if not Path(self.dir_path).joinpath(fpath).exists():
                        return {}

                if any(fpath.endswith(ext) for ext in [".json", ".yaml", ".yml"]):
                    result = {
                        k.replace("__", ""): v
                        for k, v in
                        load_data_file(Path(self.dir_path).joinpath(fpath) if not self.is_archive else fpath,
                                       self.plzip).items()
                        if k.replace("__", "") in ALLOWED_METAINFO
                    }

        if result is None and files:
            for file_name, f in files.items():
                if file_name in METAINFO_FILES:
                    result = load_data_file(f, self.plzip)
                    break

        if result is None:
            result = {}

        if result and with_defaults:
            new = BASE_METAINFO.copy()
            new.update(result)
            result = new

        reqs = result.get("requirements", "")
        result["requirements"] = [r.strip() for r in re.split(",(?!\s*[<>=!~(])", reqs) if r.strip()] if reqs else []
        if not result["requirements"]:
            result.pop("requirements", None)

        # collect .whl filenames from the wheels directory declared in refmap
        wheels_dir = self.__refmap.get("wheels") if self.__refmap else None
        if wheels_dir and self.dir_path:
            wheels_abs = Path(self.dir_path).joinpath(wheels_dir)
            if wheels_abs.is_dir():
                result["wheel_names"] = [
                    f.name for f in sorted(wheels_abs.iterdir())
                    if f.suffix == ".whl"
                ]
        elif wheels_dir and self.is_archive:
            # archive case: list .whl entries from zip
            result["wheel_names"] = [
                Path(f).name for f in self.plzip.namelist()
                if f.startswith(wheels_dir) and f.endswith(".whl")
            ]

        self.__metainfo = result
        return result

    def get_validation_result(self):
        metainfo = self.get_metainfo()
        if not metainfo:
            return None

        if self.__validation_result is not None:
            return self.__validation_result

        id_: Optional[str] = metainfo.get("id", None)
        name: Optional[str] = metainfo.get("name", None)

        if not id_ or not name:
            self._raise(ValidationError("Plugin metadata must contain non-empty '__id__' and '__name__'."))

        if not re.match(r"^[a-zA-Z0-9_]{2,32}$", id_):
            self._raise(ValidationError(
                f"Invalid plugin id: {id_}. Plugin '__id__' must be 2-32 characters long and contain only letters, numbers, and underscores."))

        min_version = metainfo.get("min_version", None)
        if min_version:
            if not SharedConfig.versionBiggerOrEqual(BuildVars.BUILD_VERSION_STRING, min_version):
                self._raise(ValidationError(
                    f"Plugin requires app version {min_version} or higher. Your app version is {BuildVars.BUILD_VERSION_STRING}."))

        pc = PluginsController.getInstance()
        metadata = Plugin(id_, name)
        metadata.setEngine(ENGINE_ID)
        metadata.setAuthor(metainfo.get("author", "Unknown author"))
        metadata.setDescription(_resolve_description(metainfo.get("description", "No description"), self.get_strings()))
        metadata.setIcon(metainfo.get("icon", None))
        metadata.setVersion(metainfo.get("version", "1.0.0"))
        metadata.setAppVersion(metainfo.get("app_version", None))
        metadata.setSdkVersion(metainfo.get("sdk_version", None))
        metadata.setEnabled(pc.preferences.getBoolean(f"plugin_enabled_{id_}", False))
        if metainfo.get("requirements", None):
            metadata.setRequirements(Arrays.asList(metainfo.get("requirements")))

        if metainfo.get("requires", None):
            for plugin_id, link_to_download in metainfo["requires"].items():
                try:
                    req_version: Optional[str] = None

                    if not re.match(r"^[a-zA-Z0-9_]{2,32}(?:\s*\(\d+(?:\.\d+)*\))?$", plugin_id):
                        self._raise(ValidationError(
                            f"Invalid plugin id requirement: \"{plugin_id}\". Plugin '__id__' must be 2-32 characters long and contain only letters, numbers, and underscores. Version must be in format \"plugin_id (min_version)\"."))
                        continue

                    if " " in plugin_id:
                        plugin_id, req_version = plugin_id.split(" ")
                        req_version = req_version[1:-1]

                    if not pc.plugins.containsKey(plugin_id):
                        self.errors.append((RequiredPlugin(plugin_id, required_version=req_version,
                                                           link_to_download=link_to_download), ""))
                    else:
                        plugin = pc.plugins.get(plugin_id)
                        if not plugin.isEnabled() or (
                                req_version and Version(req_version) > Version(plugin.getVersion())):
                            self.errors.append((RequiredPlugin(plugin_id, required_version=req_version,
                                                               link_to_download=link_to_download,
                                                               current_version=plugin.getVersion() if plugin.isEnabled() else "<disabled>"),
                                                ""))
                except ValidationError as e:
                    self._raise(e)
                except Exception as e:
                    self._raise(ValidationError(f"Failed to check plugin requirements: {e}"))

        self.__validation_result = PluginsController.PluginValidationResult(metadata, None)
        return self.__validation_result

    def is_custom_icon(self):
        return self.get_metainfo().get("icon", "") and (Path(self.dir_path) / self.get_metainfo()["icon"]).exists()

    def get_icon_path(self):
        if self.is_custom_icon():
            return str((Path(self.dir_path) / self.get_metainfo()["icon"]).absolute())


############### END Elyx Plugin PART ################


def _resolve_description(description: str, plugin_strings: Optional[Dict]) -> str:
    # replaces {key} placeholders with values from plugin strings
    dlog(
        f"[DEBUG] [ElyxDesc] input={description!r}, strings_keys={list(plugin_strings.keys()) if plugin_strings else None}")

    if not plugin_strings or "{" not in description:
        dlog(f"[DEBUG] [ElyxDesc] skip: no strings or no placeholder")
        return description

    current_lang = Strings.get_current_language()
    lang_strings = plugin_strings.get(current_lang) or plugin_strings.get("en") or plugin_strings
    dlog(
        f"[DEBUG] [ElyxDesc] lang={current_lang}, lang_strings type={type(lang_strings).__name__}, keys={list(lang_strings.keys()) if isinstance(lang_strings, dict) else lang_strings}")

    if not isinstance(lang_strings, dict):
        return description

    en_strings = plugin_strings.get("en") if isinstance(plugin_strings, dict) else None

    def replacer(m):
        key = m.group(1)
        val = lang_strings.get(key)
        if val is None and en_strings is not None:
            val = en_strings.get(key)
        if val is None:
            val = key
        dlog(f"[DEBUG] [ElyxDesc] replace {{{key}}} -> {val!r}")
        return str(val)

    result = re.sub(r"\{([^}]+)\}", replacer, description)
    dlog(f"[DEBUG] [ElyxDesc] result={result!r}")
    return result


############### Local Wheels PART ################

WHEEL_ROOTS_FILE = "roots.json"


def _wheels_base_dir(pluginId: str) -> Path:
    return Path(str(PluginsController.getInstance().pluginsDir)) / "elyx_local_libs" / pluginId


def _get_wheels_lib_dir(pluginId: str) -> Path:
    path = _wheels_base_dir(pluginId)
    path.mkdir(parents=True, exist_ok=True)
    return path


def _wheel_dir_name(whl: Path) -> str:
    return f"{whl.stem}-{hashlib.sha256(whl.read_bytes()).hexdigest()[:16]}"


def _read_wheel_roots(pluginId: str) -> tuple[Path, ...]:
    lib_dir = _wheels_base_dir(pluginId)
    try:
        names = json.loads((lib_dir / WHEEL_ROOTS_FILE).read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return ()
    if not isinstance(names, list):
        return ()
    roots = []
    for name in names:
        if not isinstance(name, str) or not name or name in (".", "..") or "/" in name or os.sep in name:
            continue
        root = lib_dir / name
        if root.is_dir():
            roots.append(root)
    return tuple(roots)


def _drop_wheel_paths(lib_dir: Path) -> None:
    prefix = str(lib_dir)
    sys.path[:] = [entry for entry in sys.path if entry != prefix and not entry.startswith(prefix + os.sep)]


def _clear_local_wheels(pluginId: str) -> None:
    lib_dir = _wheels_base_dir(pluginId)
    _drop_wheel_paths(lib_dir)
    if lib_dir.exists():
        shutil.rmtree(str(lib_dir), ignore_errors=True)


def _install_local_wheels(plugin: "ElyxPlugin", pluginId: str) -> None:
    wheels_dir = Path(plugin.dir_path) / plugin.structure.wheels_path
    whl_files = sorted(wheels_dir.glob("*.whl")) if wheels_dir.is_dir() else []
    if not whl_files:
        logcat(f"[INFO] [ElyxWheels] no .whl files in {wheels_dir}")
        _clear_local_wheels(pluginId)
        return

    lib_dir = _get_wheels_lib_dir(pluginId)
    targets = [(whl, _wheel_dir_name(whl)) for whl in whl_files]
    names = [name for _, name in targets]
    logcat(f"[INFO] [ElyxWheels] installing {len(whl_files)} wheel(s) for {pluginId}: {names}")

    _drop_wheel_paths(lib_dir)
    for entry in lib_dir.iterdir():
        if entry.name not in names:
            logcat(f"[INFO] [ElyxWheels] removing stale entry: {entry.name}")
            if entry.is_dir():
                shutil.rmtree(str(entry), ignore_errors=True)
            else:
                entry.unlink(missing_ok=True)

    for whl, name in targets:
        target = lib_dir / name
        if target.is_dir():
            continue

        logcat(f"[INFO] [ElyxWheels] extracting {whl.name} -> {target}")
        tmp = lib_dir / f"_tmp_{name}"
        if tmp.exists():
            shutil.rmtree(str(tmp))
        tmp.mkdir(parents=True)

        try:
            with ZipFile(str(whl), "r") as zf:
                canon_tmp = str(tmp.resolve())
                for entry in zf.infolist():
                    entry_path = (tmp / entry.filename).resolve()
                    if not str(entry_path).startswith(canon_tmp + os.sep):
                        raise SecurityError(f"Path traversal in wheel: {entry.filename}")
                    zf.extract(entry, str(tmp))

            tmp.rename(target)
            logcat(f"[INFO] [ElyxWheels] installed {whl.name} -> {target}")
        except Exception:
            shutil.rmtree(str(tmp), ignore_errors=True)
            logcat(f"[ERROR] [ElyxWheels] failed to install {whl.name}: {traceback.format_exc()}")
            raise

    manifest = lib_dir / f"_tmp_{WHEEL_ROOTS_FILE}"
    manifest.write_text(json.dumps(names), encoding="utf-8")
    os.replace(str(manifest), str(lib_dir / WHEEL_ROOTS_FILE))

    for root in reversed(_read_wheel_roots(pluginId)):
        sys.path.insert(0, str(root))
        logcat(f"[INFO] [ElyxWheels] added to sys.path: {root}")
