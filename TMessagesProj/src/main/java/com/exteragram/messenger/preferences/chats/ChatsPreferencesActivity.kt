/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.preferences.chats

import android.animation.ValueAnimator
import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.view.View
import androidx.core.content.ContextCompat
import com.exteragram.messenger.*
import com.exteragram.messenger.ai.AiController
import com.exteragram.messenger.ai.ui.activities.AiPreferencesActivity
import com.exteragram.messenger.camera.CameraXSession
import com.exteragram.messenger.preferences.BasePreferencesActivity
import com.exteragram.messenger.preferences.SwitchGroup
import com.exteragram.messenger.preferences.chats.components.DoubleTapCell
import com.exteragram.messenger.preferences.chats.components.MessagesPreviewCell
import com.exteragram.messenger.preferences.chats.components.SliderPreviewCell
import com.exteragram.messenger.preferences.chats.components.StickerShapeCell
import com.exteragram.messenger.speech.VoiceRecognitionController
import com.exteragram.messenger.speech.ui.RecognitionModelDialogs
import com.exteragram.messenger.translator.TranslatorUtils
import com.exteragram.messenger.utils.chats.DoubleTapUtils
import com.exteragram.messenger.utils.chats.SwipeAction
import com.exteragram.messenger.utils.ui.PopupUtils
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.ActionBarMenuItem
import org.telegram.ui.ActionBar.INavigationLayout.REBUILD_FLAG_REBUILD_LAST
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.TextCell
import org.telegram.ui.Components.ColoredImageSpan
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import org.telegram.ui.Stories.recorder.DualCameraView
import org.telegram.ui.ThemeActivity
import java.util.ArrayList
import androidx.core.content.edit
import com.exteragram.messenger.utils.addIf

class ChatsPreferencesActivity : BasePreferencesActivity() {

    companion object {
        private const val SWIPE_ACTIONS_SHOWN = 5
        private val languageCodes = listOf("none", "en", "es", "zh", "hi", "fa", "fr", "ru", "pt", "de", "ja", "ko", "it", "uk", "gu", "pl", "nl", "tr", "vi", "cs", "uz", "eo", "kk", "tg", "ca")
    }

    enum class ChatsItem {
        STICKER_SIZE,
        STICKER_TIME,
        REPLY_ELEMENTS,
        REPLY_COLORS,
        REPLY_EMOJI,
        REPLY_BACKGROUND,
        STICKER_SHAPE,
        AI,
        CHAT_SETTINGS,
        UNLIMITED_RECENT_STICKERS,
        HIDE_REACTIONS,
        DOUBLE_TAP,
        DOUBLE_TAP_ACTION,
        DOUBLE_TAP_ACTION_OUT_OWNER,
        SWIPE_ACTIONS,
        BOTTOM_BUTTON,
        WIDE_POSTS,
        WIDE_POSTS_FEED,
        WIDE_POSTS_CHANNELS,
        AI_FEATURES,
        AI_FEATURES_EDITOR,
        AI_FEATURES_SUMMARIES,
        AI_FEATURES_INSTANT_VIEW_SUMMARIES,
        ADMIN_SHORTCUTS,
        QUICK_TRANSITIONS,
        QUICK_TRANSITION_FOR_CHANNELS,
        QUICK_TRANSITION_FOR_TOPICS,
        DISABLE_GREETING_STICKER,
        AUTO_RUN_WIDGETS,
        HIDE_KEYBOARD_ON_SCROLL,
        ADD_COMMA_AFTER_MENTION,
        INLINE_MATH_RESULT,
        HIDE_SEND_AS_PEER,
        MESSAGES_PREVIEW,
        REMOVE_MESSAGE_TAIL,
        REPLACE_EDITED_WITH_ICON,
        SHOW_ONLINE_STATUS,
        HIDE_SHARE_BUTTON,
        SHOW_RESULTS_BEFORE_VOTING,
        MESSAGE_MENU,
        COPY_PHOTO,
        SAVE,
        REPEAT,
        CLEAR,
        HISTORY,
        REPORT,
        GENERATE,
        DETAILS,
        GROUP_MESSAGE_MENU,
        GROUPS,
        CHANNELS,
        PRIVATE_CHATS,
        SPEECH_RECOGNITION_LANGUAGE,
        POST_PROCESSING_WITH_AI,
        DELETE_RECOGNITION_MODEL,
        CAMERA_TYPE,
        CAMERA_SETTINGS,
        DUAL_CAMERA,
        EXTENDED_FRAMES_PER_SECOND,
        CAMERA_STABILIZATION,
        CAMERA_MIRROR_MODE,
        VIDEO_MESSAGES_CAMERA,
        REMEMBER_LAST_USED_CAMERA,
        START_WITH_WIDE_ANGLE_CAMERA,
        ZOOM_SLIDER,
        STATIC_ZOOM,
        ALWAYS_SEND_IN_HD,
        HIDE_CAMERA_TILE,
        DOUBLE_TAP_SEEK_DURATION,
        PREFER_ORIGINAL_QUALITY,
        SWIPE_TO_PIP,
        UNMUTE_WITH_VOLUME_BUTTONS,
        PAUSE_ON_MINIMIZE,
        PAUSE_ON_MINIMIZE_VIDEO,
        PAUSE_ON_MINIMIZE_VOICE,
        PAUSE_ON_MINIMIZE_ROUND,
        SHOW_FORWARDS_COUNT;

