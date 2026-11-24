/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.nowplaying.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import androidx.core.view.isVisible
import com.exteragram.messenger.badges.BadgesController
import com.exteragram.messenger.components.SupporterBottomSheet
import com.exteragram.messenger.getSectionRadiusDp
import com.exteragram.messenger.nowplaying.ServiceEmoji
import com.exteragram.messenger.utils.ui.UIUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.datasource.DefaultDataSource
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.AndroidUtilities.dp
import org.telegram.messenger.AndroidUtilities.dpf2
import org.telegram.messenger.Emoji
import org.telegram.messenger.LocaleController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.browser.Browser
import org.telegram.messenger.utils.ViewOutlineProviderImpl
import org.telegram.ui.ActionBar.SimpleTextView
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.AnimatedEmojiDrawable
import org.telegram.ui.Components.AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.PlayPauseDrawable
import org.telegram.ui.Components.ScaleStateListAnimator
import org.telegram.ui.LaunchActivity

@SuppressLint("ViewConstructor")
@androidx.annotation.OptIn(UnstableApi::class)
open class NowPlayingCard(
    context: Context, private val resourcesProvider: Theme.ResourcesProvider?
) : FrameLayout(context) {
    private val imageView: BackupImageView
    private val nameView: SimpleTextView
    private val artistView: SimpleTextView
    private val albumView: SimpleTextView
    private val cardLayout: FrameLayout

    private lateinit var nowPlayingCardData: NowPlayingCardData

    private val playPauseButton: ImageView
    private var playPauseDrawable: PlayPauseDrawable
    private var isPlaying = false
    private var player: ExoPlayer? = null

    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var resumeOnFocusGain = false
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnFocusGain = false
                player?.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (player?.isPlaying == true) {
                    resumeOnFocusGain = true
                    player?.pause()
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (resumeOnFocusGain) {
                    player?.play()
                    resumeOnFocusGain = false
                }
            }
        }
    }

    private val emoji: SwapAnimatedEmojiDrawable
    private var currentDocId: Long = -1L

    init {
        isClickable = false
        setWillNotDraw(false)

        cardLayout = object : FrameLayout(context) {
            override fun dispatchDraw(canvas: Canvas) {
                if (::nowPlayingCardData.isInitialized) {
                    emoji.color = if (nowPlayingCardData.accentColor == null) getThemedColor(
                        Theme.key_windowBackgroundWhiteBlackText
                    ) else nowPlayingCardData.accentColor

                    UIUtil.drawNowPlayingPattern(
                        canvas, emoji, width.toFloat(), height.toFloat(), if (nowPlayingCardData.coverBitmap == null) 0.4f else 1f
                    )
                }
                super.dispatchDraw(canvas)
            }
        }.apply {
            val drawable = object : GradientDrawable() {
                override fun onBoundsChange(r: Rect) {
                    super.onBoundsChange(r)
                    gradientRadius = r.width() * 2f
                }
            }.apply {
                cornerRadius = dpf2(getSectionRadiusDp().toFloat())
            }
            background = drawable
            clipToOutline = true
            outlineProvider = ViewOutlineProviderImpl.fromDrawable(drawable)
            isClickable = true
            ScaleStateListAnimator.apply(this, .035f, 1.5f)
        }
        addView(
            cardLayout,
            LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat())
        )

        emoji = SwapAnimatedEmojiDrawable(
            cardLayout, false, dp(20f), AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_STATIC
        )

        val contentLayout = LinearLayout(context)
        contentLayout.orientation = LinearLayout.HORIZONTAL
        cardLayout.addView(
            contentLayout, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.FILL,
                CARD_CONTENT_PADDING_DP,
                CARD_CONTENT_PADDING_DP,
                CARD_CONTENT_PADDING_DP,
                CARD_CONTENT_PADDING_DP
            )
        )

        imageView = BackupImageView(context).apply {
            clipToOutline = true
            outlineProvider = ViewOutlineProviderImpl.boundsWithRoundRect(getCoverCornerRadius())
        }
        contentLayout.addView(
            imageView, LayoutHelper.createLinear(64, 64, Gravity.LEFT or Gravity.CENTER_VERTICAL, 0, 0, 12, 0)
        )

        val textLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        contentLayout.addView(
            textLayout, LayoutHelper.createLinear(
                0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL
            )
        )

        nameView = SimpleTextView(context).apply {
            setGravity(Gravity.LEFT or Gravity.CENTER_VERTICAL)
            textColor = Color.WHITE
            setTextSize(16)
            setTypeface(AndroidUtilities.bold())
            NotificationCenter.listenEmojiLoading(this)
        }
        textLayout.addView(
            nameView,
            LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 22)
        )

        artistView = SimpleTextView(context).apply {
            setGravity(Gravity.LEFT or Gravity.CENTER_VERTICAL)
            setTypeface(AndroidUtilities.regular())
            setTextSize(14)
            textColor = Color.WHITE
            alpha = 0.6f
            NotificationCenter.listenEmojiLoading(this)
        }
        textLayout.addView(
            artistView, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, 19, 0f, 2f, 0f, 0f
            )
        )

        albumView = SimpleTextView(context).apply {
            setGravity(Gravity.LEFT or Gravity.CENTER_VERTICAL)
            setTypeface(AndroidUtilities.regular())
            setTextSize(14)
            textColor = Color.WHITE
            alpha = 0.6f
            NotificationCenter.listenEmojiLoading(this)
        }
        textLayout.addView(
            albumView, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, 19, 0f, 2f, 0f, 0f
            )
        )

        playPauseDrawable = PlayPauseDrawable(16).apply {
            setPause(false)
            setColor(Color.WHITE)
        }
        playPauseButton = ImageView(context).apply {
            ScaleStateListAnimator.apply(this)
            scaleType = ImageView.ScaleType.CENTER
            setImageDrawable(playPauseDrawable)
            setOnClickListener {
                togglePlayPause()
            }
        }
        contentLayout.addView(
            playPauseButton, LayoutHelper.createLinear(
                32, 32, Gravity.CENTER_VERTICAL, 8, 0, 8, 0
            )
        )
    }

    private var currentPreviewUrl: String? = null

    private fun getCoverCornerRadius(): Float {
        return dpf2((getSectionRadiusDp() - CARD_CONTENT_PADDING_DP).coerceAtLeast(8).toFloat())
    }

    fun set(cardData: NowPlayingCardData) {
        nowPlayingCardData = cardData
        val nowPlayingDTO = cardData.nowPlayingDTO
        val artists: List<String>? = nowPlayingDTO.artists

        artistView.text = null
        nameView.text = null
        albumView.text = null

        if (artists.isNullOrEmpty()) {
            artistView.text = LocaleController.getString(R.string.AudioUnknownArtist)
        } else {
            artistView.text = artists.joinToString(", ")
        }
        nameView.text =
            Emoji.replaceEmoji(nowPlayingDTO.trackName, nameView.paint.fontMetricsInt, false)

        albumView.isVisible =
            !nowPlayingDTO.albumName.isNullOrEmpty() && nowPlayingDTO.trackName != nowPlayingDTO.albumName
        if (albumView.isVisible) {
            albumView.text =
                Emoji.replaceEmoji(nowPlayingDTO.albumName, albumView.paint.fontMetricsInt, false)
        }

        setPadding(dp(12f), 0, dp(12f), 0)

        val background = cardLayout.background
        if (background is GradientDrawable) {
            background.apply {
                mutate()
                setDither(true)

                gradientType = GradientDrawable.RADIAL_GRADIENT
                setGradientCenter(1.0f, 0.5f)

                val backgroundColor = cardData.backgroundColor ?: getThemedColor(Theme.key_windowBackgroundWhite)
                colors = IntArray(2).apply {
                    this[0] = backgroundColor
                    this[1] = if (cardData.backgroundColor != null) UIUtil.adjustHsl(
                        backgroundColor,
                        1.5f
                    ) else backgroundColor
                }
            }
        }

        playPauseButton.isVisible = !nowPlayingDTO.previewUrl.isNullOrEmpty() && nowPlayingDTO.platform != "TELEGRAM"
        playPauseButton.background = Theme.createCircleDrawable(
            dp(32f), nowPlayingCardData.accentColor ?: getThemedColor(Theme.key_featuredStickers_addButton)
        )

        val targetDocId =
            if (cardData.userEmoji > 0 && nowPlayingDTO.platform == "TELEGRAM") cardData.userEmoji else ServiceEmoji.fromString(
                nowPlayingDTO.platform
            ).documentId
        if (targetDocId != currentDocId) {
            currentDocId = targetDocId
            emoji.set(currentDocId, true)
        }

        cardLayout.apply {
            val hasBadge = BadgesController.hasBadge()
            setOnClickListener {
                if (nowPlayingDTO.platform == "TELEGRAM") {
                    onSavedMusicClick()
                } else {
                    val fragment = LaunchActivity.getSafeLastFragment()
                    if (hasBadge) {
                        Browser.openUrl(context, cardData.nowPlayingDTO.songUrl)
                    } else if (fragment != null) {
                        SupporterBottomSheet.showAlert(fragment)
                    }
                }
            }
            setOnLongClickListener {
                if (nowPlayingDTO.platform == "TELEGRAM") {
                    onSavedMusicClick()
                } else {
                    Browser.openUrl(context, cardData.nowPlayingDTO.songUrl)
                }
                true
            }
        }

        if (cardData.imageLocation != null) {
            val thumbDrawable = cardData.coverBitmap?.toDrawable(context.resources)
            imageView.setImage(cardData.imageLocation, null, thumbDrawable, 0, null)

            if (cardData.coverBitmap != null) {
                Color.WHITE.also {
                    artistView.textColor = it
                    nameView.textColor = it
                    albumView.textColor = it
                }
            } else {
                getThemedColor(Theme.key_windowBackgroundWhiteBlackText).also {
                    artistView.textColor = it
                    nameView.textColor = it
                    albumView.textColor = it
                }
            }
        } else {
            imageView.setImageResource(R.drawable.nocover, getThemedColor(Theme.key_player_button))
            getThemedColor(Theme.key_windowBackgroundWhiteBlackText).also {
                artistView.textColor = it
                nameView.textColor = it
                albumView.textColor = it
            }
        }

        val newPreviewUrl = cardData.nowPlayingDTO.previewUrl
        if (newPreviewUrl != currentPreviewUrl) {
            currentPreviewUrl = newPreviewUrl
            initializePlayer()
        }

        invalidate()
    }

    private fun initializePlayer() {
        releasePlayer()
        val previewUrl = nowPlayingCardData.nowPlayingDTO.previewUrl ?: return

        player = ExoPlayer.Builder(context).build().apply {
            val dataSourceFactory = DefaultDataSource.Factory(context)
            val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(previewUrl.toUri()))
            setMediaSource(mediaSource)
            prepare()
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    this@NowPlayingCard.isPlaying = isPlaying
                    updatePlayPauseButton()
                    if (!isPlaying) {
                        abandonAudioFocus()
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        abandonAudioFocus()
                    }
                }
            })
        }
    }

    private fun togglePlayPause() {
        val p = player ?: return

        if (p.isPlaying) {
            p.pause()
            abandonAudioFocus()
        } else {
            if (requestAudioFocus()) {
                if (p.playbackState == Player.STATE_ENDED) {
                    p.seekTo(0)
                }
                p.play()
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()

            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(audioAttributes)
                .setOnAudioFocusChangeListener(audioFocusChangeListener).build()

            val result = audioManager.requestAudioFocus(audioFocusRequest!!)
            return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION") val result = audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
            return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let {
                audioManager.abandonAudioFocusRequest(it)
                audioFocusRequest = null
            }
        } else {
            @Suppress("DEPRECATION") audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    private fun updatePlayPauseButton() {
        playPauseDrawable.setPause(isPlaying)
    }

    private fun releasePlayer() {
        player?.release()
        player = null
        isPlaying = false
        updatePlayPauseButton()
        abandonAudioFocus()
    }

    private fun getThemedColor(key: Int) = Theme.getColor(key, resourcesProvider)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        emoji.attach()
        if (player == null && ::nowPlayingCardData.isInitialized) {
            initializePlayer()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        emoji.detach()
        releasePlayer()
    }

    protected open fun onSavedMusicClick() {
    }

    private companion object {
        const val CARD_CONTENT_PADDING_DP = 12
    }
}