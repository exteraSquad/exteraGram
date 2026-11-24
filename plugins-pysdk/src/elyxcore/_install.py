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

import re
import traceback
import warnings
from contextlib import suppress
from typing import Dict
from zipfile import ZipFile

from com.exteragram.messenger.plugins import PluginsController
from com.exteragram.messenger.plugins.ui.components import InstallPluginBottomSheet, RequiredPluginInfo

from elyxcore.utils import gen, gen2, resize_image
from android.content import DialogInterface
from android.graphics import Typeface
from android.graphics.drawable import Drawable
from android.text import InputType, TextWatcher
from android.util import TypedValue
from android.view import Gravity, HapticFeedbackConstants, View
from android.view.inputmethod import EditorInfo
from android.widget import FrameLayout, LinearLayout, ScrollView, TextView
from android_utils import run_on_ui_thread
from java import jarray, jbyte
from java.io import ByteArrayInputStream
from java.lang import Runnable as JavaRunnable
from java.util import Arrays
from org.telegram.messenger import AndroidUtilities, Utilities
from org.telegram.ui.ActionBar import AlertDialog, Theme
from org.telegram.ui.Components import EditTextBoldCursor, LayoutHelper, \
    OutlineTextContainerView, RLottieImageView
from org.telegram.ui.Stories.recorder import ButtonWithCounterView
from ui.bulletin import BulletinHelper

warnings.filterwarnings("ignore", category=SyntaxWarning)

OnClickListener = gen(View.OnClickListener, "onClick")
Runnable = gen(JavaRunnable, "run")
Callback = gen(Utilities.Callback, "run")

from elyxcore.localization import strings
from elyxcore._plugin import ArchiveEncryptionError, ElyxPlugin, RequiredPlugin, ValidationError

_ICON_PACK_RE = re.compile(r"^[a-zA-Z][a-zA-Z0-9_]*/\d+$")