        val id: Int = ordinal + 1
    }

    private lateinit var stickerTimeModes: Array<CharSequence>
    private lateinit var doubleTapActions: Array<CharSequence>
    private lateinit var doubleTapOutActions: Array<CharSequence>
    private lateinit var bottomButtonOptions: Array<CharSequence>
    private lateinit var videoMessagesCameraOptions: Array<CharSequence>
    private lateinit var doubleTapSeekDurationOptions: Array<CharSequence>
    private lateinit var cameraTypeOptions: Array<CharSequence>
    private lateinit var recognitionLanguageOptions: Array<CharSequence>

    private var resetItem: ActionBarMenuItem? = null
    private var stickerSizeCell: SliderPreviewCell? = null
    private var doubleTapCell: DoubleTapCell? = null
    private var stickerShapeCell: StickerShapeCell? = null
    private var messagesPreviewCell: MessagesPreviewCell? = null

    private val replyElements =
        SwitchGroup(this, ChatsItem.REPLY_ELEMENTS, R.string.RepliesTitle, linkAlias = "replyElements").apply {
            onChanged(this@ChatsPreferencesActivity::updateReplySettings)
            add(ChatsItem.REPLY_COLORS, R.string.BackgroundColors, ::replyColors)
            add(ChatsItem.REPLY_EMOJI, R.string.Emoji, ::replyEmoji)
            add(ChatsItem.REPLY_BACKGROUND, R.string.ReplyBackground, ::replyBackground)
        }

    private val hideReactions =
        SwitchGroup(this, ChatsItem.HIDE_REACTIONS, R.string.HideReactions, linkAlias = "hideReactions").apply {
            onChanged(this@ChatsPreferencesActivity::updateReplySettings)
            add(ChatsItem.CHANNELS, R.string.ChannelsTab, ::hideReactionsInChannels)
            add(ChatsItem.GROUPS, R.string.SaveToGalleryGroups, ::hideReactionsInGroups)
            add(ChatsItem.PRIVATE_CHATS, R.string.PrivateChats, ::hideReactionsInPrivateChats)
        }

    private val widePosts =
        SwitchGroup(this, ChatsItem.WIDE_POSTS, R.string.WidePosts, linkAlias = "widePosts").apply {
            onChanged { parentLayout.rebuildFragments(if (AndroidUtilities.isTablet()) REBUILD_FLAG_REBUILD_LAST else 0) }
            add(ChatsItem.WIDE_POSTS_FEED, R.string.Feed, ::widePostsInFeed)
            add(ChatsItem.WIDE_POSTS_CHANNELS, R.string.ChannelsTab, ::widePostsInChannels)
        }

    private val aiFeatures =
        SwitchGroup(this, ChatsItem.AI_FEATURES, R.string.AIFeatures, linkAlias = "aiFeatures").apply {
            onChanged { parentLayout.rebuildFragments(0) }
            add(ChatsItem.AI_FEATURES_EDITOR, R.string.AIFeaturesEditor, ::telegramAiEditor)
            add(ChatsItem.AI_FEATURES_SUMMARIES, R.string.AIFeaturesSummaries, ::telegramAiSummaries)
            add(ChatsItem.AI_FEATURES_INSTANT_VIEW_SUMMARIES, R.string.AIFeaturesInstantViewSummaries, ::telegramAiInstantViewSummaries)
        }

    private val quickTransitions =
        SwitchGroup(this, ChatsItem.QUICK_TRANSITIONS, R.string.QuickTransitions, linkAlias = "quickTransitions").apply {
            add(ChatsItem.QUICK_TRANSITION_FOR_CHANNELS, R.string.FilterChannels, ::quickTransitionForChannels)
            add(ChatsItem.QUICK_TRANSITION_FOR_TOPICS, R.string.Topics, ::quickTransitionForTopics)
        }

