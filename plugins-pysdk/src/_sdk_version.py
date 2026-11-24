# This is the source code of the exteraGram plugins SDK.
#
# We do not and cannot prevent the use of our code,
# but be respectful and credit the original author.
#
# Copyright @exteraGram, 2022-2026
#
# Licensed under the GNU GPL v. 3 or later, with the additional terms
# in ADDITIONAL_TERMS.md at the root of the repository.

__version__ = "1.4.6.5"
version = (1, 4, 6, 5)
version_str = __version__

__beta__ = False
beta = __beta__


import importlib.abc
import inspect
from pathlib import Path
import sys

_SAFE_MODE_IMPORTER_TAG = "extera.plugins_pysdk.SafeModeImporter"

class SafeModeImporter(importlib.abc.MetaPathFinder, importlib.abc.Loader):
    IS_OLD_VERSION = False
    I = False
    E = False

    @staticmethod
    def _get_caller_plugin_root():
        try:
            stack = inspect.stack()
            for frame_info in stack:
                if (frame_info.filename.endswith("importlib/_bootstrap.py") or
                        frame_info.frame.f_globals.get("__name__", "").startswith("importlib")):
                    continue

                caller_path = frame_info.filename
                if caller_path and caller_path != "<string>":
                    path = Path(caller_path).resolve()
                    for parent in path.parents:
                        if parent.name == "plugins":
                            return parent
            return None
        except Exception:
            return None

    def find_spec(self, fullname, path, target=...):
        plugin_root = self._get_caller_plugin_root()
        if plugin_root:
            if self.IS_OLD_VERSION:
                raise Exception("Please update app to v12.5.1")
            raise Exception("Safe mode enabled")
    
    @classmethod
    def c(cls): # check state
        caller_is_plugin = False
        try:
            frame = sys._getframe(1)
            lvl = 1
            while frame and not caller_is_plugin:
                filename = str(frame.f_code.co_filename)
                caller_is_plugin = filename.startswith("plugins\\") or filename.startswith("plugins/") or filename.startswith("ElyxPlugins")
                frame = frame.f_back
                lvl += 1
        except:
            pass

        if not cls.I and caller_is_plugin:
            raise RuntimeError("SDK not initialized.")
        
        if cls.E:
            if cls.IS_OLD_VERSION:
                raise RuntimeError("App version is too old. Please update app to last stable version.")
            raise RuntimeError("Safe mode enabled")

Z = SafeModeImporter.INSTANCE = SafeModeImporter()
Z.__safe_mode_importer_tag__ = _SAFE_MODE_IMPORTER_TAG
class X:
    BUILD_VERSION = None


def _is_safe_mode_importer_entry(entry):
    if entry is Z:
        return True

    try:
        if getattr(entry, "__safe_mode_importer_tag__", None) == _SAFE_MODE_IMPORTER_TAG:
            return True
    except Exception:
        pass

    try:
        entry_cls = entry.__class__
        if (
            entry_cls.__name__ == "SafeModeImporter" and
            entry_cls.__module__ == __name__
        ):
            return True
    except Exception:
        pass

    return False

def _remove_safe_mode_importers():
    sys.meta_path[:] = [
        entry for entry in sys.meta_path
        if not _is_safe_mode_importer_entry(entry)
    ]

def _reset_state():
    SafeModeImporter.IS_OLD_VERSION = False
    SafeModeImporter.I = False
    SafeModeImporter.E = False

def check_safemode():
    from com.exteragram.messenger import ExteraConfig
    from org.telegram.messenger import BuildVars

    if BuildVars.BUILD_VERSION < 65819:
        sys.meta_path.insert(0, Z)
        SafeModeImporter.IS_OLD_VERSION = SafeModeImporter.E = True
        return False

    if ExteraConfig.pluginsSafeMode:
        sys.meta_path.insert(0, Z)
        SafeModeImporter.E = True
        return False

    return True

def setup_hooks():
    from org.telegram.messenger import BuildVars
    unhooks = []

    try:
        from com.exteragram.messenger.plugins import PluginsController
        from java.lang import String
        from de.robv.android.xposed import XposedBridge
        from com.exteragram.messenger.plugins.xposed import PyMethodHook

        if BuildVars.BUILD_VERSION < 66999:
            from com.exteragram.messenger.plugins import PythonPluginsEngine
            from com.chaquo.python import PyObject
            from extera_utils.classes import Base, java_subclass, jmvelmethod
            from java.lang import Object

            @java_subclass(Object)
            class _PyObjectHookBridge(Base):
                setResultPyObject = jmvelmethod(
                    name="setResultPyObject",
                    return_type="void",
                    arguments=[
                        ("param", "de.robv.android.xposed.XC_MethodHook$MethodHookParam"),
                        ("result", "com.chaquo.python.PyObject"),
                    ],
                    modifiers="public static",
                    code="param.setResult(result);",
                )
            
            bridge = _PyObjectHookBridge.new_java_instance()

            class FindPluginClass:
                def before_hooked_method(self, param):
                    bridge.setResultPyObject(
                        param,
                        param.thisObject.basePluginClass._findPluginClass(param.args[0]),
                    )

            unhooks.append(XposedBridge.hookMethod(
                PythonPluginsEngine.getClass().getDeclaredMethod("findPluginClass", PyObject.getClass()),
                PyMethodHook("<plugins-sdk>", FindPluginClass())
            ))

        class CleanupPluginHook:
            def before_hooked_method(self, param):
                if id := param.args[0]:
                    param.thisObject.engines.get("python").basePluginClass._cleanup(id)
  
        unhooks.append(XposedBridge.hookMethod(
            PluginsController.getClass().getDeclaredMethod("cleanupPlugin", String.getClass()),
            PyMethodHook("<plugins-sdk>", CleanupPluginHook())
        ))
    except:
        for unhook in unhooks:
            if unhook:
                unhook.unhook()
        raise

    SafeModeImporter._hooks = unhooks

def __start__():
    try:
        _remove_safe_mode_importers()
        _reset_state()
        if check_safemode():
            from org.telegram.messenger import BuildVars
            X.BUILD_VERSION = BuildVars.BUILD_VERSION
            setup_hooks()

            if X.BUILD_VERSION >= 66999:
                import elyxcore
                elyxcore._setup()

        SafeModeImporter.I = True
        return True
    except:
        import traceback
        from com.exteragram.messenger.utils import AppUtils

        AppUtils.log("[Plugins-PySdk] Failed to initialize sdk: " + traceback.format_exc())
        return False


def __stop__():
    from extera_utils.lifecycle import stop_all
    stop_all()
    try:
        _remove_safe_mode_importers()
    except:
        import traceback
        sys.stderr.write(traceback.format_exc())
    
    try:
        for unhook in getattr(SafeModeImporter, "_hooks", []):
            if unhook:
                unhook.unhook()

        from file_utils import FilesController
        FilesController._stop()

        if X.BUILD_VERSION >= 66999:
            from intents import IntentsManager
            IntentsManager._stop()

            import elyxcore
            elyxcore._stop()
    except:
        import traceback
        sys.stderr.write(traceback.format_exc())
    finally:
        SafeModeImporter._hooks = []
        _reset_state()
