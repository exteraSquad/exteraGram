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

import enum
import json
import os
import shutil
import socket
import struct
import sys
import tempfile
import threading
from typing import Dict, Any, Optional, Callable
import traceback

from com.exteragram.messenger.plugins import PluginsController
from org.telegram.messenger import FileLoader, Utilities

from android_utils import run_on_ui_thread, log
from base_plugin import BasePlugin, AppEvent
from log_utils import _set_dev_mode

from java import dynamic_proxy

class DebuggerPlatform(enum.Enum):
    PyCharm = "pycharm"
    VSCode = "vscode"

class Callback(dynamic_proxy(Utilities.Callback)):
    def __init__(self, fn: Callable[[Any], None]):
        super().__init__()
        self._fn = fn

    def run(self, arg):
        try:
            self._fn(arg)
        except:
            log(f"Error running callback: {traceback.format_exc()}")


class DebuggerEventListener(BasePlugin):
    def __init__(self, host: str, port: int, platform: DebuggerPlatform):
        super().__init__()

        self.id = "debugger_event_listener"
        self.name = "Debugger Event Listener"
        self.enabled = True
        self.initialized = True

        self.host = host
        self.port = port
        self.platform = platform

    def on_app_event(self, event_type: str):
        if self.platform != DebuggerPlatform.PyCharm:
            return

        if event_type == AppEvent.PAUSE:
            DevServer.stop_remote_debugging(self.platform)
        elif event_type == AppEvent.RESUME:
            DevServer.setup_remote_debugging(self.host, self.port, self.platform)


