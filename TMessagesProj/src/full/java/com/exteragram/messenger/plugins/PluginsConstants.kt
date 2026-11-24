/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins


class PluginsConstants private constructor() {
    companion object {
        const val PYTHON = "python"

        const val SEND_MESSAGE_HOOK = "send_message_hook"
        const val STRATEGY = "strategy"
        const val PARAMS = "params"
        const val UPDATE = "update"
        const val UPDATES = "updates"
        const val REQUEST = "request"
        const val RESPONSE = "response"
        const val ERROR = "error"

        const val PLUGINS = "plugins"
        const val PLUGINS_EXT = ".plugin"
        const val PLUGINS_SDK = "plugins-sdk"

        const val CREATE_SETTINGS = "create_settings"
        const val APP_START = "app_start"
        const val APP_STOP = "app_stop"
        const val APP_PAUSE = "app_pause"
        const val APP_RESUME = "app_resume"
        const val ON_APP_EVENT = "on_app_event"
        const val ON_PLUGIN_LOAD = "on_plugin_load"
        const val ON_PLUGIN_UNLOAD = "on_plugin_unload"
    }

    class Settings private constructor() {
        companion object {
            const val TYPE = "type"
            const val KEY = "key"
            const val TEXT = "text"
            const val SUBTEXT = "subtext"
            const val ICON = "icon"
            const val ACCENT = "accent"
            const val RED = "red"
            const val ON_CLICK = "on_click"
            const val DEFAULT = "default"
            const val ITEMS = "items"
            const val HINT = "hint"
            const val MULTILINE = "multiline"
            const val MAX_LENGTH = "max_length"
            const val MASK = "mask"
            const val ON_CHANGE = "on_change"
            const val TYPE_SWITCH = "switch"
            const val TYPE_INPUT = "input"
            const val TYPE_SELECTOR = "selector"
            const val TYPE_HEADER = "header"
            const val TYPE_DIVIDER = "divider"
            const val TYPE_TEXT = "text"
            const val TYPE_EDIT_TEXT = "edit_text"
            const val TYPE_CUSTOM = "custom"
            const val VIEW = "view"
            const val ITEM = "item"
            const val FACTORY = "factory"
            const val FACTORY_ARGS = "factory_args"
            const val CREATE_SUB_FRAGMENT = "create_sub_fragment"
            const val ON_LONG_CLICK = "on_long_click"
            const val LINK_ALIAS = "link_alias"
        }
    }

    class Strategy private constructor() {
        companion object {
            const val MODIFY = "MODIFY"
            const val CANCEL = "CANCEL"
            const val DEFAULT = "DEFAULT"
            const val MODIFY_FINAL = "MODIFY_FINAL"
        }
    }

    class Xposed private constructor() {
        companion object {
            const val REPLACE_HOOKED_METHOD = "replace_hooked_method"
            const val BEFORE_HOOKED_METHOD = "before_hooked_method"
            const val AFTER_HOOKED_METHOD = "after_hooked_method"
            const val HOOK_FILTERS = "__hook_filters__"
        }
    }

    class DevServer private constructor() {
        companion object {
            const val MODULE = "dev_server"
            const val CLASS = "DevServer"
            const val START_SERVER = "start_server"
            const val STOP_SERVER = "stop_server"
        }
    }

    class MenuItemTypes private constructor() {
        companion object {
            const val MESSAGE_CONTEXT_MENU = "message_context_menu"
            const val DRAWER_MENU = "drawer_menu"
            const val MAIN_MENU = "main_menu"
            const val CHAT_ACTION_MENU = "chat_action_menu"
            const val PROFILE_ACTION_MENU = "profile_action_menu"
        }
    }

    class MenuItemProperties private constructor() {
        companion object {
            const val MENU_TYPE = "menu_type"
            const val ITEM_ID = "item_id"
            const val TEXT = "text"
            const val SUBTEXT = "subtext"
            const val ICON = "icon"
            const val ON_CLICK = "on_click"
            const val ON_LONG_CLICK = "on_long_click"
            const val CONDITION = "condition"
            const val PRIORITY = "priority"
        }
    }

    class HookFilterTypes private constructor() {
        companion object {
            const val RESULT_IS_NULL = "result_is_null"
            const val RESULT_IS_TRUE = "result_is_true"
            const val RESULT_IS_FALSE = "result_is_false"
            const val RESULT_NOT_NULL = "result_not_null"
            const val RESULT_IS_INSTANCE_OF = "result_is_instance_of"
            const val RESULT_EQUAL = "result_equal"
            const val RESULT_NOT_EQUAL = "result_not_equal"
            const val ARGUMENT_IS_NULL = "argument_is_null"
            const val ARGUMENT_IS_TRUE = "argument_is_true"
            const val ARGUMENT_IS_FALSE = "argument_is_false"
            const val ARGUMENT_NOT_NULL = "argument_not_null"
            const val ARGUMENT_IS_INSTANCE_OF = "argument_is_instance_of"
            const val ARGUMENT_EQUAL = "argument_equal"
            const val ARGUMENT_NOT_EQUAL = "argument_not_equal"
            const val CONDITION = "condition"
            const val OR = "or"
        }
    }
}