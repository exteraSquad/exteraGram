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

import contextlib
import contextvars
import os
import traceback
from typing import Any, Optional

from java.io import File
from java.lang import Object
from java.util import ArrayList, HashMap, Arrays
from android.graphics import Bitmap
from android.media import MediaMetadataRetriever
from android.webkit import MimeTypeMap

from org.telegram.messenger import (
    AccountInstance,
    AndroidUtilities,
    DispatchQueue,
    ImageLoader,
    MediaController,
    MessageObject,
    NotificationCenter,
    SendMessagesHelper,
    UserConfig,
    Utilities,
)
from org.telegram.tgnet import RequestDelegate, TLObject, TLRPC
from org.telegram.ui import LaunchActivity

from com.exteragram.messenger.plugins import PluginsController
from android_utils import R as Runnable
from android_utils import log, run_on_ui_thread
from java import dynamic_proxy, jarray, jbyte
from extera_utils.text_formatting import parse_text
from extera_utils.get_caller import get_plugin_id
from extera_utils.lifecycle import bind_request, bind_task, capture_scope


STAGE_QUEUE = "stageQueue"
GLOBAL_QUEUE = "globalQueue"
CACHE_CLEAR_QUEUE = "cacheClearQueue"
SEARCH_QUEUE = "searchQueue"
PHONE_BOOK_QUEUE = "phoneBookQueue"
THEME_QUEUE = "themeQueue"
EXTERNAL_NETWORK_QUEUE = "externalNetworkQueue"
PLUGINS_QUEUE = "pluginsQueue"


_hook_account = contextvars.ContextVar("extera_hook_account", default=None)
_warned_account_mismatches = set()


def get_selected_account() -> int:
    return UserConfig.selectedAccount


def get_hook_account() -> Optional[int]:
    return _hook_account.get()


@contextlib.contextmanager
def account_scope(account: Optional[int]):
    token = _hook_account.set(account)
    try:
        yield
    finally:
        _hook_account.reset(token)


def _resolve_account(account: Optional[int], helper: str) -> int:
    if account is not None:
        return account

    selected = UserConfig.selectedAccount
    hook_account = _hook_account.get()
    if hook_account is not None and hook_account != selected:
        key = (helper, hook_account, selected)
        if key not in _warned_account_mismatches:
            _warned_account_mismatches.add(key)
            log(
                f"{helper}() is using account {selected} because it is the one selected in the UI, "
                f"but it was called while handling account {hook_account}. "
                f"Pass account={hook_account}, or use get_client({hook_account}), if you meant that account. "
                f"Logged once per helper."
            )
    return selected


def _account_instance(account: Optional[int], helper: str):
    return AccountInstance.getInstance(_resolve_account(account, helper))


def get_queue_by_name(queue_name: str) -> Optional[DispatchQueue]:
    try:
        field = Utilities.getClass().getField(queue_name)
        field.setAccessible(True)
        queue_instance = field.get(None)
        return queue_instance if isinstance(queue_instance, DispatchQueue) else None
    except Exception as e:
        log(f"Error getting queue '{queue_name}': {e}\n{traceback.format_exc()}")
        return None


def run_on_queue(fn: callable, queue_name: str = PLUGINS_QUEUE, delay: int = 0):
    queue = get_queue_by_name(queue_name)
    if queue:
        try:
            call_info, plugin_id = get_plugin_id(fn)
            controller = PluginsController.getInstance()
            watchdog = controller.watchdog
            scheduled_account = _hook_account.get()

            def wrapped_fn():
                if plugin_id and watchdog:
                    watchdog.onPluginExecutionStarted(plugin_id)
                try:
                    with account_scope(scheduled_account):
                        fn()
                finally:
                    if plugin_id and watchdog:
                        watchdog.onPluginExecutionFinished(plugin_id)

            callback = bind_task(wrapped_fn, once=True, scope=capture_scope(fn))
            runnable = Runnable(callback)
            try:
                queue.postRunnable(runnable, delay)
                callback.set_cancel(lambda: queue.cancelRunnable(runnable))
            except BaseException:
                callback.cancel()
                raise
            return callback
        except Exception as e:
            log(f"Error posting runnable to queue '{queue_name}': {e}\n{traceback.format_exc()}")


class RequestCallback(dynamic_proxy(RequestDelegate)):
    def __init__(self, fn: callable, account: Optional[int] = None):
        super().__init__()
        self._fn = bind_request(fn)
        self._account = account

    def run(self, response: TLObject, error: TLRPC.TL_error):
        try:
            with account_scope(self._account):
                self._fn(response, error)
        except Exception as e:
            log(f"Error in RequestDelegate: {e}\n{traceback.format_exc()}")


