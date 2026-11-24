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

import traceback

from android_utils import log, run_on_ui_thread
from com.exteragram.messenger.plugins import PluginsController

from elyxcore._dev_server import ElyxDevServer
from elyxcore._importer import setup as setup_importer, stop as stop_importer
from elyxcore._plugin_engine import ElyxEngine


engine: ElyxEngine | None = None
_registration_pending = False


def _register_when_controller_is_ready():
    global _registration_pending

    if engine is None:
        _registration_pending = False
        return

    controller = PluginsController.getInstance()
    if not controller.isInitialized():
        run_on_ui_thread(_register_when_controller_is_ready, 100)
        return

    PluginsController.getEngines().put(ElyxEngine.ID, engine.java)
    _registration_pending = False

    try:
        for _pid, _inst in list(engine.plugins_instances.items()):
            try:
                if getattr(_inst, "enabled", False) and getattr(_inst, "error_message", None) is None:
                    controller.loadPluginSettings(_pid)
            except Exception:
                pass
    except Exception:
        pass


def _setup():
    global engine, _registration_pending

    if engine is not None:
        return

    setup_importer()

    engine = ElyxEngine.new_instance()
    engine.init(None, True)
    engine.checkDevServer()

    _registration_pending = True
    run_on_ui_thread(_register_when_controller_is_ready)


def _stop():
    global engine, _registration_pending

    current = engine
    engine = None
    _registration_pending = False

    PluginsController.getEngines().remove(ElyxEngine.ID)

    if current is not None:
        try:
            current.shutdown(None)
        except Exception:
            log("[ElyxCore] Failed to stop engine:\n" + traceback.format_exc())

    ElyxDevServer.teardown()
    stop_importer()
