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

from typing import Callable, Optional, Tuple

from android_utils import R as Runnable
from android_utils import run_on_ui_thread
from client_utils import get_last_fragment
from org.telegram.messenger import LocaleController
from org.telegram.ui.ActionBar import BaseFragment, Theme
from org.telegram.ui.Components import BulletinFactory, Bulletin


class BulletinHelper:
    """
    A helper class to easily show various types of Bulletins from Python.
    Wraps common functionalities of org.telegram.ui.Components.BulletinFactory.
    """

    DURATION_SHORT = 1500
    DURATION_LONG = 2750
    DURATION_PROLONG = 5000

    @classmethod
    def _get_factory_and_provider(cls, fragment: Optional[BaseFragment] = None) -> Tuple[
        BulletinFactory, Optional[Theme.ResourcesProvider]]:
        """
        Internal helper to get a BulletinFactory instance and its associated ResourcesProvider.
        The returned provider is the one that should be passed to factory methods
        that explicitly accept a Theme.ResourcesProvider.
        """
        if fragment:
            return BulletinFactory.of(fragment), fragment.getResourceProvider()

        safe_fragment = get_last_fragment()
        if safe_fragment:
            provider = safe_fragment.getResourceProvider()
            if safe_fragment.visibleDialog is not None and isinstance(safe_fragment.visibleDialog,
                                                                      Bulletin.BottomSheet):
                return BulletinFactory.of(safe_fragment.visibleDialog.container, provider), provider
            return BulletinFactory.of(safe_fragment), provider

        return BulletinFactory.global_(), None

    @classmethod
    def show_info(cls, message: str, fragment: Optional[BaseFragment] = None):
        """Shows a simple informational bulletin with a default info icon."""

        def _task():
            from org.telegram.messenger import R
            factory, _ = cls._get_factory_and_provider(fragment)
            b = factory.createSimpleBulletin(R.raw.info, message)
            b.show()

        run_on_ui_thread(_task)

    @classmethod
    def show_error(cls, message: str, fragment: Optional[BaseFragment] = None):
        """Shows an error bulletin with a default error icon."""

        def _task():
            factory, _ = cls._get_factory_and_provider(fragment)
            b = factory.createErrorBulletin(message)
            b.show()

        run_on_ui_thread(_task)

    @classmethod
    def show_success(cls, message: str, fragment: Optional[BaseFragment] = None):
        """Shows a success bulletin with a default success/check icon."""

        def _task():
            factory, _ = cls._get_factory_and_provider(fragment)
            b = factory.createSuccessBulletin(message)
            b.show()

        run_on_ui_thread(_task)

    @classmethod
    def show_simple(cls, text: str, icon_res_id: int, fragment: Optional[BaseFragment] = None):
        """
        Shows a bulletin with a custom icon and a single line of text.
        :param text: The message to display.
        :param icon_res_id: The R.raw.xxx resource ID for the Lottie animation.
        :param fragment: Optional BaseFragment to attach the bulletin to.
        """

        def _task():
            factory, _ = cls._get_factory_and_provider(fragment)
            b = factory.createSimpleBulletin(icon_res_id, text)
            b.show()

        run_on_ui_thread(_task)

    @classmethod
    def show_two_line(cls, title: str, subtitle: str, icon_res_id: int, fragment: Optional[BaseFragment] = None):
        """
        Shows a bulletin with a custom icon, a title, and a subtitle.
        :param title: The main text (title).
        :param subtitle: The secondary text (subtitle).
        :param icon_res_id: The R.raw.xxx resource ID for the Lottie animation.
        :param fragment: Optional BaseFragment to attach the bulletin to.
        """

        def _task():
            factory, _ = cls._get_factory_and_provider(fragment)
            b = factory.createSimpleBulletin(icon_res_id, title, subtitle)
            b.show()

        run_on_ui_thread(_task)

    @classmethod
    def show_with_button(cls,
                         text: str,
                         icon_res_id: int,
                         button_text: str,
                         on_click: Optional[Callable[[], None]],
                         fragment: Optional[BaseFragment] = None,
                         duration: int = DURATION_PROLONG):
        """
        Shows a bulletin with an icon, text, and a button.
        :param text: The main message.
        :param icon_res_id: The R.raw.xxx resource ID for the Lottie animation. Can be 0 for no icon.
        :param button_text: Text for the button.
        :param on_click: Callable to execute when the button is clicked.
        :param fragment: Optional BaseFragment to attach the bulletin to.
        :param duration: Duration to show the bulletin (e.g., Bulletin.DURATION_SHORT, Bulletin.DURATION_LONG).
        """

        def _task():
            factory, _ = cls._get_factory_and_provider(fragment)
            runnable = Runnable(on_click) if on_click else None
            b = factory.createSimpleBulletin(icon_res_id, text, button_text, duration, runnable)
            b.show()

        run_on_ui_thread(_task)

    @classmethod
    def show_undo(cls,
                  text: str,
                  on_undo: Callable[[], None],
                  on_action: Optional[Callable[[], None]] = None,
                  subtitle: Optional[str] = None,
                  fragment: Optional[BaseFragment] = None):
        """
        Shows an undo-style bulletin.
        :param text: The main message.
        :param on_undo: Callable executed if the "Undo" button is pressed.
        :param on_action: Callable executed after a delay if "Undo" is not pressed.
        :param subtitle: Optional subtitle for the bulletin.
        :param fragment: Optional BaseFragment to attach the bulletin to.
        """

        def _task():
            factory, _ = cls._get_factory_and_provider(fragment)
            undo_runnable = Runnable(on_undo)
            action_runnable = Runnable(on_action) if on_action else None

            if subtitle:
                b = factory.createUndoBulletin(text, subtitle, undo_runnable, action_runnable)
            else:
                b = factory.createUndoBulletin(text, undo_runnable, action_runnable)
            b.show()

        run_on_ui_thread(_task)

    @classmethod
    def show_copied_to_clipboard(cls, message: Optional[str] = None, fragment: Optional[BaseFragment] = None):
        """Shows a "Copied to clipboard" bulletin."""
        display_message = message
        if display_message is None:
            from org.telegram.messenger import R
            display_message = LocaleController.getString("TextCopied", R.string.TextCopied)

        def _task():
            factory, provider = cls._get_factory_and_provider(fragment)
            b = factory.createCopyBulletin(display_message, provider)
            if not isinstance(b, Bulletin.EmptyBulletin):
                b.show()

        run_on_ui_thread(_task)

    @classmethod
    def show_link_copied(cls, is_private_link_info: bool = False, fragment: Optional[BaseFragment] = None):
        """Shows a "Link copied" bulletin."""

        def _task():
            factory, _ = cls._get_factory_and_provider(fragment)
            b = factory.createCopyLinkBulletin(is_private_link_info)
            if not isinstance(b, Bulletin.EmptyBulletin):
                b.show()

        run_on_ui_thread(_task)

    @classmethod
    def show_file_saved_to_gallery(cls, is_video: bool = False, amount: int = 1,
                                   fragment: Optional[BaseFragment] = None):
        """Shows "Photo/Video saved to gallery" or plural version."""

        def _task():
            factory, provider = cls._get_factory_and_provider(fragment)

            if is_video:
                file_type_enum = BulletinFactory.FileType.VIDEOS if amount > 1 else BulletinFactory.FileType.VIDEO
            else:
                file_type_enum = BulletinFactory.FileType.PHOTOS if amount > 1 else BulletinFactory.FileType.PHOTO

            b = factory.createDownloadBulletin(file_type_enum, amount, provider)
            b.show()

        run_on_ui_thread(_task)

    @classmethod
    def show_file_saved_to_downloads(cls, file_type_enum_name: str = "UNKNOWN", amount: int = 1,
                                     fragment: Optional[BaseFragment] = None):
        """
        Shows "File saved to downloads" or similar, based on FileType enum.
        :param file_type_enum_name: String name of the enum from BulletinFactory.FileType (e.g., "UNKNOWN", "PHOTO_TO_DOWNLOADS").
        :param amount: Number of files.
        :param fragment: Optional fragment.
        """

        def _task():
            factory, provider = cls._get_factory_and_provider(fragment)
            try:
                file_type_java_class = BulletinFactory.FileType
                file_type_enum = getattr(file_type_java_class, file_type_enum_name.upper())

                if amount > 1:
                    plural_name = file_type_enum.name() + "S"
                    if hasattr(file_type_java_class, plural_name) and \
                            file_type_enum.getText(2) != file_type_enum.getText(1):
                        file_type_enum = getattr(file_type_java_class, plural_name)

            except AttributeError:
                file_type_enum = BulletinFactory.FileType.UNKNOWN
                if amount > 1:
                    file_type_enum = BulletinFactory.FileType.UNKNOWNS

            b = factory.createDownloadBulletin(file_type_enum, amount, provider)
            b.show()

        run_on_ui_thread(_task)