    private val messageMenu =
        SwitchGroup(this, ChatsItem.MESSAGE_MENU, R.string.MessageMenu, linkAlias = "messageMenu").apply {
            onChanged { parentLayout.rebuildFragments(0) }
            add(ChatsItem.COPY_PHOTO, R.string.CopyPhoto, ::showCopyPhotoButton)
            add(ChatsItem.SAVE, R.string.Save, ::showSaveMessageButton)
            add(ChatsItem.REPEAT, R.string.Repeat, ::showRepeatMessageButton)
            add(ChatsItem.CLEAR, R.string.Clear, ::showClearButton)
            add(ChatsItem.HISTORY, R.string.MessageHistory, ::showHistoryButton)
            add(ChatsItem.REPORT, R.string.ReportChat, ::showReportButton)
            addIf({ AiController.canUseAI() }, ChatsItem.GENERATE, R.string.Generate, ::showGenerateButton)
            add(ChatsItem.DETAILS, R.string.Details, ::showDetailsButton)
        }

    private val cameraSettings =
        SwitchGroup(this, ChatsItem.CAMERA_SETTINGS, R.string.ExtendedSettings, linkAlias = "cameraSettings").apply {
            addIf(
                { isSeamlessSwitchingAvailable() }, ChatsItem.DUAL_CAMERA, R.string.SeamlessSwitching,
                { DualCameraView.roundDualAvailableStatic(context) },
                { checked -> MessagesController.getGlobalMainSettings().edit {
                    putBoolean("rounddual_available", checked)
                } }
            )
            add(ChatsItem.EXTENDED_FRAMES_PER_SECOND, R.string.ExtendedFramesPerSecond, ::extendedFramesPerSecond)
            add(ChatsItem.CAMERA_STABILIZATION, R.string.CameraStabilization, ::cameraStabilization)
            addIf({ cameraType != CameraType.CAMERA_2 }, ChatsItem.CAMERA_MIRROR_MODE, R.string.CameraMirrorMode, ::cameraMirrorMode)
            addIf({ cameraType == CameraType.CAMERA_X }, ChatsItem.START_WITH_WIDE_ANGLE_CAMERA, R.string.StartWithWideAngleCamera, ::startWithWideAngleCamera)
            markNew("Camera-ExtendedSettings-StartWithWideAngle")
        }

    private val pauseOnMinimize =
        SwitchGroup(this, ChatsItem.PAUSE_ON_MINIMIZE, R.string.PauseOnMinimize, linkAlias = "pauseOnMinimize").apply {
            add(ChatsItem.PAUSE_ON_MINIMIZE_VIDEO, R.string.PauseOnMinimizeVideo, ::pauseOnMinimizeVideo)
            add(ChatsItem.PAUSE_ON_MINIMIZE_VOICE, R.string.PauseOnMinimizeVoice, ::pauseOnMinimizeVoice)
            add(ChatsItem.PAUSE_ON_MINIMIZE_ROUND, R.string.PauseOnMinimizeRound, ::pauseOnMinimizeRound)
        }

    override fun initializeOptionStrings() {
        stickerTimeModes = arrayOf(
            getString(R.string.Default),
            getString(R.string.StickerTimeSide),
            getString(R.string.StickerTimeHidden)
        )
        doubleTapActions = DoubleTapUtils.getDoubleTapActions(false)
        doubleTapOutActions = DoubleTapUtils.getDoubleTapActions(true)
        bottomButtonOptions = arrayOf(
            getString(R.string.Hide),
            getString(R.string.ChannelMuteNoCaps),
            getString(R.string.ChannelDiscussNoCaps)
        )
        videoMessagesCameraOptions = arrayOf(
            getString(R.string.VideoMessagesCameraFront),
            getString(R.string.VideoMessagesCameraRear),
            getString(R.string.VideoMessagesCameraAsk)
        )
        doubleTapSeekDurationOptions = arrayOf(
            LocaleController.formatPluralString("Seconds", 5),
            LocaleController.formatPluralString("Seconds", 10),
            LocaleController.formatPluralString("Seconds", 15),
            LocaleController.formatPluralString("Seconds", 30)
        )
        cameraTypeOptions = arrayOf(
            "Camera 1",
            "Camera 2",
            "Camera X"
        )
        recognitionLanguageOptions = languageCodes.map {
            RecognitionModelDialogs.getRecognitionLanguageOption(it)
        }.toTypedArray()
    }