class DevServer:
    SOCKET_NAME = "exteragram_devserver"
    ADB_UIDS = (0, 2000)
    SOCKET_TIMEOUT = 600
    BUFFER_SIZE = 4096

    PYCHARM_DEBUGGER_DIR = os.path.join(
        FileLoader.getDirectory(FileLoader.MEDIA_DIR_FILES).getAbsolutePath(),
        "debugger",
        "pydevd-pycharm.egg",
    )

    _server_thread: Optional[threading.Thread] = None
    _server_socket: Optional[socket.socket] = None
    _is_running = False
    _active_debugging = False
    _active_platform = None

    @staticmethod
    def _get_plugins_map():
        return PluginsController.getInstance().plugins

    @classmethod
    def start_server(cls) -> threading.Thread:
        if cls._is_running and cls._server_thread and cls._server_thread.is_alive():
            log(f"Server already running on localabstract:{cls.SOCKET_NAME}")
            return cls._server_thread

        cls._server_thread = threading.Thread(target=cls._server_thread_function)
        cls._server_thread.daemon = True
        cls._server_thread.start()
        cls._is_running = True
        _set_dev_mode(True)
        log(f"Server started in background thread on localabstract:{cls.SOCKET_NAME}")
        return cls._server_thread

    @classmethod
    def stop_server(cls) -> bool:
        if not cls._is_running:
            log("Server not running")
            return False

        log("Stopping development server...")

        if cls._active_debugging and cls._active_platform:
            cls.stop_remote_debugging(cls._active_platform)

        if cls._server_socket:
            try:
                cls._server_socket.close()
                cls._server_socket = None
                log("Server socket closed")
            except Exception as e:
                log(f"Error closing server socket: {e}\n{traceback.format_exc()}")

        cls._is_running = False
        _set_dev_mode(False)

        engine = PluginsController.getEngines().get("python")
        debugger_listener = engine.debuggerListener if engine else None
        if debugger_listener:
            engine.setDebuggerListener(None)

        log("Development server stopped")
        return True

    @classmethod
    def _server_thread_function(cls):
        cls._server_socket = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)

        try:
            cls._server_socket.bind(cls._address())
            cls._server_socket.listen(1)
            log(f"Server listening on localabstract:{cls.SOCKET_NAME}")

            while cls._is_running:
                try:
                    sock = cls._server_socket
                    if sock is None:
                        break
                    sock.settimeout(0.5)
                    client_socket, _ = sock.accept()
                    uid = cls._peer_uid(client_socket)
                    if not cls._is_trusted_peer(uid):
                        log(f"Rejected connection from uid {uid}")
                        client_socket.close()
                        continue
                    log(f"Accepted connection from uid {uid}")
                    cls._handle_client(client_socket)
                except socket.timeout:
                    continue
                except Exception as e:
                    if cls._is_running:
                        log(f"Accept error: {e}\n{traceback.format_exc()}")

        except Exception as e:
            log(f"Server error: {e}\n{traceback.format_exc()}")
        finally:
            if cls._server_socket:
                cls._server_socket.close()
                cls._server_socket = None

            cls._is_running = False
            log("Server thread terminated")

    @classmethod
    def _address(cls):
        return "\0" + cls.SOCKET_NAME

    @staticmethod
    def _peer_uid(client_socket: socket.socket) -> Optional[int]:
        try:
            option = getattr(socket, "SO_PEERCRED", 17)
            _, uid, _ = struct.unpack("3i", client_socket.getsockopt(socket.SOL_SOCKET, option, struct.calcsize("3i")))
            return uid
        except OSError:
            return None

    @classmethod
    def _is_trusted_peer(cls, uid: Optional[int]) -> bool:
        return uid is not None and (uid in cls.ADB_UIDS or uid == os.getuid())

    @classmethod
    def _handle_client(cls, client_socket: socket.socket):
        buffer = b""

        try:
            client_socket.settimeout(cls.SOCKET_TIMEOUT)

            while cls._is_running:
                data = client_socket.recv(cls.BUFFER_SIZE)
                if not data:
                    break

                buffer += data
                buffer = cls._process_buffer(buffer, client_socket)

        except socket.timeout:
            log("Connection timed out")
        except Exception as e:
            log(f"Error handling client: {e}\n{traceback.format_exc()}")
        finally:
            client_socket.close()
            log("Client connection closed")

    @classmethod
    def _process_buffer(cls, buffer: bytes, client_socket: socket.socket) -> bytes:
        buffer_str = buffer.decode("utf-8", errors="replace")
        pos = 0

        while pos < len(buffer_str):
            while pos < len(buffer_str) and buffer_str[pos].isspace():
                pos += 1

            if pos >= len(buffer_str):
                break

            try:
                json_obj, end_pos = json.JSONDecoder().raw_decode(buffer_str[pos:])
                cls._process_command(json_obj, client_socket)
                pos += end_pos
            except json.JSONDecodeError:
                break

        return buffer[len(buffer_str[:pos].encode("utf-8")) :]

    @classmethod
    def _process_command(cls, command: Dict[str, Any], client_socket: socket.socket):
        log(f"Received command: {command}")
        action = command.get("@")
        request_id = command.get("#")

        handlers = {
            "get_plugins": cls._handle_get_plugins,
            "enable_plugin": cls._handle_enable_plugin,
            "disable_plugin": cls._handle_disable_plugin,
            "reload_plugin": cls._handle_reload_plugin,
            "write_plugin": cls._handle_write_plugin,
            "remove_plugin": cls._handle_remove_plugin,
            "start_debugger": cls._handle_start_debugger,
            "stop_debugger": cls._handle_stop_debugger,
            "ping": cls._handle_ping,
        }

        handler = handlers.get(action)
        if handler:
            result = handler(command) or {}
        else:
            log(f"Unknown action: {action}")
            result = {}

        data = {"#": request_id, **result}

        try:
            response_data = json.dumps(data).encode("utf-8")
            client_socket.sendall(response_data)
            log(f"Sent response for request {request_id}")
        except Exception as e:
            log(f"Error sending response: {e}\n{traceback.format_exc()}")

    @classmethod
    def _handle_get_plugins(cls, command: Dict[str, Any]):
        result = {}
        plugins = cls._get_plugins_map()
        for plugin in plugins.values().toArray():
            error = plugin.getError()
            result[plugin.getId()] = {
                "name": plugin.getName(),
                "description": plugin.getDescription(),
                "author": plugin.getAuthor(),
                "version": plugin.getVersion(),
                "error": error.getLocalizedMessage() if error else None,
                "enabled": plugin.isEnabled(),
            }
        return result

    @classmethod
    def _handle_enable_plugin(cls, command: Dict[str, Any]):
        plugin_id = command.get("plugin_id")
        if not plugin_id:
            log("Missing plugin_id in enable_plugin command")
            return {"error": "Missing plugin_id"}

        plugins = cls._get_plugins_map()
        if not plugins.containsKey(plugin_id):
            log(f"Plugin {plugin_id} not found")
            return {"error": "Plugin not found"}

        log(f"Enabling plugin {plugin_id}")
        run_on_ui_thread(
            lambda: PluginsController.getInstance().setPluginEnabled(
                plugin_id, True, None
            )
        )
        return None

    @classmethod
    def _handle_disable_plugin(cls, command: Dict[str, Any]):
        plugin_id = command.get("plugin_id")
        if not plugin_id:
            log("Missing plugin_id in disable_plugin command")
            return {"error": "Missing plugin_id"}

        plugins = cls._get_plugins_map()
        if not plugins.containsKey(plugin_id):
            log(f"Plugin {plugin_id} not found")
            return {"error": "Plugin not found"}

        log(f"Disabling plugin {plugin_id}")
        run_on_ui_thread(
            lambda: PluginsController.getInstance().setPluginEnabled(
                plugin_id, False, None
            )
        )
        return None

    @classmethod
    def _handle_reload_plugin(cls, command: Dict[str, Any]):
        plugin_id = command.get("plugin_id")
        if not plugin_id:
            log("Missing plugin_id in reload_plugin command")
            return {"error": "Missing plugin_id"}

        plugins = cls._get_plugins_map()
        if not plugins.containsKey(plugin_id):
            log(f"Plugin {plugin_id} not found")
            return {"error": "Plugin not found"}

        log(f"Reloading plugin {plugin_id}")
        run_on_ui_thread(lambda: cls._reload_plugin(plugin_id))
        return None

    @classmethod
    def _handle_write_plugin(cls, command: Dict[str, Any]):
        plugin_id = command.get("plugin_id")
        content = command.get("content")

        if not plugin_id or not content:
            log("Missing plugin_id or content in write_plugin command")
            return {"error": "Missing plugin_id or content"}

        log(f"Writing plugin {plugin_id}")
        controller = PluginsController.getInstance()

        try:
            with tempfile.NamedTemporaryFile(mode="w", suffix=".py", delete=False, encoding="utf-8") as tmp_file:
                tmp_file.write(content)
                tmp_file.flush()
                os.fsync(tmp_file.fileno())
                temp_plugin_path = tmp_file.name

            log(f"Plugin content written to temporary file: {temp_plugin_path}")

            def activate():
                def _callback(error: str):
                    if os.path.exists(temp_plugin_path):
                        os.remove(temp_plugin_path)

                    if error:
                        log(f"Error activating plugin {plugin_id}: {error}")
                        return

                    plugins = cls._get_plugins_map()
                    plugin = plugins.get(plugin_id) if plugins else None
                    if plugin and not plugin.isEnabled():
                        controller.setPluginEnabled(plugin_id, True, None)

                try:
                    PluginsController.getEngines().get("python").loadPluginFromFile(temp_plugin_path, None, Callback(_callback))
                except Exception as e:
                    log(f"Error activating plugin {plugin_id}: {e}\n{traceback.format_exc()}")
                    if os.path.exists(temp_plugin_path):
                        os.remove(temp_plugin_path)

            run_on_ui_thread(activate)
            log(f"Plugin {plugin_id} written successfully")
            return None
        except Exception as e:
            log(f"Error writing plugin {plugin_id}: {e}\n{traceback.format_exc()}")
            return {"error": str(e)}

    @classmethod
    def _handle_remove_plugin(cls, command: Dict[str, Any]):
        plugin_id = command.get("plugin_id")
        if not plugin_id:
            log("Missing plugin_id in remove_plugin command")
            return {"error": "Missing plugin_id"}

        plugins = cls._get_plugins_map()
        if not plugins.containsKey(plugin_id):
            log(f"Plugin {plugin_id} not found")
            return {"error": "Plugin not found"}

        log(f"Removing plugin {plugin_id}")
        run_on_ui_thread(
            lambda: PluginsController.getInstance().deletePlugin(plugin_id, None)
        )
        return None

    @classmethod
    def _handle_start_debugger(cls, command: Dict[str, Any]):
        if cls._active_debugging:
            log("Debugger is already active.")
            return {"status": "already_running"}

        host = command.get("host")
        port = command.get("port")
        platform_str = command.get("platform")

        if not host or not port or not platform_str:
            log("Missing host, port, or platform in start_debugger command")
            return {"error": "Missing required parameters"}

        try:
            platform = DebuggerPlatform(platform_str)
        except ValueError:
            log(f"Invalid platform: {platform_str}")
            return {"error": f"Invalid platform: {platform_str}"}

        log(f"Starting remote debugger on {host}:{port}, platform: {platform}")
        success = cls.setup_remote_debugging(host, port, platform)

        if success:
            listener = DebuggerEventListener(host, port, platform)
            PluginsController.getEngines().get("python").setDebuggerListener(listener)
            cls._active_debugging = True
            cls._active_platform = platform
            return {"status": "started"}
        else:
            return {"error": "Failed to start debugger"}

    @classmethod
    def _handle_stop_debugger(cls, command: Dict[str, Any]):
        log("Stopping remote debugger")

        if not cls._active_debugging:
            log("Debugger is not active.")
            return {"status": "not_running"}

        platform_str = command.get("platform")
        if not platform_str:
            log("Missing platform in stop_debugger command")
            return {"error": "Missing platform"}

        try:
            platform = DebuggerPlatform(platform_str)
        except ValueError:
            log(f"Invalid platform: {platform_str}")
            return {"error": f"Invalid platform: {platform_str}"}

        run_on_ui_thread(lambda: cls.stop_remote_debugging(platform))

        PluginsController.getEngines().get("python").setDebuggerListener(None)

        cls._active_debugging = False
        cls._active_platform = None

        return {"status": "stopped"}

    @classmethod
    def _handle_ping(cls, command: Dict[str, Any]):
        log("Received ping command")
        return {"pong": True}

    @classmethod
    def _reload_plugin(cls, plugin_id: str):
        try:
            controller = PluginsController.getInstance()
            plugin_path = controller.getPluginPath(plugin_id)

            if not plugin_path or not os.path.exists(plugin_path):
                log(f"Cannot reload plugin '{plugin_id}': file not found.")
                return

            with tempfile.NamedTemporaryFile(mode="w", suffix=".py", delete=False, encoding="utf-8") as tmp_file:
                temp_copy_path = tmp_file.name
            shutil.copyfile(plugin_path, temp_copy_path)

            def _callback(error: str):
                if os.path.exists(temp_copy_path):
                    os.remove(temp_copy_path)
                if error:
                    log(f"Error reloading plugin {plugin_id}: {error}")
                else:
                    log(f"Plugin '{plugin_id}' reloaded successfully.")

            PluginsController.getEngines().get("python").loadPluginFromFile(temp_copy_path, None, Callback(_callback))
            log(f"Scheduled reload for plugin '{plugin_id}.py'.")
        except Exception as e:
            log(f"Error during _reload_plugin for '{plugin_id}.py': {e}\n{traceback.format_exc()}")

    @classmethod
    def setup_remote_debugging(
            cls, host: str, port: int, platform: DebuggerPlatform
    ) -> bool:
        if platform == DebuggerPlatform.PyCharm:
            success = cls._setup_pycharm_remote_debugger(host, port)
        elif platform == DebuggerPlatform.VSCode:
            success = cls._setup_vscode_remote_debugger(host, port)
        else:
            log(f"Unsupported debugger platform: {platform}")
            success = False

        if success:
            cls._active_debugging = True
            cls._active_platform = platform

        return success

    @classmethod
    def stop_remote_debugging(cls, platform: DebuggerPlatform) -> bool:
        if platform == DebuggerPlatform.PyCharm:
            success = cls._stop_pycharm_remote_debugging()
        elif platform == DebuggerPlatform.VSCode:
            success = cls._stop_vscode_remote_debugger()
        else:
            log(f"Unsupported debugger platform: {platform}")
            success = False

        if success:
            cls._active_debugging = False
            cls._active_platform = None

        return success

    @classmethod
    def _setup_pycharm_remote_debugger(cls, host: str, port: int) -> bool:
        try:
            sys.path.append(cls.PYCHARM_DEBUGGER_DIR)
            import pydevd_pycharm

            log(f"Setting up PyCharm remote debugger on {host}:{port}")
            pydevd_pycharm.settrace(
                host,
                port=port,
                stdoutToServer=True,
                stderrToServer=True,
                suspend=False,
            )
            log("PyCharm remote debugger setup complete")
            return True
        except ImportError:
            log("PyCharm debugger module not found")
            return False
        except Exception as e:
            log(f"Error setting up PyCharm remote debugger: {e}")
            return False

    @classmethod
    def _setup_vscode_remote_debugger(cls, host: str, port: int) -> bool:
        try:
            import debugpy

            log(f"Connecting VS Code remote debugger to {host}:{port}")
            debugpy.listen((host, port), in_process_debug_adapter=True)
            log("VS Code remote debugger connected successfully")
            return True
        except ImportError:
            log("VS Code debugger module not found")
            return False
        except Exception as e:
            log(f"Error setting up VS Code remote debugger: {e}")
            return False

    @classmethod
    def _stop_pycharm_remote_debugging(cls) -> bool:
        try:
            import pydevd_pycharm

            log("Stopping PyCharm remote debugger")
            pydevd_pycharm.stoptrace()
            log("PyCharm remote debugger stopped successfully")
            return True
        except ImportError:
            log("PyCharm debugger module not found")
            return False
        except Exception as e:
            log(f"Error stopping PyCharm remote debugger: {e}")
            return False

    @classmethod
    def _stop_vscode_remote_debugger(cls) -> bool:
        try:
            import debugpy._vendored.pydevd.pydevd

            log("Stopping VS Code remote debugger")
            debugpy._vendored.pydevd.pydevd.stoptrace()
            log("VS Code remote debugger stopped successfully")
            return True
        except ImportError:
            log("VS Code debugger module not found")
            return False
        except Exception as e:
            log(f"Error stopping VS Code remote debugger: {e}")
            return False