/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.player.mini;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.dynamicanimation.animation.SpringAnimation;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.player.CoverColorExtractor;
import com.exteragram.messenger.player.CoverColorsWait;
import com.exteragram.messenger.player.PlayerColors;
import com.exteragram.messenger.player.PlayerCovers;
import com.exteragram.messenger.player.ui.ExpressiveStateDrawable;
import com.exteragram.messenger.utils.ui.material.ColorTones;
import com.exteragram.messenger.utils.ui.material.Material3Tokens;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedLinearLayout;
import org.telegram.ui.Components.AudioPlayerAlert;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.ChatActivityTopPanelLayout;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.DialogsActivityTopPanelLayout;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PlayPauseDrawable;
import org.telegram.ui.Components.RLottieImageView;

import java.util.function.Predicate;

public class MiniPlayerUiHelper implements NotificationCenter.NotificationCenterDelegate {

    public static final int HEIGHT_DP = 48;

    public static final int PADDING_DP = 6;
    public static final int BUTTON_SIZE_DP = 36;
    private static final int COVER_SIZE_DP = 36;
    private static final float SWAP_SCALE = 0.2f;
    private static final float SWAP_FADE_OUT = 0.5f;
    private static final float SWAP_FADE_IN_DELAY = 0.15f;
    private static final int TEXT_LEFT_DP = PADDING_DP + COVER_SIZE_DP + 8;
    private static final int TEXT_RIGHT_DP = PADDING_DP + BUTTON_SIZE_DP * 3 + 8;
    private static final int TITLE_TOP_DP = 6;
    private static final int SUBTITLE_TOP_DP = 24;
    private static final int CHAT_CAPSULE_RADIUS_DP = 18;
    private static final int SKIP_ICON_DP = 16;
    private static final int PLAY_ICON_DP = 14;
    private static final int PLACEHOLDER_ICON_DP = 20;
    public static final float RING_RADIUS_DP = 15f;
    private static final float RING_STROKE_DP = 2.5f;
    private static final float RING_GAP_DP = 2f;
    private static final float RING_TRACK_ALPHA = 0.3f;
    private static final float WAVE_AMPLITUDE_DP = 1f;
    private static final float WAVE_LENGTH_DP = 11f;
    private static final float WAVE_SPEED_DP = 7f;
    private static final float WAVE_STEP_DP = 1f;
    private static final float WAVE_RAMP = 0.1f;
    private static final long WAVE_MIN_FRAME_GAP_MS = 10;
    private static final float SWIPE_CLOSE_DRAG_DP = 40f;
    private static final float SWIPE_RESISTANCE = 3f;
    private static final float SWIPE_MAX_SHRINK = 0.3f;
    private static final float SWIPE_FADE = 0.35f;
    private static final float SWIPE_FLING_DP = 500f;
    private static final float SWIPE_DIRECTION_RATIO = 1.2f;
    private static final float RESET_THRESHOLD = 0.02f;
    private static final float RESET_MIN_DROP = 0.05f;
    private static final long RESET_DURATION_MS = 400;
    private static final int LONG_TRACK_SECONDS = 10 * 60;
    private static final float SURFACE_TINT_LIGHT = 0.08f;
    private static final float SURFACE_TINT_DARK = 0.12f;
    private static final float SURFACE_RIPPLE_ALPHA = 0.16f;
    private static final long SURFACE_TINT_DURATION = 250;

    private final FrameLayout host;
    private final Theme.ResourcesProvider resourcesProvider;
    private Runnable onColorsChanged;

    private final CoverView coverView;
    private final AudioPlayerAlert.ClippingTextViewSwitcher titleView;
    private final AudioPlayerAlert.ClippingTextViewSwitcher subtitleView;
    private final RLottieImageView prevButton;
    private final ImageView playButton;
    private final PlayPauseDrawable playPauseDrawable;
    private final FrameLayout nextButton;
    private final RLottieImageView nextIcon;
    private final ImageView closeIcon;
    private final ExpressiveStateDrawable prevBackground;
    private final ExpressiveStateDrawable playBackground;
    private final ExpressiveStateDrawable nextBackground;
    private final ExpressiveStateDrawable silentBackground;
    private final ExpressiveStateDrawable speedBackground;
    private final SpringAnimation closeSpring;
    private final SpringAnimation waveSpring;
    private final SpringAnimation swipeSpring;
    private final SpringAnimation collapseSpring;
    private float swipeCollapse;
    private float collapseFrom;
    private final int touchSlop;
    private VelocityTracker swipeVelocity;
    private float swipeDownX;
    private float swipeDownY;
    private float swipeStartOffset;
    private float swipeOffset;
    private boolean swipeTracking;
    private boolean swipeDragging;
    private boolean swipeClosing;
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path wavePath = new Path();
    private final RectF ringRect = new RectF();
    private float waveFactor;
    private float wavePhase;
    private long lastWaveFrame;
    private float lastProgress;
    private float displayedProgress;
    private float previousTarget;
    private float resetFrom;
    private long resetStart;
    private boolean playing;

    private View speedButton;
    private View silentButton;
    private ImageView silentImage;
    private LegacyState speedLegacy;
    private LegacyState silentLegacy;
    private ColorFilter silentLegacyFilter;
    private View surfaceView;
    private View selectorView;
    private ValueAnimator surfaceAnimator;
    private int surfaceColor;
    private int surfaceTarget;
    private boolean coverTinted;

    private boolean active;
    private MessageObject boundMessage;
    private boolean voice;
    private boolean closeMode;
    private float closeProgress;