class InstallAlert:

    @classmethod
    def show_password_dialog(cls, lib, fragment, params):
        activity = fragment.getParentActivity()
        try:
            def on_field_error(outlineView, _field, clear):
                outlineView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP,
                                                  HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
                if clear:
                    _field.setText("")
                outlineView.animateError(1)
                _field.setCursorColor(Theme.getColor(Theme.key_text_RedBold))

                def _fn2():
                    nonlocal is_error
                    AndroidUtilities.cancelRunOnUIThread(error_color_timeout)
                    AndroidUtilities.runOnUIThread(error_color_timeout, 1500)
                    is_error = True

                AndroidUtilities.shakeViewSpring(outlineView, 5, Runnable(_fn2))

            def process_done():
                pwd = passwordEditText.getText().toString()
                if len(pwd) == 0:
                    on_field_error(passwordOutlineView, passwordEditText, False)
                    return

                plugin = None

                try:
                    plugin = ElyxPlugin(plzip=ZipFile(params.filePath, "r"), pwd=pwd, raise_errors=False)
                except ArchiveEncryptionError:
                    on_field_error(passwordOutlineView, passwordEditText, True)
                    return
                except Exception as e:
                    lib.log(f"Error in process_done: {str(e)}\n{traceback.format_exc()}")

                dialog.dismiss()
                if plugin:
                    cls.show(lib, fragment, plugin, params)

            builder = AlertDialog.Builder(activity)

            frameLayout = FrameLayout(activity)
            builder.setView(frameLayout)

            scrollView = ScrollView(activity)
            scrollView.setFillViewport(True)
            frameLayout.addView(scrollView, LayoutHelper.createFrame(-1, -1))

            linearLayout = LinearLayout(activity)
            linearLayout.setOrientation(LinearLayout.VERTICAL)
            linearLayout.setGravity(Gravity.CENTER_HORIZONTAL)
            scrollView.addView(linearLayout, LayoutHelper.createFrame(-1, -2, Gravity.LEFT | Gravity.TOP))

            from org.telegram.messenger import R
            lockImageView = RLottieImageView(activity)
            lockImageView.setAnimation(R.raw.tsv_setup_intro, 100, 100)
            lockImageView.playAnimation()
            lockImageView.setVisibility(
                View.GONE
                if AndroidUtilities.isSmallScreen() or (
                        AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y
                        and not AndroidUtilities.isTablet()
                ) else View.VISIBLE
            )
            linearLayout.addView(lockImageView, LayoutHelper.createLinear(100, 100, Gravity.CENTER_HORIZONTAL))

            titleTextView = TextView(activity)
            titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
            titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18)
            titleTextView.setGravity(Gravity.CENTER_HORIZONTAL)
            titleTextView.setTypeface(AndroidUtilities.bold())
            titleTextView.setText(strings["archive_password_title"])
            linearLayout.addView(titleTextView,
                                 LayoutHelper.createFrame(-2, -2, Gravity.CENTER_HORIZONTAL, 24, 8, 24, 0))

            is_error = False

            def _fn():
                nonlocal is_error
                is_error = False
                passwordOutlineView.animateError(0)
                passwordEditText.setCursorColor(
                    Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated))

            error_color_timeout = Runnable(_fn)
            passwordOutlineView = OutlineTextContainerView(activity)
            passwordOutlineView.setText(strings["enter_password"])
            passwordOutlineView.animateSelection(1, False)
            linearLayout.addView(passwordOutlineView,
                                 LayoutHelper.createLinear(-1, -2, Gravity.CENTER_HORIZONTAL, 24, 24, 24, 16))

            passwordEditText = EditTextBoldCursor(activity)
            passwordEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18)
            passwordEditText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
            passwordEditText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText))
            passwordEditText.setBackground(None)
            passwordEditText.setSingleLine(True)
            passwordEditText.setInputType(InputType.TYPE_CLASS_TEXT)
            passwordEditText.setTypeface(Typeface.DEFAULT)
            passwordEditText.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated))
            passwordEditText.setCursorWidth(1.5)
            passwordEditText.setContentDescription(strings["enter_password"])
            padding = AndroidUtilities.dp(16)
            passwordEditText.setPadding(padding, padding, padding, padding)
            passwordOutlineView.addView(passwordEditText, LayoutHelper.createFrame(-1, -2))
            passwordOutlineView.attachEditText(passwordEditText)
            passwordEditText.setOnFocusChangeListener(gen(View.OnFocusChangeListener, "onFocusChange")(
                lambda v, hasFocus: passwordOutlineView.animateSelection(1 if hasFocus else 0)))

            def on_action():
                try:
                    process_done()
                except Exception as e:
                    lib.log(f"Error in on_action: {str(e)}\n{traceback.format_exc()}")

                return True

            def after_text_changed(_):
                nonlocal is_error
                if is_error:
                    AndroidUtilities.cancelRunOnUIThread(error_color_timeout)
                    error_color_timeout.run()

            passwordEditText.setOnEditorActionListener(
                gen(
                    TextView.OnEditorActionListener,
                    "onEditorAction", True, True
                )(
                    lambda v, actionId, event: (
                        on_action()
                        if actionId in [EditorInfo.IME_ACTION_NEXT, EditorInfo.IME_ACTION_DONE]
                        else False
                    )
                )
            )
            passwordEditText.addTextChangedListener(
                gen2(TextWatcher, **{
                    "onTextChanged": lambda text, *_: None,
                    "beforeTextChanged": lambda *_: None,
                    "afterTextChanged": lambda s: after_text_changed(s)
                })()
            )

            doneBtn = ButtonWithCounterView(activity, True, None)
            doneBtn.setRound()
            doneBtn.setText(strings["decrypt"], False)
            doneBtn.setOnClickListener(OnClickListener(lambda _: process_done()))
            linearLayout.addView(doneBtn,
                                 LayoutHelper.createLinear(-1, 48, Gravity.CENTER_HORIZONTAL, 24, 0, 24, 16))

            dialog = builder.create()
            dialog.setOnDismissListener(gen(DialogInterface.OnDismissListener, "onDismiss")(
                lambda _: AndroidUtilities.hideKeyboard(passwordEditText)))

            def _on_show():
                passwordEditText.requestFocus()
                passwordEditText.setSelection(passwordEditText.length())
                AndroidUtilities.showKeyboard(passwordEditText)

            dialog.setOnShowListener(gen(DialogInterface.OnShowListener, "onShow")(lambda _: _on_show()))
            dialog.setDismissDialogByButtons(False)
            dialog.show()
        except:  # noqa
            lib.log(traceback.format_exc())

    @classmethod
    def show(cls, lib, fragment, plugin: ElyxPlugin, params):
        required: Dict[str, RequiredPlugin] = {}

        if cls._report_errors(plugin, fragment, required):
            return

        validation_result = plugin.get_validation_result()
        if not validation_result or not validation_result.plugin:
            return

        metainfo = plugin.get_metainfo()
        is_update = PluginsController.getInstance().plugins.containsKey(metainfo["id"])

        params.iconDrawable = cls._read_archive_icon(plugin, metainfo)
        params.requiredPlugins = Arrays.asList(cls._build_requirements(metainfo, required))
        params.bundledPackages = Arrays.asList(metainfo.get("wheel_names", []))

        if required:
            params.blockedTitle = strings["update_required_plugins" if is_update else "install_required_plugins"]
            params.blockedSubtitle = strings["update_required_desc" if is_update else "install_required_desc"]
        else:
            lib.pending_installs.clear()
            lib.pending_installs[params.filePath] = plugin

        InstallPluginBottomSheet(fragment, validation_result, params).show()

    @staticmethod
    def _build_requirements(metainfo, required: Dict[str, RequiredPlugin]) -> list:
        pc = PluginsController.getInstance()
        result = []

        for raw_id, link_to_download in metainfo.get("requires", {}).items():
            plugin_id, req_version = raw_id, ""
            if " " in plugin_id:
                plugin_id, req_version = plugin_id.split(" ")
                req_version = req_version[1:-1]

            info = RequiredPluginInfo(plugin_id)
            info.requiredVersion = req_version or None
            info.downloadUrl = link_to_download

            installed = pc.plugins.get(plugin_id)
            if installed:
                info.name = installed.getName()
                info.currentVersion = installed.getVersion()

            if plugin_id not in required:
                info.state = RequiredPluginInfo.STATE_SATISFIED
            elif not installed:
                info.state = RequiredPluginInfo.STATE_MISSING
            elif not installed.isEnabled():
                info.state = RequiredPluginInfo.STATE_DISABLED
            else:
                info.state = RequiredPluginInfo.STATE_OUTDATED

            result.append(info)

        return result

    @staticmethod
    def _report_errors(plugin: ElyxPlugin, fragment, required: Dict[str, RequiredPlugin]) -> bool:
        if not plugin.errors:
            return False

        from org.telegram.messenger import R

        validation_errors = [str(e) for e, _ in plugin.errors if isinstance(e, ValidationError)]
        if validation_errors:
            run_on_ui_thread(
                lambda: BulletinHelper.show_with_button(
                    strings["failed_validate"] + (
                        (": " + strings.pluralize(len(validation_errors), "error_plural"))
                        if len(validation_errors) > 1 else ""),
                    R.raw.error,
                    strings["copy"],
                    lambda: (
                        BulletinHelper.show_copied_to_clipboard()
                        if AndroidUtilities.addToClipboard("- " + "\n\n- ".join(validation_errors))
                        else None
                    ), fragment
                )
            )
            return True

        for e, full_traceback in plugin.errors:
            if isinstance(e, RequiredPlugin):
                required[e.plugin_id] = e
            else:
                run_on_ui_thread(
                    lambda tb=full_traceback: BulletinHelper.show_with_button(
                        strings["failed_install"],
                        R.raw.error,
                        strings["copy"],
                        lambda: (
                            BulletinHelper.show_copied_to_clipboard()
                            if AndroidUtilities.addToClipboard(tb)
                            else None
                        ), fragment
                    )
                )
                return True

        return False

    @staticmethod
    def _read_archive_icon(plugin: ElyxPlugin, metainfo):
        icon = metainfo.get("icon", "") or ""
        if not icon or _ICON_PACK_RE.match(icon):
            return None

        dp = AndroidUtilities.dp
        with suppress(Exception):
            info = plugin.plzip.getinfo(icon)
            return Drawable.createFromStream(
                ByteArrayInputStream(
                    jarray(jbyte)(resize_image(bytes(plugin.plzip.read(info)), dp(200), dp(200)))
                ), info.filename
            )

        return None
