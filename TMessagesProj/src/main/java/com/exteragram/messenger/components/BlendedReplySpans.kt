/*
 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.
*/

package com.exteragram.messenger.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.CharacterStyle
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import com.exteragram.messenger.backup.PreferencesUtils
import com.exteragram.messenger.icons.IconManager
import com.exteragram.messenger.plugins.PluginsController
import com.exteragram.messenger.utils.chats.ChatUtils
import com.exteragram.messenger.utils.text.LocaleUtils
import org.telegram.messenger.Emoji
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.MediaDataController
import org.telegram.messenger.MessageObject
import org.telegram.messenger.R
import org.telegram.tgnet.TLObject
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.ColoredImageSpan
import java.io.File
import java.util.EnumMap

private val baseHsv = FloatArray(3)
private val nameHsv = FloatArray(3)

private fun blendedReplyColor(strength: Float = 0.75f): Int {
    val base = Theme.chat_replyTextPaint.color
    val hueFrom = Theme.chat_replyNamePaint.color

    Color.colorToHSV(base, baseHsv)
    Color.colorToHSV(hueFrom, nameHsv)

    if (baseHsv[2] < 0.35f) baseHsv[2] = 0.5f

    baseHsv[0] = nameHsv[0]
    baseHsv[1] = (nameHsv[1] * strength).coerceIn(0f, 1f)

    return Color.HSVToColor(Color.alpha(base), baseHsv)
}

class BlendedReplyColorIconSpan(drawable: Drawable) : ColoredImageSpan(drawable) {
    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        setOverrideColor(blendedReplyColor())
        super.draw(canvas, text, start, end, x, top, y, bottom, paint)
    }
}

class BlendedReplyColorSpan : CharacterStyle() {
    override fun updateDrawState(tp: TextPaint) {
        tp.color = blendedReplyColor()
    }
}

class BlendedReplyFileText {

    enum class AttachType(val iconRes: Int, val stringRes: Int? = null) {
        MUSIC(R.drawable.filled_widget_music),
        GIFT(R.drawable.msg_filled_reply_gift, R.string.ActionStarGift),
        VOICE(R.drawable.msg_filled_reply_voice, R.string.AttachAudio),
        ROUND_VIDEO(R.drawable.filled_profile_video_24, R.string.AttachRound),
        PHOTO(R.drawable.msg_filled_reply_image, R.string.AttachPhoto),
        POLL(R.drawable.filled_chatlist_poll, R.string.Poll),
        TODO(R.drawable.msg_filled_reply_todo, R.string.Todo),
        VIDEO(R.drawable.filled_profile_video_24, R.string.AttachVideo),
        PLUGIN(R.drawable.plugins_filled),
        BACKUP(R.drawable.filled_profile_settings),
        ICON_PACK(R.drawable.stickers_filled),
        FILE(R.drawable.msg_filled_reply_files),
        UNKNOWN(R.drawable.msg_filled_reply_files)
    }

    private val icons = EnumMap<AttachType, BlendedReplyColorIconSpan>(AttachType::class.java)