    override fun createView(context: Context): View {
        val slider = SliderPreviewCell(
            parentLayout, context,
            ChatsItem.STICKER_SIZE.id,
            4, 20,
            stickerSize,
            getString(R.string.StickerSize),
            getString(R.string.StickerSizeLeft),
            getString(R.string.StickerSizeRight),
            false
        )
        slider.setListener { p ->
            stickerSize = p
            resetItem?.let { reset ->
                if (reset.visibility != View.VISIBLE)
                    AndroidUtilities.updateViewVisibilityAnimated(reset, true, 0.5f, true)
            }
        }
        stickerSizeCell = slider

        stickerShapeCell = object : StickerShapeCell(context) {
            override fun updateStickerPreview() {
                parentLayout.rebuildFragments(0)
                stickerSizeCell?.invalidate()
            }
        }
        doubleTapCell = DoubleTapCell(context)
        messagesPreviewCell = MessagesPreviewCell(context, parentLayout, MessagesPreviewCell.TYPE_SINGLE)

        val contentView = super.createView(context)

        resetItem = actionBar.createMenu().addItem(0, R.drawable.msg_reset).apply {
            contentDescription = getString(R.string.Reset)
            visibility = if (stickerSize != 12.0f) View.VISIBLE else View.GONE
            tag = null
            setOnClickListener {
                AndroidUtilities.updateViewVisibilityAnimated(it, false, 0.5f, true)
                val animator = ValueAnimator.ofFloat(stickerSize, 12.0f)
                animator.duration = 200
                animator.addUpdateListener { valueAnimator ->
                    val progress = valueAnimator.animatedValue as Float
                    stickerSize = progress
                    stickerSizeCell?.seekBar?.setProgress(progress)
                    stickerSizeCell?.invalidate()
                }
                animator.start()
            }
        }

        return contentView
    }

    override fun getTitle(): String = getString(R.string.SearchAllChatsShort)