    private final CoverColorsWait accentWait = new CoverColorsWait(ExteraConfig::getPlayerMiniCoverColors, this::updateColors);
    private int accentColor;
    private int closeColor;
    private int titleColor;
    private int subtitleColor;
    private int placeholderColor;
    private int ringTrackColor;
    private boolean darkSurface;
    private NotificationCenter.ObserversGroup colorObservers;

    public static boolean isActive(MiniPlayerUiHelper helper) {
        return helper != null && helper.active;
    }

    public MiniPlayerUiHelper(FrameLayout host, Theme.ResourcesProvider resourcesProvider) {
        this.host = host;
        this.resourcesProvider = resourcesProvider;
        final Context context = host.getContext();
        resolveColors();

        coverView = new CoverView(context, receiver -> requestCoverColors());
        host.addView(coverView, LayoutHelper.createFrame(COVER_SIZE_DP, COVER_SIZE_DP, Gravity.TOP | Gravity.LEFT, PADDING_DP, PADDING_DP, 0, 0));

        titleView = new AudioPlayerAlert.ClippingTextViewSwitcher(context) {
            @Override
            protected TextView createTextView() {
                return createLine(context, 14, true);
            }
        };
        host.addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, Gravity.TOP | Gravity.LEFT, TEXT_LEFT_DP, TITLE_TOP_DP, TEXT_RIGHT_DP, 0));

        subtitleView = new AudioPlayerAlert.ClippingTextViewSwitcher(context) {
            @Override
            protected TextView createTextView() {
                return createLine(context, 13, false);
            }
        };
        host.addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 18, Gravity.TOP | Gravity.LEFT, TEXT_LEFT_DP, SUBTITLE_TOP_DP, TEXT_RIGHT_DP, 0));

        prevButton = new RLottieImageView(context);
        prevButton.setScaleType(ImageView.ScaleType.CENTER);
        prevButton.setAnimation(R.raw.player_prev, SKIP_ICON_DP, SKIP_ICON_DP);
        prevButton.setBackground(prevBackground = createBackground());
        prevButton.setContentDescription(getString(R.string.AccDescrPrevious));
        prevButton.setOnClickListener(v -> {
            if (MediaController.getInstance().getPlayingMessageObject() == null) {
                return;
            }
            MediaController.getInstance().playPreviousMessage();
            prevButton.setProgress(0f);
            prevButton.playAnimation();
        });
        host.addView(prevButton, createSlot(0));

        playButton = new ImageView(context);
        playButton.setScaleType(ImageView.ScaleType.CENTER);
        playButton.setImageDrawable(playPauseDrawable = new PlayPauseDrawable(PLAY_ICON_DP));
        playButton.setBackground(playBackground = createBackground());
        playButton.setOnClickListener(v -> {
            final MediaController controller = MediaController.getInstance();
            final MessageObject messageObject = controller.getPlayingMessageObject();
            if (messageObject == null) {
                return;
            }
            if (controller.isMessagePaused()) {
                controller.playMessage(messageObject);
            } else {
                controller.pauseMessage(messageObject);
            }
        });
        host.addView(playButton, createSlot(1));

        nextButton = new FrameLayout(context);
        nextButton.setBackground(nextBackground = createBackground());
        nextIcon = new RLottieImageView(context);
        nextIcon.setScaleType(ImageView.ScaleType.CENTER);
        nextIcon.setAnimation(R.raw.player_prev, SKIP_ICON_DP, SKIP_ICON_DP);
        nextIcon.setRotation(180f);
        nextButton.addView(nextIcon, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        closeIcon = new ImageView(context);
        closeIcon.setScaleType(ImageView.ScaleType.CENTER);
        closeIcon.setImageResource(R.drawable.ic_close_white);
        nextButton.addView(closeIcon, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        nextButton.setOnClickListener(v -> onNextOrCloseClick());
        host.addView(nextButton, createSlot(2));

        silentBackground = createBackground();
        speedBackground = createBackground();

        closeSpring = Material3Tokens.createSpring(Material3Tokens.FAST_SPATIAL_DAMPING, Material3Tokens.FAST_SPATIAL_STIFFNESS, Material3Tokens.MIN_VISIBLE_CHANGE, this::applyCloseProgress);
        applyCloseProgress(0f);

        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        swipeSpring = Material3Tokens.createSpring(Material3Tokens.STANDARD_SPATIAL_DAMPING, Material3Tokens.STANDARD_SPATIAL_STIFFNESS, 1f, value -> {
            swipeOffset = Math.max(0f, value);
            applySwipeShrink();
        });
        collapseSpring = Material3Tokens.createSpring(Material3Tokens.EFFECTS_DAMPING, Material3Tokens.STANDARD_SPATIAL_STIFFNESS, Material3Tokens.MIN_VISIBLE_CHANGE, value -> {
            swipeCollapse = Utilities.clamp01(value);
            host.invalidate();
        });

        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeCap(Paint.Cap.ROUND);
        ringPaint.setStrokeJoin(Paint.Join.ROUND);
        ringPaint.setStrokeWidth(dpf2(RING_STROKE_DP));
        waveSpring = Material3Tokens.createSpring(Material3Tokens.EFFECTS_DAMPING, Material3Tokens.DEFAULT_SPATIAL_STIFFNESS, Material3Tokens.MIN_VISIBLE_CHANGE, value -> {
            waveFactor = Utilities.clamp01(value);
            host.invalidate();
        });

        host.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(@NonNull View v) {
                coverView.setCoverRadius(dp(resolveCoverRadiusDp()));
                observeColors(true);
                if (active) {
                    updateColors();
                    requestCoverColors();
                }
            }

            @Override
            public void onViewDetachedFromWindow(@NonNull View v) {
                observeColors(false);
                closeSpring.cancel();
                applyCloseProgress(closeMode ? 1f : 0f);
                waveSpring.cancel();
                waveFactor = playing ? 1f : 0f;
                lastWaveFrame = 0;
            }
        });

        observeColors(host.isAttachedToWindow());
        setOwnViewsVisible(false);
        updateColors();
    }

    private void observeColors(boolean observe) {
        if (observe && colorObservers == null) {
            colorObservers = NotificationCenter.getGlobalInstance().createObserversGroup(this).add(NotificationCenter.playerColorsChanged);
        } else if (!observe && colorObservers != null) {
            colorObservers.removeAllObservers();
            colorObservers = null;
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.playerColorsChanged && active) {
            updateColors();
            requestCoverColors();
        }
    }

    private void requestCoverColors() {
        if (!active || voice || boundMessage == null || !ExteraConfig.getPlayerMiniCoverColors()) {
            return;
        }
        final MessageObject messageObject = boundMessage;
        final ImageReceiver receiver = coverView.getCurrentReceiver();
        if (receiver == null || !receiver.hasBitmapImage()) {
            return;
        }
        CoverColorExtractor.extract(messageObject, receiver, seed -> {
            if (boundMessage == messageObject) {
                updateColors();
            }
        });
    }

    private ExpressiveStateDrawable createBackground() {
        return new ExpressiveStateDrawable(RING_RADIUS_DP).setInset(BUTTON_SIZE_DP / 2f - RING_RADIUS_DP);
    }

    private static FrameLayout.LayoutParams createSlot(int slot) {
        final int right = PADDING_DP + BUTTON_SIZE_DP * (2 - slot);
        return LayoutHelper.createFrame(BUTTON_SIZE_DP, BUTTON_SIZE_DP, Gravity.TOP | Gravity.RIGHT, 0, (HEIGHT_DP - BUTTON_SIZE_DP) / 2f, right, 0);
    }

    private int resolveCapsuleRadiusDp() {
        for (ViewParent parent = host.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof DialogsActivityTopPanelLayout) {
                return ((DialogsActivityTopPanelLayout) parent).getDefaultRadiusDp();
            } else if (parent instanceof ChatActivityTopPanelLayout) {
                break;
            }
        }
        return CHAT_CAPSULE_RADIUS_DP;
    }

    private int resolveCoverRadiusDp() {
        return Math.max(0, Math.min(COVER_SIZE_DP / 2, Math.min(resolveCapsuleRadiusDp(), HEIGHT_DP / 2) - PADDING_DP));
    }

    private void updateCoverRadius(float shrink) {
        if (shrink <= 0f || voice) {
            coverView.setRadiusOverride(-1f);
            return;
        }
        final float capsule = Math.min(dp(resolveCapsuleRadiusDp()), (dp(HEIGHT_DP) - shrink) / 2f);
        coverView.setRadiusOverride(Math.max(0f, Math.min(dp(COVER_SIZE_DP) / 2f, capsule - dp(PADDING_DP))));
    }

    private TextView createLine(Context context, int textSizeDp, boolean title) {
        final TextView textView = new TextView(context);
        textView.setMaxLines(1);
        textView.setLines(1);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, textSizeDp);
        textView.setTextColor(title ? titleColor : subtitleColor);
        textView.setTypeface(title ? AndroidUtilities.bold() : AndroidUtilities.regular());
        return textView;
    }

    public void onStyleApplied(boolean audioPlayer, View legacyPlayButton, View legacyTitleView, View legacyCloseButton, View speedButton, View silentButton, ImageView silentImage, View surfaceView, View selectorView) {
        this.speedButton = speedButton;
        this.silentButton = silentButton;
        this.silentImage = silentImage;
        if (surfaceAnimator != null) {
            surfaceAnimator.cancel();
            surfaceAnimator = null;
        }
        surfaceColor = 0;
        surfaceTarget = 0;
        this.surfaceView = audioPlayer ? surfaceView : null;
        this.selectorView = audioPlayer ? selectorView : null;
        final boolean wasActive = active;
        active = audioPlayer;
        if (active) {
            if (!wasActive) {
                speedLegacy = LegacyState.capture(speedButton);
                silentLegacy = LegacyState.capture(silentButton);
                silentLegacyFilter = silentImage != null ? silentImage.getColorFilter() : null;
            }
            if (legacyPlayButton != null) {
                legacyPlayButton.setVisibility(View.GONE);
            }
            if (legacyCloseButton != null) {
                legacyCloseButton.setVisibility(View.GONE);
            }
            if (legacyTitleView != null) {
                legacyTitleView.setVisibility(View.INVISIBLE);
            }
            if (speedButton != null) {
                speedButton.setLayoutParams(createSlot(0));
                speedButton.setBackground(speedBackground);
            }
            if (silentButton != null) {
                silentButton.setLayoutParams(createSlot(0));
                silentButton.setBackground(silentBackground);
            }
            setOwnViewsVisible(true);
            updateColors();
        } else if (wasActive) {
            if (legacyTitleView != null) {
                legacyTitleView.setVisibility(View.VISIBLE);
            }
            if (speedLegacy != null) {
                speedLegacy.restore(speedButton);
                speedButton.setAlpha(1f);
                speedButton.setEnabled(true);
                if (speedButton.getVisibility() == View.INVISIBLE) {
                    speedButton.setVisibility(View.GONE);
                }
            }
            if (silentLegacy != null) {
                silentLegacy.restore(silentButton);
                silentButton.setVisibility(View.GONE);
            }
            if (silentImage != null) {
                silentImage.setColorFilter(silentLegacyFilter);
            }
            speedLegacy = silentLegacy = null;
            silentLegacyFilter = null;
            setOwnViewsVisible(false);
            boundMessage = null;
        }
    }

    private void setOwnViewsVisible(boolean visible) {
        final int visibility = visible ? View.VISIBLE : View.GONE;
        coverView.setVisibility(visibility);
        titleView.setVisibility(visibility);
        subtitleView.setVisibility(visibility);
        playButton.setVisibility(visibility);
        nextButton.setVisibility(visibility);
        if (!visible) {
            prevButton.setVisibility(View.GONE);
        }
    }

    public void bind(MessageObject messageObject, boolean animated) {
        if (!active || messageObject == null) {
            return;
        }
        final MediaController controller = MediaController.getInstance();
        if (!swipeDragging && (swipeClosing || swipeOffset != 0f)) {
            swipeSpring.cancel();
            collapseSpring.cancel();
            swipeClosing = false;
            swipeCollapse = 0f;
            swipeOffset = 0f;
            updateCoverRadius(0f);
            host.requestLayout();
            host.invalidate();
        }
        final boolean changed = boundMessage != messageObject;
        if (changed) {
            final boolean animate = animated && boundMessage != null;
            boundMessage = messageObject;
            voice = messageObject.isVoice() || messageObject.isRoundVideo();
            final String author = messageObject.getMusicAuthor();
            final String title = messageObject.getMusicTitle();
            titleView.setText(voice ? author : title, animate);
            subtitleView.setText(voice ? title : author, animate);
            coverView.setCoverRadius(dp(resolveCoverRadiusDp()));
            coverView.set(messageObject, voice, animate);
            updateColors();
            requestCoverColors();
            nextIcon.setVisibility(voice ? View.GONE : View.VISIBLE);
            nextButton.setContentDescription(getString(voice ? R.string.AccDescrClosePlayer : R.string.Next));
            if (!voice) {
                PlayerCovers.preloadNeighbors(messageObject.currentAccount);
            }
        }

        final boolean silent = controller.isSilent;
        final boolean speedSlot = !silent && (voice || messageObject.getDuration() >= LONG_TRACK_SECONDS);
        prevButton.setVisibility(!silent && !speedSlot ? View.VISIBLE : View.GONE);
        if (speedButton != null) {
            speedButton.setAlpha(speedSlot ? 1f : 0f);
            speedButton.setEnabled(speedSlot);
            speedButton.setVisibility(speedSlot ? View.VISIBLE : View.INVISIBLE);
        }
        if (silentButton != null) {
            silentButton.setVisibility(silent ? View.VISIBLE : View.GONE);
        }

        final boolean paused = controller.isMessagePaused();
        playPauseDrawable.setPause(!paused, animated);
        playButton.setContentDescription(getString(paused ? R.string.AccActionPlay : R.string.AccActionPause));
        setPlaying(!paused, animated);

        if (voice) {
            setCloseMode(true, false);
        } else {
            setCloseMode(paused, animated && !changed);
        }
    }

    private void setPlaying(boolean playing, boolean animated) {
        if (this.playing == playing && (animated || waveFactor == (playing ? 1f : 0f))) {
            return;
        }
        this.playing = playing;
        lastWaveFrame = 0;
        if (animated && host.isAttachedToWindow()) {
            Material3Tokens.animate(waveSpring, waveFactor, playing ? 1f : 0f);
        } else {
            waveSpring.cancel();
            waveFactor = playing ? 1f : 0f;
            host.invalidate();
        }
    }

    private void setCloseMode(boolean close, boolean animated) {
        if (closeMode != close) {
            closeMode = close;
            if (!voice) {
                nextButton.setContentDescription(getString(close ? R.string.AccDescrClosePlayer : R.string.Next));
            }
        }
        final float target = close ? 1f : 0f;
        if (animated) {
            Material3Tokens.animate(closeSpring, closeProgress, target);
        } else {
            closeSpring.cancel();
            applyCloseProgress(target);
        }
    }

    private void applyCloseProgress(float t) {
        closeProgress = t;
        final float nextScale = 1f - 0.4f * t;
        nextIcon.setAlpha(Utilities.clamp01(1f - t));
        nextIcon.setRotation(180f - 90f * t);
        nextIcon.setScaleX(nextScale);
        nextIcon.setScaleY(nextScale);
        final float closeScale = 0.6f + 0.4f * t;
        closeIcon.setAlpha(Utilities.clamp01(t));
        closeIcon.setRotation(90f * (1f - t));
        closeIcon.setScaleX(closeScale);
        closeIcon.setScaleY(closeScale);
        nextBackground.setColors(ColorUtils.blendARGB(accentColor, closeColor, Utilities.clamp01(t)), 0);
    }

    private void onNextOrCloseClick() {
        final MediaController controller = MediaController.getInstance();
        final MessageObject messageObject = controller.getPlayingMessageObject();
        if (messageObject == null) {
            return;
        }
        if (voice) {
            controller.cleanupPlayer(true, true);
            return;
        }
        if (closeMode && controller.isMessagePaused()) {
            controller.cleanupPlayer(true, true);
            return;
        }
        controller.playNextMessage();
        nextIcon.setProgress(0f);
        nextIcon.playAnimation();
    }

    public int getSpeedColor(float speed) {
        return Math.abs(speed - 1f) < 0.01f ? closeColor : accentColor;
    }

    private void resolveColors() {
        final int themeAccent = Theme.getColor(Theme.key_inappPlayerPlayPause, resourcesProvider);
        if (!accentWait.shouldWait(boundMessage)) {
            accentColor = PlayerColors.getMiniPlayerAccent(themeAccent, resourcesProvider);
        }
        coverTinted = ExteraConfig.getPlayerMiniCoverColors() && accentColor != themeAccent;
        final int themeSecondary = Theme.getColor(Theme.key_inappPlayerClose, resourcesProvider);
        closeColor = coverTinted ? PlayerColors.getMiniPlayerSecondary(themeSecondary, resourcesProvider) : themeSecondary;
        final int themeText = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
        titleColor = coverTinted ? PlayerColors.getMiniPlayerText(themeText, resourcesProvider) : themeText;
        subtitleColor = closeColor;
        darkSurface = !ColorTones.isLightSurface(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
        placeholderColor = ColorUtils.setAlphaComponent(accentColor, (int) (0xFF * (darkSurface ? 0.2f : 0.14f)));
        ringTrackColor = ColorUtils.setAlphaComponent(accentColor, (int) (0xFF * RING_TRACK_ALPHA));
    }

    public void setOnColorsChanged(Runnable onColorsChanged) {
        this.onColorsChanged = onColorsChanged;
    }

    public void updateColors() {
        resolveColors();
        final PorterDuffColorFilter accentFilter = new PorterDuffColorFilter(accentColor, PorterDuff.Mode.SRC_IN);
        prevButton.setColorFilter(accentFilter);
        nextIcon.setColorFilter(accentFilter);
        playPauseDrawable.setColor(accentColor);
        playButton.invalidate();
        closeIcon.setColorFilter(new PorterDuffColorFilter(closeColor, PorterDuff.Mode.SRC_IN));
        prevBackground.setColors(accentColor, 0);
        playBackground.setColors(accentColor, 0);
        silentBackground.setColors(accentColor, 0);
        speedBackground.setColors(accentColor, 0);
        applyCloseProgress(closeProgress);
        for (int i = 0; i < 2; i++) {
            final TextView title = i == 0 ? titleView.getTextView() : titleView.getNextTextView();
            final TextView subtitle = i == 0 ? subtitleView.getTextView() : subtitleView.getNextTextView();
            if (title != null) {
                title.setTextColor(titleColor);
            }
            if (subtitle != null) {
                subtitle.setTextColor(subtitleColor);
            }
        }
        if (active && silentImage != null) {
            silentImage.setColorFilter(accentFilter);
        }
        coverView.setColors(accentColor, placeholderColor);
        applySurfaceTint();
        host.invalidate();
        if (onColorsChanged != null) {
            onColorsChanged.run();
        }
    }

    private void applySurfaceTint() {
        if (surfaceView == null) {
            return;
        }
        final int target = coverTinted ? ColorUtils.setAlphaComponent(accentColor, (int) (0xFF * (darkSurface ? SURFACE_TINT_DARK : SURFACE_TINT_LIGHT))) : 0;
        if (target == surfaceTarget && (surfaceAnimator != null || surfaceColor == target)) {
            return;
        }
        surfaceTarget = target;
        if (selectorView != null) {
            selectorView.setBackground(coverTinted
                    ? Theme.createSelectorDrawable(ColorUtils.setAlphaComponent(accentColor, (int) (0xFF * SURFACE_RIPPLE_ALPHA)), Theme.RIPPLE_MASK_ALL)
                    : Theme.getSelectorDrawable(false));
        }
        if (surfaceAnimator != null) {
            surfaceAnimator.cancel();
            surfaceAnimator = null;
        }
        if (!host.isAttachedToWindow() || !active) {
            setSurfaceColor(target);
            return;
        }
        final int from = Color.alpha(surfaceColor) == 0 ? ColorUtils.setAlphaComponent(target, 0) : surfaceColor;
        final int to = Color.alpha(target) == 0 ? ColorUtils.setAlphaComponent(surfaceColor, 0) : target;
        surfaceAnimator = ValueAnimator.ofFloat(0f, 1f);
        surfaceAnimator.setDuration(SURFACE_TINT_DURATION);
        surfaceAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
        surfaceAnimator.addUpdateListener(animation -> setSurfaceColor(ColorUtils.blendARGB(from, to, (float) animation.getAnimatedValue())));
        surfaceAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (surfaceAnimator == animation) {
                    surfaceAnimator = null;
                    setSurfaceColor(surfaceTarget);
                }
            }
        });
        surfaceAnimator.start();
    }

    private void setSurfaceColor(int color) {
        surfaceColor = color;
        if (surfaceView != null) {
            surfaceView.setBackgroundColor(color);
        }
    }

    public int beginSwipeDraw(Canvas canvas) {
        final int shrink = getSwipeShrink();
        float translation = shrink;
        if (swipeClosing) {
            final float full = dp(HEIGHT_DP);
            translation = full - (full - collapseFrom) * getEntryVisibility();
            updateCoverRadius(translation);
            host.postInvalidateOnAnimation();
        }
        if (translation <= 0f) {
            return -1;
        }
        final float progress = Utilities.clamp01((swipeClosing ? collapseFrom : shrink) / (dp(HEIGHT_DP) * SWIPE_MAX_SHRINK));
        final float alpha = (1f - SWIPE_FADE * progress) * (swipeClosing ? 1f - swipeCollapse : 1f);
        final int save = canvas.saveLayerAlpha(0, 0, host.getMeasuredWidth(), host.getMeasuredHeight(), (int) (0xFF * alpha));
        canvas.translate(0, -translation);
        return save;
    }

    private float getEntryVisibility() {
        View view = host;
        ViewParent parent = host.getParent();
        while (parent instanceof View) {
            if (parent instanceof AnimatedLinearLayout) {
                return Utilities.clamp01(view.getAlpha());
            }
            view = (View) parent;
            parent = parent.getParent();
        }
        return 1f;
    }

    public boolean isSwipeClosing() {
        return swipeClosing;
    }

    public int getSwipeShrink() {
        if (swipeClosing) {
            return Math.round(collapseFrom);
        }
        if (swipeOffset <= 0f) {
            return 0;
        }
        final float max = dp(HEIGHT_DP) * SWIPE_MAX_SHRINK;
        return Math.round(max * (float) Math.tanh(swipeOffset / (SWIPE_RESISTANCE * max)));
    }

    private void applySwipeShrink() {
        updateCoverRadius(getSwipeShrink());
        setPanelLayoutAnimationSkipped(true);
        host.requestLayout();
        host.invalidate();
    }

    private void setPanelLayoutAnimationSkipped(boolean skipped) {
        for (ViewParent parent = host.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof AnimatedLinearLayout) {
                ((AnimatedLinearLayout) parent).setSkipNextLayoutAnimation(skipped);
                return;
            }
        }
    }

    public boolean onInterceptSwipe(MotionEvent event) {
        return switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                trackSwipeDown(event);
                yield false;
            }
            case MotionEvent.ACTION_MOVE -> {
                if (swipeTracking && !swipeDragging) {
                    swipeVelocity.addMovement(event);
                    updateSwipe(event);
                }
                yield swipeDragging;
            }
            case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!swipeDragging) {
                    resetSwipeTracking();
                }
                yield swipeDragging;
            }
            default -> swipeDragging;
        };
    }

    public boolean onSwipeTouchEvent(MotionEvent event, Predicate<MotionEvent> superTouch) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                trackSwipeDown(event);
                return false;
            case MotionEvent.ACTION_MOVE:
                if (!swipeTracking) {
                    return false;
                }
                swipeVelocity.addMovement(event);
                final boolean wasDragging = swipeDragging;
                updateSwipe(event);
                if (!wasDragging && swipeDragging) {
                    final MotionEvent cancel = MotionEvent.obtain(event);
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    superTouch.test(cancel);
                    cancel.recycle();
                }
                return swipeDragging;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (swipeDragging) {
                    if (swipeVelocity != null) {
                        swipeVelocity.addMovement(event);
                    }
                    finishSwipe(event.getActionMasked() == MotionEvent.ACTION_UP);
                    return true;
                }
                resetSwipeTracking();
                return false;
        }
        return swipeDragging;
    }

    private void trackSwipeDown(MotionEvent event) {
        swipeTracking = active && boundMessage != null && !swipeClosing && MediaController.getInstance().getPlayingMessageObject() != null;
        swipeDragging = false;
        if (!swipeTracking) {
            return;
        }
        swipeSpring.cancel();
        swipeDownX = event.getX();
        swipeDownY = event.getY();
        swipeStartOffset = swipeOffset;
        if (swipeVelocity == null) {
            swipeVelocity = VelocityTracker.obtain();
        } else {
            swipeVelocity.clear();
        }
        swipeVelocity.addMovement(event);
    }

    private void updateSwipe(MotionEvent event) {
        final float dx = event.getX() - swipeDownX;
        final float dy = swipeDownY - event.getY();
        if (!swipeDragging) {
            if (dy > touchSlop && dy > Math.abs(dx) * SWIPE_DIRECTION_RATIO) {
                swipeDragging = true;
                swipeDownY -= touchSlop;
                final ViewParent parent = host.getParent();
                if (parent != null) {
                    parent.requestDisallowInterceptTouchEvent(true);
                }
                host.cancelLongPress();
                host.setPressed(false);
            } else if (Math.abs(dx) > touchSlop || dy < -touchSlop) {
                resetSwipeTracking();
                return;
            } else {
                return;
            }
        }
        swipeOffset = Math.max(0f, swipeStartOffset + swipeDownY - event.getY());
        applySwipeShrink();
    }

    private void finishSwipe(boolean commit) {
        float velocity = 0f;
        if (swipeVelocity != null) {
            swipeVelocity.computeCurrentVelocity(1000);
            velocity = -swipeVelocity.getYVelocity();
        }
        final boolean close = commit && swipeOffset > 0f && (swipeOffset >= dp(SWIPE_CLOSE_DRAG_DP) || velocity > dp(SWIPE_FLING_DP));
        resetSwipeTracking();
        swipeSpring.cancel();
        if (close) {
            collapseFrom = getSwipeShrink();
            swipeCollapse = 0f;
            swipeClosing = true;
            collapseSpring.cancel();
            collapseSpring.setStartValue(0f);
            collapseSpring.getSpring().setFinalPosition(1f);
            collapseSpring.start();
            setPanelLayoutAnimationSkipped(false);
            performSwipeClose();
            return;
        }
        swipeSpring.setStartValue(swipeOffset);
        swipeSpring.setStartVelocity(0f);
        swipeSpring.getSpring().setFinalPosition(0f);
        swipeSpring.start();
    }

    private void resetSwipeTracking() {
        swipeTracking = false;
        swipeDragging = false;
        if (swipeVelocity != null) {
            swipeVelocity.recycle();
            swipeVelocity = null;
        }
    }

    private void performSwipeClose() {
        final MediaController controller = MediaController.getInstance();
        if (controller.getPlayingMessageObject() != null) {
            controller.cleanupPlayer(true, true);
        }
    }

    public void drawProgress(Canvas canvas, MessageObject playingMessageObject) {
        if (playingMessageObject != null) {
            lastProgress = playingMessageObject.audioProgress;
        }
        final long frameTime = AnimationUtils.currentAnimationTimeMillis();
        final float target = Utilities.clamp01(lastProgress);
        if (target <= RESET_THRESHOLD && previousTarget - target >= RESET_MIN_DROP) {
            resetFrom = displayedProgress;
            resetStart = frameTime;
        }
        previousTarget = target;
        float resetValue = 0f;
        if (resetStart != 0) {
            final float t = (frameTime - resetStart) / (float) RESET_DURATION_MS;
            if (t >= 1f) {
                resetStart = 0;
            } else {
                final float inverse = 1f - Math.max(0f, t);
                resetValue = resetFrom * inverse * inverse * inverse;
            }
        }
        displayedProgress = Math.max(target, resetValue);
        if (resetStart != 0 && host.isShown()) {
            host.postInvalidateOnAnimation();
        }
        if (playButton.getVisibility() != View.VISIBLE || playButton.getWidth() == 0) {
            return;
        }
        final float cx = playButton.getX() + playButton.getWidth() / 2f;
        final float cy = playButton.getY() + playButton.getHeight() / 2f;
        final float radius = dpf2(RING_RADIUS_DP);
        final float stroke = dpf2(RING_STROKE_DP);
        final float p = displayedProgress;
        final float circumference = (float) (2 * Math.PI * radius);
        ringRect.set(cx - radius, cy - radius, cx + radius, cy + radius);
        if (p < 1f) {
            final float gap = p > 0f ? (dpf2(RING_GAP_DP) + stroke) / circumference * 360f : 0f;
            final float sweep = 360f * (1f - p) - gap * 2f;
            if (sweep > 0f) {
                ringPaint.setColor(ringTrackColor);
                canvas.drawArc(ringRect, -90f + 360f * p + gap, sweep, false, ringPaint);
            }
        }
        if (p <= 0f) {
            lastWaveFrame = 0;
            return;
        }
        final long now = AnimationUtils.currentAnimationTimeMillis();
        final boolean moving = playing && waveFactor > 0f;
        if (moving && lastWaveFrame != 0) {
            wavePhase = (wavePhase + (now - lastWaveFrame) / 1000f * WAVE_SPEED_DP / WAVE_LENGTH_DP * 2f * (float) Math.PI) % (2f * (float) Math.PI * 1000f);
        }
        lastWaveFrame = moving ? now : 0;
        final float ramp = p < WAVE_RAMP ? p / WAVE_RAMP : p > 1f - WAVE_RAMP ? (1f - p) / WAVE_RAMP : 1f;
        final float amplitude = dpf2(WAVE_AMPLITUDE_DP) * waveFactor * Utilities.clamp01(ramp);
        ringPaint.setColor(accentColor);
        if (amplitude < 0.25f) {
            canvas.drawArc(ringRect, -90f, 360f * p, false, ringPaint);
        } else {
            final float length = circumference * p;
            final float wavelength = dpf2(WAVE_LENGTH_DP);
            final float step = dpf2(WAVE_STEP_DP);
            final int count = Math.max(2, (int) Math.ceil(length / step));
            wavePath.rewind();
            for (int i = 0; i <= count; i++) {
                final float arc = length * i / count;
                final double angle = -Math.PI / 2 + arc / radius;
                final float r = radius + amplitude * (float) Math.sin(arc / wavelength * 2 * Math.PI + wavePhase);
                final float x = cx + r * (float) Math.cos(angle);
                final float y = cy + r * (float) Math.sin(angle);
                if (i == 0) {
                    wavePath.moveTo(x, y);
                } else {
                    wavePath.lineTo(x, y);
                }
            }
            canvas.drawPath(wavePath, ringPaint);
        }
        if (moving && host.isShown()) {
            host.postInvalidateDelayed(Math.max(0, now + WAVE_MIN_FRAME_GAP_MS - SystemClock.uptimeMillis()));
        }
    }

    private record LegacyState(ViewGroup.LayoutParams params, Drawable background) {

        static LegacyState capture(View view) {
            return view != null ? new LegacyState(view.getLayoutParams(), view.getBackground()) : null;
        }

        void restore(View view) {
            view.setLayoutParams(params);
            view.setBackground(background);
        }
    }

    private static class CoverView extends View {

        private final ImageReceiver[] receivers = new ImageReceiver[2];
        private final AvatarDrawable[] avatarDrawables = new AvatarDrawable[2];
        private final boolean[] placeholders = new boolean[2];
        private final boolean[] voices = new boolean[2];
        private int coverRadius = dp(12);
        private final Paint placeholderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Drawable placeholderIcon;
        private final SpringAnimation swapSpring;
        private final Utilities.Callback<ImageReceiver> onImageSet;

        private int current;
        private float swap = 1f;
        private float radiusOverride = -1f;
        private int containerColor;

        CoverView(Context context, Utilities.Callback<ImageReceiver> onImageSet) {
            super(context);
            this.onImageSet = onImageSet;
            for (int i = 0; i < 2; i++) {
                final int index = i;
                receivers[i] = new ImageReceiver(this);
                receivers[i].setRoundRadius(coverRadius);
                receivers[i].setDelegate((imageReceiver, set, thumb, memCache) -> {
                    if (set && index == current && this.onImageSet != null) {
                        this.onImageSet.run(imageReceiver);
                    }
                });
                avatarDrawables[i] = new AvatarDrawable();
            }
            final Drawable icon = ContextCompat.getDrawable(context, R.drawable.files_music);
            placeholderIcon = icon != null ? icon.mutate() : null;
            swapSpring = Material3Tokens.createSpring(Material3Tokens.DEFAULT_SPATIAL_DAMPING, Material3Tokens.DEFAULT_SPATIAL_STIFFNESS, Material3Tokens.MIN_VISIBLE_CHANGE, value -> {
                swap = value;
                invalidate();
            });
            swapSpring.addEndListener((animation, canceled, value, velocity) -> {
                if (!canceled) {
                    receivers[1 - current].clearImage();
                    placeholders[1 - current] = false;
                }
            });
        }

        void setCoverRadius(int radius) {
            if (coverRadius == radius) {
                return;
            }
            coverRadius = radius;
            for (int i = 0; i < 2; i++) {
                receivers[i].setRoundRadius(radiusOverride >= 0f ? (int) radiusOverride : radius);
            }
            invalidate();
        }

        void setRadiusOverride(float radius) {
            if (radiusOverride == radius) {
                return;
            }
            radiusOverride = radius;
            for (int i = 0; i < 2; i++) {
                receivers[i].setRoundRadius(radius >= 0f ? (int) radius : coverRadius);
            }
            invalidate();
        }

        ImageReceiver getCurrentReceiver() {
            return placeholders[current] ? null : receivers[current];
        }

        void setColors(int iconColor, int containerColor) {
            this.containerColor = containerColor;
            if (placeholderIcon != null) {
                placeholderIcon.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN));
            }
            invalidate();
        }

        void set(MessageObject messageObject, boolean voice, boolean animated) {
            if (animated) {
                current = 1 - current;
            }
            final ImageReceiver receiver = receivers[current];
            voices[current] = voice;
            receiver.setRoundRadius(radiusOverride >= 0f ? (int) radiusOverride : coverRadius);
            placeholders[current] = false;
            if (voice) {
                final TLObject peer = PlayerCovers.getVoicePeer(messageObject);
                avatarDrawables[current].setInfo(messageObject.currentAccount, peer);
                receiver.setForUserOrChat(peer, avatarDrawables[current]);
            } else if (!PlayerCovers.setCover(receiver, messageObject, COVER_SIZE_DP, false)) {
                placeholders[current] = true;
            }
            if (animated) {
                swap = 0f;
                Material3Tokens.animate(swapSpring, 0f, 1f);
            } else {
                swapSpring.cancel();
                swap = 1f;
                receivers[1 - current].clearImage();
                placeholders[1 - current] = false;
            }
            invalidate();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            for (ImageReceiver receiver : receivers) {
                receiver.onAttachedToWindow();
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            swapSpring.cancel();
            swap = 1f;
            for (ImageReceiver receiver : receivers) {
                receiver.onDetachedFromWindow();
            }
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            if (swap >= 1f) {
                drawLayer(canvas, current, 1f, 1f);
                return;
            }
            final float t = Utilities.clamp01(swap);
            drawLayer(canvas, 1 - current, 1f - SWAP_SCALE * t, 1f - Utilities.clamp01(t / SWAP_FADE_OUT));
            drawLayer(canvas, current, Math.min(1f, 1f - SWAP_SCALE + SWAP_SCALE * swap), Utilities.clamp01((t - SWAP_FADE_IN_DELAY) / (1f - SWAP_FADE_IN_DELAY)));
        }

        private void drawLayer(Canvas canvas, int index, float scale, float alpha) {
            if (alpha <= 0f || scale <= 0f) {
                return;
            }
            final float w = getWidth();
            final float h = getHeight();
            final float radius = radiusOverride >= 0f ? radiusOverride : coverRadius;
            canvas.save();
            canvas.scale(scale, scale, w / 2f, h / 2f);
            placeholderPaint.setColor(containerColor);
            placeholderPaint.setAlpha((int) (Color.alpha(containerColor) * alpha));
            canvas.drawRoundRect(0, 0, w, h, radius, radius, placeholderPaint);
            final ImageReceiver receiver = receivers[index];
            if (placeholderIcon != null && !voices[index] && (placeholders[index] || !receiver.hasBitmapImage() || receiver.getCurrentAlpha() < 1f)) {
                final int size = dp(PLACEHOLDER_ICON_DP);
                final int l = (int) ((w - size) / 2f);
                final int top = (int) ((h - size) / 2f);
                placeholderIcon.setBounds(l, top, l + size, top + size);
                placeholderIcon.setAlpha((int) (0xFF * alpha));
                placeholderIcon.draw(canvas);
            }
            if (!placeholders[index]) {
                receiver.setImageCoords(0, 0, w, h);
                receiver.setAlpha(alpha);
                receiver.draw(canvas);
            }
            canvas.restore();
        }
    }
}