    companion object {
        @JvmStatic
        @OptIn(UnstableApi::class)
        fun getType(message: MessageObject?): AttachType {
            val reply = message?.replyMessageObject ?: return AttachType.UNKNOWN
            val replyTo = message.messageOwner?.reply_to

            val mime = reply.document?.mime_type
            val file by lazy(LazyThreadSafetyMode.NONE) {
                if (reply.type != MessageObject.TYPE_FILE) null
                else ChatUtils.getInstance().getPathToMessage(reply)
                    ?.takeIf { it.isNotEmpty() }
                    ?.let(::File)
            }

            fun isFile() = reply.type == MessageObject.TYPE_FILE
            fun isMusic() = reply.type == MessageObject.TYPE_MUSIC
            fun isGift() = reply.isAnyGift
            fun isVoice() = reply.type == MessageObject.TYPE_VOICE
            fun isRound() = reply.type == MessageObject.TYPE_ROUND_VIDEO
            fun isPoll() = reply.isPoll && replyTo?.poll_option == null
            fun isTodo() = reply.isTodo && (replyTo == null || (replyTo.flags and TLObject.FLAG_11) == 0)
            fun isPhoto() = reply.type == MessageObject.TYPE_PHOTO || (isFile() && MimeTypes.isImage(mime))
            fun isVideo() = reply.type == MessageObject.TYPE_VIDEO || (isFile() && MimeTypes.isVideo(mime))
            fun isGif() = reply.document?.mime_type == "image/gif" || MimeTypes.isVideo(mime)
            fun isPlugin() = file?.let { PluginsController.isPlugin(it, reply) } == true
            fun isBackup() = PreferencesUtils.isBackupFile(file?.name)
            fun isIconPack() = file?.let { IconManager.isIconPack(it.path, reply) } == true

            return when {
                isMusic() -> AttachType.MUSIC
                isGift() -> AttachType.GIFT
                isVoice() -> AttachType.VOICE
                isRound() -> AttachType.ROUND_VIDEO
                isPoll() -> AttachType.POLL
                isTodo() -> AttachType.TODO
                isPhoto() -> AttachType.PHOTO
                isVideo() -> AttachType.VIDEO
                isGif() -> AttachType.UNKNOWN  // keep stock behaviour
                isPlugin() -> AttachType.PLUGIN
                isBackup() -> AttachType.BACKUP
                isIconPack() -> AttachType.ICON_PACK
                isFile() -> AttachType.FILE
                else -> AttachType.UNKNOWN
            }
        }
    }

    fun build(context: Context, reply: MessageObject, type: AttachType, fontMetrics: Paint.FontMetricsInt): CharSequence {
        val icon = icons.getOrPut(type) { createIcon(context, type.iconRes) }

        val title = entitiesTitle(reply, type)?.let { formatEntities(it.text, it.entities, fontMetrics) } ?: Emoji.replaceEmoji(
            when {
                type == AttachType.MUSIC -> "${reply.musicAuthor} – ${reply.musicTitle}"
                type.stringRes != null -> getString(type.stringRes)
                else -> reply.documentName?.takeIf { it.isNotEmpty() } ?: getString(R.string.AttachDocument)
            },
            fontMetrics, false
        )

        val text = SpannableStringBuilder("d ").append(title).apply {
            setSpan(icon, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(BlendedReplyColorSpan(), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (reply.caption.isNullOrEmpty()) return text
        return text.append(", ").append(formatEntities(reply.caption, reply.messageOwner?.entities, fontMetrics))
    }

    private fun formatEntities(
        source: CharSequence,
        entities: ArrayList<TLRPC.MessageEntity>?,
        fontMetrics: Paint.FontMetricsInt
    ): CharSequence {
        val text = Emoji.replaceEmoji(source.toString().take(150).replace('\n', ' '), fontMetrics, true)
        entities ?: return text
        val newEntities = ArrayList(entities)
        LocaleUtils.parseCustomEmojis(text, newEntities)
        val spannable = MessageObject.replaceAnimatedEmoji(text, newEntities, fontMetrics, true)
        MediaDataController.addTextStyleRuns(entities, source, spannable)
        return spannable
    }

    private fun entitiesTitle(reply: MessageObject, type: AttachType): TLRPC.TL_textWithEntities? = when (type) {
        AttachType.POLL -> (MessageObject.getMedia(reply) as? TLRPC.TL_messageMediaPoll)?.poll?.question
        AttachType.TODO -> (MessageObject.getMedia(reply) as? TLRPC.TL_messageMediaToDo)?.todo?.title
        else -> null
    }

    private fun createIcon(context: Context, res: Int) =
        BlendedReplyColorIconSpan(ContextCompat.getDrawable(context, res)!!.mutate()).apply {
            setScale(.7f, .7f)
        }
}