def send_request(request: Any, fn: callable, *, account: Optional[int] = None) -> int:
    resolved = _resolve_account(account, "send_request")
    manager = get_connections_manager(resolved)
    callback = RequestCallback(fn, resolved)
    if callback._fn.cancelled:
        return 0
    try:
        request_id = manager.sendRequest(request, callback)
        callback._fn.set_cancel(lambda: manager.cancelRequest(request_id, True))
        return request_id
    except BaseException:
        callback._fn.cancel()
        raise


def get_last_fragment():
    return LaunchActivity.getSafeLastFragment()


def get_account_instance(account: Optional[int] = None):
    return _account_instance(account, "get_account_instance")


def get_messages_controller(account: Optional[int] = None):
    return _account_instance(account, "get_messages_controller").getMessagesController()


def get_contacts_controller(account: Optional[int] = None):
    return _account_instance(account, "get_contacts_controller").getContactsController()


def get_media_data_controller(account: Optional[int] = None):
    return _account_instance(account, "get_media_data_controller").getMediaDataController()


def get_connections_manager(account: Optional[int] = None):
    return _account_instance(account, "get_connections_manager").getConnectionsManager()


def get_location_controller(account: Optional[int] = None):
    return _account_instance(account, "get_location_controller").getLocationController()


def get_notifications_controller(account: Optional[int] = None):
    return _account_instance(account, "get_notifications_controller").getNotificationsController()


def get_messages_storage(account: Optional[int] = None):
    return _account_instance(account, "get_messages_storage").getMessagesStorage()


def get_send_messages_helper(account: Optional[int] = None):
    return _account_instance(account, "get_send_messages_helper").getSendMessagesHelper()


def get_file_loader(account: Optional[int] = None):
    return _account_instance(account, "get_file_loader").getFileLoader()


def get_secret_chat_helper(account: Optional[int] = None):
    return _account_instance(account, "get_secret_chat_helper").getSecretChatHelper()


def get_download_controller(account: Optional[int] = None):
    return _account_instance(account, "get_download_controller").getDownloadController()


def get_notifications_settings(account: Optional[int] = None):
    return _account_instance(account, "get_notifications_settings").getNotificationsSettings()


def get_notification_center(account: Optional[int] = None):
    return _account_instance(account, "get_notification_center").getNotificationCenter()


def get_media_controller():
    return MediaController.getInstance()


def get_user_config(account: Optional[int] = None):
    return _account_instance(account, "get_user_config").getUserConfig()


def send_message(params: dict, parse_mode: str = None, *, account: Optional[int] = None):
    send_messages_helper = get_send_messages_helper(_resolve_account(account, "send_message"))
    message_params = SendMessagesHelper.SendMessageParams()

    defaults = {
        "peer": 0,
        "message": None,
        "caption": None,
        "location": None,
        "photo": None,
        "document": None,
        "videoEditedInfo": None,
        "user": None,
        "game": None,
        "poll": None,
        "pollSendParams": None,
        "pollIndex": 0,
        "todo": None,
        "invoice": None,
        "mediaWebPage": None,
        "cover": None,
        "path": None,
        "replyToMsg": None,
        "replyToTopMsg": None,
        "webPage": None,
        "retryMessageObject": None,
        "entities": None,
        "replyMarkup": None,
        "params": None,
        "searchLinks": True,
        "notify": True,
        "updateStickersOrder": False,
        "hasMediaSpoilers": False,
        "invert_media": False,
        "sendingHighQuality": False,
        "scheduleDate": 0,
        "scheduleRepeatPeriod": 0,
        "ttl": 0,
        "effect_id": 0,
        "stars": 0,
        "payStars": 0,
        "dice_stake": 0,
        "monoForumPeer": 0,
        "quick_reply_shortcut_id": 0,
        "quick_reply_shortcut": None,
        "parentObject": None,
        "sendAnimationData": None,
        "replyToStoryItem": None,
        "sendingStory": None,
        "replyQuote": None,
        "suggestionParams": None,
        "isLivePhoto": False,
        "livePhotoTimestamp": 0,
        "ephemeralReceiverBotId": 0,
        "richMessage": None,
        "richMessageInputUsers": None,
    }

    final_params = defaults.copy()
    final_params.update(params)
    explicit_keys = set(params)
    if parse_mode:
        if parse_mode.lower() not in ["html", "markdown"]:
            raise ValueError(f"Invalid parse mode: {parse_mode}. Must be 'html' or 'markdown'.")

        parsed = None
        if text := final_params.get("message", None):
            parsed = parse_text(text, parse_mode)
        elif caption := final_params.get("caption", None):
            parsed = parse_text(caption, parse_mode, True)

        if parsed:
            final_params.update(parsed)
            explicit_keys.update(parsed)

    def cast_to_array_list(py_list):
        if not py_list:
            return None
        java_list = ArrayList(len(py_list))
        for item in py_list:
            java_list.add(item)
        return java_list

    def cast_to_hash_map(py_dict):
        if not py_dict:
            return None
        java_map = HashMap()
        for k, v in py_dict.items():
            java_map.put(str(k), str(v))
        return java_map

    for key, value in final_params.items():
        try:
            if key in ("entities", "richMessageInputUsers") and value is not None:
                setattr(message_params, key, cast_to_array_list(value))
            elif key == "params" and value is not None:
                setattr(message_params, key, cast_to_hash_map(value))
            elif key == "notify":
                message_params.getClass().getField("notify").setBoolean(message_params, value)
            else:
                setattr(message_params, key, value)
        except Exception as e:
            if key in explicit_keys:
                log(f"Could not set parameter '{key}': {e}")

    run_on_ui_thread(lambda: send_messages_helper.sendMessage(message_params))