    private fun swipeActionsValue(): CharSequence {
        val enabled = SwipeAction.enabled()
        if (enabled.isEmpty()) return getString(R.string.Disable)
        val value = SpannableStringBuilder()
        val shown = enabled.size.coerceAtMost(SWIPE_ACTIONS_SHOWN)

        for (a in 0 until shown) {
            if (a > 0) value.append(' ')
            val icon = ContextCompat.getDrawable(context, enabled[a].iconRes) ?: continue
            val start = value.length
            value.append('*')
            val span = ColoredImageSpan(icon.mutate())
            span.setSize(AndroidUtilities.dp(18f))
            value.setSpan(span, start, value.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        if (enabled.size > shown)
            value.append(" +").append((enabled.size - shown).toString())
        return value
    }

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        items.add(UItem.asCustom(ChatsItem.STICKER_SIZE.id, stickerSizeCell).setLinkAlias("stickerSize", this))
        items.add(uButton(ChatsItem.STICKER_TIME, getString(R.string.StickerTimeMode), value = stickerTimeModes[stickerTimeMode.ordinal], linkAlias = "stickerTime"))
        replyElements.fill(items)
        items.add(UItem.asShadow())

        items.add(UItem.asHeader(getString(R.string.StickerShape)))
        items.add(UItem.asCustom(ChatsItem.STICKER_SHAPE.id, stickerShapeCell).setLinkAlias("stickerShape", this))
        items.add(UItem.asShadow())

        items.add(uButton(ChatsItem.AI, getString(R.string.AIChat), R.drawable.ai_chat, subtext = getString(R.string.AIChatInfo), linkAlias = "aiChat"))
        items.add(uButton(ChatsItem.INLINE_MATH_RESULT, getString(R.string.InlineMathResult), R.drawable.msg_calculator, subtext = getString(R.string.InlineMathResultInfo), linkAlias = "inlineMathResult"))
        items.add(uButton(ChatsItem.CHAT_SETTINGS, getString(R.string.ChatSettings), R.drawable.msg_discussion, subtext = getString(R.string.ChatSettingsInfo), searchable = false, linkAlias = "chatSettings"))
        items.add(UItem.asShadow())

        items.add(UItem.asHeader(getString(R.string.StickersName)))
        items.add(uCheck(ChatsItem.UNLIMITED_RECENT_STICKERS, getString(R.string.UnlimitedRecentStickers), unlimitedRecentStickers, linkAlias = "unlimitedRecentStickers"))
        hideReactions.fill(items)
        items.add(UItem.asShadow(getString(R.string.HideReactionsInfo)))

        items.add(UItem.asHeader(getString(R.string.Gestures)))
        items.add(UItem.asCustom(ChatsItem.DOUBLE_TAP.id, doubleTapCell))
        items.add(uButton(ChatsItem.DOUBLE_TAP_ACTION, getString(R.string.DoubleTapIncoming), value = DoubleTapUtils.getDoubleTapActionLabel(doubleTapAction, false), linkAlias = "doubleTapIncoming"))
        items.add(uButton(ChatsItem.DOUBLE_TAP_ACTION_OUT_OWNER, getString(R.string.DoubleTapOutgoing), value = DoubleTapUtils.getDoubleTapActionLabel(doubleTapActionOutOwner, true), linkAlias = "doubleTapOutgoing"))
        items.add(uButton(ChatsItem.SWIPE_ACTIONS, getString(R.string.SwipeActions), value = swipeActionsValue(), linkAlias = "swipeActions"))
        items.add(UItem.asShadow(getString(R.string.DoubleTapInfo)))

        items.add(UItem.asHeader(getString(R.string.MainTabsChats)))
        items.add(uButton(ChatsItem.BOTTOM_BUTTON, getString(R.string.BottomButton), value = bottomButtonOptions[bottomButton], linkAlias = "bottomButton"))
        widePosts.fill(items)
        aiFeatures.fill(items)
        items.add(uCheck(ChatsItem.ADMIN_SHORTCUTS, getString(R.string.AdminShortcuts), quickAdminShortcuts, linkAlias = "adminShortcuts"))
        quickTransitions.fill(items)
        items.add(uCheck(ChatsItem.DISABLE_GREETING_STICKER, getString(R.string.DisableGreetingSticker), disableGreetingSticker, linkAlias = "disableGreetingSticker"))
        items.add(uCheck(ChatsItem.AUTO_RUN_WIDGETS, getString(R.string.AutoRunWidgets), autoRunWidgets, getString(R.string.AutoRunWidgetsInfo), linkAlias = "autoRunWidgets"))
        items.add(uCheck(ChatsItem.HIDE_KEYBOARD_ON_SCROLL, getString(R.string.HideKeyboardOnScroll), hideKeyboardOnScroll, linkAlias = "hideKeyboardOnScroll"))
        items.add(uCheck(ChatsItem.ADD_COMMA_AFTER_MENTION, getString(R.string.AddCommaAfterMention), addCommaAfterMention, linkAlias = "addCommaAfterMention"))
        items.add(uCheck(ChatsItem.HIDE_SEND_AS_PEER, getString(R.string.HideSendAsPeer), hideSendAsPeer, linkAlias = "hideSendAsPeer"))
        items.add(UItem.asShadow(getString(R.string.HideSendAsPeerInfo)))

        items.add(UItem.asHeader(getString(R.string.MessagesChartTitle)))
        items.add(UItem.asCustom(ChatsItem.MESSAGES_PREVIEW.id, messagesPreviewCell))
        items.add(uCheck(ChatsItem.REMOVE_MESSAGE_TAIL, getString(R.string.RemoveMessageTail), removeMessageTail, linkAlias = "removeMessageTail"))
        items.add(uCheck(ChatsItem.REPLACE_EDITED_WITH_ICON, LocaleController.formatString(R.string.ReplaceEditedWithIcon, getString(R.string.EditedMessage)), replaceEditedWithIcon, linkAlias = "replaceEditedWithIcon"))
        items.add(uCheck(ChatsItem.SHOW_ONLINE_STATUS, getString(R.string.ShowOnlineStatus), showOnlineStatus, linkAlias = "showOnlineStatus"))
        items.add(uCheck(ChatsItem.SHOW_FORWARDS_COUNT, getString(R.string.ShowForwardsCount), showForwardsCount, linkAlias = "showForwardsCount"))
        items.add(uCheck(ChatsItem.HIDE_SHARE_BUTTON, LocaleController.formatString(R.string.HideShareButton, getString(R.string.ShareFile)), hideShareButton, linkAlias = "hideShareButton"))
        items.add(uCheck(ChatsItem.SHOW_RESULTS_BEFORE_VOTING, getString(R.string.ShowPollResultsBeforeVoting), showResultsBeforeVoting, getString(R.string.ShowPollResultsBeforeVotingHint), linkAlias = "showResultsBeforeVoting"))
        messageMenu.fill(items)
        items.add(uCheck(ChatsItem.GROUP_MESSAGE_MENU, getString(R.string.GroupMessageMenu), groupMessageMenu, linkAlias = "groupMessageMenu"))
        items.add(UItem.asShadow(getString(R.string.GroupMessageMenuInfo)))

        items.add(UItem.asHeader(getString(R.string.PremiumPreviewVoiceToText)))
        items.add(uButton(ChatsItem.SPEECH_RECOGNITION_LANGUAGE, getString(R.string.RecognitionLanguage), value = TranslatorUtils.getLanguageTitleSystem(recognitionLanguage), linkAlias = "recognitionLanguage"))
        items.addIf(AiController.canUseAI() && VoiceRecognitionController.isCustomRecognitionEnabled(), uCheck(ChatsItem.POST_PROCESSING_WITH_AI, getString(R.string.PostProcessingWithAi), postprocessingWithAi, getString(R.string.PostProcessingWithAiInfo), linkAlias = "postprocessingWithAi"))
        items.addIf(downloadedRecognitionModels.isNotEmpty(), uButton(ChatsItem.DELETE_RECOGNITION_MODEL, getString(R.string.DeleteRecognitionModel), icon = R.drawable.msg_delete, red = true, linkAlias = "deleteRecognitionModel"))
        items.add(UItem.asShadow(getString(R.string.RecognitionInfo)))

        items.add(UItem.asHeader(getString(R.string.VoipCamera)))
        items.add(uButton(ChatsItem.CAMERA_TYPE, getString(R.string.CameraType), value = cameraTypeOptions[cameraType.ordinal], linkAlias = "cameraType"))
        if (cameraType != CameraType.CAMERA_1) {
            cameraSettings.fill(items)
            if (!isSeamlessSwitchingAvailable())
                MessagesController.getGlobalMainSettings().edit { putBoolean("rounddual_available", false) }
        }
        items.add(uButton(ChatsItem.VIDEO_MESSAGES_CAMERA, getString(R.string.VideoMessagesCamera), value = videoMessagesCameraOptions[videoMessagesCamera.ordinal], linkAlias = "videoMessagesCamera"))
        items.addIf(videoMessagesCamera != VideoMessagesCamera.ASK, uCheck(ChatsItem.REMEMBER_LAST_USED_CAMERA, getString(R.string.RememberLastUsedCamera), rememberLastUsedCamera, getString(R.string.RememberLastUsedCameraInfo), linkAlias = "rememberLastUsedCamera"))
        items.add(uCheck(ChatsItem.ZOOM_SLIDER, getString(R.string.ZoomSlider), zoomSlider, getString(R.string.ZoomSliderInfo), linkAlias = "zoomSlider"))
        items.add(uCheck(ChatsItem.STATIC_ZOOM, getString(R.string.StaticZoom), staticZoom, linkAlias = "staticZoom"))
        items.add(UItem.asShadow(getString(R.string.StaticZoomInfo)))

        items.add(UItem.asHeader(getString(R.string.AutoDownloadPhotos)))
        items.add(uCheck(ChatsItem.ALWAYS_SEND_IN_HD, getString(R.string.AlwaysSendInHD), alwaysSendInHD, linkAlias = "alwaysSendInHD"))
        items.add(uCheck(ChatsItem.HIDE_CAMERA_TILE, getString(R.string.HideCameraTile), hideCameraTile, linkAlias = "hideCameraTile"))
        items.add(UItem.asShadow(getString(R.string.HideCameraTileInfo)))

        items.add(UItem.asHeader(getString(R.string.AutoDownloadVideos)))
        items.add(uButton(ChatsItem.DOUBLE_TAP_SEEK_DURATION, getString(R.string.DoubleTapSeekDuration), value = doubleTapSeekDurationOptions[doubleTapSeekDuration], linkAlias = "doubleTapSeekDuration"))
        items.add(uCheck(ChatsItem.PREFER_ORIGINAL_QUALITY, getString(R.string.PreferOriginalQuality), preferOriginalQuality, linkAlias = "preferOriginalQuality"))
        items.add(uCheck(ChatsItem.SWIPE_TO_PIP, getString(R.string.SwipeToPip), swipeToPip, linkAlias = "swipeToPip"))
        items.add(uCheck(ChatsItem.UNMUTE_WITH_VOLUME_BUTTONS, getString(R.string.UnmuteWithVolumeButtons), unmuteWithVolumeButtons, getString(R.string.UnmuteWithVolumeButtonsInfo), linkAlias = "unmuteWithVolumeButtons"))
        pauseOnMinimize.fill(items)
        items.add(UItem.asShadow(getString(R.string.PauseOnMinimizeInfo)))
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        if (item.id <= 0 || item.id > ChatsItem.entries.size) return
        val clickedItem = ChatsItem.entries[item.id - 1]
        when (clickedItem) {
            ChatsItem.STICKER_TIME ->
                showListDialog(item, stickerTimeModes, getString(R.string.StickerTimeMode), stickerTimeMode.ordinal) { i ->
                    stickerTimeMode = StickerTimeMode.entries[i]
                    stickerSizeCell?.invalidate()
                    parentLayout.rebuildFragments(0)
                }
            ChatsItem.REPLY_ELEMENTS, ChatsItem.REPLY_COLORS, ChatsItem.REPLY_EMOJI, ChatsItem.REPLY_BACKGROUND -> replyElements.onClick(item)
            ChatsItem.AI -> presentFragment(AiPreferencesActivity())
            ChatsItem.INLINE_MATH_RESULT -> presentFragment(InlineResultsPreferencesActivity())
            ChatsItem.CHAT_SETTINGS -> presentFragment(ThemeActivity(ThemeActivity.THEME_TYPE_BASIC))
            ChatsItem.UNLIMITED_RECENT_STICKERS ->
                toggleBooleanSettingAndRefresh(item) { unlimitedRecentStickers = it }
            ChatsItem.HIDE_REACTIONS, ChatsItem.CHANNELS, ChatsItem.GROUPS, ChatsItem.PRIVATE_CHATS -> hideReactions.onClick(item)
            ChatsItem.DOUBLE_TAP_ACTION ->
                showListDialog(item, doubleTapActions, DoubleTapUtils.getDoubleTapIcons(false), getString(R.string.DoubleTapIncoming), doubleTapAction,
                    { i ->
                        doubleTapAction = i
                        handleDoubleTapActionButtonClick(false)
                    })
            ChatsItem.DOUBLE_TAP_ACTION_OUT_OWNER ->
                showListDialog(item, doubleTapOutActions, DoubleTapUtils.getDoubleTapIcons(true), getString(R.string.DoubleTapOutgoing), doubleTapActionOutOwner,
                    { i ->
                        doubleTapActionOutOwner = i
                        handleDoubleTapActionButtonClick(true)
                    })
            ChatsItem.SWIPE_ACTIONS -> presentFragment(SwipeActionsPreferencesActivity())
            ChatsItem.BOTTOM_BUTTON ->
                showListDialog(item, bottomButtonOptions, getString(R.string.BottomButton), bottomButton) { i ->
                    bottomButton = i
                    parentLayout.rebuildFragments(0)
                }
            ChatsItem.WIDE_POSTS, ChatsItem.WIDE_POSTS_FEED, ChatsItem.WIDE_POSTS_CHANNELS -> widePosts.onClick(item)
            ChatsItem.AI_FEATURES, ChatsItem.AI_FEATURES_EDITOR, ChatsItem.AI_FEATURES_SUMMARIES, ChatsItem.AI_FEATURES_INSTANT_VIEW_SUMMARIES -> aiFeatures.onClick(item)
            ChatsItem.ADMIN_SHORTCUTS -> toggleBooleanSettingAndRefresh(item) { quickAdminShortcuts = it }
            ChatsItem.QUICK_TRANSITIONS, ChatsItem.QUICK_TRANSITION_FOR_CHANNELS, ChatsItem.QUICK_TRANSITION_FOR_TOPICS -> quickTransitions.onClick(item)
            ChatsItem.DISABLE_GREETING_STICKER -> toggleBooleanSettingAndRefresh(item) { disableGreetingSticker = it }
            ChatsItem.AUTO_RUN_WIDGETS -> toggleBooleanSettingAndRefresh(item) { autoRunWidgets = it }
            ChatsItem.HIDE_KEYBOARD_ON_SCROLL -> toggleBooleanSettingAndRefresh(item) { hideKeyboardOnScroll = it }
            ChatsItem.ADD_COMMA_AFTER_MENTION -> toggleBooleanSettingAndRefresh(item) { addCommaAfterMention = it }
            ChatsItem.HIDE_SEND_AS_PEER -> toggleBooleanSettingAndRefresh(item) { hideSendAsPeer = it }
            ChatsItem.REPLACE_EDITED_WITH_ICON -> {
                toggleBooleanSettingAndRefresh(item) { replaceEditedWithIcon = it }
                messagesPreviewCell?.refreshMessages()
            }
            ChatsItem.SHOW_ONLINE_STATUS -> {
                toggleBooleanSettingAndRefresh(item) { showOnlineStatus = it }
                messagesPreviewCell?.refreshMessages()
            }
            ChatsItem.SHOW_FORWARDS_COUNT -> {
                toggleBooleanSettingAndRefresh(item) { showForwardsCount = it }
                messagesPreviewCell?.refreshMessages()
                parentLayout.rebuildFragments(if (AndroidUtilities.isTablet()) REBUILD_FLAG_REBUILD_LAST else 0)
            }
            ChatsItem.REMOVE_MESSAGE_TAIL -> {
                toggleBooleanSettingAndRefresh(item) { removeMessageTail = it }
                Theme.chat_msgInDrawable = null
                Theme.createChatResources(parentActivity, false)
                messagesPreviewCell?.refreshMessages()
            }
            ChatsItem.SHOW_RESULTS_BEFORE_VOTING -> {
                toggleBooleanSettingAndRefresh(item) { showResultsBeforeVoting = it }
                parentLayout.rebuildFragments(0)
            }
            ChatsItem.HIDE_SHARE_BUTTON -> {
                toggleBooleanSettingAndRefresh(item) { hideShareButton = it }
                messagesPreviewCell?.refreshMessages()
            }
            ChatsItem.MESSAGE_MENU, ChatsItem.COPY_PHOTO, ChatsItem.SAVE, ChatsItem.REPEAT, ChatsItem.CLEAR, ChatsItem.HISTORY, ChatsItem.REPORT, ChatsItem.GENERATE, ChatsItem.DETAILS -> messageMenu.onClick(item)
            ChatsItem.GROUP_MESSAGE_MENU -> toggleBooleanSettingAndRefresh(item) { groupMessageMenu = it }
            ChatsItem.SPEECH_RECOGNITION_LANGUAGE -> handleSpeechRecognitionLanguageClick(item)
            ChatsItem.POST_PROCESSING_WITH_AI -> toggleBooleanSettingAndRefresh(item) { postprocessingWithAi = it }
            ChatsItem.DELETE_RECOGNITION_MODEL -> handleDeleteRecognitionModelClick()
            ChatsItem.CAMERA_TYPE -> showListDialog(item, cameraTypeOptions, getString(R.string.CameraType), cameraType.ordinal) { i -> cameraType = CameraType.entries[i] }
            ChatsItem.CAMERA_SETTINGS, ChatsItem.DUAL_CAMERA, ChatsItem.EXTENDED_FRAMES_PER_SECOND, ChatsItem.CAMERA_STABILIZATION,
            ChatsItem.CAMERA_MIRROR_MODE, ChatsItem.START_WITH_WIDE_ANGLE_CAMERA -> cameraSettings.onClick(item)
            ChatsItem.VIDEO_MESSAGES_CAMERA -> showListDialog(item, videoMessagesCameraOptions, getString(R.string.VideoMessagesCamera), videoMessagesCamera.ordinal) { i -> videoMessagesCamera = VideoMessagesCamera.entries[i] }
            ChatsItem.REMEMBER_LAST_USED_CAMERA -> toggleBooleanSettingAndRefresh(item) { rememberLastUsedCamera = it }
            ChatsItem.ZOOM_SLIDER -> toggleBooleanSettingAndRefresh(item) { zoomSlider = it }
            ChatsItem.STATIC_ZOOM -> toggleBooleanSettingAndRefresh(item) { staticZoom = it }
            ChatsItem.ALWAYS_SEND_IN_HD -> toggleBooleanSettingAndRefresh(item) { alwaysSendInHD = it }
            ChatsItem.HIDE_CAMERA_TILE -> toggleBooleanSettingAndRefresh(item) { hideCameraTile = it }
            ChatsItem.DOUBLE_TAP_SEEK_DURATION -> showListDialog(item, doubleTapSeekDurationOptions, getString(R.string.DoubleTapSeekDuration), doubleTapSeekDuration) { doubleTapSeekDuration = it }
            ChatsItem.PREFER_ORIGINAL_QUALITY -> toggleBooleanSettingAndRefresh(item) { preferOriginalQuality = it }
            ChatsItem.SWIPE_TO_PIP -> toggleBooleanSettingAndRefresh(item) { swipeToPip = it }
            ChatsItem.UNMUTE_WITH_VOLUME_BUTTONS -> toggleBooleanSettingAndRefresh(item) { unmuteWithVolumeButtons = it }
            ChatsItem.PAUSE_ON_MINIMIZE, ChatsItem.PAUSE_ON_MINIMIZE_VIDEO, ChatsItem.PAUSE_ON_MINIMIZE_VOICE, ChatsItem.PAUSE_ON_MINIMIZE_ROUND -> pauseOnMinimize.onClick(item)
            else -> {}
        }
    }

    private fun updateReplySettings() {
        stickerSizeCell?.invalidate()
        parentLayout.rebuildFragments(0)
    }

    private fun handleDoubleTapActionButtonClick(outOwner: Boolean) = doubleTapCell?.apply {
        updateIcons(if (outOwner) 2 else 1, true)
        invalidate()
    }

    private fun isSeamlessSwitchingAvailable(): Boolean =
        cameraType == CameraType.CAMERA_X
                && CameraXSession.isSeamlessSwitchingAvailable(context)
                || cameraType == CameraType.CAMERA_2
                && DualCameraView.dualAvailableStatic(context)

    private fun handleSpeechRecognitionLanguageClick(item: UItem) {
        PopupUtils.showDialog(recognitionLanguageOptions, null, getString(R.string.RecognitionLanguage), languageCodes.indexOf(recognitionLanguage), context) { i ->
            val old = recognitionLanguage
            val newValue = languageCodes[i]
            if (old == newValue) return@showDialog

            val onDownloaded = Runnable {
                recognitionLanguage = newValue
                val view = listView.findViewByItemId(item.id)
                if (view is TextCell)
                    view.setValue(TranslatorUtils.getLanguageTitleSystem(newValue), true)
                listView.adapter.update(true)
            }

            if (newValue != "none" && downloadedRecognitionModels.none { model -> model.language == newValue }) {
                val model = VoiceRecognitionController.getInstance()
                    .listAvailableModels("vosk")
                    .firstOrNull { m -> m.language == newValue }
                    ?: return@showDialog

                RecognitionModelDialogs.showDownloadDialog(this, newValue, model, onDownloaded)
            } else {
                onDownloaded.run()
            }
        }
    }

    private val downloadedRecognitionModels: List<VoiceRecognitionController.RecognitionModel>
        get() = VoiceRecognitionController.getInstance().listDownloadedModels("vosk")

    private fun handleDeleteRecognitionModelClick() {
        RecognitionModelDialogs.showDeleteFlow(this, downloadedRecognitionModels) { model ->
            if (recognitionLanguage == model.language) recognitionLanguage = "none"
            listView.adapter.update(true)
        }
    }
}