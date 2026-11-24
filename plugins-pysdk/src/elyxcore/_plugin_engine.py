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
import importlib.util
import os
import random
import shutil
import sys
import traceback
import warnings
from pathlib import Path
from typing import Any, Callable, Dict, Optional
from zipfile import BadZipFile, ZipFile

from androidx.core.content import ContextCompat
from com.chaquo.python import PyException
from com.exteragram.messenger.plugins import Plugin, PluginsController
from com.exteragram.messenger.plugins.hooks import PluginsHooks
from com.exteragram.messenger.plugins.models import CustomSetting, DividerSetting, EditTextSetting, HeaderSetting, \
    InputSetting, SelectorSetting, SwitchSetting, TextSetting
from com.exteragram.messenger.plugins.pip import PipController
from com.exteragram.messenger.plugins.ui import PluginSettingsActivity, PluginsActivity
from com.exteragram.messenger.plugins.ui.components import InstallPluginBottomSheet, PluginCellDelegate, \
    PluginFileViewer, PluginRequirementsView
from de.robv.android.xposed import XposedBridge
from org.mvel2 import MVEL

import base_plugin
import plugin_settings
from elyxcore.utils import gen, hash_map, safe_func, safe_run_on_queue
from android.view import View
from android_utils import log as logcat
from base_plugin import BasePlugin
from extera_utils.classes import Base, java_subclass, joverride, release_plugin
from extera_utils import lifecycle
from hook_utils import get_private_field
from java import jint
from java.io import File
from java.lang import Object, Runnable as JavaRunnable, String
from java.util import ArrayList, Arrays
from org.telegram.messenger import AndroidUtilities, LocaleController, MessageObject, SendMessagesHelper, Utilities
from org.telegram.tgnet import TLObject, TLRPC
from org.telegram.ui import LaunchActivity
from org.telegram.ui.ActionBar import BaseFragment
from org.telegram.ui.Components import UItem

warnings.filterwarnings("ignore", category=SyntaxWarning)

ELYX_ID = "elyxcore"
_debug_logs = True


def dlog(msg: str) -> None:
    if _debug_logs:
        logcat(msg)


OnClickListener = gen(View.OnClickListener, "onClick")
Runnable = gen(JavaRunnable, "run")
Callback = gen(Utilities.Callback, "run")
Callback2 = gen(Utilities.Callback2, "run")
Callback3 = gen(Utilities.Callback3, "run")
CallbackReturn = gen(Utilities.CallbackReturn, "run", True)

from elyxcore._dev_server import ElyxDevServer
from elyxcore._install import InstallAlert
from elyxcore._plugin import ALLOWED_METAINFO, ArchiveEncryptionError, ElyxPlugin, InvalidPluginError, _clear_local_wheels, _install_local_wheels, _resolve_description
from elyxcore.utils import mvel_execute