def _generate_photo_sizes(file_path: str, high_quality: bool = False) -> Optional[TLRPC.TL_photo]:
    try:
        photo_size_limit = AndroidUtilities.getPhotoSize(high_quality)
        bitmap = ImageLoader.loadBitmap(file_path, None, photo_size_limit, photo_size_limit, True)
        if not bitmap:
            log(f"Failed to load bitmap from {file_path}")
            return None

        sizes = ArrayList()

        thumb_size = ImageLoader.scaleAndSaveImage(bitmap, 90, 90, 55, True)
        if thumb_size:
            sizes.add(thumb_size)

        if high_quality:
            main_size = ImageLoader.scaleAndSaveImage(None, bitmap, Bitmap.CompressFormat.JPEG, True, photo_size_limit, photo_size_limit, 99, False, 101, 101, False)
        else:
            main_size = ImageLoader.scaleAndSaveImage(bitmap, photo_size_limit, photo_size_limit, True, 80, False, 101, 101)

        if main_size:
            sizes.add(main_size)

        bitmap.recycle()

        if sizes.isEmpty():
            return None

        photo = TLRPC.TL_photo()
        photo.date = get_connections_manager().getCurrentTime()
        photo.sizes = sizes
        photo.file_reference = jarray(jbyte)(0)
        return photo
    except Exception as e:
        log(f"Error generating photo sizes for '{file_path}': {e}\n{traceback.format_exc()}")
        return None


def _prepare_document(file_path: str, mime_type_str: Optional[str] = None) -> Optional[TLRPC.TL_document]:
    try:
        file = File(file_path)
        if not file.exists():
            log(f"File not found: {file_path}")
            return None

        file_name = file.getName()

        if not mime_type_str:
            extension = ""
            dot_index = file_name.rfind('.')
            if dot_index != -1:
                extension = file_name[dot_index + 1:]
            mime_type_str = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lower())
            if not mime_type_str:
                mime_type_str = "application/octet-stream"

        doc = TLRPC.TL_document()
        doc.id = 0
        doc.access_hash = 0
        doc.file_reference = jarray(jbyte)(0)
        doc.date = get_connections_manager().getCurrentTime()
        doc.mime_type = mime_type_str
        doc.size = file.length()
        doc.dc_id = 0

        attr_file_name = TLRPC.TL_documentAttributeFilename()
        attr_file_name.file_name = file_name
        doc.attributes = ArrayList()
        doc.attributes.add(attr_file_name)

        doc.thumbs = ArrayList()
        return doc
    except Exception as e:
        log(f"Error preparing document for '{file_path}': {e}")
        return None


def _add_video_attributes(document: TLRPC.TL_document, file_path: str):
    try:
        retriever = MediaMetadataRetriever()
        retriever.setDataSource(file_path)

        attr_video = TLRPC.TL_documentAttributeVideo()
        attr_video.w = int(
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH))
        attr_video.h = int(
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT))
        attr_video.duration = int(
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)) / 1000.0
        attr_video.supports_streaming = True

        document.attributes.add(attr_video)

        thumb_bitmap = retriever.getFrameAtTime()
        if thumb_bitmap:
            thumb_size = ImageLoader.scaleAndSaveImage(thumb_bitmap, 320, 320, 80, False)
            if thumb_size:
                document.thumbs.add(thumb_size)
            thumb_bitmap.recycle()
        retriever.release()
    except Exception as e:
        log(f"Could not get video metadata for '{file_path}': {e}")


