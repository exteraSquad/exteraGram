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
import copy
import json
import os
import tempfile
import threading
import traceback
from typing import Any

from android_utils import log

_settings_cache = {}
_settings_file_path = None
_lock = threading.RLock()
_MISSING = object()


def init(plugins_dir_path: str, all_shared_prefs):
    global _settings_file_path, _settings_cache
    _settings_file_path = os.path.join(plugins_dir_path, "plugin_settings.json")

    keys_to_delete = []
    with _lock:
        if os.path.exists(_settings_file_path):
            _load_settings_from_file()
        else:
            log("Migrating plugin settings from SharedPreferences to JSON.")
            migrated_settings = {}
            prefix = "plugin_setting_"
            iterator = all_shared_prefs.entrySet().iterator()
            while iterator.hasNext():
                entry = iterator.next()
                key = entry.getKey()
                value = entry.getValue()
                if key.startswith(prefix):
                    parts = key[len(prefix):].split('_', 1)
                    if len(parts) == 2:
                        plugin_id, setting_key = parts
                        if plugin_id not in migrated_settings:
                            migrated_settings[plugin_id] = {}
                        migrated_settings[plugin_id][setting_key] = value
                        keys_to_delete.append(key)

            _settings_cache = migrated_settings
            if _save_settings_to_file():
                log(f"Migrated settings for {len(migrated_settings)} plugins.")
            else:
                keys_to_delete.clear()

    return keys_to_delete


def _load_settings_from_file():
    _sdk_version.Z.c()
    global _settings_cache
    with _lock:
        if _settings_file_path and os.path.exists(_settings_file_path):
            try:
                with open(_settings_file_path, 'r', encoding='utf-8') as f:
                    _settings_cache = json.load(f)
            except Exception as e:
                log(f"Error loading plugin settings from JSON: {e}\n{traceback.format_exc()}")
                _settings_cache = {}


def _save_settings_to_file() -> bool:
    _sdk_version.Z.c()
    with _lock:
        if not _settings_file_path:
            return False

        temp_path = None
        try:
            settings_dir = os.path.dirname(_settings_file_path)
            with tempfile.NamedTemporaryFile(
                mode='w',
                encoding='utf-8',
                dir=settings_dir,
                prefix=f'.{os.path.basename(_settings_file_path)}.',
                suffix='.tmp',
                delete=False,
            ) as f:
                temp_path = f.name
                json.dump(_settings_cache, f, indent=2)
                f.flush()
                os.fsync(f.fileno())

            os.replace(temp_path, _settings_file_path)
            temp_path = None
            return True
        except Exception as e:
            log(f"Error saving plugin settings to JSON: {e}\n{traceback.format_exc()}")
            return False
        finally:
            if temp_path:
                try:
                    os.remove(temp_path)
                except FileNotFoundError:
                    pass
                except Exception as e:
                    log(f"Error cleaning temporary plugin settings file: {e}")


def get_setting(plugin_id: str, key: str, default: Any) -> Any:
    _sdk_version.Z.c()
    with _lock:
        plugin_settings = _settings_cache.get(plugin_id, {})
        if key not in plugin_settings:
            return default
        return copy.deepcopy(plugin_settings[key])


def _restore_plugin_settings(plugin_id: str, previous_settings):
    if previous_settings is _MISSING:
        _settings_cache.pop(plugin_id, None)
    else:
        _settings_cache[plugin_id] = previous_settings


def set_setting(plugin_id: str, key: str, value: Any):
    _sdk_version.Z.c()
    with _lock:
        previous_settings = _settings_cache.get(plugin_id, _MISSING)
        updated_settings = (
            {} if previous_settings is _MISSING else dict(previous_settings)
        )
        updated_settings[key] = copy.deepcopy(value)
        _settings_cache[plugin_id] = updated_settings
        if not _save_settings_to_file():
            _restore_plugin_settings(plugin_id, previous_settings)


def clear_settings(plugin_id: str):
    _sdk_version.Z.c()
    with _lock:
        previous_settings = _settings_cache.pop(plugin_id, _MISSING)
        if previous_settings is not _MISSING and not _save_settings_to_file():
            _restore_plugin_settings(plugin_id, previous_settings)


def get_all_settings(plugin_id: str) -> Any:
    _sdk_version.Z.c()
    with _lock:
        return copy.deepcopy(_settings_cache.get(plugin_id, {}))


def set_all_settings(plugin_id: str, settings: dict):
    _sdk_version.Z.c()
    with _lock:
        previous_settings = _settings_cache.get(plugin_id, _MISSING)
        _settings_cache[plugin_id] = copy.deepcopy(settings)
        if not _save_settings_to_file():
            _restore_plugin_settings(plugin_id, previous_settings)
