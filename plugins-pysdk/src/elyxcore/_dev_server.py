# This is the source code of the exteraGram plugins SDK.
#
# We do not and cannot prevent the use of our code,
# but be respectful and credit the original author.
#
# Copyright @exteraGram, 2022-2026
#
# Licensed under the GNU GPL v. 3 or later, with the additional terms
# in ADDITIONAL_TERMS.md at the root of the repository.

import base64
import hashlib
import json
import os
import shutil
import socket
import traceback
import zlib
from pathlib import Path
from typing import Dict, Any, Optional, Callable

import dev_server
from android_utils import log as logcat
from android_utils import run_on_ui_thread
from client_utils import get_last_fragment
from hook_utils import get_private_field
from java.io import File


def _get_plugin_types():
    from elyxcore._plugin_engine import ElyxEngine
    from elyxcore._plugin import ElyxPlugin
    return ElyxEngine, ElyxPlugin


################ DevServer Elyx patch PART ################

class ElyxDevServer:
    original_method = None
    hash_cache = {}

    # noinspection SpellCheckingInspection
    @classmethod
    def get_file_hash(cls, filepath):
        mtime = os.path.getmtime(filepath)

        if filepath in cls.hash_cache:
            cached_hash, cached_mtime = cls.hash_cache[filepath]
            if cached_mtime == mtime:
                return cached_hash

        hash_md5 = hashlib.md5()

        with open(filepath, "rb") as f:
            for chunk in iter(lambda: f.read(65536), b""):
                hash_md5.update(chunk)

        file_hash = hash_md5.hexdigest()
        cls.hash_cache[filepath] = (file_hash, mtime)

        return file_hash

    @classmethod
    def setup(cls):
        orig_method = getattr(dev_server.DevServer, "orig_process_command", None)
        if not orig_method:
            orig_method = dev_server.DevServer._process_command

        setattr(dev_server.DevServer, "orig_process_command", orig_method)
        cls.original_method = orig_method
        dev_server.DevServer._process_command = cls._process_command

    @classmethod
    def teardown(cls):
        orig_method = getattr(dev_server.DevServer, "orig_process_command", None)
        if orig_method:
            dev_server.DevServer._process_command = orig_method
            delattr(dev_server.DevServer, "orig_process_command")

    @staticmethod
    def _process_command(command: Dict[str, Any], client_socket: socket.socket) -> None:
        logcat(f"[ElyxDevServer] Processing command: {command}")
        action = command.get("@")
        request_id = command.get("#")

        def on_complete(result):
            _data = {"#": request_id}
            if result:
                _data["data"] = base64.b64encode(zlib.compress(json.dumps(result).encode(), level=7)).decode()

            try:
                response_data = json.dumps(_data).encode("utf-8")
                client_socket.sendall(response_data)
                logcat(f"[ElyxDevServer] Sent response for request {request_id}")
            except Exception as e:
                logcat(f"[ElyxDevServer] Error sending response: {e}\n{traceback.format_exc()}")

        try:
            handlers = {
                "elyx_ping": ElyxDevServer._handle_elyx_ping,
                "elyx_changes": ElyxDevServer._handle_elyx_changes,
                "get_elyx_plugins": ElyxDevServer._handle_get_elyx_plugins,
                "elyx_compare_folder": ElyxDevServer._handle_elyx_compare_folder,
            }

            handler: Optional[Callable[[Dict[str, Any], Callable], Dict[str, Any]]] = handlers.get(action)
            if handler:
                data = json.loads(zlib.decompress(base64.b64decode(command["data"]))) if command.get("data",
                                                                                                     None) else None
                handler(data, on_complete) or {}
            else:
                ElyxDevServer.original_method(command, client_socket)
                return
        except:  # noqa
            exception = traceback.format_exc()
            logcat(f"[ElyxDevServer] Error processing command: {exception}")
            on_complete({"error": "Error processing command: " + exception})

    # noinspection SpellCheckingInspection
    @staticmethod
    def cmpss(rs):
        return base64.b64encode(zlib.compress(json.dumps(rs).encode(), level=7)).decode()

    @staticmethod
    def _handle_elyx_changes(command: Dict[str, Any], on_complete, custom_on_complete=None):
        try:
            ElyxEngine, ElyxPlugin = _get_plugin_types()
            plugin_id = command.get("plugin_id")
            if plugin_id not in ElyxPlugin.instances_by_id:
                X = {"error": f"Plugin with id {plugin_id} not found!"}
                return on_complete(X) if not custom_on_complete else custom_on_complete.run(ElyxDevServer.cmpss(X))

            instance = ElyxPlugin.instances_by_id[plugin_id]
            plugin_dir = instance.dir_path

            errors = []
            for change in command.get("changes", []):
                event_type = change.get("#")
                try:
                    if event_type == "deleted":
                        path = Path(plugin_dir) / change["path"]
                        if not path.exists():
                            continue

                        if change["is_directory"]:
                            shutil.rmtree(Path(plugin_dir) / change["path"])
                        else:
                            os.remove(Path(plugin_dir) / change["path"])
                    elif event_type == "created":
                        path = Path(plugin_dir) / change["path"]
                        File(str(path.parent)).mkdirs()

                        with open(path, "wb") as f:
                            f.write(base64.b64decode(change["content"]))
                    elif event_type == "moved":
                        path = Path(plugin_dir) / change["dest_path"]
                        File(str(path)).mkdirs()

                        shutil.move(
                            Path(plugin_dir) / change["path"],
                            path,
                        )
                    elif event_type == "modified":
                        path = Path(plugin_dir) / change["path"]
                        File(str(path)).mkdirs()

                        with open(path, "wb") as f:
                            f.write(base64.b64decode(change["content"]))
                except Exception as e:
                    errors.append(f"Failed to apply change <{event_type}>: {e}\n{traceback.format_exc()}")

            ElyxEngine.instance.unloadPlugin(plugin_id)

            def callback(_e):
                if _e:
                    errors.append(_e)

                if not custom_on_complete:
                    on_complete({"errors": errors, "success": not errors})
                else:
                    custom_on_complete.run(ElyxDevServer.cmpss({"errors": errors, "success": not errors}))

                frag = get_last_fragment()
                if frag and (frag.getClass().getName().split(".") or [""])[-1] == "PluginsActivity":
                    run_on_ui_thread(lambda: get_private_field(frag, "listView").adapter.update(True))

            new = ElyxPlugin(instance.dir_path)
            new.init()
            ElyxEngine.instance.loadPlugin(plugin_id, plugin_dir, callback=callback)
        except:  # noqa
            X = {"error": f"Error applying changes:\n{traceback.format_exc()}"}
            if not custom_on_complete:
                on_complete(X)
            else:
                custom_on_complete.run(ElyxDevServer.cmpss(X))

    @staticmethod
    def _handle_get_elyx_plugins(_, on_complete, custom_on_complete=None):
        try:
            _, ElyxPlugin = _get_plugin_types()
            plugins = {
                plugin_id: plugin.get_metainfo()
                for plugin_id, plugin in ElyxPlugin.instances_by_id.items()
            }
            if custom_on_complete:
                custom_on_complete.run(ElyxDevServer.cmpss(plugins))
            else:
                on_complete(plugins)
        except:  # noqa
            X = {"error": f"Error getting plugins: {traceback.format_exc()}"}
            if not custom_on_complete:
                on_complete(X)
            else:
                custom_on_complete.run(ElyxDevServer.cmpss(X))

    @staticmethod
    def _handle_elyx_ping(_, on_complete):
        on_complete({"pong": True})

    @staticmethod
    def _handle_elyx_compare_folder(command, on_complete, custom_on_complete=None):
        try:
            _, ElyxPlugin = _get_plugin_types()
            plugin_id = command.get("plugin_id")
            if plugin_id not in ElyxPlugin.instances_by_id:
                X = {"error": f"Plugin with id {plugin_id} not found!"}
                return on_complete(X) if not custom_on_complete else custom_on_complete.run(ElyxDevServer.cmpss(X))

            instance = ElyxPlugin.instances_by_id[plugin_id]
            plugin_dir: str = instance.dir_path

            response = {}
            for relpath, file_hash in command.get("data", {}).items():
                path = Path(plugin_dir).joinpath(relpath)
                if not path.exists():
                    response["created"] = response.get("created", []) + [relpath]
                else:
                    if file_hash != ElyxDevServer.get_file_hash(path):
                        response["modified"] = response.get("modified", []) + [relpath]

            def walk(dirpath: str):
                if "__pycache__" in dirpath:
                    return

                for root, dirs, files in os.walk(dirpath):
                    for file in files:
                        file_path = os.path.join(root, file)
                        relpath_ = os.path.relpath(file_path, plugin_dir).replace('\\', '/')
                        if "__pycache__" in relpath_:
                            continue

                        if relpath_ not in command.get("data", {}):
                            response["deleted"] = response.get("deleted", []) + [relpath_]

                    for _dir in dirs:
                        if "__pycache__" in _dir:
                            continue

                        walk(os.path.join(root, _dir))

            walk(plugin_dir)
            if not custom_on_complete:
                on_complete(response)
            else:
                custom_on_complete.run(ElyxDevServer.cmpss(response))
        except:  # noqa
            X = {"error": f"Error comparing folder: {traceback.format_exc()}"}
            if not custom_on_complete:
                on_complete(X)
            else:
                custom_on_complete.run(ElyxDevServer.cmpss(X))


################ END DevServer Elyx patch PART ################