def _add_audio_attributes(document: TLRPC.TL_document, file_path: str):
    try:
        retriever = MediaMetadataRetriever()
        retriever.setDataSource(file_path)

        attr_audio = TLRPC.TL_documentAttributeAudio()
        attr_audio.duration = int(
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)) / 1000.0
        attr_audio.title = retriever.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_TITLE) or ""
        attr_audio.performer = retriever.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_ARTIST) or ""

        document.attributes.add(attr_audio)
        retriever.release()
    except Exception as e:
        log(f"Could not get audio metadata for '{file_path}': {e}")


def send_text(peer: int, text: str, *, account: Optional[int] = None, parse_mode: Optional[str] = None, **kwargs):
    params = {
        "peer": peer,
        "message": text
    }
    params.update(kwargs)
    send_message(params, parse_mode, account=_resolve_account(account, "send_text"))


def send_photo(peer: int, file_path: str, caption: str = "", high_quality: bool = False, *, account: Optional[int] = None, parse_mode: Optional[str] = None, **kwargs):
    photo = _generate_photo_sizes(file_path, high_quality)
    if not photo:
        log(f"Failed to create photo object for {file_path}")
        return

    params = {
        "peer": peer,
        "photo": photo,
        "path": file_path,
        "caption": caption,
        "sendingHighQuality": high_quality
    }
    params.update(kwargs)
    send_message(params, parse_mode, account=_resolve_account(account, "send_photo"))


def send_document(peer: int, file_path: str, caption: str = "", *, account: Optional[int] = None, parse_mode: Optional[str] = None, **kwargs):
    document = _prepare_document(file_path)
    if not document:
        log(f"Failed to prepare document for {file_path}")
        return

    params = {
        "peer": peer,
        "document": document,
        "path": file_path,
        "caption": caption
    }
    params.update(kwargs)
    send_message(params, parse_mode, account=_resolve_account(account, "send_document"))


def send_video(peer: int, file_path: str, caption: str = "", *, account: Optional[int] = None, parse_mode: Optional[str] = None, **kwargs):
    document = _prepare_document(file_path, "video/mp4")
    if not document:
        log(f"Failed to prepare video document for {file_path}")
        return

    _add_video_attributes(document, file_path)

    params = {
        "peer": peer,
        "document": document,
        "path": file_path,
        "caption": caption
    }
    params.update(kwargs)
    send_message(params, parse_mode, account=_resolve_account(account, "send_video"))


def send_audio(peer: int, file_path: str, caption: str = "", *, account: Optional[int] = None, parse_mode: Optional[str] = None, **kwargs):
    document = _prepare_document(file_path, "audio/mpeg")
    if not document:
        log(f"Failed to prepare audio document for {file_path}")
        return

    _add_audio_attributes(document, file_path)

    params = {
        "peer": peer,
        "document": document,
        "path": file_path,
        "caption": caption
    }
    params.update(kwargs)
    send_message(params, parse_mode, account=_resolve_account(account, "send_audio"))


def edit_message(
        message_obj: Any,
        text: Optional[str] = None,
        file_path: Optional[str] = None,
        with_spoiler: bool = False,
        *, account: Optional[int] = None,
        parse_mode: Optional[str] = None,
        **kwargs
):
    if not isinstance(message_obj, MessageObject):
        log("'message_obj' must be a valid MessageObject instance.")
        return

    if text is None and file_path is None:
        log("edit_message called with no changes (text or file_path). Cancelled.")
        return

    if file_path and not os.path.exists(file_path):
        log(f"Media file not found at path: {file_path}")
        return

    send_helper = get_send_messages_helper(_resolve_account(account, "edit_message"))

    if text is not None:
        if parse_mode:
            if parse_mode.lower() not in ["html", "markdown"]:
                raise ValueError(f"Invalid parse mode: {parse_mode}. Must be 'html' or 'markdown'.")

            parsed = parse_text(text, parse_mode)
        else:
            parsed = {"message": text, "entities": None}

        message_obj.editingMessage = parsed["message"]
        message_obj.editingMessageEntities = ArrayList(Arrays.asList(parsed["entities"])) if parsed["entities"] else None

    photo = None
    document = None
    video_info = None
    cover = None

    if file_path:
        ext = os.path.splitext(file_path)[1].lower()

        if ext in ['.jpg', '.jpeg', '.png', '.webp']:
            photo = _generate_photo_sizes(file_path)
            if not photo:
                log(f"Failed to generate photo sizes for: {file_path}")
                return

        elif ext in ['.mp4', '.mov', '.mkv']:
            document = _prepare_document(file_path, "video/mp4")
            if not document:
                log(f"Failed to prepare document for video: {file_path}")
                return
            _add_video_attributes(document, file_path)

        elif ext in ['.mp3', '.m4a', '.ogg', '.opus', '.flac']:
            document = _prepare_document(file_path)
            if not document:
                log(f"Failed to prepare document for audio: {file_path}")
                return
            _add_audio_attributes(document, file_path)

        else:
            document = _prepare_document(file_path)
            if not document:
                log(f"Failed to prepare document for file: {file_path}")
                return

    java_params = HashMap()
    for key, value in kwargs.items():
        java_params.put(str(key), str(value))

    try:
        run_on_ui_thread(
            lambda: send_helper.editMessage(message_obj, photo, video_info, document, file_path,
                                            cover, java_params, False, with_spoiler, None))
        log(f"Edit request sent for message {message_obj.getId()} in dialog {message_obj.getDialogId()}.")
    except Exception as e:
        log(f"An error occurred while calling editMessage: {e}\n{traceback.format_exc()}")