@java_subclass(Object, PluginsController.PluginsEngine)
class ElyxEngine(Base):
    ID = "elyx-py"
    DEFAULT_EXTENSIONS = ["elyx", "eaf"]
    instance: Optional["ElyxEngine"] = None

    def __init__(self):
        super().__init__()
        ElyxEngine.instance = self
        self.python_engine = PluginsController.getEngines().get("python")
        self._plugins_dir = None
        self.inited = False
        self._generation = 0
        self.plugins_instances: Dict[str, BasePlugin] = {}
        self.pending_installs: Dict[str, ElyxPlugin] = {}

        self.temp_file_names = []
        self._h = None

    @property
    def plugins_dir(self):
        if self._plugins_dir is None:
            self._plugins_dir = File(PluginsController.getInstance().pluginsDir, "ElyxPlugins")
            if not self._plugins_dir.exists():
                self._plugins_dir.mkdirs()

        return self._plugins_dir

    @property
    def plugins_dir_path(self):
        return self.plugins_dir.getAbsolutePath()

    def plugin_dir(self, plugin_id):
        return Path(self.plugins_dir_path, plugin_id).absolute().as_posix()

    @joverride(arg_types=[File, MessageObject])
    def isPlugin(self, file: File | None, message_object: MessageObject | None = None) -> bool:
        if not file:
            return False

        name = message_object.getDocumentName() if message_object else file.getName()
        if name in self.temp_file_names:
            self.temp_file_names.remove(name)
            return True

        name = name.lower()
        for i in self.DEFAULT_EXTENSIONS:
            if name.endswith(f".{i}"):
                return True
            elif name.endswith(f".{i}.zip"):
                if file.exists():
                    self.temp_file_names.append(file.getName())
                return True

        return False

    @joverride(arg_types=[String])
    @safe_func
    def parsePluginMetadata(self, filePath: str):
        try:
            with ZipFile(filePath, "r") as plzip:
                metainfo, strings = ElyxPlugin.read_metainfo(plzip)
        except Exception as e:
            self.log(f"Failed to parse metadata of {filePath}: {e}")
            return None

        if not metainfo or not metainfo.get("id") or not metainfo.get("name"):
            return None

        metadata = {}
        for key in ALLOWED_METAINFO:
            value = metainfo.get(key)
            if isinstance(value, (list, tuple)):
                value = ",".join(map(str, value))
            if value and not isinstance(value, dict):
                metadata[key] = str(value)
        if "description" in metadata:
            metadata["description"] = _resolve_description(metadata["description"], strings)
        min_version = metadata.pop("min_version", None)
        if min_version:
            metadata.setdefault("app_version", ">=" + min_version)
        return hash_map(metadata)

    @joverride()
    def isEngineAvailable(self) -> bool:
        return self.inited

    @staticmethod
    def get_setting(key, default=None):
        return plugin_settings.get_setting(ELYX_ID, key, default) or default

    @staticmethod
    def log(message):
        logcat(f"[ElyxCore] {message}")

    def load_from_archive(self, pl: ElyxPlugin, enable_after_install: bool = False,
                          on_complete: Optional[Callable[[], None]] = None,
                          on_error: Optional[Callable[[Any], None]] = None, delegate=None):
        if not pl.is_archive:
            return

        archive_plugin = object.__new__(type(pl))
        archive_plugin.__dict__.update(vars(pl))
        pl = archive_plugin
        generation = self._generation
        archive = pl.plzip

        def install():
            if not self.inited or generation != self._generation or not PluginsController.isPluginEngineAvailable():
                if getattr(pl.instances.get(archive.filename), "plzip", None) is archive:
                    pl.instances.pop(archive.filename, None)
                archive.close()
                from org.telegram.messenger import R
                self.run_callback(on_error, LocaleController.getString(R.string.PluginsPySdkEngineDisabled),
                                  on_ui_thread=True)
                return

            plugin_id = None
            plugin_dir = None
            plugin_file = None
            backup_file = None
            backup_dir = None
            is_update = None
            was_enabled = False

            pc = PluginsController.getInstance()

            def _on_error(_error):
                try:
                    _rollback()
                except BaseException:
                    logcat(f"[ERROR] [ElyxPlugin] Failed to roll back plugin {plugin_id}: {traceback.format_exc()}")
                finally:
                    logcat(f"[ERROR] [ElyxPlugin] Failed to load plugin {archive.filename}: " + _error)
                    self.run_callback(on_error, _error, on_ui_thread=True)

            def _rollback():
                if plugin_id and plugin_id in pl.instances_by_id:
                    x = pl.instances_by_id.pop(plugin_id, None)
                    if x:
                        pl.instances.pop(x.dir_path, None)

                if plugin_dir and plugin_dir.exists():
                    shutil.rmtree(plugin_dir.getAbsolutePath())

                restored = False
                if backup_dir and backup_dir.exists() and is_update:
                    if backup_dir.renameTo(plugin_dir):
                        logcat(f"[INFO] [ElyxPlugin] Restored plugin {plugin_id} from backup")
                        self.loadPlugin(plugin_id, self.plugin_dir(plugin_id), was_enabled)
                        restored = True
                    else:
                        logcat(f"[ERROR] [ElyxPlugin] Failed to restore plugin {plugin_id} from backup")

                if not restored:
                    self.cleanup_plugin(plugin_id, pc)
                    pc.plugins.remove(plugin_id)
                    self.plugins_instances.pop(plugin_id, None)
                    try:
                        PipController.INSTANCE.uninstallDependencies(plugin_id)
                    except:
                        logcat(f"[ERROR] Failed to uninstall dependencies for {plugin_id}: {traceback.format_exc()}")

                    pc.clearPluginSettingsPreferences(plugin_id, True)

                    if plugin_dir and plugin_dir.exists():
                        shutil.rmtree(plugin_dir.getAbsolutePath())

                    if plugin_file and plugin_file.exists():
                        os.remove(plugin_file.getAbsolutePath())

                self.notify_plugins_changed()

            try:
                elyx_plugins_dir = self.plugins_dir

                metainfo = pl.get_metainfo()
                plugin_id = metainfo["id"]

                logcat(f"[INFO] [ElyxPlugin] Loading plugin {plugin_id} from archive {pl.plzip.filename}")

                plugin_dir = File(elyx_plugins_dir, plugin_id)
                plugin_file = File(pc.pluginsDir, f"{plugin_id}.py")
                is_update = plugin_dir.exists() or plugin_file.exists()
                was_enabled = pc.preferences.getBoolean("plugin_enabled_" + plugin_id, False)

                if is_update:
                    self.unloadPlugin(plugin_id)
                    if plugin_dir.exists():
                        backup_dir = File(elyx_plugins_dir, f"{plugin_id}_backup")
                        if backup_dir.exists():
                            shutil.rmtree(backup_dir.getAbsolutePath())

                        plugin_dir.renameTo(backup_dir)
                        plugin_dir.mkdirs()
                    else:
                        backup_file = File(pc.pluginsDir, f"{plugin_id}.py.bak")
                        if backup_file.exists():
                            os.remove(backup_file.getAbsolutePath())

                        plugin_file.renameTo(backup_file)

                pl.plzip.extractall(plugin_dir.getAbsolutePath())
                pl.plzip.close()
                new = ElyxPlugin(plugin_dir.getAbsolutePath())

                def cb(X):
                    if X:
                        _on_error(X)
                        return
                    self.run_callback(on_complete, on_ui_thread=True)

                    self.notify_plugins_changed()

                    if backup_dir and backup_dir.exists():
                        shutil.rmtree(backup_dir.getAbsolutePath())

                    if backup_file and backup_file.exists():
                        os.remove(backup_file.getAbsolutePath())

                self.loadPlugin(plugin_id, new.dir_path, enable_after_install, cb, delegate=delegate, callback_on_ui_thread=False)
            except:  # noqa
                _on_error(traceback.format_exc())
            finally:
                archive.close()
                if getattr(pl.instances.get(archive.filename), "plzip", None) is archive:
                    pl.instances.pop(archive.filename, None)

        PluginsController.runOnPluginsQueue(Runnable(install))

    @safe_func
    def clear_py_cache(self, plugin_id: Optional[str] = None):
        from elyxcore._importer import importer

        try:
            release_plugin(plugin_id)
        except Exception:
            logcat(f"[ERROR] [ElyxPlugin] Failed to release objects of plugin {plugin_id}: {traceback.format_exc()}")

        if plugin_id:
            importer.unload_plugin(plugin_id)
        else:
            importer.unload_all()

        self.remove_pycache(self.plugins_dir_path if not plugin_id else self.plugin_dir(plugin_id))

    @classmethod
    def remove_pycache(cls, dir_path: str):
        try:
            if not dir_path or not os.path.exists(dir_path):
                return

            for root, dirs, _ in os.walk(dir_path):
                if "__pycache__" in dirs:
                    pycache_path = os.path.join(root, "__pycache__")
                    shutil.rmtree(pycache_path)
                    dirs.remove("__pycache__")

                for dir_ in dirs:
                    cls.remove_pycache(os.path.join(root, dir_))
        except:  # noqa
            logcat(f"[ERROR] [ElyxPlugin] Failed to remove pycache: {traceback.format_exc()}")

    @joverride(arg_types=[JavaRunnable])
    @safe_func
    def init(self, callback, force=False):
        if self.inited:
            self.run_callback(callback)
            return

        global _debug_logs
        _debug_logs = plugin_settings.get_setting(ELYX_ID, "debug_logs", False)

        self.__ih()

        self._generation += 1
        self.inited = True
        self.loadPlugins(callback)
        self.checkDevServer()

    def __ih(self):
        ############### HOOKS ###############
        try:
            old_hooks = self._h
            if old_hooks and isinstance(old_hooks, list):
                for hook in old_hooks:
                    hook.unhook()
                self._h = None
        except:
            pass

        from com.exteragram.messenger.plugins.xposed import PyMethodHook
        from com.exteragram.messenger.plugins.ui import PluginsInfoActivity
        from org.telegram.ui.Components import UniversalAdapter
        from java.lang import Integer, Float

        class SettingItemsHook:
            def __init__(self, t):
                self.t = t
                self.btns = {}

            def after_hooked_method(self, param):
                if self.t == 1:
                    items = param.args[0]
                    self.btns = ElyxEngine.instance._fill_settings(items, items.size() - 4,
                                                      PluginsInfoActivity.PreferenceItem.getEntries().size())
                else:
                    item, view, pos, x, y = param.args
                    if item.id in self.btns:
                        ElyxEngine.instance._on_click_setting(self.btns[item.id], item, view)

        self._h = [
            XposedBridge.hookMethod(
                PluginsInfoActivity.getClass().getDeclaredMethod("fillItems", ArrayList.getClass(),
                                                                 UniversalAdapter.getClass()),
                PyMethodHook("<plugins-sdk-elyx>", SettingItemsHook(1))
            ),
            XposedBridge.hookMethod(
                PluginsInfoActivity.getClass().getDeclaredMethod("onClick", UItem.getClass(), View.getClass(),
                                                                 Integer.TYPE, Float.TYPE, Float.TYPE),
                PyMethodHook("<plugins-sdk-elyx>", SettingItemsHook(2))
            ),
        ]
        ############### HOOKS ###############

    def __unh(self):
        ############### HOOKS ###############
        old_hooks = self._h
        if old_hooks and isinstance(old_hooks, list):
            self._h = None
            for hook in old_hooks:
                try:
                    hook.unhook()
                except BaseException:
                    logcat(traceback.format_exc())
        ############### HOOKS ###############

    @staticmethod
    def run_callback(callback, *args, on_ui_thread=False, on_queue=False, **kwargs):
        if not callback:
            return

        def run():
            try:
                if isinstance(callback, Callable):
                    callback(*args, **kwargs)
                elif isinstance(callback, (
                        Utilities.Callback, Utilities.Callback2, Utilities.Callback3, Utilities.CallbackReturn)):
                    callback.run(*args, **kwargs)
                else:  # instance(callback, JavaRunnable):
                    callback.run()
            except:  # noqa
                logcat(f"[ERROR] [ElyxEngine] Failed to run callback: {traceback.format_exc()}")

        if on_ui_thread:
            AndroidUtilities.runOnUIThread(Runnable(run))
        elif on_queue:
            safe_run_on_queue(run)
        else:
            run()

    @joverride()
    @safe_func
    def checkDevServer(self):
        ElyxDevServer.setup()

    @joverride(arg_types=[JavaRunnable])
    @safe_func
    def shutdown(self, callback):
        if not self.inited and not self.plugins_instances and not self._h:
            self.run_callback(callback)
            return

        self.inited = False
        self._generation += 1
        try:
            self.unloadPlugins()
        finally:
            try:
                self.__unh()
            finally:
                try:
                    self.clear_py_cache()
                finally:
                    self.run_callback(callback)

    @staticmethod
    def notify_plugins_changed():
        updateNotificationRunnable = get_private_field(PluginsController.getInstance(), "updateNotificationRunnable")
        AndroidUtilities.cancelRunOnUIThread(updateNotificationRunnable)
        AndroidUtilities.runOnUIThread(updateNotificationRunnable, 150)

    @safe_func
    def unloadPlugins(self, callback: Any = None):
        try:
            for plugin_id in tuple(self.plugins_instances):
                try:
                    self.unloadPlugin(plugin_id)
                except BaseException:
                    logcat(f"[ERROR] [ElyxPlugin] Failed to unload {plugin_id}: {traceback.format_exc()}")
        finally:
            self.run_callback(callback)

    @safe_func
    def loadPlugins(self, callback):
        generation = self._generation

        def _fn():
            if not self.inited or generation != self._generation:
                self.run_callback(callback)
                return
            sys.path.insert(0, self.plugins_dir_path)

            for plugin_dir in Path(self.plugins_dir_path).iterdir():
                if plugin_dir.is_dir() and ElyxPlugin.is_plugin_dir(plugin_dir):
                    plugin_id = plugin_dir.name
                    if plugin_id == "archives":
                        continue

                    try:
                        self.loadPlugin(plugin_id, plugin_dir.as_posix())
                    except:  # noqa
                        logcat(f"[ERROR] [ElyxPlugin] Failed to load plugin {plugin_id}: {traceback.format_exc()}")

                        try:
                            metadata = ElyxPlugin(plugin_dir.as_posix()).get_metainfo()
                            p = Plugin(plugin_id, metadata.get("name", plugin_id))
                            p.setEngine(self.ID)
                            p.setAuthor(metadata.get("author", "Unknown author"))
                            p.setIcon(metadata.get("icon", None))
                            p.setVersion(metadata.get("version", "1.0.0"))
                            p.setEnabled(False)
                            p.setError(PyException("\n".join(traceback.format_exc().splitlines()[-5:])))
                            PluginsController.getInstance().plugins.put(plugin_id, p)
                        except:  # noqa
                            p = Plugin(plugin_id, plugin_id)
                            p.setEngine(self.ID)
                            p.setAuthor("Unknown author")
                            p.setVersion("1.0")
                            p.setEnabled(False)
                            p.setError(PyException("\n".join(traceback.format_exc().splitlines()[-5:])))
                            PluginsController.getInstance().plugins.put(plugin_id, p)

            self.notify_plugins_changed()
            logcat(f"[INFO] [ElyxPlugin] Loaded {len(self.plugins_instances)} plugins")
            self.run_callback(callback, on_ui_thread=True)

        safe_run_on_queue(_fn)

    def loadPlugin(self, pluginId: str, filePath: str, enable_after_install=None, callback=None, delegate=None,
                   callback_on_ui_thread=True):
        if pluginId == "archives":
            return
        if not self.inited:
            from org.telegram.messenger import R
            self.run_callback(callback, LocaleController.getString(R.string.PluginsPySdkEngineDisabled), on_ui_thread=callback_on_ui_thread)
            return

        try:
            pc = PluginsController.getInstance()
            was_enabled = enable_after_install or pc.preferences.getBoolean("plugin_enabled_" + pluginId, False)

            if pluginId in self.plugins_instances:
                self.unloadPlugin(pluginId)

            logcat(f"[INFO] [ElyxPlugin] Loading plugin {pluginId} from {filePath}")

            plugin = ElyxPlugin(self.plugin_dir(pluginId))
            metainfo = plugin.get_metainfo()
            main_file_path = plugin.structure.main_file_path
            main_file_path = (
                    f"ElyxPlugins.{pluginId}."
                    + (
                        (".".join(main_file_path.split("/")[:-1]) + ".")
                        if "/" in main_file_path else ""
                    ) + main_file_path.split("/")[-1].split(".")[0]
            )

            reqs = metainfo.get("requirements", [])
            if reqs:
                logcat(f"[INFO] [ElyxWheels] installing pip deps for {pluginId}: {reqs}")
                paths = PipController.INSTANCE.installDependencies(
                    Arrays.asList(reqs),
                    pluginId,
                    delegate
                )
                for path in paths.toArray():
                    if path in sys.path:
                        sys.path.remove(path)
                    sys.path.insert(0, path)
                logcat(f"[INFO] [ElyxWheels] pip deps ready for {pluginId}")

            if plugin.structure.wheels_path:
                logcat(f"[INFO] [ElyxWheels] processing local wheels for {pluginId}")
                _install_local_wheels(plugin, pluginId)
                logcat(f"[INFO] [ElyxWheels] local wheels done for {pluginId}")
            else:
                _clear_local_wheels(pluginId)

            plugin.get_validation_result().plugin.setEnabled(False)
            pc.plugins.put(pluginId, plugin.get_validation_result().plugin)
            if not was_enabled:
                self.run_callback(callback, None)
                return

            lifecycle.begin(pluginId)
            from elyxcore._importer import importer
            importer.invalidate_caches()
            with lifecycle.plugin_context(pluginId):
                main_file = importlib.import_module(main_file_path)

                plugin_class = None
                for k, v in main_file.__dict__.items():
                    if isinstance(v, type) and issubclass(v, BasePlugin) and v != BasePlugin:
                        plugin_class = v
                        break

                if not plugin_class:
                    raise InvalidPluginError.plugin_class_not_found(main_file_path)

                logcat(f"[INFO] [ElyxPlugin] Loading plugin {pluginId}")
                plugin_instance: BasePlugin = plugin_class()

            plugin_instance.id = metainfo["id"]
            plugin_instance.name = metainfo["name"]
            plugin_instance.description = _resolve_description(metainfo["description"], plugin.get_strings())
            plugin_instance.author = metainfo["author"]
            plugin_instance.version = metainfo["version"]
            plugin_instance.icon = metainfo["icon"]
            plugin_instance.min_version = metainfo["min_version"]
            plugin_instance.enabled = False
            plugin_instance.initialized = False
            plugin_instance.error_message = None
            pl_strings = plugin.get_strings()
            if pl_strings:
                plugin_instance.strings = pl_strings

            plugin.get_validation_result().plugin.setEnabled(False)
            self.plugins_instances[pluginId] = plugin_instance
            pc.plugins.put(pluginId, plugin.get_validation_result().plugin)

            logcat(f"[INFO] [ElyxPlugin] Loaded plugin {pluginId} {plugin_instance} {was_enabled=}")

            if was_enabled:
                self.setPluginEnabled(pluginId, True, callback, callback_on_ui_thread=callback_on_ui_thread)
            else:
                self.run_callback(callback, None)
        except BaseException:
            error = traceback.format_exc()
            logcat(f"[ERROR] [ElyxPlugin] Failed to load plugin {pluginId}: {error}")
            try:
                self.unloadPlugin(pluginId)
                plugin = pc.plugins.get(pluginId)
                if plugin:
                    plugin.setError(PyException(error))
                pc.preferences.edit().putBoolean(f"plugin_enabled_{pluginId}", False).apply()
            finally:
                self.run_callback(callback, error, on_ui_thread=callback_on_ui_thread)

    @safe_func
    def unloadPlugin(self, pluginId: str):
        pc = PluginsController.getInstance()
        plugin = pc.plugins.get(pluginId)
        if plugin:
            plugin.setEnabled(False)
        instance = self.plugins_instances.pop(pluginId, None)
        lifecycle.stop(pluginId)
        try:
            if instance:
                instance.enabled = False
                initialized = instance.initialized
                instance.initialized = False
                if initialized:
                    instance._invoke_plugin_unload()
        except BaseException:
            logcat(f"[ERROR] [ElyxPlugin] Failed to unload plugin {pluginId}: {traceback.format_exc()}")
        finally:
            try:
                self.cleanup_plugin(pluginId)
            finally:
                self.clear_py_cache(pluginId)
                descriptor = ElyxPlugin.instances_by_id.pop(pluginId, None)
                if descriptor is not None:
                    ElyxPlugin.instances.pop(descriptor.dir_path, None)

    @staticmethod
    def cleanup_plugin(plugin_id: str, pc: Optional[PluginsController] = None):
        if not pc:
            pc = PluginsController.getInstance()

        try:
            BasePlugin._cleanup(plugin_id)
        finally:
            pc.cleanupPlugin(plugin_id)

    @joverride(arg_types=[String, "boolean", Utilities.Callback])
    @safe_func
    def setPluginEnabled(self, pluginId: str, enabled: bool, callback, callback_on_ui_thread=True):
        pc = PluginsController.getInstance()
        try:
            plugin = pc.plugins.get(pluginId)
            if not plugin:
                raise Exception(f"Plugin {pluginId} not found")
            instance = self.plugins_instances.get(pluginId)
            if enabled and instance is None:
                self.loadPlugin(pluginId, self.plugin_dir(pluginId), True, callback,
                                callback_on_ui_thread=callback_on_ui_thread)
                return
            if enabled and instance.initialized and not plugin.hasError():
                self.run_callback(callback, None, on_ui_thread=callback_on_ui_thread)
                return

            if enabled:
                self.cleanup_plugin(pluginId)
                with lifecycle.plugin_context(pluginId):
                    instance.on_plugin_load()
                instance.initialized = True
                instance.enabled = True
                instance.error_message = None
                plugin.setError(None)
                plugin.setEnabled(True)
                pc.preferences.edit().putBoolean(f"plugin_enabled_{pluginId}", True).apply()
                pc.loadPluginSettings(pluginId)
            else:
                pc.preferences.edit().putBoolean(f"plugin_enabled_{pluginId}", False).apply()
                self.unloadPlugin(pluginId)

            self.notify_plugins_changed()
            self.run_callback(callback, None, on_ui_thread=callback_on_ui_thread)
        except BaseException:
            error = traceback.format_exc()
            logcat(f"[ERROR] [ElyxPlugin] Failed to set plugin {pluginId} enabled to {enabled}: {error}")
            pc.preferences.edit().putBoolean(f"plugin_enabled_{pluginId}", False).apply()
            self.unloadPlugin(pluginId)
            plugin = pc.plugins.get(pluginId)
            if plugin:
                plugin.setError(PyException(error))
            self.notify_plugins_changed()
            self.run_callback(callback, error, on_ui_thread=callback_on_ui_thread)

    @joverride(arg_types=[String, Utilities.Callback])
    @safe_func
    def deletePlugin(self, pluginId: str, callback):
        self.unloadPlugin(pluginId)
        PluginsController.getInstance().plugins.remove(pluginId)

        try:
            PipController.INSTANCE.uninstallDependencies(pluginId)
        except:
            logcat(f"[ERROR] Failed to uninstall dependencies for {pluginId}: {traceback.format_exc()}")

        if Path(self.plugin_dir(pluginId)).exists():
            shutil.rmtree(self.plugin_dir(pluginId))
        _clear_local_wheels(pluginId)

        if PluginsController.isPluginPinned(pluginId):
            PluginsController.setPluginPinned(pluginId, False)

        PluginsController.getInstance().clearPluginSettingsPreferences(pluginId, True)
        self.notify_plugins_changed()

        self.run_callback(callback, None, on_ui_thread=True)

        logcat(f"[INFO] [ElyxPlugin] Deleted plugin {pluginId}")

    @joverride(arg_types=[String])
    @safe_func
    def getPluginPath(self, plugin_id: str) -> str:
        archives_dir = Path(self.plugins_dir_path, "archives").absolute()
        archives_dir.mkdir(exist_ok=True)

        archive_path = archives_dir / f"{plugin_id}.{self.DEFAULT_EXTENSIONS[0]}"

        built = shutil.make_archive(archive_path.as_posix(), "zip", self.plugin_dir(plugin_id))
        Path(built).replace(archive_path)

        return archive_path.as_posix()

    @joverride()
    def canOpenInExternalApp(self) -> bool:
        return True

    @joverride(arg_types=[String])
    @safe_func
    def openInExternalApp(self, plugin_id: str):
        fragment = LaunchActivity.getSafeLastFragment()
        if not fragment:
            return

        archive = File(self.getPluginPath(plugin_id))
        PluginFileViewer.openExternally(
            fragment, archive, f"{plugin_id}.{self.DEFAULT_EXTENSIONS[0]}"
        )

    @joverride(arg_types=[String])
    @safe_func
    def sharePlugin(self, plugin_id: str):
        try:
            mvel_execute(
                """
fragment = org.telegram.ui.LaunchActivity.getSafeLastFragment();
uri = androidx.core.content.FileProvider.getUriForFile(fragment.getContext(), org.telegram.messenger.ApplicationLoader.getApplicationId() + ".provider", archiveFile);
if (uri == null) {
    return;
}
shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
shareIntent.setFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
shareIntent.putExtra(android.content.Intent.EXTRA_STREAM, uri);
shareIntent.setType("application/zip");

fragment.startActivityForResult(android.content.Intent.createChooser(shareIntent, org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.ShareFile)), 500);""",
                {
                    "archiveFile": File(self.getPluginPath(plugin_id))
                }
            )
        except Exception as e:
            logcat(f"[ERROR] [ElyxPlugin] Failed to share plugin {plugin_id}: {e}\n{traceback.format_exc()}")

    @joverride(arg_types=[String])
    @safe_func
    def loadPluginSettings(self, plugin_id: str):
        try:
            plugin = PluginsController.getInstance().plugins.get(plugin_id)
            instance = self.plugins_instances.get(plugin_id, None)
            if not plugin or not plugin.isEnabled() or plugin.hasError() or not instance:
                PluginsController.getInstance().invalidatePluginSettings(plugin_id)
                return None

            with lifecycle.plugin_context(plugin_id):
                settings = instance.create_settings()
            if not settings:
                return None

            return self.revert_to_java_settings(settings)
        except Exception as e:
            logcat(
                f"[ERROR] [ElyxPlugin] Failed to load plugin settings for plugin {plugin_id}: {e}\n{traceback.format_exc()}")
            return None

    @staticmethod
    def revert_to_java_settings(settings: list):
        java_settings = ArrayList(len(settings))

        for setting in settings:
            item = None
            try:
                item_type = getattr(setting, "type", None)
                if not item_type:
                    continue

                key = getattr(setting, "key", None)
                text = getattr(setting, "text", None)
                subtext = getattr(setting, "subtext", None)
                icon = getattr(setting, "icon", None)
                on_change = getattr(setting, "on_change", None)
                on_long_click = getattr(setting, "on_long_click", None)
                link_alias = getattr(setting, "link_alias", None)
                default_value = getattr(setting, "default", None)

                if item_type == "header":
                    if text:
                        item = HeaderSetting(text)
                elif item_type == "switch":
                    if key and text and isinstance(default_value, bool):
                        item = SwitchSetting(key, text, default_value, subtext, icon, on_change, on_long_click,
                                             link_alias)
                elif item_type == "selector":
                    items_list = getattr(setting, "items", None)
                    if key and text and items_list and default_value is not None:
                        item = SelectorSetting(key, text, jint(default_value), items_list, icon, on_change,
                                               on_long_click, link_alias)
                elif item_type == "input":
                    if key and text:
                        item = InputSetting(key, text, default_value or "", subtext, icon, on_change, on_long_click,
                                            link_alias)
                elif item_type == "edit_text":
                    hint = getattr(setting, "hint", None)
                    multiline = getattr(setting, "multiline", False)
                    max_length = getattr(setting, "max_length", 256)
                    mask = getattr(setting, "mask", None)
                    if key and hint:
                        item = EditTextSetting(key, hint, default_value or "", multiline, jint(max_length), mask,
                                               on_change)
                elif item_type == "text":
                    accent = getattr(setting, "accent", False)
                    red = getattr(setting, "red", False)
                    on_click = getattr(setting, "on_click", None)
                    create_sub_fragment = getattr(setting, "create_sub_fragment", None)
                    if text:
                        item = TextSetting(text, subtext, icon, accent, red, on_click, create_sub_fragment,
                                           on_long_click,
                                           link_alias)
                elif item_type == "divider":
                    item = DividerSetting(text)
                elif item_type == "custom":
                    view = getattr(setting, "view", None)
                    item_ = getattr(setting, "item", None)
                    factory = getattr(setting, "factory", None)
                    on_click = getattr(setting, "on_click", None)
                    args = getattr(setting, "factory_args", None)
                    create_sub_fragment = getattr(setting, "create_sub_fragment", None)

                    if factory is not None:
                        factory = getattr(factory, "java", factory)
                        _A = [factory]
                        if args:
                            _A.append(args)
                        _A += [on_click, create_sub_fragment, on_long_click, link_alias]
                        item = CustomSetting(*_A)
                    elif item_ is not None:
                        item = CustomSetting(item_, on_click, create_sub_fragment, on_long_click, link_alias)
                    elif view is not None:
                        item = CustomSetting(view, on_click, create_sub_fragment, on_long_click, link_alias)
            except:
                dlog(f"[ERROR] [ElyxPlugin] Failed to convert setting {setting}: {traceback.format_exc()}")

            if item:
                java_settings.add(item)

        return java_settings

    @joverride(arg_types=[String, String, Object])
    @safe_func
    def getPluginSetting(self, pluginId, key, defaultValue):
        try:
            result = plugin_settings.get_setting(pluginId, key, defaultValue)
            return defaultValue if result is None else result
        except Exception as e:
            logcat(
                f"[ERROR] [ElyxPlugin] Failed to get plugin setting {key} for plugin {pluginId}: {e}\n{traceback.format_exc()}")
            return defaultValue

    @joverride(arg_types=[String, String, Object])
    @safe_func
    def setPluginSetting(self, pluginId, key, value):
        try:
            plugin_settings.set_setting(pluginId, key, value)
        except Exception as e:
            logcat(
                f"[ERROR] [ElyxPlugin] Failed to set plugin setting {key} for plugin {pluginId}: {e}\n{traceback.format_exc()}")

    @joverride(arg_types=[String])
    @safe_func
    def clearPluginSettings(self, plugin_id):
        try:
            plugin_settings.clear_settings(plugin_id)
        except Exception as e:
            logcat(
                f"[ERROR] [ElyxPlugin] Failed to clear plugin settings for plugin {plugin_id}: {e}\n{traceback.format_exc()}")

    @joverride(arg_types=[String])
    @safe_func
    def getAllPluginSettings(self, pluginId, to_java_map=True):
        try:
            settings = plugin_settings.get_all_settings(pluginId)
            if not to_java_map:
                return settings if settings else None

            return hash_map(settings) if settings else None
        except Exception as e:
            logcat(
                f"[ERROR] [ElyxPlugin] Failed to get all plugin settings for plugin {pluginId}: {e}\n{traceback.format_exc()}")
            return None

    @joverride(arg_types=[String])
    @safe_func
    def executeOnAppEvent(self, eventType):
        try:
            for instance in self.plugins_instances.values():
                if instance.enabled and instance.error_message is None:
                    try:
                        instance.on_app_event(eventType)
                    except Exception as e:
                        logcat(
                            f"[ERROR] [ElyxPlugin] Failed to execute on app event {eventType} for plugin {instance.id}: {e}\n{traceback.format_exc()}")
        except Exception as e:
            logcat(f"[ERROR] [ElyxPlugin] Failed to execute on app event {eventType}: {e}\n{traceback.format_exc()}")

    @safe_func
    def executeHook(self, instance, initial_value, result_key, caller):
        modified_value = initial_value
        if instance:
            try:
                result = caller(instance, modified_value)
                logcat(f"[INFO] [ElyxPlugin] Executed hook {result_key} for plugin {instance.id}: {result}")
                if result:
                    strategy = getattr(result, "strategy", "DEFAULT")
                    if strategy == base_plugin.HookStrategy.CANCEL:
                        return PluginsController.HookResult(modified_value, True, False)
                    elif strategy in [base_plugin.HookStrategy.MODIFY, base_plugin.HookStrategy.MODIFY_FINAL]:
                        modified_value = getattr(result, result_key)
                        if modified_value is not None:
                            return PluginsController.HookResult(modified_value, False,
                                                                strategy == base_plugin.HookStrategy.MODIFY_FINAL)
            except Exception as e:
                logcat(
                    f"[ERROR] [ElyxPlugin] Failed to execute hook {result_key} for plugin {instance.id}: {e}\n{traceback.format_exc()}")

        return PluginsController.HookResult(modified_value, False, False)

    @joverride(arg_types=[String, "int", TLObject, String])
    @safe_func
    def executePreRequestHook(self, requestName, account, request, pluginId):
        return self.executeHook(
            self.plugins_instances.get(pluginId, None),
            request,
            "request",
            lambda instance, r: instance.pre_request_hook(requestName, account, r)
        )

    @joverride(arg_types=[String, "int", TLObject, TLRPC.TL_error, String])
    @safe_func
    def executePostRequestHook(self, requestName, account, response, error, pluginId):
        modified_value = response
        modified_error = error
        instance = self.plugins_instances.get(pluginId, None)
        if instance:
            try:
                result = instance.post_request_hook(requestName, account, response, error)
                if result:
                    strategy = getattr(result, "strategy", "DEFAULT")
                    if strategy in [base_plugin.HookStrategy.MODIFY, base_plugin.HookStrategy.MODIFY_FINAL]:
                        resp = getattr(result, "response")
                        if resp:
                            modified_value = resp

                        err = getattr(result, "error")
                        if err:
                            modified_error = err

                        return PluginsController.HookResult(modified_value, False, strategy == "MODIFY_FINAL")
            except Exception as e:
                logcat(
                    f"[ERROR] [ElyxPlugin] Failed to execute hook {requestName} for plugin {instance.id}: {e}\n{traceback.format_exc()}")

        return PluginsController.HookResult(PluginsHooks.PostRequestResult(modified_value, modified_error), False,
                                            False)

    @joverride(arg_types=[String, "int", TLRPC.Update, String])
    @safe_func
    def executeUpdateHook(self, updateName, account, update, pluginId):
        return self.executeHook(
            self.plugins_instances.get(pluginId, None),
            update,
            "update",
            lambda instance, r: instance.on_update_hook(updateName, account, r)
        )

    @joverride(arg_types=[String, "int", TLRPC.Updates, String])
    @safe_func
    def executeUpdatesHook(self, containerName, account, updates, pluginId):
        return self.executeHook(
            self.plugins_instances.get(pluginId, None),
            updates,
            "updates",
            lambda instance, r: instance.on_updates_hook(containerName, account, r)
        )

    @joverride(arg_types=["int", SendMessagesHelper.SendMessageParams, String])
    @safe_func
    def executeSendMessageHook(self, account, params, pluginId):
        return self.executeHook(
            self.plugins_instances.get(pluginId, None),
            params,
            "params",
            lambda instance, r: instance.on_send_message_hook(account, r)
        )

    @joverride(arg_types=[BaseFragment, InstallPluginBottomSheet.PluginInstallParams])
    @safe_func
    def showInstallDialog(self, fragment, params):
        try:
            plugin = ElyxPlugin(plzip=ZipFile(params.filePath, "r"), raise_errors=False)
        except ArchiveEncryptionError:
            InstallAlert.show_password_dialog(self, fragment, params)
            return
        except BadZipFile:
            return
        except Exception as e:
            self.log(f"Error in showInstallDialog: {str(e)}\n{traceback.format_exc()}")
            return

        InstallAlert.show(self, fragment, plugin, params)

    @joverride(arg_types=[String, Plugin, Utilities.Callback, PipController.InstallerDelegate])
    @safe_func
    def loadPluginFromFile(self, filePath, pluginMetadata, callback, delegate):
        plugin = self.pending_installs.pop(filePath, None)
        if plugin is None:
            try:
                plugin = ElyxPlugin(plzip=ZipFile(filePath, "r"), raise_errors=False)
            except Exception:
                self.run_callback(callback, traceback.format_exc())
                return

        if not plugin.is_archive:
            self.run_callback(callback, f"{filePath} is not an Elyx archive")
            return

        self.load_from_archive(
            plugin,
            False,
            lambda: self.run_callback(callback, None),
            lambda error: self.run_callback(callback, str(error)),
            delegate,
        )

    @joverride("openPluginSettings", [String, BaseFragment])
    @safe_func
    def open_plugin_settings_by_id(self, plugin_id, fragment):
        self.python_engine.openPluginSettings(plugin_id, fragment)

    @joverride("openPluginSettings", [Plugin, BaseFragment])
    @safe_func
    def open_plugin_settings_by_plugin(self, plugin, fragment):
        self.python_engine.openPluginSettings(plugin, fragment)

    @joverride("openPluginSetting", [Plugin, String, BaseFragment])
    @safe_func
    def open_plugin_setting_by_plugin(self, plugin, link_alias, fragment):
        self.python_engine.openPluginSetting(plugin, link_alias, fragment)

    @joverride("openPluginSetting", [String, String, BaseFragment])
    @safe_func
    def open_plugin_setting_by_id(self, plugin_id, link_alias, fragment):
        self.python_engine.openPluginSetting(plugin_id, link_alias, fragment)

    def _fill_settings(self, items, start_index, start_item_id):
        index = start_index
        iid = start_item_id
        def add(item, without_id: bool = False):
            nonlocal index, iid
            if not without_id:
                item_id = iid
                item.id = item_id
                iid += 1
            else:
                item_id = 0

            items.add(index, item)
            index += 1
            return item_id if not without_id else random.randint(600, 1000)

        from org.telegram.messenger import R
        return {
            add(UItem.asHeader("Elyx"), True): None,
            add(UItem.asButton(0, R.drawable.msg_download, "Install from file")): "install_from_file",
        }

    def _on_click_setting(self, name, item, view):
        if name == "install_from_file":
            ...

    def __load_plugins(self, callback: Runnable):
        ...

    def __unload_plugins(self, callback: Runnable):
        ...

    def __clear_py_cache(self):
        ...
