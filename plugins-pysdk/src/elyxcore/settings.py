# This is the source code of the exteraGram plugins SDK.
#
# We do not and cannot prevent the use of our code,
# but be respectful and credit the original author.
#
# Copyright @exteraGram, 2022-2026
#
# Licensed under the GNU GPL v. 3 or later, with the additional terms
# in ADDITIONAL_TERMS.md at the root of the repository.

############### Settings PART ################

import plugin_settings
from com.exteragram.messenger.plugins import PluginsController  # noqa


class SettingsController:
    def __init__(self, plugin_id: str):
        self.plugin_id = plugin_id

    def get_settings(self):
        return plugin_settings.get_all_settings(self.plugin_id)

    def get_setting(self, key: str, default=None):
        return plugin_settings.get_setting(self.plugin_id, key, default)

    def get(self, key: str, default=None):
        return self.get_setting(key, default)

    def set_setting(self, key: str, value, reload_settings=False):
        plugin_settings.set_setting(self.plugin_id, key, value)

        if reload_settings:
            PluginsController.getInstance().loadPluginSettings(self.plugin_id)

    def set(self, key: str, value, reload_settings=False):
        self.set_setting(key, value, reload_settings)

    def __getitem__(self, item):  # settings["key"]
        return self.get_setting(item)

    def __setitem__(self, key, value):  # settings["key"] = value
        self.set_setting(key, value)

    def clear_settings(self):
        plugin_settings.clear_settings(self.plugin_id)

    def __call__(self, key, default=None):  # settings("key") | settings("key", default_value)
        return self.get_setting(key, default)

############### END Settings PART ################