class AccountClient:

    __slots__ = ("account",)

    def __init__(self, account: int):
        if account is None:
            raise ValueError("AccountClient requires an account; use the module level helpers for the selected one")
        self.account = int(account)

    def __repr__(self):
        return f"AccountClient(account={self.account})"

    def __eq__(self, other):
        return isinstance(other, AccountClient) and other.account == self.account

    def __hash__(self):
        return hash((AccountClient, self.account))

    def get_account_instance(self):
        return get_account_instance(self.account)

    def get_messages_controller(self):
        return get_messages_controller(self.account)

    def get_contacts_controller(self):
        return get_contacts_controller(self.account)

    def get_media_data_controller(self):
        return get_media_data_controller(self.account)

    def get_connections_manager(self):
        return get_connections_manager(self.account)

    def get_location_controller(self):
        return get_location_controller(self.account)

    def get_notifications_controller(self):
        return get_notifications_controller(self.account)

    def get_messages_storage(self):
        return get_messages_storage(self.account)

    def get_send_messages_helper(self):
        return get_send_messages_helper(self.account)

    def get_file_loader(self):
        return get_file_loader(self.account)

    def get_secret_chat_helper(self):
        return get_secret_chat_helper(self.account)

    def get_download_controller(self):
        return get_download_controller(self.account)

    def get_notifications_settings(self):
        return get_notifications_settings(self.account)

    def get_notification_center(self):
        return get_notification_center(self.account)

    def get_user_config(self):
        return get_user_config(self.account)

    def send_request(self, request: Any, fn: callable) -> int:
        return send_request(request, fn, account=self.account)

    def send_message(self, params: dict, parse_mode: str = None):
        return send_message(params, parse_mode, account=self.account)

    def send_text(self, peer: int, text: str, *, parse_mode: Optional[str] = None, **kwargs):
        return send_text(peer, text, account=self.account, parse_mode=parse_mode, **kwargs)

    def send_photo(self, peer: int, file_path: str, caption: str = "", high_quality: bool = False, *, parse_mode: Optional[str] = None, **kwargs):
        return send_photo(peer, file_path, caption, high_quality, account=self.account, parse_mode=parse_mode, **kwargs)

    def send_document(self, peer: int, file_path: str, caption: str = "", *, parse_mode: Optional[str] = None, **kwargs):
        return send_document(peer, file_path, caption, account=self.account, parse_mode=parse_mode, **kwargs)

    def send_video(self, peer: int, file_path: str, caption: str = "", *, parse_mode: Optional[str] = None, **kwargs):
        return send_video(peer, file_path, caption, account=self.account, parse_mode=parse_mode, **kwargs)

    def send_audio(self, peer: int, file_path: str, caption: str = "", *, parse_mode: Optional[str] = None, **kwargs):
        return send_audio(peer, file_path, caption, account=self.account, parse_mode=parse_mode, **kwargs)

    def edit_message(self, message_obj: Any, text: Optional[str] = None, file_path: Optional[str] = None, with_spoiler: bool = False, *, parse_mode: Optional[str] = None, **kwargs):
        return edit_message(message_obj, text, file_path, with_spoiler, account=self.account, parse_mode=parse_mode, **kwargs)


def get_client(account: Optional[int] = None) -> AccountClient:
    if account is None:
        account = _hook_account.get()
    if account is None:
        account = UserConfig.selectedAccount
    return AccountClient(account)


class NotificationCenterDelegate(dynamic_proxy(NotificationCenter.NotificationCenterDelegate)):
    def __init__(self):
        super().__init__()

    def didReceivedNotification(self, id: int, account: int, args: jarray(Object)):
        pass