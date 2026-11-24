/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.player;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.media3.common.C;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.player.lyrics.Lyrics;
import com.exteragram.messenger.player.lyrics.LyricsRepository;
import com.exteragram.messenger.player.motion.ArtworkRepository;
import com.exteragram.messenger.player.motion.MotionCoverRepository;
import com.exteragram.messenger.player.motion.CanvasRepository;
import com.exteragram.messenger.player.rhythm.RhythmTap;
import com.exteragram.messenger.player.ui.ExpressiveStateDrawable;
import com.exteragram.messenger.player.ui.LyricsView;
import com.exteragram.messenger.player.ui.PlayerControlsView;
import com.exteragram.messenger.player.ui.PlayerCoverView;
import com.exteragram.messenger.player.ui.PlayerDimens;
import com.exteragram.messenger.player.ui.PlayerQueueCell;
import com.exteragram.messenger.player.ui.PlayerQueueView;
import com.exteragram.messenger.player.ui.PlayerSettingsSheet;
import com.exteragram.messenger.player.ui.RhythmBackground;
import com.exteragram.messenger.player.ui.CanvasBackground;
import com.exteragram.messenger.player.ui.SpeedTextDrawable;
import com.exteragram.messenger.player.ui.WavySeekBar;
import com.exteragram.messenger.utils.ui.material.ColorTones;
import com.exteragram.messenger.utils.ui.material.Material3Tokens;
import com.google.android.gms.cast.framework.CastContext;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.chromecast.ChromecastController;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenuSlider;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.CastSync;
import org.telegram.ui.ChooseQualityLayout;
import org.telegram.ui.Components.AudioPlayerAlert;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CastMediaRouteButton;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SeekSpeedDrawable;
import org.telegram.ui.Components.SpeedIconDrawable;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.Stories.recorder.HintView2;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;

public class MusicPlayerSheet extends BottomSheet implements NotificationCenter.NotificationCenterDelegate, DownloadController.FileDownloadProgressListener, PlayerActions.Host {

    public static MusicPlayerSheet instance;

    private static final int MODE_COVER = 0;
    private static final int MODE_LYRICS = 1;
    private static final int MODE_QUEUE = 2;

    private static final int SIDE_DP = PlayerDimens.SIDE_DP;
    private static final int ICON_BUTTON_DP = PlayerDimens.ICON_BUTTON_DP;
    private static final int ICON_GAP_DP = PlayerDimens.ICON_GAP_DP;
    private static final int HEADER_HEIGHT_DP = 56;
    private static final int TABLET_TOP_GAP_DP = 8;
    private static final int TABLET_SHEET_MIN_HEIGHT_DP = 600;
    private static final int TABLET_HEADER_TOP_DP = 8;
    private static final int CORNER_RADIUS_DP = 28;
    private static final int COVER_GAP_DP = 16;
    private static final int COVER_RADIUS_DP = 28;
    private static final int COMPACT_GAP_DP = 8;
    private static final int COMPACT_COVER_RADIUS_DP = 14;
    private static final int COMPACT_TEXT_GAP_DP = 16;
    private static final int STAGE_BOTTOM_GAP_DP = 16;
    private static final int TITLE_GAP_DP = 24;
    private static final int TITLE_HEIGHT_DP = 28;
    private static final int ARTIST_TOP_DP = 30;
    private static final int ARTIST_HEIGHT_DP = 20;
    private static final int ARTIST_PADDING_X_DP = 6;
    private static final int ARTIST_PADDING_Y_DP = 4;
    private static final int ARTIST_RADIUS_DP = 8;
    private static final int TITLE_BLOCK_DP = ARTIST_TOP_DP + ARTIST_HEIGHT_DP;
    private static final int SEEK_GAP_DP = TITLE_GAP_DP;
    private static final int SEEK_HANDLE_HALF_DP = 9;
    private static final int SEEK_VIEW_HEIGHT_DP = 68;
    private static final int SEEK_LINE_BOTTOM_DP = 16;
    private static final int SEEK_SPEED_HEIGHT_DP = 48;
    private static final int TIMES_GAP_DP = 14;
    private static final int TIMES_HEIGHT_DP = 18;
    private static final int TRANSPORT_GAP_DP = 12;
    private static final int TOOLBAR_GAP_DP = 8;
    private static final int TRANSPORT_GAP_EXTRA_DP = 12;
    private static final int TOOLBAR_GAP_EXTRA_DP = 32;
    private static final int TOOLBAR_HEIGHT_DP = 56;
    private static final int MODE_SEGMENT_WIDTH_DP = 64;
    private static final int MODE_SEGMENT_HEIGHT_DP = 48;
    private static final int MODE_SEGMENT_GAP_DP = 2;
    private static final int MODE_INNER_RADIUS_DP = 8;
    private static final int MODE_PRESS_GROWTH_DP = 8;
    private static final int ACTION_BUTTON_DP = 48;
    private static final float TOGGLE_DOT_DP = 15f;
    private static final int SPEED_BUTTON_WIDTH_DP = 64;
    private static final int SPEED_OUTLINE_HEIGHT_DP = 36;
    private static final float SPEED_OUTLINE_DP = 1.5f;
    private static final int SPEED_HINT_GAP_DP = 4;
    private static final int BOTTOM_PADDING_DP = 8;
    private static final float TOOLBAR_LIFT_FRACTION = 0.25f;
    private static final int MAX_TOOLBAR_LIFT_DP = 12;
    private static final float LANDSCAPE_STAGE_FRACTION = 0.5f;
    private static final int SEEK_TOUCH_HALF_DP = 24;
    private static final int SEEK_PLATE_SHIFT_DP = 15;
    private static final int SEEK_PLATE_GAP_DP = 8;
    private static final int SEEK_PLATE_BOTTOM_DP = 37;
    private static final int MENU_DIM_ALPHA = 0x66;
    private static final int MENU_CARD_RADIUS_DP = 12;
    private static final int DETAIL_MAX_WIDTH_DP = 220;
    private static final long COLOR_DURATION_MS = 450;
    private static final float BUFFER_STIFFNESS = 750f;
    private static final long REWIND_STEP_MS = 2000;
    private static final float REWIND_RESYNC_SPEED = 6f;
    private static final float[] SPEEDS = {0.5f, 1f, 1.2f, 1.5f, 1.7f, 2f};
    private static final float[] TOGGLE_SPEEDS = {1f, 1.5f, 2f};
    private static final int[] SPEED_ICONS = {R.drawable.msg_speed_slow, R.drawable.msg_speed_normal, R.drawable.msg_speed_medium, R.drawable.msg_speed_fast, R.drawable.msg_speed_veryfast, R.drawable.msg_speed_superfast};
    private static final int[] SPEED_NAMES = {R.string.SpeedSlow, R.string.SpeedNormal, R.string.SpeedMedium, R.string.SpeedFast, R.string.SpeedVeryFast, R.string.SpeedSuperFast};

    private final LaunchActivity parentActivity;
    private final int currentAccount;
    private final Theme.ResourcesProvider baseResourcesProvider;
    private final PlayerActions actions;
    private final int TAG;

    private PlayerColors themeColors;
    private final PlayerColors colors;
    private PlayerColors targetColors;
    private ValueAnimator colorAnimator;
    private boolean speedMenuShown;
    private PlayerSettingsSheet settingsSheet;
    private final CoverColorsWait coverColorsWait = new CoverColorsWait(ExteraConfig::getPlayerCoverColors, () -> updateCoverColors(true));
    private final RhythmBackground rhythmBackground = new RhythmBackground();
    private final CanvasBackground canvasBackground;

    private final PlayerLayout playerLayout;
    private final ImageView collapseButton;
    private final ImageView moreButton;
    private final ChooseQualityLayout.QualityIcon moreIcon;
    private final ExpressiveStateDrawable collapseBackground;
    private final ExpressiveStateDrawable moreBackground;
    private final TextView headerTitle;
    private final TextView headerSubtitle;
    private final PlayerCoverView coverView;
    private final AudioPlayerAlert.ClippingTextViewSwitcher titleView;
    private final AudioPlayerAlert.ClippingTextViewSwitcher artistView;
    private final ImageView profileButton;
    private final ExpressiveStateDrawable profileBackground;
    private final WavySeekBar seekBar;
    private final TextView timeView;
    private final TextView durationView;
    private final ImageView shuffleButton;
    private final ImageView repeatButton;
    private final ExpressiveStateDrawable shuffleBackground;
    private final ExpressiveStateDrawable repeatBackground;
    private final PlayerControlsView controlsView;
    private final ImageView speedButton;
    private final SpeedTextDrawable speedIcon;
    private final ExpressiveStateDrawable speedBackground;
    private final ImageView lyricsButton;
    private final ExpressiveStateDrawable lyricsBackground;
    private final ImageView queueButton;
    private final ExpressiveStateDrawable queueBackground;
    private final LyricsView lyricsView;
    private final PlayerQueueView queueView;
    private final SeekSpeedDrawable seekSpeedDrawable;
    private final SpringAnimation seekPlateSpring;
    private float seekPlate;
    private CastMediaRouteButton castButton;
    private boolean castAvailable;

    private final SpringAnimation modeSpring;
    private final SpringAnimation lyricsSpring;
    private final SpringAnimation queueSpring;
    private final SpringAnimation bufferSpring;
    private final SpringAnimation canvasSpring;
    private final SpringAnimation canvasTitleSpring;
    private final SpringAnimation immersiveSpring;
    private final LruCache<String, String> canvasPaths = new LruCache<>(64);
    private final String[] canvasKeys = new String[3];
    private final String[] canvasRolePaths = new String[3];
    private final MessageObject[] canvasMessages = new MessageObject[3];
    private float modeProgress;
    private float canvasProgress;
    private float canvasTitleProgress;
    private float immersiveProgress;
    private boolean immersive;
    private boolean canvasShown;
    private float lyricsAlpha;
    private float queueAlpha;

    private int mode = MODE_COVER;
    private MessageObject lastMessageObject;
    private MessagesController.SavedMusicList savedMusicList;
    private long musicCountDialogId;
    private int musicCount;
    private boolean noforwards;
    private boolean tabletSheet;
    private boolean showRemaining;
    private int lastTime = -1;
    private int lastDuration;
    private String currentFile;
    private boolean currentAudioFinishedLoading;
    private long lastBufferedPositionCheck;
    private boolean swipeDismissAllowed;
    private int pendingDirection;
    private String lyricsKey;
    private int lyricsRequest;
    private int motionRequest;
    private HintView2 speedHintView;
    private NotificationCenter.ObserversGroup observers;
    private long lastSpeedClick;
    private boolean lightStatusBar;

    private int rewindingState;
    private float rewindingProgress = -1;
    private int rewindPressedCount;
    private long lastRewindingTime;
    private long lastUpdateRewindingPlayerTime;

    private final Runnable rewindStepRunnable = new Runnable() {
        @Override
        public void run() {
            if (rewindingState == 0 || rewindPressedCount >= 3) {
                return;
            }
            rewindPressedCount++;
            if (rewindingState > 0 && !MediaController.getInstance().isMessagePaused()) {
                MediaController.getInstance().setRewindPlaybackSpeed(getRewindMultiplier());
            }
            seekSpeedDrawable.setSpeed(rewindingState * getRewindMultiplier(), true);
            if (rewindPressedCount < 3) {
                AndroidUtilities.runOnUIThread(this, REWIND_STEP_MS);
            }
        }
    };

    private final Runnable rewindSeek = new Runnable() {
        @Override
        public void run() {
            final int direction = rewindingState;
            final MediaController controller = MediaController.getInstance();
            final long duration = controller.getDuration();
            if (direction == 0) {
                return;
            }
            if (duration == 0 || duration == C.TIME_UNSET) {
                lastRewindingTime = SystemClock.elapsedRealtime();
                return;
            }
            final long now = SystemClock.elapsedRealtime();
            final long dt = (long) ((now - lastRewindingTime) * getRewindMultiplier());
            lastRewindingTime = now;
            final long updateDt = now - lastUpdateRewindingPlayerTime;
            rewindingProgress = Utilities.clamp01((duration * rewindingProgress + direction * dt) / (float) duration);
            final MessageObject messageObject = controller.getPlayingMessageObject();
            if (messageObject != null && messageObject.isMusic()) {
                if (direction > 0 && !controller.isMessagePaused()) {
                    messageObject.audioProgress = rewindingProgress;
                }
                updateProgress(messageObject);
            }
            if (rewindPressedCount <= 0 || direction > 0 && !controller.isMessagePaused()) {
                return;
            }
            if (updateDt > 200 || rewindingProgress == 0) {
                lastUpdateRewindingPlayerTime = now;
                lyricsView.onSeek();
                controller.seekToProgress(messageObject, rewindingProgress);
                if (direction < 0 && rewindingProgress == 0) {
                    controller.pauseByRewind();
                }
            }
            if (rewindingProgress > 0 && (direction < 0 || rewindingProgress < 1f)) {
                AndroidUtilities.runOnUIThread(this, 16);
            }
        }
    };

    public MusicPlayerSheet(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, true, resourcesProvider);
        useLightStatusBar = false;
        baseResourcesProvider = resourcesProvider;
        final Activity activity = AndroidUtilities.findActivity(context);
        parentActivity = activity instanceof LaunchActivity ? (LaunchActivity) activity : LaunchActivity.instance;

        final MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
        currentAccount = playing != null ? playing.currentAccount : UserConfig.selectedAccount;
        TAG = DownloadController.getInstance(currentAccount).generateObserverTag();
        actions = new PlayerActions(currentAccount, parentActivity, this);
        savedMusicList = MediaController.getInstance().currentSavedMusicList;

        themeColors = PlayerColors.fromTheme(resourcesProvider);
        colors = themeColors.copy();

        doNotOverlayNavigationBar = true;
        occupyNavigationBarWithoutKeyboard = true;
        useSmoothKeyboard = !AndroidUtilities.isTablet();
        updateTabletSheet(context.getResources().getConfiguration());
        allowNestedScroll = false;
        updateNavigationBar(colors);
        setCanDismissWithSwipe(true);

        playerLayout = new PlayerLayout(context);
        playerLayout.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);
        containerView = playerLayout;
        canvasBackground = new CanvasBackground(playerLayout, this::syncCanvas);

        collapseButton = createIconButton(context, R.drawable.arrow_more, collapseBackground = new ExpressiveStateDrawable(12));
        collapseButton.setContentDescription(getString(R.string.PlayerCollapse));
        collapseButton.setOnClickListener(v -> dismiss());
        playerLayout.addOwnView(collapseButton);

        moreIcon = new ChooseQualityLayout.QualityIcon(context, R.drawable.ic_ab_other, resourcesProvider);
        moreButton = createIconButton(context, 0, moreBackground = new ExpressiveStateDrawable(12));
        moreButton.setImageDrawable(moreIcon);
        moreButton.setContentDescription(getString(R.string.AccDescrMoreOptions));
        moreButton.setOnClickListener(v -> showMenu());
        playerLayout.addOwnView(moreButton);

        headerTitle = new TextView(context);
        headerTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        headerTitle.setTypeface(AndroidUtilities.bold());
        headerTitle.setSingleLine(true);
        headerTitle.setEllipsize(TextUtils.TruncateAt.END);
        headerTitle.setGravity(Gravity.CENTER);
        playerLayout.addOwnView(headerTitle);

        headerSubtitle = new TextView(context);
        headerSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        headerSubtitle.setTypeface(AndroidUtilities.regular());
        headerSubtitle.setSingleLine(true);
        headerSubtitle.setEllipsize(TextUtils.TruncateAt.END);
        headerSubtitle.setGravity(Gravity.CENTER);
        playerLayout.addOwnView(headerSubtitle);

        lyricsView = new LyricsView(context, resourcesProvider);
        lyricsView.setProgressMode(ExteraConfig.getLyricsProgress());
        lyricsView.setDelegate(new LyricsView.Delegate() {
            @Override
            public void onSeekTo(long ms) {
                final MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
                if (messageObject != null) {
                    MediaController.getInstance().seekToProgressMs(messageObject, ms);
                    if (MediaController.getInstance().isMessagePaused()) {
                        MediaController.getInstance().playMessage(messageObject);
                    }
                }
            }

            @Override
            public void onConsent() {
                ExteraConfig.setLyricsOnline(true);
                lyricsKey = null;
                loadLyrics(true);
            }

            @Override
            public void onRetry() {
                lyricsKey = null;
                loadLyrics(true);
            }
        });
        lyricsView.setAlpha(0f);
        lyricsView.setVisibility(View.INVISIBLE);
        playerLayout.addOwnView(lyricsView);

        queueView = new PlayerQueueView(context, currentAccount);
        queueView.setDelegate(new PlayerQueueView.Delegate() {
            @Override
            public void onShowOptions(PlayerQueueCell cell, MessageObject messageObject) {
                showTrackOptions(cell, messageObject);
            }

            @Override
            public void onReverseOrder() {
                MediaController.getInstance().setPlaybackOrderType(SharedConfig.playOrderReversed ? 0 : 1);
                updateOrderButtons(true);
                queueView.reverse();
            }

            @Override
            public void onAddToList() {
                actions.addToMyList(() -> {
                    queueView.update();
                    updateHeader();
                    updateProfileState(true);
                });
            }

            @Override
            public void onSearchFocus(boolean focused) {
                setAllowNestedScroll(!focused);
                playerLayout.requestLayout();
            }

            @Override
            public boolean isMyList() {
                return MusicPlayerSheet.this.isMyList();
            }

            @Override
            public boolean isNoForwards() {
                return noforwards;
            }

            @Override
            public MessagesController.SavedMusicList getSavedMusicList() {
                return savedMusicList;
            }
        });
        queueView.setAlpha(0f);
        queueView.setVisibility(View.INVISIBLE);
        playerLayout.addOwnView(queueView);

        coverView = new PlayerCoverView(context);
        coverView.setDelegate(new PlayerCoverView.Delegate() {
            @Override
            public void onSwipe(int direction) {
                if (direction > 0) {
                    pendingDirection = 1;
                    MediaController.getInstance().playNextMessage();
                } else {
                    pendingDirection = -1;
                    final MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
                    if (messageObject != null) {
                        messageObject.audioProgressSec = 0;
                    }
                    MediaController.getInstance().playPreviousMessage();
                }
                coverView.cancelPendingSwipe();
            }

            @Override
            public void onCoverLoaded(MessageObject messageObject, ImageReceiver receiver) {
                onCoverBitmapLoaded(messageObject, receiver);
            }

            @Override
            public boolean canDrag() {
                return mode == MODE_COVER && MediaController.getInstance().getPlaylist().size() > 1;
            }

            @Override
            public MessageObject getNeighbor(int direction) {
                return getNeighborMessage(direction);
            }

            @Override
            public boolean hasCanvas(MessageObject messageObject) {
                for (int i = 0; i < canvasMessages.length; i++) {
                    if (canvasMessages[i] == messageObject && isCanvasRole(i)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public void onPageMoved() {
                playerLayout.invalidate();
            }

            @Override
            public boolean canTap() {
                return canBeImmersive();
            }

            @Override
            public void onTap() {
                setImmersive(!immersive, true);
            }
        });
        playerLayout.addOwnView(coverView);

        titleView = new AudioPlayerAlert.ClippingTextViewSwitcher(context) {
            @Override
            protected TextView createTextView() {
                final TextView textView = new TextView(context);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
                textView.setTypeface(AndroidUtilities.bold());
                textView.setSingleLine(true);
                textView.setEllipsize(TextUtils.TruncateAt.END);
                textView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
                textView.setTextColor(colors.text);
                return textView;
            }
        };
        titleView.setPivotX(0);
        titleView.setPivotY(0);
        playerLayout.addOwnView(titleView);

        artistView = new AudioPlayerAlert.ClippingTextViewSwitcher(context) {
            @Override
            protected TextView createTextView() {
                final TextView textView = new TextView(context);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                textView.setTypeface(AndroidUtilities.regular());
                textView.setPadding(dp(ARTIST_PADDING_X_DP), 0, dp(ARTIST_PADDING_X_DP), 0);
                textView.setSingleLine(true);
                textView.setEllipsize(TextUtils.TruncateAt.END);
                textView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
                textView.setTextColor(colors.textSecondary);
                textView.setOnClickListener(v -> {
                    final CharSequence text = textView.getText();
                    if (!TextUtils.isEmpty(text)) {
                        actions.searchArtist(text.toString());
                    }
                });
                return textView;
            }
        };
        artistView.setPivotX(0);
        artistView.setPivotY(0);
        playerLayout.addOwnView(artistView);

        profileButton = createIconButton(context, R.drawable.media_like, profileBackground = createToggle());
        profileButton.setOnClickListener(v -> toggleProfile());
        playerLayout.addOwnView(profileButton);

        seekBar = new WavySeekBar(context);
        seekBar.setDelegate(new WavySeekBar.Delegate() {
            @Override
            public void onSeek(float progress) {
                final MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
                if (messageObject == null) {
                    return;
                }
                lyricsView.onSeek();
                MediaController.getInstance().seekToProgress(messageObject, progress);
                updateProgress(messageObject);
            }

            @Override
            public void onDragStateChanged(boolean dragging) {
                final MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
                if (messageObject != null) {
                    updateProgress(messageObject);
                }
            }

            @Override
            public CharSequence getTimeLabel(float progress) {
                final MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
                return messageObject == null ? null : AndroidUtilities.formatShortDuration((int) (messageObject.getDuration() * progress));
            }

            @Override
            public CharSequence getAccessibilityValue() {
                final int time = Math.max(0, lastTime);
                final String current = LocaleController.formatPluralString("Minutes", time / 60) + ' ' + LocaleController.formatPluralString("Seconds", time % 60);
                final String total = LocaleController.formatPluralString("Minutes", lastDuration / 60) + ' ' + LocaleController.formatPluralString("Seconds", lastDuration % 60);
                return formatString(R.string.AccDescrPlayerDuration, current, total);
            }
        });
        playerLayout.addOwnView(seekBar);

        timeView = new TextView(context);
        timeView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        timeView.setTypeface(AndroidUtilities.regular());
        timeView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        timeView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        playerLayout.addOwnView(timeView);

        durationView = new TextView(context);
        durationView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        durationView.setTypeface(AndroidUtilities.regular());
        durationView.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        durationView.setContentDescription(getString(R.string.PlayerShowRemaining));
        durationView.setOnClickListener(v -> {
            showRemaining = !showRemaining;
            durationView.setContentDescription(getString(showRemaining ? R.string.PlayerShowDuration : R.string.PlayerShowRemaining));
            lastTime = -1;
            final MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
            if (messageObject != null) {
                updateProgress(messageObject);
            }
        });
        playerLayout.addOwnView(durationView);

        controlsView = new PlayerControlsView(context);
        controlsView.setDelegate(new PlayerControlsView.Delegate() {
            @Override
            public void onPrevious() {
                if (MediaController.getInstance().getPlayingMessageObject() == null) {
                    return;
                }
                pendingDirection = -1;
                MediaController.getInstance().playPreviousMessage();
            }

            @Override
            public void onPlayPause() {
                togglePlay();
            }

            @Override
            public void onNext() {
                if (MediaController.getInstance().getPlayingMessageObject() == null) {
                    return;
                }
                pendingDirection = 1;
                MediaController.getInstance().playNextMessage();
            }

            @Override
            public void onHold(int direction, boolean start) {
                onRewindHold(direction, start);
            }
        });
        playerLayout.addOwnView(controlsView);

        shuffleButton = createIconButton(context, R.drawable.player_new_shuffle, shuffleBackground = createToggle());
        shuffleButton.setOnClickListener(v -> {
            MediaController.getInstance().setPlaybackOrderType(SharedConfig.shuffleMusic ? 0 : 2);
            updateOrderButtons(true);
            queueView.update();
        });
        shuffleButton.setOnLongClickListener(v -> {
            showOrderMenu(v);
            return true;
        });
        playerLayout.addOwnView(shuffleButton);

        repeatButton = createIconButton(context, R.drawable.player_new_repeatall, repeatBackground = createToggle());
        repeatButton.setOnClickListener(v -> {
            SharedConfig.setRepeatMode((SharedConfig.repeatMode + 1) % 3);
            updateOrderButtons(true);
        });
        repeatButton.setOnLongClickListener(v -> {
            showOrderMenu(v);
            return true;
        });
        playerLayout.addOwnView(repeatButton);

        speedButton = createIconButton(context, 0, speedBackground = new ExpressiveStateDrawable(12).setSelectedRadius(ACTION_BUTTON_DP / 2f).setInset(0, (ACTION_BUTTON_DP - SPEED_OUTLINE_HEIGHT_DP) / 2f));
        speedButton.setImageDrawable(speedIcon = new SpeedTextDrawable());
        speedButton.setOnClickListener(v -> cycleSpeed());
        speedButton.setOnLongClickListener(v -> {
            showSpeedMenu(v);
            return true;
        });
        playerLayout.addOwnView(speedButton);

        lyricsButton = createIconButton(context, R.drawable.formatting_quote, lyricsBackground = createModeSegment(ExpressiveStateDrawable.INNER_RIGHT));
        lyricsButton.setContentDescription(getString(R.string.PlayerLyrics));
        lyricsButton.setOnClickListener(v -> selectMode(mode == MODE_LYRICS ? MODE_COVER : MODE_LYRICS));
        playerLayout.addOwnView(lyricsButton);

        queueButton = createIconButton(context, R.drawable.player_new_queue, queueBackground = createModeSegment(ExpressiveStateDrawable.INNER_LEFT));
        queueButton.setContentDescription(getString(R.string.PlayerQueue));
        queueButton.setOnClickListener(v -> selectMode(mode == MODE_QUEUE ? MODE_COVER : MODE_QUEUE));
        playerLayout.addOwnView(queueButton);

        seekSpeedDrawable = new SeekSpeedDrawable(playerLayout::invalidate, false, true);
        seekPlateSpring = Material3Tokens.createSpring(Material3Tokens.DEFAULT_SPATIAL_DAMPING, Material3Tokens.DEFAULT_SPATIAL_STIFFNESS, Material3Tokens.MIN_VISIBLE_CHANGE, value -> {
            seekPlate = value;
            playerLayout.invalidate();
        });
        seekPlateSpring.addEndListener((animation, canceled, value, velocity) -> {
            if (!canceled && value <= 0.01f && rewindingState == 0) {
                seekPlate = 0f;
                seekSpeedDrawable.setShown(false, false);
            }
        });

        modeSpring = createSpring(Material3Tokens.DEFAULT_SPATIAL_DAMPING, Material3Tokens.DEFAULT_SPATIAL_STIFFNESS, value -> modeProgress = value);
        lyricsSpring = createSpring(Material3Tokens.EFFECTS_DAMPING, Material3Tokens.EFFECTS_STIFFNESS, value -> lyricsAlpha = Utilities.clamp01(value));
        queueSpring = createSpring(Material3Tokens.EFFECTS_DAMPING, Material3Tokens.EFFECTS_STIFFNESS, value -> queueAlpha = Utilities.clamp01(value));
        canvasSpring = createSpring(Material3Tokens.DEFAULT_SPATIAL_DAMPING, Material3Tokens.DEFAULT_SPATIAL_STIFFNESS, value -> canvasProgress = value);
        canvasTitleSpring = createSpring(Material3Tokens.DEFAULT_SPATIAL_DAMPING, Material3Tokens.DEFAULT_SPATIAL_STIFFNESS, value -> canvasTitleProgress = value);
        immersiveSpring = createSpring(Material3Tokens.DEFAULT_SPATIAL_DAMPING, Material3Tokens.DEFAULT_SPATIAL_STIFFNESS, value -> immersiveProgress = value);
        bufferSpring = Material3Tokens.createSpring(Material3Tokens.EFFECTS_DAMPING, BUFFER_STIFFNESS, 1f, value -> seekBar.setBufferedProgress(value / 1000f));

        setupCast(context);

        observers = NotificationCenter.getInstance(currentAccount).createObserversGroup(this)
                .add(NotificationCenter.messagePlayingDidReset)
                .add(NotificationCenter.messagePlayingPlayStateChanged)
                .add(NotificationCenter.messagePlayingDidStart)
                .add(NotificationCenter.messagePlayingProgressDidChanged)
                .add(NotificationCenter.messagePlayingDidSeek)
                .add(NotificationCenter.fileLoaded)
                .add(NotificationCenter.fileLoadProgressChanged)
                .add(NotificationCenter.musicDidLoad)
                .add(NotificationCenter.moreMusicDidLoad)
                .add(NotificationCenter.musicIdsLoaded)
                .add(NotificationCenter.mediaCountDidLoad)
                .addGlobal(NotificationCenter.messagePlayingSpeedChanged);

        for (ExpressiveStateDrawable toggle : new ExpressiveStateDrawable[]{profileBackground, shuffleBackground, repeatBackground, speedBackground}) {
            toggle.setOnProgressChanged(this::onToggleProgress);
        }
        lyricsBackground.setOnProgressChanged(this::onModeSegmentsChanged);
        queueBackground.setOnProgressChanged(this::onModeSegmentsChanged);

        applyColors(colors);
        queueView.update();
        bindMessage(false, false);
        final int savedMode = ExteraConfig.getPlayerMode();
        if ((savedMode == MODE_LYRICS || savedMode == MODE_QUEUE)) {
            setMode(savedMode, false);
        }
        updateOrderButtons(false);
        updateSpeedButtons(false);
        updateHeader();
    }

    private ExpressiveStateDrawable createModeSegment(int innerSide) {
        return new ExpressiveStateDrawable(MODE_SEGMENT_HEIGHT_DP / 2f)
                .setInnerCorners(innerSide, MODE_INNER_RADIUS_DP)
                .setAlwaysShowContainer(true);
    }

    private void onModeSegmentsChanged() {
        applyModeSegment(lyricsButton, lyricsBackground, colors);
        applyModeSegment(queueButton, queueBackground, colors);
        playerLayout.layoutModeSegments();
    }

    private static void applyModeSegment(ImageView view, ExpressiveStateDrawable background, PlayerColors c) {
        final int content = ColorUtils.blendARGB(c.icon, c.onAccent, background.getSelectProgress());
        setIconTint(view, background, content);
        background.setColors(content, c.surfaceContainer).setSelectedContainerColor(c.accent);
    }

    private ImageView createIconButton(Context context, int iconRes, ExpressiveStateDrawable background) {
        final ImageView view = new ImageView(context);
        view.setScaleType(ImageView.ScaleType.CENTER);
        if (iconRes != 0) {
            view.setImageResource(iconRes);
        }
        view.setBackground(background);
        return view;
    }

    private SpringAnimation createSpring(float damping, float stiffness, Utilities.Callback<Float> setter) {
        return Material3Tokens.createSpring(damping, stiffness, Material3Tokens.MIN_VISIBLE_CHANGE, value -> {
            setter.run(value);
            applyTransitions();
        });
    }

    private void setupCast(Context context) {
        castButton = new CastMediaRouteButton(context) {
            @Override
            public void stateUpdated(boolean connected) {
                moreIcon.setCasting(CastSync.isActive(), true);
            }
        };
        castAvailable = true;
        try {
            castButton.setRouteSelector(CastContext.getSharedInstance(context).getMergedSelector());
        } catch (Exception e) {
            FileLog.e(e);
            castAvailable = false;
        }
        castButton.setVisibility(View.INVISIBLE);
        moreIcon.setCasting(CastSync.isActive(), false);
    }

    private MessageObject getNeighborMessage(int direction) {
        if (SharedConfig.shuffleMusic) {
            return null;
        }
        final MediaController controller = MediaController.getInstance();
        final ArrayList<MessageObject> playlist = controller.getPlaylist();
        final int size = playlist.size();
        final int index = controller.getPlayingMessageObjectNum();
        if (size < 2 || index < 0 || index >= size) {
            return null;
        }
        int target = index + (direction > 0 ? 1 : -1) * controller.getNextTrackDirection();
        if (target >= size) {
            target = 0;
        } else if (target < 0) {
            target = size - 1;
        }
        return playlist.get(target);
    }

    private void showSeekPlate(boolean show) {
        if (show) {
            seekSpeedDrawable.setShown(true, false);
        }
        Material3Tokens.animate(seekPlateSpring, seekPlate, show ? 1f : 0f);
    }

    private boolean isMyList() {
        return savedMusicList != null && savedMusicList.dialogId == UserConfig.getInstance(currentAccount).getClientUserId();
    }

    @Override
    public FrameLayout getBulletinContainer() {
        return playerLayout;
    }

    @Override
    public Theme.ResourcesProvider getBulletinResourcesProvider() {
        return getThemedResourcesProvider();
    }

    @Override
    public Theme.ResourcesProvider getSheetResourcesProvider() {
        return (targetColors != null ? targetColors : colors).wrapSettings(baseResourcesProvider);
    }

    private Theme.ResourcesProvider getThemedResourcesProvider() {
        return colors.wrap(baseResourcesProvider);
    }

    @Override
    public MessagesController.SavedMusicList getSavedMusicList() {
        return savedMusicList;
    }

    @Override
    public void dismissPlayer() {
        dismiss();
    }

    private BulletinFactory bulletins() {
        return BulletinFactory.of(playerLayout, getThemedResourcesProvider());
    }

    private void togglePlay() {
        final MediaController controller = MediaController.getInstance();
        final MessageObject messageObject = controller.getPlayingMessageObject();
        if (messageObject == null || controller.isDownloadingCurrentMessage()) {
            return;
        }
        if (controller.isMessagePaused()) {
            controller.playMessage(messageObject);
        } else {
            controller.pauseMessage(messageObject);
        }
    }

    private float getRewindMultiplier() {
        if (rewindingState < 0) {
            return rewindPressedCount <= 1 ? 3f : rewindPressedCount == 2 ? 6f : 12f;
        }
        return rewindPressedCount <= 1 ? 4f : rewindPressedCount == 2 ? 7f : 13f;
    }

    private void onRewindHold(int direction, boolean start) {
        final MediaController controller = MediaController.getInstance();
        final MessageObject messageObject = controller.getPlayingMessageObject();
        if (start) {
            if (messageObject == null || seekBar.isDragging()) {
                return;
            }
            rewindingState = direction;
            rewindPressedCount = 1;
            lastRewindingTime = SystemClock.elapsedRealtime();
            lastUpdateRewindingPlayerTime = 0;
            if (direction < 0) {
                rewindingProgress = messageObject.audioProgress;
                AndroidUtilities.runOnUIThread(rewindSeek);
            } else if (controller.isMessagePaused()) {
                startForwardRewindingSeek();
            } else {
                controller.setRewindPlaybackSpeed(getRewindMultiplier());
            }
            seekSpeedDrawable.setSpeed(direction * getRewindMultiplier(), false);
            showSeekPlate(true);
            AndroidUtilities.runOnUIThread(rewindStepRunnable, REWIND_STEP_MS);
            playerLayout.invalidate();
        } else {
            if (rewindingState == 0) {
                return;
            }
            AndroidUtilities.cancelRunOnUIThread(rewindStepRunnable);
            AndroidUtilities.cancelRunOnUIThread(rewindSeek);
            if (rewindingState < 0) {
                lastUpdateRewindingPlayerTime = 0;
                rewindSeek.run();
                controller.resumeByRewind();
            } else {
                final boolean resync = getRewindMultiplier() >= REWIND_RESYNC_SPEED;
                controller.restoreMusicPlaybackSpeed();
                if (controller.isMessagePaused()) {
                    lastUpdateRewindingPlayerTime = 0;
                    rewindSeek.run();
                } else if (resync && messageObject != null) {
                    controller.seekToProgress(messageObject, messageObject.audioProgress);
                }
            }
            rewindingState = 0;
            rewindingProgress = -1;
            rewindPressedCount = 0;
            showSeekPlate(false);
            if (messageObject != null) {
                updateProgress(messageObject);
            }
        }
    }

    private void startForwardRewindingSeek() {
        if (rewindingState == 1) {
            final MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
            if (messageObject == null) {
                return;
            }
            lastRewindingTime = SystemClock.elapsedRealtime();
            rewindingProgress = messageObject.audioProgress;
            AndroidUtilities.cancelRunOnUIThread(rewindSeek);
            AndroidUtilities.runOnUIThread(rewindSeek);
        }
    }

    private void cycleSpeed() {
        final MediaController controller = MediaController.getInstance();
        final float current = controller.getPlaybackSpeed(true);
        int index = -1;
        for (int i = 0; i < TOGGLE_SPEEDS.length; ++i) {
            if (current - 0.1f <= TOGGLE_SPEEDS[i]) {
                index = i;
                break;
            }
        }
        index++;
        if (index >= TOGGLE_SPEEDS.length) {
            index = 0;
        }
        controller.setPlaybackSpeed(true, TOGGLE_SPEEDS[index]);
        checkSpeedHint();
    }

    private void checkSpeedHint() {
        final long now = SystemClock.elapsedRealtime();
        if (now - lastSpeedClick > 300) {
            int hintValue = MessagesController.getGlobalNotificationsSettings().getInt("speedhint", 0);
            hintValue++;
            if (hintValue > 2) {
                hintValue = -10;
            }
            MessagesController.getGlobalNotificationsSettings().edit().putInt("speedhint", hintValue).apply();
            if (hintValue >= 0) {
                showSpeedHint();
            }
        }
        lastSpeedClick = now;
    }

    private void showSpeedHint() {
        if (speedHintView != null || speedButton.getWidth() == 0) {
            return;
        }
        final HintView2 hint = speedHintView = new HintView2(getContext(), HintView2.DIRECTION_BOTTOM);
        hint.setMultilineText(true);
        hint.setText(getString(R.string.SpeedHint));
        hint.setBgColor(colors.text);
        hint.setTextColor(colors.background);
        hint.setPadding(dp(SIDE_DP), 0, dp(SIDE_DP), 0);
        hint.setJointPx(0f, dp(SPEED_BUTTON_WIDTH_DP) / 2f);
        hint.setDuration(3500);
        hint.setOnHiddenListener(() -> {
            AndroidUtilities.removeFromParent(hint);
            if (speedHintView == hint) {
                speedHintView = null;
            }
        });
        playerLayout.addView(hint);
        hint.show();
    }

    private ItemOptions makeMenu(View anchor, boolean swipeback, boolean fromBottom) {
        return ItemOptions.makeOptions(container, getThemedResourcesProvider(), anchor, swipeback, fromBottom).setDimAlpha(MENU_DIM_ALPHA);
    }

    private Rect getMenuPadding() {
        final Rect padding = new Rect();
        final Drawable drawable = ContextCompat.getDrawable(getContext(), R.drawable.popup_fixed_alert4);
        if (drawable != null) {
            drawable.getPadding(padding);
        }
        return padding;
    }

    private Drawable createScrimBackground(View anchor) {
        return anchor.getBackground() instanceof ExpressiveStateDrawable ? ((ExpressiveStateDrawable) anchor.getBackground()).createShapeFill(colors.background) : null;
    }

    private void alignMenu(ItemOptions o, View anchor) {
        final boolean right = anchor.getX() + anchor.getWidth() / 2f > playerLayout.getWidth() / 2f;
        o.setGravity(right ? Gravity.RIGHT : Gravity.LEFT);
        if (!right) {
            o.translate(-getMenuPadding().left, 0);
        }
    }

    private int getSpeedSliderColor() {
        return ColorTones.withTone(colors.accent, 40f, 64.0);
    }

    private void showSpeedMenu(View anchor) {
        final float currentSpeed = MediaController.getInstance().getPlaybackSpeed(true);
        final ItemOptions o = makeMenu(anchor, false, true);
        final ActionBarMenuSlider.SpeedSlider slider = new ActionBarMenuSlider.SpeedSlider(getContext(), getThemedResourcesProvider()) {
            @Override
            protected int getColorValue(float value) {
                return getSpeedSliderColor();
            }
        };
        slider.setRoundRadiusDp(MENU_CARD_RADIUS_DP);
        slider.setDrawShadow(false);
        slider.setSpeed(currentSpeed, false);
        slider.setOnValueChange((value, isFinal) -> {
            final float clamped = Utilities.clamp01(value);
            if (Math.abs(slider.getValue() - clamped) > 0.001f) {
                slider.setValue(clamped, true);
            }
            MediaController.getInstance().setPlaybackSpeed(true, slider.getSpeed(clamped));
        });
        o.addView(slider, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44));
        o.addSpaceGap();
        for (int i = 0; i < SPEEDS.length; i++) {
            final float speed = SPEEDS[i];
            o.add(SPEED_ICONS[i], getString(SPEED_NAMES[i]), () -> MediaController.getInstance().setPlaybackSpeed(true, speed));
            if (Math.abs(currentSpeed - speed) < 0.05f && o.getLast() != null) {
                final int accent = colors.accent;
                o.getLast().setColors(accent, accent);
            }
        }
        MessagesController.getGlobalNotificationsSettings().edit().putInt("speedhint", -15).apply();
        o.addGap();
        o.addChecked(ExteraConfig.getPlayerSpeedPitch(), R.drawable.msg_tone_on, getString(R.string.PlayerSpeedPitch), () -> {
            ExteraConfig.setPlayerSpeedPitch(!ExteraConfig.getPlayerSpeedPitch());
            MediaController.getInstance().restoreMusicPlaybackSpeed();
        });
        matchFirstCardWidth(o, slider);
        alignMenu(o, anchor);
        o.setScrimViewBackground(createScrimBackground(anchor));
        o.setDismissOnMoveOutside(true);
        o.setOnDismiss(() -> {
            speedMenuShown = false;
            applySpeedColors(colors);
        });
        speedMenuShown = true;
        applySpeedColors(colors);
        o.show();
    }

    private void matchFirstCardWidth(ItemOptions o, View view) {
        final ViewGroup layout = o.getLayout();
        if (layout == null || layout.getChildCount() < 2 || view.getLayoutParams() == null) {
            return;
        }
        int width = 0;
        for (int i = 1; i < layout.getChildCount(); i++) {
            final View card = layout.getChildAt(i);
            card.measure(View.MeasureSpec.makeMeasureSpec(container.getWidth(), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(container.getHeight(), View.MeasureSpec.AT_MOST));
            width = Math.max(width, card.getMeasuredWidth() - card.getPaddingLeft() - card.getPaddingRight());
        }
        view.getLayoutParams().width = Math.max(dp(120), width);
    }

    private void showOrderMenu(View anchor) {
        final ItemOptions o = makeMenu(anchor, false, true);
        o.addChecked(SharedConfig.repeatMode == 2, R.drawable.player_new_repeatone, getString(R.string.RepeatSong), () -> {
            SharedConfig.setRepeatMode(SharedConfig.repeatMode == 2 ? 0 : 2);
            updateOrderButtons(true);
        });
        o.addChecked(SharedConfig.repeatMode == 1, R.drawable.player_new_repeatall, getString(R.string.RepeatList), () -> {
            SharedConfig.setRepeatMode(SharedConfig.repeatMode == 1 ? 0 : 1);
            updateOrderButtons(true);
        });
        o.addGap();
        o.addChecked(SharedConfig.shuffleMusic, R.drawable.player_new_shuffle, getString(R.string.ShuffleList), () -> {
            MediaController.getInstance().setPlaybackOrderType(SharedConfig.shuffleMusic ? 0 : 2);
            updateOrderButtons(true);
            queueView.update();
        });
        o.addGap();
        o.addChecked(SharedConfig.playOrderReversed, R.drawable.player_new_order, getString(R.string.ReverseOrder), () -> {
            MediaController.getInstance().setPlaybackOrderType(SharedConfig.playOrderReversed ? 0 : 1);
            updateOrderButtons(true);
            queueView.reverse();
        });
        alignMenu(o, anchor);
        o.setScrimViewBackground(createScrimBackground(anchor));
        o.setDismissOnMoveOutside(true);
        o.show();
    }

    private void showMenu() {
        final MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
        if (messageObject == null) {
            return;
        }
        final boolean serviceTrack = PlayerActions.isServiceTrack(messageObject);
        final ItemOptions o = makeMenu(moreButton, true, false);
        if (!serviceTrack) {
            if (!isMyList() && !noforwards) {
                final ItemOptions saveTo = buildSaveOptions(o, messageObject);
                o.add(R.drawable.msg_stories_save, getString(R.string.AudioSaveTo), () -> o.openSwipeback(saveTo));
                if (o.getLast() != null) {
                    o.getLast().setRightIcon(R.drawable.msg_arrowright);
                }
                o.addGap();
            }
            addForwardAndShare(o, messageObject);
            o.addIf(isMyList() && !noforwards, R.drawable.menu_download_round, getString(R.string.SaveToMusic), () -> {
                o.dismiss();
                actions.saveToMusic(messageObject);
            });
            o.addIf(messageObject.getId() > 0, R.drawable.msg_message, getString(R.string.ShowInChat), () -> {
                o.dismiss();
                actions.showInChat(messageObject);
            });
            if (castAvailable) {
                final ActionBarMenuSubItem castItem = o.add();
                castItem.setTextAndIcon(getString(R.string.VideoPlayerChromecast), R.drawable.menu_video_chromecast);
                castItem.setOnClickListener(v -> {
                    o.dismiss();
                    ChromecastController.getInstance().setCurrentMediaAndCastIfNeeded(MediaController.getInstance().getCurrentChromecastMedia());
                    castButton.performClick();
                });
                AndroidUtilities.removeFromParent(castButton);
                castItem.addView(castButton, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                final boolean connected = castButton.isConnected();
                castItem.setEnabledByColor(connected, Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, getThemedResourcesProvider()), Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon, getThemedResourcesProvider()), colors.accent);
            }
            o.addIf(isMyList(), R.drawable.msg_delete, getString(R.string.ProfilePlaylistRemoveFromProfile), true, () -> {
                o.dismiss();
                actions.removeFromMyList(messageObject, this::onMyListChanged);
            });
            o.addGap();
        }
        if (!serviceTrack) {
            final ItemOptions details = buildDetailsOptions(o, messageObject);
            o.add(R.drawable.msg_info, getString(R.string.Details), () -> o.openSwipeback(details));
            if (o.getLast() != null) {
                o.getLast().setRightIcon(R.drawable.msg_arrowright);
            }
        }
        o.add(R.drawable.msg_settings, getString(R.string.PlayerSettings), () -> {
            o.dismiss();
            openSettings();
        });
        o.setGravity(Gravity.RIGHT);
        o.setSwipebackGravity(false, false);
        if (o.getLayout() instanceof ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout && popupLayout.getSwipeBack() != null) {
            popupLayout.getSwipeBack().setStickToRight(true);
        }
        o.setOnTopOfScrim();
        o.translate(0, -getMenuPadding().top);
        o.show();
    }

    private ItemOptions buildDetailsOptions(ItemOptions o, MessageObject messageObject) {
        final ItemOptions o2 = o.makeSwipeback();
        o2.add(R.drawable.ic_ab_back, getString(R.string.Back), o::closeSwipeback);
        o2.addGap();
        final TrackQuality.Info info = TrackQuality.get(messageObject);
        final File file = PlayerActions.getLocalFile(messageObject);
        final ActionBarMenuSubItem format = addDetail(o, o2, R.drawable.msg_media, getString(R.string.PlayerInfoFormat), info != null ? info.format : null);
        final ActionBarMenuSubItem bitrate = addDetail(o, o2, R.drawable.msg_noise_on, getString(R.string.Bitrate), info != null ? info.formatBitrate() : null);
        final ActionBarMenuSubItem sampleRate = addDetail(o, o2, R.drawable.msg_tone_on, getString(R.string.PlayerInfoSampleRate), info != null ? info.formatSampleRate() : null);
        final ActionBarMenuSubItem bitDepth = addDetail(o, o2, R.drawable.menu_quality_hd, getString(R.string.PlayerInfoBitDepth), info != null ? info.formatBitDepth() : null);
        final long size = messageObject.getSize();
        addDetail(o, o2, R.drawable.msg_sendfile, getString(R.string.FileSize), size > 0 ? AndroidUtilities.formatFileSize(size) : null);
        final int duration = (int) messageObject.getDuration();
        addDetail(o, o2, R.drawable.msg2_animations, getString(R.string.Duration), duration > 0 ? AndroidUtilities.formatShortDuration(duration) : null);
        addDetail(o, o2, R.drawable.msg_log, getString(R.string.FileName), FileLoader.getDocumentFileName(messageObject.getDocument()));
        addDetail(o, o2, R.drawable.msg_map, getString(R.string.FilePath), file != null && file.exists() ? file.getAbsolutePath() : null);
        if (info != null && !info.probed) {
            TrackQuality.load(messageObject, loaded -> {
                setDetail(format, loaded.format);
                setDetail(bitrate, loaded.formatBitrate());
                setDetail(sampleRate, loaded.formatSampleRate());
                setDetail(bitDepth, loaded.formatBitDepth());
            });
        }
        return o2;
    }

    private ActionBarMenuSubItem addDetail(ItemOptions o, ItemOptions o2, int icon, String title, String value) {
        o2.add(icon, title, null);
        final ActionBarMenuSubItem item = o2.getLast();
        if (item == null) {
            return null;
        }
        item.setMinimumWidth(dp(196));
        item.setOnClickListener(v -> copyDetail(o, item));
        item.setOnLongClickListener(v -> {
            copyDetail(o, item);
            return true;
        });
        setDetail(item, value);
        return item;
    }

    private void setDetail(ActionBarMenuSubItem item, String value) {
        if (item == null) {
            return;
        }
        item.setVisibility(TextUtils.isEmpty(value) ? View.GONE : View.VISIBLE);
        if (!TextUtils.isEmpty(value)) {
            item.setSubtext(value);
            item.subtextView.setMaxWidth(dp(DETAIL_MAX_WIDTH_DP));
            item.subtextView.setEllipsize(TextUtils.TruncateAt.MARQUEE);
            item.subtextView.setMarqueeRepeatLimit(-1);
            item.subtextView.setSelected(true);
            item.setItemHeight(56);
        }
    }

    private void copyDetail(ItemOptions o, ActionBarMenuSubItem item) {
        if (item.subtextView == null || TextUtils.isEmpty(item.subtextView.getText())) {
            return;
        }
        o.dismiss();
        if (AndroidUtilities.addToClipboard(item.subtextView.getText()) && AndroidUtilities.shouldShowClipboardToast()) {
            bulletins().createCopyBulletin(getString(R.string.TextCopied)).show();
        }
    }

    private void addForwardAndShare(ItemOptions o, MessageObject messageObject) {
        if (noforwards) {
            return;
        }
        o.add(R.drawable.msg_forward, getString(R.string.Forward), () -> {
            o.dismiss();
            actions.forward(messageObject, false);
        });
        final ActionBarMenuSubItem forwardItem = o.getLast();
        if (forwardItem != null) {
            forwardItem.setOnLongClickListener(v -> {
                o.dismiss();
                actions.forward(messageObject, true);
                return true;
            });
        }
        o.add(R.drawable.msg_shareout, getString(R.string.ShareFile), () -> {
            o.dismiss();
            actions.share(messageObject);
        });
    }

    private void openSettings() {
        final BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (fragment == null) {
            return;
        }
        final PlayerSettingsSheet sheet = new PlayerSettingsSheet(getContext(), fragment, getSheetResourcesProvider(), new PlayerSettingsSheet.Delegate() {
            @Override
            public void onCoverColorsChanged() {
                if (ExteraConfig.getPlayerCoverColors() && coverView.hasBitmap()) {
                    onCoverBitmapLoaded(coverView.getMessageObject(), coverView.getImageReceiver());
                }
                updateCoverColors(true);
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.playerColorsChanged);
            }

            @Override
            public void onMotionCoversChanged() {
                coverView.setMotionCover(coverView.getMessageObject(), null);
                loadMotionCover(coverView.getMessageObject());
            }

            @Override
            public void onCanvasChanged() {
                for (Map.Entry<String, String> entry : canvasPaths.snapshot().entrySet()) {
                    if (entry.getValue().isEmpty() || !CanvasRepository.isPathEnabled(entry.getValue())) {
                        canvasPaths.remove(entry.getKey());
                    }
                }
                loadCanvas(0);
            }

            @Override
            public void onRhythmBackgroundChanged() {
                updateRhythm();
            }

            @Override
            public void onLyricsOnlineChanged() {
                reloadLyrics();
            }

            @Override
            public void onLyricsProvidersChanged() {
                reloadLyrics();
            }

            @Override
            public void onLyricsProgressChanged() {
                lyricsView.setProgressMode(ExteraConfig.getLyricsProgress());
            }

            @Override
            public void onLyricsSubtitleChanged() {
                if (lyricsView.getState() == LyricsView.STATE_READY && lyricsView.getLyrics() != null) {
                    showLyrics(lyricsView.getLyrics());
                }
            }
        });
        sheet.setOnHideListener(d -> {
            if (settingsSheet == sheet) {
                settingsSheet = null;
            }
        });
        settingsSheet = sheet;
        sheet.show();
    }

    private ItemOptions buildSaveOptions(ItemOptions o, MessageObject messageObject) {
        final ItemOptions o2 = o.makeSwipeback();
        o2.add(R.drawable.ic_ab_back, getString(R.string.Back), o::closeSwipeback);
        o2.addGap();
        o2.addIf(!actions.isInProfile(messageObject), R.drawable.left_status_profile, getString(R.string.AudioSaveToMyProfile), () -> actions.setInProfile(messageObject, true, () -> {
            updateProfileState(true);
            bulletins().createSimpleBulletin(R.raw.saved_messages, getString(R.string.AudioSaveToMyProfileSaved)).show();
            o.dismiss();
        }));
        o2.add(R.drawable.msg_saved, getString(R.string.AudioSaveToSavedMessages), () -> {
            actions.forwardTo(messageObject, UserConfig.getInstance(currentAccount).getClientUserId(), false);
            o.dismiss();
            bulletins().createSimpleBulletin(R.raw.saved_messages, getString(R.string.AudioSaveToSavedMessagesSaved)).show();
        });
        o2.add(R.drawable.menu_download_round, getString(R.string.AudioSaveToMusicFolder), () -> {
            actions.saveToMusic(messageObject);
            o.dismiss();
        });
        o2.addGap();
        o2.addText(getString(R.string.AudioSaveToInfo), 12, dp(200));
        return o2;
    }

    private void showTrackOptions(PlayerQueueCell cell, MessageObject messageObject) {
        final ItemOptions o = makeMenu(cell, true, false);
        if (isMyList()) {
            addForwardAndShare(o, messageObject);
            o.addIf(!noforwards, R.drawable.menu_download_round, getString(R.string.SaveToMusic), () -> {
                o.dismiss();
                actions.saveToMusic(messageObject);
            });
            o.add(R.drawable.msg_delete, getString(R.string.Delete), true, () -> actions.removeFromMyList(messageObject, () -> {
                onMyListChanged();
                o.dismiss();
                updateProfileState(true);
                bulletins().createSimpleBulletin(R.raw.ic_delete, getString(R.string.AudioSaveToMyProfileUnsaved)).show();
            }));
        } else {
            final ItemOptions o2 = buildSaveOptions(o, messageObject);
            o.addIf(!noforwards, R.drawable.msg_stories_save, getString(R.string.AudioSaveTo), () -> o.openSwipeback(o2));
            if (!noforwards && o.getLast() != null) {
                o.getLast().setRightIcon(R.drawable.msg_arrowright);
            }
            o.addGap();
            addForwardAndShare(o, messageObject);
            o.addIf(messageObject.getId() > 0, R.drawable.msg_view_file, getString(R.string.ShowInChat), () -> {
                o.dismiss();
                actions.showInChat(messageObject);
            });
        }
        o.setGravity(LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT);
        o.setDismissOnMoveOutside(true);
        o.show();
    }

    private void toggleProfile() {
        final MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
        if (messageObject == null || actions.isProfileIdsLoading()) {
            return;
        }
        final boolean save = !actions.isInProfile(messageObject);
        profileButton.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        final Runnable done = () -> {
            updateProfileState(true);
            bulletins().createSimpleBulletin(save ? R.raw.saved_messages : R.raw.ic_delete, getString(save ? R.string.AudioSaveToMyProfileSaved : R.string.AudioSaveToMyProfileUnsaved)).show();
        };
        if (isMyList()) {
            final Runnable changed = () -> {
                onMyListChanged();
                done.run();
            };
            if (save) {
                actions.addBackToMyList(messageObject, changed);
            } else {
                actions.removeFromMyList(messageObject, changed);
            }
        } else {
            actions.setInProfile(messageObject, save, done);
        }
    }

    private void onMyListChanged() {
        queueView.update();
        updateHeader();
    }

    private void selectMode(int newMode) {
        setMode(newMode, true);
        ExteraConfig.setPlayerMode(newMode);
    }

    private void setMode(int newMode, boolean animated) {
        if (mode == newMode && animated) {
            return;
        }
        final int previous = mode;
        mode = newMode;
        if (newMode != MODE_COVER) {
            setImmersive(false, animated);
        }
        if (previous == MODE_QUEUE && newMode != MODE_QUEUE) {
            queueView.clearSearch();
        }
        final float modeTarget = newMode == MODE_COVER ? 0f : 1f;
        final float lyricsTarget = newMode == MODE_LYRICS ? 1f : 0f;
        final float queueTarget = newMode == MODE_QUEUE ? 1f : 0f;
        if (animated) {
            Material3Tokens.animate(modeSpring, modeProgress, modeTarget);
            Material3Tokens.animate(lyricsSpring, lyricsAlpha, lyricsTarget);
            Material3Tokens.animate(queueSpring, queueAlpha, queueTarget);
        } else {
            modeSpring.cancel();
            lyricsSpring.cancel();
            queueSpring.cancel();
            modeProgress = modeTarget;
            lyricsAlpha = lyricsTarget;
            queueAlpha = queueTarget;
        }
        if (canvasShown && (previous == MODE_COVER) != (newMode == MODE_COVER)) {
            updateCoverColors(animated);
        }
        lyricsBackground.setSelected(newMode == MODE_LYRICS, animated);
        queueBackground.setSelected(newMode == MODE_QUEUE, animated);
        updateToggleColors();
        if (newMode == MODE_LYRICS) {
            loadLyrics(false);
        } else if (newMode == MODE_QUEUE) {
            queueView.update();
            queueView.scrollToCurrent(false);
        }
        applyTransitions();
    }

    private void loadArtwork(MessageObject messageObject) {
        final TrackQuery query = PlayerCovers.needsArtwork(messageObject) ? TrackQuery.forMessage(messageObject) : null;
        if (query == null || ArtworkRepository.isResolved(query)) {
            return;
        }
        ArtworkRepository.fetch(query, url -> {
            if (url != null && coverView.getMessageObject() == messageObject) {
                coverView.refreshCover();
            }
        });
    }

    private void loadMotionCover(MessageObject messageObject) {
        final int request = ++motionRequest;
        final TrackQuery query = ExteraConfig.getPlayerMotionCovers() ? TrackQuery.forMessage(messageObject) : null;
        if (query == null) {
            return;
        }
        final int targetSide = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - dp(SIDE_DP * 2);
        MotionCoverRepository.fetch(query, targetSide, path -> {
            if (request == motionRequest && path != null) {
                coverView.setMotionCover(messageObject, path);
            }
        });
    }

    private void loadCanvas(int direction) {
        final boolean enabled = CanvasRepository.isEnabled();
        final TrackQuery[] queries = new TrackQuery[3];
        canvasMessages[CanvasBackground.PREVIOUS] = getNeighborMessage(-1);
        canvasMessages[CanvasBackground.CURRENT] = lastMessageObject;
        canvasMessages[CanvasBackground.NEXT] = getNeighborMessage(1);
        for (int i = 0; i < 3; i++) {
            queries[i] = enabled && canvasMessages[i] != null ? TrackQuery.forMessage(canvasMessages[i]) : null;
            canvasKeys[i] = queries[i] != null ? queries[i].key() : null;
        }
        CanvasRepository.setWantedKeys(Arrays.asList(canvasKeys));
        applyCanvas(direction);
        final String currentKey = canvasKeys[CanvasBackground.CURRENT];
        if (currentKey != null && canvasPaths.get(currentKey) == null) {
            fetchCanvas(queries[CanvasBackground.CURRENT], currentKey, true, () -> {
                if (currentKey.equals(canvasKeys[CanvasBackground.CURRENT])) {
                    fetchCanvasNeighbors(queries);
                }
            });
        } else {
            fetchCanvasNeighbors(queries);
        }
    }

    private void fetchCanvasNeighbors(TrackQuery[] queries) {
        for (int i = 0; i < 3; i++) {
            final String queryKey = canvasKeys[i];
            if (i != CanvasBackground.CURRENT && queryKey != null && canvasPaths.get(queryKey) == null) {
                fetchCanvas(queries[i], queryKey, false, null);
            }
        }
    }

    private void fetchCanvas(TrackQuery query, String queryKey, boolean priority, Runnable after) {
        CanvasRepository.fetch(query, priority, path -> {
            if (isDismissed()) {
                return;
            }
            canvasPaths.put(queryKey, path != null ? path : "");
            if (Arrays.asList(canvasKeys).contains(queryKey)) {
                applyCanvas(0);
            }
            if (after != null) {
                after.run();
            }
        });
    }

    private void applyCanvas(int direction) {
        for (int i = 0; i < 3; i++) {
            final String path = canvasKeys[i] != null ? canvasPaths.get(canvasKeys[i]) : null;
            canvasRolePaths[i] = TextUtils.isEmpty(path) ? null : path;
        }
        canvasBackground.setPaths(canvasRolePaths, canvasMessages, direction);
        syncCanvas();
    }

    private void syncCanvas() {
        updateCanvasShown();
        coverView.refreshCanvas();
    }

    private boolean isCanvasRole(int role) {
        if (playerLayout.landscape) {
            return false;
        }
        if (canvasBackground.hasFrame(role)) {
            return true;
        }
        if (!canvasShown || canvasKeys[role] == null) {
            return false;
        }
        final String path = canvasPaths.get(canvasKeys[role]);
        return !TextUtils.isEmpty(path);
    }

    private void updateCanvasShown() {
        final boolean shown = !isDismissed() && isCanvasRole(CanvasBackground.CURRENT);
        final float target = shown ? 1f : 0f;
        if (canvasProgress != target && (!canvasSpring.isRunning() || canvasSpring.getSpring().getFinalPosition() != target)) {
            Material3Tokens.animate(canvasSpring, canvasProgress, target);
        }
        if (shown != canvasShown) {
            canvasShown = shown;
            if (!shown) {
                setImmersive(false, true);
            }
            Material3Tokens.animate(canvasTitleSpring, canvasTitleProgress, target);
            updateCoverColors(true);
        }
    }

    private void reloadLyrics() {
        lyricsKey = null;
        if (mode == MODE_LYRICS) {
            loadLyrics(false);
        }
    }

    private void loadLyrics(boolean force) {
        final MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
        if (messageObject == null) {
            return;
        }
        final TrackQuery query = TrackQuery.forMessage(messageObject);
        final String key = (query != null ? LyricsRepository.key(query) : "") + "\u0002" + messageObject.getId() + "\u0002" + ExteraConfig.getLyricsOnline();
        if (!force && key.equals(lyricsKey)) {
            return;
        }
        lyricsKey = key;
        final int request = ++lyricsRequest;
        final Lyrics fromTags = LyricsRepository.fromTags(messageObject);
        if (fromTags != null) {
            showLyrics(fromTags);
            return;
        }
        if (query == null) {
            showLyricsState(LyricsView.STATE_NOT_FOUND);
            return;
        }
        final Lyrics cached = LyricsRepository.getCached(query);
        if (cached != null) {
            showLyrics(cached);
            return;
        }
        if (!ExteraConfig.getLyricsOnline()) {
            showLyricsState(LyricsView.STATE_LOADING);
            LyricsRepository.loadCached(query, (lyrics, status) -> {
                if (request != lyricsRequest) {
                    return;
                }
                if (lyrics != null) {
                    showLyrics(lyrics);
                } else {
                    showLyricsState(LyricsView.STATE_OFFER);
                }
            });
            return;
        }
        if (!force && LyricsRepository.isKnownMissing(query)) {
            showLyricsState(LyricsView.STATE_NOT_FOUND);
            return;
        }
        showLyricsState(LyricsView.STATE_LOADING);
        LyricsRepository.fetch(query, (lyrics, status) -> {
            if (request != lyricsRequest) {
                return;
            }
            if (lyrics != null) {
                showLyrics(lyrics);
            } else {
                showLyricsState(status == LyricsRepository.STATUS_ERROR ? LyricsView.STATE_ERROR : LyricsView.STATE_NOT_FOUND);
            }
        });
    }

    private void showLyrics(Lyrics lyrics) {
        if (lyrics.instrumental) {
            showLyricsState(LyricsView.STATE_INSTRUMENTAL);
            return;
        }
        final String creditName = lyrics.getCreditName();
        final Lyrics shown = lyricsView.getState() == LyricsView.STATE_READY ? lyricsView.getLyrics() : null;
        final boolean replacing = shown != null && shown.synced && lyrics.synced;
        final int subtitleMode = ExteraConfig.getLyricsSubtitle();
        int subtitle = LyricsView.SUBTITLE_NONE;
        if (subtitleMode == LyricsView.SUBTITLE_TRANSLATION && lyrics.hasTranslationFor(LocaleController.getInstance().getCurrentLocale())) {
            subtitle = LyricsView.SUBTITLE_TRANSLATION;
        } else if (subtitleMode == LyricsView.SUBTITLE_ROMANIZATION && lyrics.hasRomanization) {
            subtitle = LyricsView.SUBTITLE_ROMANIZATION;
        }
        lyricsView.setLyrics(lyrics, creditName != null ? formatString(R.string.LyricsCredit, creditName) : null, subtitle);
        final MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
        if (messageObject != null) {
            lyricsView.setPosition(getPositionMs(messageObject), !MediaController.getInstance().isMessagePaused());
            if (!replacing) {
                lyricsView.post(lyricsView::jumpToCurrent);
            }
        }
    }

    private void showLyricsState(int state) {
        switch (state) {
            case LyricsView.STATE_OFFER:
                lyricsView.setState(state, getString(R.string.LyricsOfferTitle), getString(R.string.LyricsOfferText), getString(R.string.LyricsOfferButton));
                break;
            case LyricsView.STATE_NOT_FOUND:
                lyricsView.setState(state, getString(R.string.LyricsNotFoundTitle), getString(ExteraConfig.getLyricsOnline() ? R.string.LyricsNotFoundText : R.string.LyricsNotFoundTextOffline), null);
                break;
            case LyricsView.STATE_INSTRUMENTAL:
                lyricsView.setState(state, getString(R.string.LyricsInstrumentalTitle), getString(R.string.LyricsInstrumentalText), null);
                break;
            case LyricsView.STATE_ERROR:
                lyricsView.setState(state, getString(R.string.LyricsErrorTitle), getString(R.string.LyricsErrorText), getString(R.string.Retry));
                break;
            default:
                lyricsView.setState(state, null, null, null);
                break;
        }
    }

    private static long getPositionMs(MessageObject messageObject) {
        final long position = MediaController.getInstance().getProgressMs(messageObject);
        if (position >= 0) {
            return position;
        }
        if (messageObject.audioProgressMs > 0) {
            return messageObject.audioProgressMs;
        }
        return (long) (messageObject.audioProgress * messageObject.getDuration() * 1000);
    }

    private void onCoverBitmapLoaded(MessageObject messageObject, ImageReceiver receiver) {
        if (messageObject == null || receiver == null) {
            return;
        }
        if (!ExteraConfig.getPlayerCoverColors() || !receiver.hasBitmapImage()) {
            return;
        }
        CoverColorExtractor.extract(messageObject, receiver, seed -> {
            if (lastMessageObject == messageObject) {
                updateCoverColors(true);
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.playerColorsChanged);
            }
        });
    }

    private void updateCoverColors(boolean animated) {
        if (coverColorsWait.shouldWait(lastMessageObject)) {
            return;
        }
        final boolean dark = themeColors.dark || canvasShown && mode == MODE_COVER;
        PlayerColors target = dark == themeColors.dark ? themeColors : PlayerColors.fromSeed(themeColors.accent, true);
        if (ExteraConfig.getPlayerCoverColors() && lastMessageObject != null) {
            final Integer seed = CoverColorExtractor.getCachedSeed(lastMessageObject);
            if (seed != null && seed != CoverColorExtractor.NO_SEED) {
                target = PlayerColors.fromSeed(seed, dark);
                final int[] glow = CoverColorExtractor.getCachedGlow(lastMessageObject);
                if (glow != null) {
                    target.setCoverGlow(glow);
                }
            }
        }
        animateColors(target, animated);
    }

    private void animateColors(PlayerColors target, boolean animated) {
        if (targetColors != null && targetColors.equalsTo(target) || targetColors == null && colors.equalsTo(target)) {
            return;
        }
        targetColors = target;
        if (settingsSheet != null) {
            settingsSheet.updateColors(target.wrapSettings(baseResourcesProvider));
        }
        if (colorAnimator != null) {
            colorAnimator.cancel();
            colorAnimator = null;
        }
        if (!animated || !playerLayout.isAttachedToWindow()) {
            colors.set(target);
            applyColors(colors);
            return;
        }
        final PlayerColors from = colors.copy();
        colorAnimator = ValueAnimator.ofFloat(0f, 1f);
        colorAnimator.setDuration(COLOR_DURATION_MS);
        colorAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        colorAnimator.addUpdateListener(a -> {
            colors.blend(from, target, (float) a.getAnimatedValue());
            applyColors(colors);
        });
        colorAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (colorAnimator == animation) {
                    colorAnimator = null;
                    colors.set(target);
                    applyColors(colors);
                }
            }
        });
        colorAnimator.start();
    }

    private void applyColors(PlayerColors c) {
        setBackgroundColor(c.background);
        updateNavigationBar(c);
        collapseButton.setColorFilter(new PorterDuffColorFilter(c.icon, PorterDuff.Mode.SRC_IN));
        moreButton.setColorFilter(new PorterDuffColorFilter(c.icon, PorterDuff.Mode.SRC_IN));
        collapseBackground.setColors(c.icon, 0);
        moreBackground.setColors(c.icon, 0);
        headerTitle.setTextColor(c.text);
        headerSubtitle.setTextColor(c.textSecondary);
        for (int i = 0; i < 2; i++) {
            final TextView title = i == 0 ? titleView.getTextView() : titleView.getNextTextView();
            final TextView artist = i == 0 ? artistView.getTextView() : artistView.getNextTextView();
            if (title != null) {
                title.setTextColor(c.text);
            }
            if (artist != null) {
                artist.setTextColor(c.textSecondary);
                artist.setBackground(Theme.createRadSelectorDrawable(ColorUtils.setAlphaComponent(c.text, 0x14), ARTIST_RADIUS_DP, ARTIST_RADIUS_DP));
            }
        }
        coverView.setColors(c.tonal, c.onTonal);
        seekBar.setColors(c.wave, c.track, c.buffer, ColorUtils.setAlphaComponent(c.text, 0xF0), c.background);
        timeView.setTextColor(c.textSecondary);
        durationView.setTextColor(c.textSecondary);
        controlsView.setColors(c.tonal, c.onTonal, c.accent, c.onAccent);
        lyricsView.setColors(c.text, c.textSecondary, c.accent, c.onAccent);
        queueView.setColors(c);
        rhythmBackground.setColors(c.glow, c.background, c.dark);
        canvasBackground.setScrimColor(c.background);
        if (speedHintView != null) {
            speedHintView.setTextColor(c.background);
            speedHintView.setBgColor(c.text);
        }
        updateToggleColors();
        updateStatusBar();
        playerLayout.invalidate();
    }

    private void updateNavigationBar(PlayerColors c) {
        setOverlayNavBarColor(ColorUtils.setAlphaComponent(c.background, 0));
        AndroidUtilities.setLightNavigationBar(this, !c.dark);
    }

    private void updateStatusBar() {
        final boolean light = playerLayout.fullscreen && !colors.dark && playerLayout.getTranslationY() < getStatusBarHeight();
        if (lightStatusBar != light) {
            lightStatusBar = light;
            AndroidUtilities.setLightStatusBar(getWindow(), light);
            AndroidUtilities.setLightStatusBar(container, light);
        }
    }

    private ExpressiveStateDrawable createToggle() {
        return new ExpressiveStateDrawable(8).setSelectedRadius(ICON_BUTTON_DP / 2f);
    }

    private void onToggleProgress() {
        applyToggle(shuffleButton, shuffleBackground, colors, true);
        applyToggle(repeatButton, repeatBackground, colors, true);
        applyToggle(profileButton, profileBackground, colors, false);
        applySpeedColors(colors);
    }

    private void updateToggleColors() {
        final PlayerColors c = colors;
        applyToggle(shuffleButton, shuffleBackground, c, true);
        applyToggle(repeatButton, repeatBackground, c, true);
        applyModeSegment(lyricsButton, lyricsBackground, c);
        applyModeSegment(queueButton, queueBackground, c);
        applyToggle(profileButton, profileBackground, c, false);
        applySpeedColors(c);
    }

    private void applySpeedColors(PlayerColors c) {
        final int content = ColorUtils.blendARGB(c.icon, c.accent, speedBackground.getSelectProgress());
        final int outline = speedMenuShown ? 0 : ColorUtils.blendARGB(ColorUtils.setAlphaComponent(c.textSecondary, 0x80), c.accent, speedBackground.getSelectProgress());
        speedBackground.setColors(content, 0).setOutline(outline, SPEED_OUTLINE_DP);
        speedIcon.setColor(content);
        speedButton.invalidate();
    }

    private static void applyToggle(ImageView view, ExpressiveStateDrawable background, PlayerColors c, boolean dot) {
        final int content = ColorUtils.blendARGB(c.icon, c.accent, background.getSelectProgress());
        setIconTint(view, background, content);
        background.setColors(content, 0).setDot(dot ? c.accent : 0, TOGGLE_DOT_DP);
    }

    private static void setIconTint(ImageView view, ExpressiveStateDrawable background, int content) {
        if (background.getContentColor() != content || view.getColorFilter() == null) {
            view.setColorFilter(new PorterDuffColorFilter(content, PorterDuff.Mode.SRC_IN));
        }
    }

    public void updateOrderButtons(boolean animated) {
        final int repeatMode = SharedConfig.repeatMode;
        shuffleBackground.setSelected(SharedConfig.shuffleMusic, animated);
        shuffleButton.setContentDescription(getString(R.string.ShuffleList));
        repeatBackground.setSelected(repeatMode != 0, animated);
        repeatButton.setImageResource(repeatMode == 2 ? R.drawable.player_new_repeatone : R.drawable.player_new_repeatall);
        repeatButton.setContentDescription(getString(repeatMode == 0 ? R.string.AccDescrRepeatOff : repeatMode == 1 ? R.string.AccDescrRepeatList : R.string.AccDescrRepeatOne));
        queueView.setReversed(SharedConfig.playOrderReversed, animated);
        updateToggleColors();
        updateHeader();
    }

    private void updateSpeedButtons(boolean animated) {
        final float speed = MediaController.getInstance().getPlaybackSpeed(true);
        speedIcon.setValue(speed, animated);
        final boolean changed = Math.abs(speed - 1f) > 0.05f;
        speedBackground.setSelected(changed, animated);
        final String description = formatString(R.string.PlayerSpeedDescription, SpeedIconDrawable.formatNumber(speed));
        speedButton.setContentDescription(description);
        updateToggleColors();
    }

    private void updateProfileState(boolean animated) {
        final MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
        final boolean inProfile = messageObject != null && actions.isInProfile(messageObject);
        profileBackground.setSelected(inProfile, animated);
        profileButton.setImageResource(inProfile ? R.drawable.media_like_active : R.drawable.media_like);
        profileButton.setContentDescription(getString(inProfile ? R.string.AudioRemoveFromProfile : R.string.AudioAddToProfile));
        profileButton.setEnabled(!actions.isProfileIdsLoading());
        final boolean visible = messageObject != null && !noforwards && messageObject.getDocument() != null && !PlayerActions.isServiceTrack(messageObject);
        profileButton.setVisibility(visible ? View.VISIBLE : View.GONE);
        updateToggleColors();
    }

    private void updateHeader() {
        final MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
        final MessagesController.SavedMusicList list = savedMusicList;
        CharSequence title = getString(R.string.AttachMusic);
        if (list != null) {
            if (list.dialogId == UserConfig.getInstance(currentAccount).getClientUserId()) {
                title = getString(R.string.ProfilePlaylistTitleMine);
            } else {
                title = formatString(R.string.ProfilePlaylistTitle, DialogObject.getShortName(list.dialogId));
            }
        } else if (messageObject != null && !MediaController.getInstance().currentPlaylistIsGlobalSearch()) {
            final CharSequence chatTitle = getChatTitle(messageObject);
            if (chatTitle != null) {
                title = chatTitle;
            }
        }
        headerTitle.setText(title);
        CharSequence subtitle = null;
        if (messageObject != null) {
            final ArrayList<MessageObject> playlist = MediaController.getInstance().getPlaylist();
            final int index = playlist.indexOf(messageObject);
            final long dialogId = list == null && !MediaController.getInstance().currentPlaylistIsGlobalSearch() ? messageObject.getDialogId() : 0;
            if (dialogId != musicCountDialogId) {
                musicCountDialogId = dialogId;
                musicCount = 0;
                if (dialogId != 0) {
                    MediaDataController.getInstance(currentAccount).getMediaCount(dialogId, 0, MediaDataController.MEDIA_MUSIC, 0, false);
                }
            }
            final int total = Math.max(playlist.size(), musicCount);
            if (index >= 0 && total > 1) {
                final boolean direct = (list == null) == SharedConfig.playOrderReversed;
                final int number = direct ? total - playlist.size() + index + 1 : playlist.size() - index;
                subtitle = formatString(R.string.PlayerTrackOf, number, total);
            }
        }
        headerSubtitle.setText(subtitle);
        final int subtitleVisibility = TextUtils.isEmpty(subtitle) ? View.GONE : View.VISIBLE;
        if (headerSubtitle.getVisibility() != subtitleVisibility) {
            headerSubtitle.setVisibility(subtitleVisibility);
            playerLayout.requestLayout();
        }
    }

    private CharSequence getChatTitle(MessageObject messageObject) {
        long did = messageObject.getDialogId();
        if (did == UserConfig.getInstance(currentAccount).getClientUserId()) {
            return getString(messageObject.getSavedDialogId() == UserObject.ANONYMOUS ? R.string.AnonymousForward : R.string.SavedMessages);
        }
        if (DialogObject.isEncryptedDialog(did)) {
            final TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance(currentAccount).getEncryptedChat(DialogObject.getEncryptedChatId(did));
            if (encryptedChat == null) {
                return null;
            }
            did = encryptedChat.user_id;
        }
        final String name = DialogObject.getName(currentAccount, did);
        return TextUtils.isEmpty(name) ? null : name;
    }

    private void bindMessage(boolean shutdown, boolean animated) {
        final MediaController controller = MediaController.getInstance();
        final MessageObject messageObject = controller.getPlayingMessageObject();
        if (messageObject == null && shutdown || messageObject != null && !messageObject.isMusic()) {
            dismiss();
            return;
        }
        if (messageObject == null) {
            lastMessageObject = null;
            return;
        }
        final boolean same = messageObject == lastMessageObject;
        final boolean animate = animated && !same && lastMessageObject != null;
        final int direction = pendingDirection != 0 ? pendingDirection : 1;
        pendingDirection = 0;
        lastMessageObject = messageObject;

        moreButton.setVisibility(messageObject.eventId != 0 || messageObject.getId() <= -2000000000 ? View.INVISIBLE : View.VISIBLE);
        noforwards = PlayerActions.isNoForwards(currentAccount, messageObject);

        checkIfMusicDownloaded(messageObject);
        updateProgress(messageObject);

        final boolean paused = controller.isMessagePaused();
        controlsView.setPlaying(!paused, animated);
        coverView.setPlaying(!paused, animated);
        seekBar.setPlaying(!paused, controller.getPlaybackSpeed(true), animated);
        updateRhythm();

        if (!same) {
            setImmersive(false, animate);
            coverView.setCover(messageObject, animate, direction);
            canvasSpring.cancel();
            canvasProgress = coverView.getCurrentCanvas();
            loadCanvas(animate ? direction : 0);
            loadMotionCover(messageObject);
            loadArtwork(messageObject);
            titleView.setText(messageObject.getMusicTitle(), animate);
            artistView.setText(messageObject.getMusicAuthor(), animate);
            PlayerCovers.preloadNeighbors(currentAccount);
            lastDuration = (int) messageObject.getDuration();
            lastTime = -1;
            updateProgress(messageObject);
            updateCoverColors(animated);
            if (mode == MODE_LYRICS) {
                loadLyrics(false);
            }
            queueView.updateCells();
        }
        updateProfileState(animated && same);
        updateHeader();
        moreIcon.setCasting(CastSync.isActive(), animated);
    }

    private void checkIfMusicDownloaded(MessageObject messageObject) {
        final File cacheFile = PlayerActions.getLocalFile(messageObject);
        final boolean canStream = SharedConfig.streamMedia && messageObject.getDialogId() != 0 && !DialogObject.isEncryptedDialog(messageObject.getDialogId()) && messageObject.isMusic();
        final String fileName = messageObject.getFileName();
        currentFile = FileLoader.getAttachFileName(messageObject.getDocument());
        currentAudioFinishedLoading = cacheFile.exists() || PlayerCovers.getPlayingAudioInfo(messageObject) != null;
        if (!cacheFile.exists() && !canStream) {
            DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, this);
            final Float progress = ImageLoader.getInstance().getFileProgress(fileName);
            final float p = progress != null ? progress : 0f;
            seekBar.setLoading(true, p);
            controlsView.setLoading(true);
            controlsView.setPlayEnabled(false);
        } else {
            DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
            seekBar.setLoading(false, 0f);
            controlsView.setLoading(false);
            controlsView.setPlayEnabled(true);
        }
    }

    private void updateProgress(MessageObject messageObject) {
        final MediaController controller = MediaController.getInstance();
        final int newTime;
        if (seekBar.isDragging()) {
            newTime = messageObject.audioProgressSec;
        } else {
            final boolean updateRewinding = rewindingProgress >= 0 && (rewindingState == -1 || rewindingState == 1 && controller.isMessagePaused());
            seekBar.setProgress(updateRewinding ? rewindingProgress : messageObject.audioProgress);
            updateBuffer(messageObject);
            if (updateRewinding) {
                newTime = (int) (messageObject.getDuration() * rewindingProgress);
                messageObject.audioProgressSec = newTime;
            } else {
                newTime = messageObject.audioProgressSec;
            }
        }
        if (lastTime != newTime) {
            lastTime = newTime;
            timeView.setText(AndroidUtilities.formatShortDuration(newTime));
            if (lastDuration <= 0) {
                durationView.setText("-:--");
            } else if (showRemaining) {
                durationView.setText(formatString(R.string.PlayerTimeRemaining, AndroidUtilities.formatShortDuration(Math.max(0, lastDuration - newTime))));
            } else {
                durationView.setText(AndroidUtilities.formatShortDuration(lastDuration));
            }
        }
        if (mode == MODE_LYRICS || lyricsAlpha > 0) {
            lyricsView.setPosition(getPositionMs(messageObject), !controller.isMessagePaused());
        }
    }

    private void updateBuffer(MessageObject messageObject) {
        float bufferedProgress;
        if (currentAudioFinishedLoading) {
            bufferedProgress = 1f;
        } else {
            final long time = SystemClock.elapsedRealtime();
            if (Math.abs(time - lastBufferedPositionCheck) >= 500) {
                bufferedProgress = MediaController.getInstance().isStreamingCurrentAudio() ? FileLoader.getInstance(currentAccount).getBufferedProgressFromPosition(messageObject.audioProgress, currentFile) : 1f;
                lastBufferedPositionCheck = time;
            } else {
                bufferedProgress = -1;
            }
        }
        if (bufferedProgress != -1) {
            bufferSpring.getSpring().setFinalPosition(bufferedProgress * 1000);
            bufferSpring.start();
        }
    }

    @Override
    public int getLeftInset() {
        return tabletSheet ? super.getLeftInset() : 0;
    }

    @Override
    public int getRightInset() {
        return tabletSheet ? super.getRightInset() : 0;
    }

    private boolean updateTabletSheet(Configuration configuration) {
        final boolean value = AndroidUtilities.isTablet() && configuration.screenHeightDp >= TABLET_SHEET_MIN_HEIGHT_DP;
        fullWidth = AndroidUtilities.isTablet() && !value;
        if (tabletSheet == value) {
            return false;
        }
        tabletSheet = value;
        return true;
    }

    private int getSafeLeftInset() {
        return super.getLeftInset();
    }

    private int getSafeRightInset() {
        return super.getRightInset();
    }

    @Override
    protected int getBottomSheetWidth(boolean isPortrait, int width, int height) {
        return tabletSheet ? super.getBottomSheetWidth(isPortrait, width, height) : width;
    }

    @Override
    public boolean dispatchTouchEvent(@NonNull MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN && containerView != null) {
            swipeDismissAllowed = isSwipeDismissArea(event.getX() - containerView.getX(), event.getY() - containerView.getY());
        }
        return super.dispatchTouchEvent(event);
    }

    private boolean isSwipeDismissArea(float x, float y) {
        final PlayerLayout layout = playerLayout;
        if (y < 0) {
            return false;
        }
        if (layout.keyboardLayout) {
            return y < layout.headerBottom;
        }
        if (mode != MODE_COVER && y >= layout.stageTop && y < layout.stageBottom && x >= layout.stageLeft && x < layout.stageLeft + layout.stageWidth) {
            return false;
        }
        return Math.abs(y - layout.lineCenter - getImmersiveShift()) > dp(SEEK_TOUCH_HALF_DP) || x < layout.paneLeft || x >= layout.paneLeft + layout.paneWidth;
    }

    @Override
    protected boolean canDismissWithSwipe() {
        return swipeDismissAllowed;
    }

    private void applyTransitions() {
        final PlayerLayout layout = playerLayout;
        if (layout.contentWidth <= 0) {
            return;
        }
        final float m = modeProgress;
        final float c = 1f - (1f - m) * (1f - canvasTitleProgress);
        final float compactLeft = layout.paneLeft + dp(SIDE_DP);
        final float compactSize = layout.compactCoverSize;
        final float hide = layout.keyboardLayout ? m : 0f;
        final float coverLeft = lerp(layout.coverLeft, compactLeft, m);
        final float coverTop = lerp(layout.coverTop, layout.titleTop, m);
        final float coverSize = lerp(layout.coverSize, compactSize, m);
        final float coverRadius = lerp(dp(COVER_RADIUS_DP), dp(COMPACT_COVER_RADIUS_DP), m);
        coverView.setCoverRect(coverLeft, coverTop, coverSize, coverRadius, m);
        final float immersiveShift = getImmersiveShift();
        canvasBackground.setScrimShift(immersiveShift);
        final float hidden = 1f - Utilities.clamp01(immersiveProgress);
        final float hiddenShift = dp(24) * Utilities.clamp01(immersiveProgress);
        coverView.setCanvasLayout(compactLeft, layout.titleTop + immersiveShift, compactSize, dp(COMPACT_COVER_RADIUS_DP), Math.max(m, Utilities.clamp01(canvasTitleProgress)), layout.getMeasuredWidth() - backgroundPaddingLeft * 2);
        coverView.setCurrentCanvas(canvasProgress);
        coverView.setPagerClipRight(layout.landscape ? layout.paneLeft : -1f);
        coverView.setHideProgress(hide);

        final float textAlpha = 1f - hide;
        final float shift = (compactSize + dp(COMPACT_TEXT_GAP_DP)) * c;
        final int shiftPx = Math.round(shift);
        setWidth(titleView, layout.textWidth - shiftPx);
        titleView.setTranslationX(shift);
        titleView.setTranslationY(immersiveShift);
        titleView.setAlpha(textAlpha);
        titleView.setVisibility(textAlpha < 0.01f ? View.INVISIBLE : View.VISIBLE);
        setWidth(artistView, layout.textWidth - shiftPx + dp(ARTIST_PADDING_X_DP) * 2);
        artistView.setTranslationX(shift);
        artistView.setTranslationY(immersiveShift);
        artistView.setAlpha(textAlpha);
        artistView.setVisibility(textAlpha < 0.01f ? View.INVISIBLE : View.VISIBLE);
        profileButton.setTranslationY(immersiveShift);
        profileButton.setAlpha(textAlpha);

        final float stageShift = dp(16);
        setPanel(lyricsView, lyricsAlpha * smooth(m), (1f - lyricsAlpha) * stageShift);
        setPanel(queueView, queueAlpha * smooth(m), (1f - queueAlpha) * stageShift);

        setPanel(shuffleButton, hidden, hiddenShift);
        setPanel(repeatButton, hidden, hiddenShift);
        final float toolbarAlpha = layout.keyboardLayout ? 0f : hidden;
        setPanel(speedButton, toolbarAlpha, hiddenShift);
        setPanel(lyricsButton, toolbarAlpha, hiddenShift);
        setPanel(queueButton, toolbarAlpha, hiddenShift);
        final float bottomAlpha = layout.keyboardLayout ? 0f : 1f;
        setPanel(seekBar, bottomAlpha, immersiveShift);
        setPanel(timeView, bottomAlpha * hidden, immersiveShift);
        setPanel(durationView, bottomAlpha * hidden, immersiveShift);
        setPanel(controlsView, bottomAlpha * hidden, hiddenShift);
        if (layout.keyboardLayout) {
            setPanel(shuffleButton, 0f, 0);
            setPanel(repeatButton, 0f, 0);
        }
        layout.invalidate();
    }

    private boolean canBeImmersive() {
        return canvasShown && mode == MODE_COVER && !playerLayout.landscape && !playerLayout.keyboardLayout;
    }

    private void setImmersive(boolean value, boolean animated) {
        immersive = value && canBeImmersive();
        final float target = immersive ? 1f : 0f;
        if (animated) {
            if (immersiveProgress != target && (!immersiveSpring.isRunning() || immersiveSpring.getSpring().getFinalPosition() != target)) {
                Material3Tokens.animate(immersiveSpring, immersiveProgress, target);
            }
        } else {
            immersiveSpring.cancel();
            if (immersiveProgress != target) {
                immersiveProgress = target;
                applyTransitions();
            }
        }
    }

    private float getImmersiveShift() {
        final PlayerLayout layout = playerLayout;
        final float target = layout.toolbarTop + dp(TOOLBAR_HEIGHT_DP) / 2f;
        return Math.max(0f, target - layout.lineCenter) * immersiveProgress;
    }

    private static void setWidth(View view, int width) {
        width = Math.max(0, width);
        if (view.getWidth() == width && view.getMeasuredWidth() == width) {
            return;
        }
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(view.getMeasuredHeight(), View.MeasureSpec.EXACTLY));
        view.layout(view.getLeft(), view.getTop(), view.getLeft() + width, view.getTop() + view.getMeasuredHeight());
    }

    private static float smooth(float t) {
        t = Utilities.clamp01(t);
        return t * t * (3 - 2 * t);
    }

    private static void setPanel(View view, float alpha, float translationY) {
        alpha = Utilities.clamp01(alpha);
        view.setAlpha(alpha);
        view.setTranslationY(translationY);
        final int visibility = alpha < 0.01f ? View.INVISIBLE : View.VISIBLE;
        if (view.getVisibility() != visibility && view.getVisibility() != View.GONE) {
            view.setVisibility(visibility);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.messagePlayingDidStart || id == NotificationCenter.messagePlayingPlayStateChanged || id == NotificationCenter.messagePlayingDidReset) {
            bindMessage(id == NotificationCenter.messagePlayingDidReset && args.length > 1 && (Boolean) args[1], true);
            if (id == NotificationCenter.messagePlayingDidStart) {
                final MessageObject messageObject = (MessageObject) args[0];
                if (messageObject != null && messageObject.eventId != 0) {
                    return;
                }
            }
            queueView.updateCells();
            if (id == NotificationCenter.messagePlayingPlayStateChanged && MediaController.getInstance().getPlayingMessageObject() != null) {
                if (MediaController.getInstance().isMessagePaused()) {
                    startForwardRewindingSeek();
                } else if (rewindingState == 1 && rewindingProgress != -1f) {
                    AndroidUtilities.cancelRunOnUIThread(rewindSeek);
                    lastUpdateRewindingPlayerTime = 0;
                    rewindSeek.run();
                    rewindingProgress = -1f;
                }
            }
        } else if (id == NotificationCenter.messagePlayingProgressDidChanged || id == NotificationCenter.messagePlayingDidSeek) {
            final MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
            if (messageObject != null && messageObject.isMusic()) {
                updateProgress(messageObject);
            }
        } else if (id == NotificationCenter.messagePlayingSpeedChanged) {
            updateSpeedButtons(true);
            final MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
            if (messageObject != null) {
                seekBar.setPlaying(!MediaController.getInstance().isMessagePaused(), MediaController.getInstance().getPlaybackSpeed(true), true);
            }
        } else if (id == NotificationCenter.mediaCountDidLoad) {
            if ((Long) args[0] == musicCountDialogId && musicCountDialogId != 0 && (Long) args[1] == 0 && (Integer) args[4] == MediaDataController.MEDIA_MUSIC) {
                musicCount = (Integer) args[2];
                updateHeader();
            }
        } else if (id == NotificationCenter.musicDidLoad || id == NotificationCenter.moreMusicDidLoad) {
            savedMusicList = MediaController.getInstance().currentSavedMusicList;
            queueView.update();
            updateHeader();
        } else if (id == NotificationCenter.fileLoaded) {
            final String name = (String) args[0];
            if (name != null && name.equals(currentFile)) {
                currentAudioFinishedLoading = true;
                final MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
                if (messageObject != null) {
                    checkIfMusicDownloaded(messageObject);
                    coverView.refreshCover();
                    if (mode == MODE_LYRICS && lyricsView.getState() != LyricsView.STATE_READY) {
                        lyricsKey = null;
                        loadLyrics(false);
                    }
                }
            } else if (name != null) {
                PlayerCovers.onFileLoaded(name);
            }
        } else if (id == NotificationCenter.fileLoadProgressChanged) {
            final String name = (String) args[0];
            final MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
            if (name != null && name.equals(currentFile) && messageObject != null) {
                updateBuffer(messageObject);
            }
        } else if (id == NotificationCenter.musicIdsLoaded) {
            updateProfileState(true);
        }
    }

    @Override
    public void onFailedDownload(String fileName, boolean canceled) {
    }

    @Override
    public void onSuccessDownload(String fileName) {
    }

    @Override
    public void onProgressDownload(String fileName, long downloadedSize, long totalSize) {
        final float progress = totalSize > 0 ? Math.min(1f, downloadedSize / (float) totalSize) : 0f;
        seekBar.setLoading(true, progress);
        controlsView.setLoading(true);
    }

    @Override
    public void onProgressUpload(String fileName, long uploadedSize, long totalSize, boolean isEncrypted) {
    }

    @Override
    public int getObserverTag() {
        return TAG;
    }

    @Override
    public void show() {
        super.show();
        instance = this;
        updateRhythm();
    }

    private void updateRhythm() {
        final boolean active = ExteraConfig.getPlayerRhythmBackground() && !tabletSheet && !isDismissed();
        final boolean visible = active && isShowing() && playerLayout.getWindowVisibility() == View.VISIBLE
                && LiteMode.isEnabled(LiteMode.FLAG_CHAT_BACKGROUND);
        final boolean animating = visible && !MediaController.getInstance().isMessagePaused();
        RhythmTap.setEnabled(visible);
        rhythmBackground.setActive(active);
        rhythmBackground.setAnimating(animating);
        rhythmBackground.setParticlesEnabled(ExteraConfig.getPlayerParticles() && LiteMode.isEnabled(LiteMode.FLAG_PARTICLES));
        playerLayout.invalidate();
    }

    @Override
    public void dismiss() {
        super.dismiss();
        if (observers != null) {
            observers.removeAllObservers();
            observers = null;
        }
        DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
        if (rewindingState != 0) {
            onRewindHold(rewindingState, false);
        }
        AndroidUtilities.cancelRunOnUIThread(rewindStepRunnable);
        AndroidUtilities.cancelRunOnUIThread(rewindSeek);
        seekPlateSpring.cancel();
        if (colorAnimator != null) {
            colorAnimator.cancel();
            colorAnimator = null;
        }
        coverColorsWait.cancel();
        RhythmTap.setEnabled(false);
        rhythmBackground.setAnimating(false);
        lyricsRequest++;
        motionRequest++;
        canvasSpring.cancel();
        canvasTitleSpring.cancel();
        immersiveSpring.cancel();
        CanvasRepository.setWantedKeys(Collections.emptySet());
        if (instance == this) {
            instance = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (mode == MODE_QUEUE && queueView.isSearchFocused()) {
            queueView.clearSearch();
            return;
        }
        if (mode != MODE_COVER) {
            selectMode(MODE_COVER);
            return;
        }
        super.onBackPressed();
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        final ArrayList<ThemeDescription> descriptions = new ArrayList<>();
        final ThemeDescription.ThemeDescriptionDelegate delegate = () -> {
            themeColors = PlayerColors.fromTheme(baseResourcesProvider);
            targetColors = null;
            updateCoverColors(false);
        };
        final int[] keys = {
            Theme.key_player_background, Theme.key_player_actionBarTitle, Theme.key_player_time, Theme.key_player_button,
            Theme.key_player_progress, Theme.key_player_progressBackground, Theme.key_featuredStickers_addButton,
            Theme.key_featuredStickers_buttonText, Theme.key_windowBackgroundWhite, Theme.key_dialogBackground
        };
        for (int key : keys) {
            descriptions.add(new ThemeDescription(null, 0, null, null, null, delegate, key));
        }
        return descriptions;
    }

    private class PlayerLayout extends FrameLayout {

        int contentLeft;
        int contentTop;
        int contentWidth;
        int paneLeft;
        int paneWidth;
        int stageLeft;
        int stageWidth;
        boolean landscape;
        float coverLeft;
        float coverTop;
        float coverSize;
        int headerBottom;
        int compactCoverSize;
        int textWidth;
        int stageTop;
        int stageBottom;
        int titleTop;
        int lineCenter;
        int timesTop;
        int transportTop;
        int toolbarTop;
        int modeTop;
        boolean fullscreen;
        boolean keyboardLayout;
        private float topRadius;
        private final RectF backgroundRect = new RectF();
        private final RectF rhythmRect = new RectF();
        private final RectF rhythmCoverRect = new RectF();
        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final HashSet<View> ownViews = new HashSet<>();

        PlayerLayout(Context context) {
            super(context);
            setWillNotDraw(false);
            setClipChildren(false);
        }

        @Override
        protected void onConfigurationChanged(Configuration newConfig) {
            super.onConfigurationChanged(newConfig);
            if (updateTabletSheet(newConfig)) {
                updateRhythm();
            }
            requestLayout();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            canvasBackground.onAttachedToWindow();
            Bulletin.addDelegate(this, new Bulletin.Delegate() {
                @Override
                public int getBottomOffset(int tag) {
                    return Math.max(0, getMeasuredHeight() - (keyboardLayout ? getMeasuredHeight() : toolbarTop));
                }
            });
        }

        @Override
        protected void onWindowVisibilityChanged(int visibility) {
            super.onWindowVisibilityChanged(visibility);
            updateRhythm();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            canvasBackground.onDetachedFromWindow();
            Bulletin.removeDelegate(this);
            if (isDismissed()) {
                controlsView.recycle();
            }
        }

        @Override
        public void setTranslationY(float translationY) {
            super.setTranslationY(translationY);
            final float radius = getTopRadius();
            if (radius != topRadius) {
                topRadius = radius;
                invalidate();
            }
            updateStatusBar();
        }

        private float getTopRadius() {
            if (!fullscreen) {
                return 0f;
            }
            return dp(CORNER_RADIUS_DP) * Utilities.clamp01(getTranslationY() / dp(CORNER_RADIUS_DP));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            final int width = MeasureSpec.getSize(widthMeasureSpec);
            final int maxHeight = MeasureSpec.getSize(heightMeasureSpec);
            fullscreen = !tabletSheet;
            final int height = fullscreen ? maxHeight : Math.max(dp(320), maxHeight - getStatusBarHeight() - dp(TABLET_TOP_GAP_DP));
            final int safeLeft = fullscreen ? getSafeLeftInset() : 0;
            final int safeRight = fullscreen ? getSafeRightInset() : 0;
            contentLeft = backgroundPaddingLeft + safeLeft;
            contentTop = fullscreen ? getStatusBarHeight() : backgroundPaddingTop + dp(TABLET_HEADER_TOP_DP);
            contentWidth = width - backgroundPaddingLeft * 2 - safeLeft - safeRight;
            final boolean imeVisible = isImeVisible();
            keyboardLayout = imeVisible && mode == MODE_QUEUE;
            final boolean wasLandscape = landscape;
            landscape = fullscreen && contentWidth > height;
            if (wasLandscape != landscape) {
                post(MusicPlayerSheet.this::syncCanvas);
            }
            computeGeometry(height - (imeVisible ? 0 : getNavigationBottomInset()));
            final int W = contentWidth;
            final int P = paneWidth;
            final int icon = dp(ICON_BUTTON_DP);
            measureExact(collapseButton, icon, icon);
            measureExact(moreButton, icon, icon);
            final int headerTextWidth = W - dp(SIDE_DP + ICON_BUTTON_DP + ICON_GAP_DP) * 2;
            measureExact(headerTitle, headerTextWidth, dp(22));
            measureExact(headerSubtitle, headerTextWidth, dp(18));
            measureExact(coverView, width, height);
            final int heartSpace = profileButton.getVisibility() != GONE ? dp(ICON_BUTTON_DP + ICON_GAP_DP) : 0;
            final int textWidth = P - dp(SIDE_DP) * 2 - heartSpace;
            this.textWidth = textWidth;
            measureExact(titleView, textWidth, dp(TITLE_HEIGHT_DP));
            measureExact(artistView, textWidth + dp(ARTIST_PADDING_X_DP) * 2, dp(ARTIST_HEIGHT_DP + ARTIST_PADDING_Y_DP * 2));
            measureExact(profileButton, icon, icon);
            measureExact(seekBar, P - dp(SIDE_DP) * 2, dp(SEEK_VIEW_HEIGHT_DP));
            measureExact(timeView, dp(120), dp(TIMES_HEIGHT_DP));
            measureExact(durationView, dp(120), dp(TIMES_HEIGHT_DP));
            measureExact(shuffleButton, icon, icon);
            measureExact(repeatButton, icon, icon);
            measureExact(controlsView, P - dp(SIDE_DP + ICON_BUTTON_DP + ICON_GAP_DP) * 2, dp(PlayerControlsView.getHeightDp()));
            measureExact(speedButton, dp(SPEED_BUTTON_WIDTH_DP), dp(ACTION_BUTTON_DP));
            measureExact(lyricsButton, dp(MODE_SEGMENT_WIDTH_DP), dp(MODE_SEGMENT_HEIGHT_DP));
            measureExact(queueButton, dp(MODE_SEGMENT_WIDTH_DP), dp(MODE_SEGMENT_HEIGHT_DP));
            final int stageHeight = Math.max(dp(64), stageBottom - stageTop);
            measureExact(lyricsView, stageWidth, stageHeight);
            measureExact(queueView, stageWidth, stageHeight);
            final int foreignWidthSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
            final int foreignHeightSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
            for (int i = 0, n = getChildCount(); i < n; i++) {
                final View child = getChildAt(i);
                if (child == speedHintView) {
                    measureExact(child, paneWidth, toolbarTop + dp((TOOLBAR_HEIGHT_DP - SPEED_OUTLINE_HEIGHT_DP) / 2 - SPEED_HINT_GAP_DP));
                } else if (child.getVisibility() != GONE && !ownViews.contains(child)) {
                    measureChildWithMargins(child, foreignWidthSpec, 0, foreignHeightSpec, 0);
                }
            }
            setMeasuredDimension(width, height);
        }

        private int getNavigationBottomInset() {
            final WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(this);
            if (insets == null) {
                return Math.max(0, getBottomInset());
            }
            return insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
        }

        private boolean isImeVisible() {
            final WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(this);
            return insets != null ? insets.isVisible(WindowInsetsCompat.Type.ime()) : keyboardVisible;
        }

        void addOwnView(View view) {
            ownViews.add(view);
            addView(view);
        }

        private void measureExact(View view, int width, int height) {
            view.measure(MeasureSpec.makeMeasureSpec(Math.max(0, width), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Math.max(0, height), MeasureSpec.EXACTLY));
        }

        private void computeGeometry(int height) {
            if (landscape) {
                computeLandscapeGeometry(height);
                return;
            }
            paneLeft = stageLeft = contentLeft;
            paneWidth = stageWidth = contentWidth;
            final int W = contentWidth;
            final int controlsHeight = dp(PlayerControlsView.getHeightDp());
            headerBottom = contentTop + dp(HEADER_HEIGHT_DP);
            stageTop = headerBottom + dp(COMPACT_GAP_DP);
            final int fullCover = W - dp(SIDE_DP) * 2;
            final int coverTopMin = headerBottom + dp(COVER_GAP_DP);
            final int belowCover = controlsHeight + dp(TITLE_GAP_DP + TITLE_BLOCK_DP + SEEK_GAP_DP + SEEK_HANDLE_HALF_DP + TIMES_GAP_DP + TIMES_HEIGHT_DP + TRANSPORT_GAP_DP + TOOLBAR_GAP_DP);
            final int toolbarBottom = height - dp(BOTTOM_PADDING_DP + TOOLBAR_HEIGHT_DP);
            final float slack = Math.max(0, toolbarBottom - coverTopMin - belowCover - fullCover);
            toolbarTop = Math.round(toolbarBottom - Math.min(slack * TOOLBAR_LIFT_FRACTION, dp(MAX_TOOLBAR_LIFT_DP)));
            coverSize = Math.max(0, Math.min(fullCover, toolbarTop - belowCover - coverTopMin));
            coverLeft = contentLeft + (W - coverSize) / 2f;
            int extra = Math.max(0, toolbarTop - Math.round(coverTopMin + coverSize) - belowCover);
            final int transportExtra = Math.min(extra, dp(TRANSPORT_GAP_EXTRA_DP));
            extra -= transportExtra;
            final int toolbarExtra = Math.min(extra, dp(TOOLBAR_GAP_EXTRA_DP));
            transportTop = toolbarTop - dp(TOOLBAR_GAP_DP) - toolbarExtra - controlsHeight;
            timesTop = transportTop - dp(TRANSPORT_GAP_DP) - transportExtra - dp(TIMES_HEIGHT_DP);
            lineCenter = timesTop - dp(TIMES_GAP_DP);
            titleTop = lineCenter - dp(SEEK_HANDLE_HALF_DP + SEEK_GAP_DP + TITLE_BLOCK_DP);
            coverTop = headerBottom + (titleTop - headerBottom - coverSize) / 2f;
            compactCoverSize = dp(TITLE_BLOCK_DP);
            stageBottom = keyboardLayout ? height - dp(STAGE_BOTTOM_GAP_DP) : titleTop - dp(STAGE_BOTTOM_GAP_DP);
        }

        private void computeLandscapeGeometry(int height) {
            final int stagePaneWidth = Math.round(contentWidth * LANDSCAPE_STAGE_FRACTION);
            paneLeft = contentLeft + stagePaneWidth;
            paneWidth = contentWidth - stagePaneWidth;
            stageLeft = contentLeft;
            stageWidth = keyboardLayout ? contentWidth : stagePaneWidth;
            final int controlsHeight = dp(PlayerControlsView.getHeightDp());
            headerBottom = contentTop + dp(HEADER_HEIGHT_DP);
            stageTop = headerBottom + dp(COMPACT_GAP_DP);
            final int bottom = height - dp(BOTTOM_PADDING_DP);
            toolbarTop = bottom - dp(TOOLBAR_HEIGHT_DP);
            transportTop = toolbarTop - dp(TOOLBAR_GAP_DP) - controlsHeight;
            timesTop = transportTop - dp(TRANSPORT_GAP_DP) - dp(TIMES_HEIGHT_DP);
            lineCenter = timesTop - dp(TIMES_GAP_DP);
            titleTop = lineCenter - dp(SEEK_HANDLE_HALF_DP + SEEK_GAP_DP + TITLE_BLOCK_DP);
            final int lift = Math.max(0, (titleTop - headerBottom) / 2);
            titleTop -= lift;
            lineCenter -= lift;
            timesTop -= lift;
            transportTop -= lift;
            toolbarTop -= lift;
            final int coverArea = bottom - headerBottom;
            coverSize = Math.max(0, Math.min(stagePaneWidth - dp(SIDE_DP) * 2, coverArea - dp(COVER_GAP_DP)));
            coverLeft = contentLeft + (stagePaneWidth - coverSize) / 2f;
            coverTop = headerBottom + (coverArea - coverSize) / 2f;
            compactCoverSize = dp(TITLE_BLOCK_DP);
            stageBottom = height - dp(STAGE_BOTTOM_GAP_DP);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            super.onLayout(changed, l, t, r, b);
            final int headerLeft = contentLeft + dp(SIDE_DP);
            final int headerRight = contentLeft + contentWidth - dp(SIDE_DP);
            final int left = paneLeft + dp(SIDE_DP);
            final int right = paneLeft + paneWidth - dp(SIDE_DP);
            final int icon = dp(ICON_BUTTON_DP);
            final int headerButtonTop = contentTop + (dp(HEADER_HEIGHT_DP) - icon) / 2;
            place(collapseButton, headerLeft, headerButtonTop);
            place(moreButton, headerRight - icon, headerButtonTop);
            final int textLeft = headerLeft + icon + dp(ICON_GAP_DP);
            if (headerSubtitle.getVisibility() == GONE) {
                place(headerTitle, textLeft, contentTop + (dp(HEADER_HEIGHT_DP) - headerTitle.getMeasuredHeight()) / 2);
            } else {
                final int block = headerTitle.getMeasuredHeight() + headerSubtitle.getMeasuredHeight();
                final int textTop = contentTop + (dp(HEADER_HEIGHT_DP) - block) / 2;
                place(headerTitle, textLeft, textTop);
                place(headerSubtitle, textLeft, textTop + headerTitle.getMeasuredHeight());
            }
            place(coverView, 0, 0);
            place(titleView, left, titleTop);
            place(artistView, left - dp(ARTIST_PADDING_X_DP), titleTop + dp(ARTIST_TOP_DP - ARTIST_PADDING_Y_DP));
            place(profileButton, right - icon, titleTop + (dp(TITLE_BLOCK_DP) - icon) / 2);
            place(seekBar, left, lineCenter + dp(SEEK_LINE_BOTTOM_DP) - dp(SEEK_VIEW_HEIGHT_DP));
            place(timeView, left, timesTop);
            place(durationView, right - durationView.getMeasuredWidth(), timesTop);
            final int sideTop = transportTop + (controlsView.getMeasuredHeight() - icon) / 2;
            place(shuffleButton, left, sideTop);
            place(repeatButton, right - icon, sideTop);
            place(controlsView, left + icon + dp(ICON_GAP_DP), transportTop);
            modeTop = toolbarTop + (dp(TOOLBAR_HEIGHT_DP) - dp(MODE_SEGMENT_HEIGHT_DP)) / 2;
            layoutModeSegments();
            final int action = dp(ACTION_BUTTON_DP);
            final int actionTop = toolbarTop + (dp(TOOLBAR_HEIGHT_DP) - action) / 2;
            place(speedButton, left, actionTop);
            if (speedHintView != null) {
                place(speedHintView, paneLeft, 0);
            }
            place(lyricsView, stageLeft, stageTop);
            place(queueView, stageLeft, stageTop);
            applyTransitions();
        }

        void layoutModeSegments() {
            final int right = paneLeft + paneWidth - dp(SIDE_DP);
            final int height = dp(MODE_SEGMENT_HEIGHT_DP);
            final int lyricsWidth = dp(MODE_SEGMENT_WIDTH_DP) + Math.round(dp(MODE_PRESS_GROWTH_DP) * Math.max(0, lyricsBackground.getPressProgress()));
            final int queueWidth = dp(MODE_SEGMENT_WIDTH_DP) + Math.round(dp(MODE_PRESS_GROWTH_DP) * Math.max(0, queueBackground.getPressProgress()));
            queueButton.layout(right - queueWidth, modeTop, right, modeTop + height);
            final int lyricsRight = right - queueWidth - dp(MODE_SEGMENT_GAP_DP);
            lyricsButton.layout(lyricsRight - lyricsWidth, modeTop, lyricsRight, modeTop + height);
        }

        private void place(View view, int x, int y) {
            view.layout(x, y, x + view.getMeasuredWidth(), y + view.getMeasuredHeight());
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            if (fullscreen) {
                final float radius = getTopRadius();
                backgroundPaint.setColor(colors.background);
                backgroundRect.set(backgroundPaddingLeft, 0, getMeasuredWidth() - backgroundPaddingLeft, getMeasuredHeight() + radius);
                canvas.drawRoundRect(backgroundRect, radius, radius, backgroundPaint);
                final float coverMode = 1f - Utilities.clamp01(modeProgress);
                final float canvasAlpha = Utilities.clamp01(canvasProgress) * coverMode;
                final float pageOffset = coverView.getPageOffset();
                if (rhythmBackground.isVisible() && (canvasAlpha < 1f || pageOffset != 0f)) {
                    final float rhythmOffset = coverView.getRhythmOffset();
                    rhythmRect.set(backgroundRect);
                    rhythmRect.offset(rhythmOffset, 0);
                    rhythmCoverRect.set(coverView.getCoverRect());
                    rhythmCoverRect.offset(rhythmOffset, 0);
                    canvas.save();
                    canvas.clipRect(backgroundRect);
                    if (rhythmBackground.draw(canvas, rhythmRect, radius, rhythmCoverRect, coverView.getCoverRadius(), 1f - coverView.getHideProgress(), coverView.getCompactProgress())) {
                        postInvalidateOnAnimation();
                    }
                    canvas.restore();
                }
                if (canvasBackground.draw(canvas, backgroundRect, radius, coverMode, canvasProgress, pageOffset, coverView.getPageWidth(), coverView.getSideCanvas(-1), coverView.getSideCanvas(1))) {
                    postInvalidateOnAnimation();
                }
            } else {
                shadowDrawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight() + dp(24));
                shadowDrawable.draw(canvas);
            }
        }

        @Override
        protected void dispatchDraw(@NonNull Canvas canvas) {
            super.dispatchDraw(canvas);
            if (seekPlate > 0.01f && seekSpeedDrawable.isShown()) {
                final int anchor = transportTop - dp(SEEK_PLATE_GAP_DP);
                final int drawTop = anchor - dp(SEEK_PLATE_BOTTOM_DP);
                final float progress = Utilities.clamp01(seekPlate);
                final float scale = 0.6f + 0.4f * seekPlate;
                canvas.save();
                canvas.translate(0, dp(SEEK_PLATE_SHIFT_DP) * (1f - seekPlate));
                canvas.scale(scale, scale, paneLeft + paneWidth / 2f, anchor);
                canvas.saveLayerAlpha(paneLeft, drawTop, paneLeft + paneWidth, drawTop + dp(SEEK_SPEED_HEIGHT_DP), (int) (0xFF * progress));
                seekSpeedDrawable.setBounds(paneLeft, drawTop, paneLeft + paneWidth, drawTop + dp(SEEK_SPEED_HEIGHT_DP));
                seekSpeedDrawable.draw(canvas);
                canvas.restore();
                canvas.restore();
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (isDismissed()) {
                return false;
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    tapTracking = canBeImmersive() && event.getY() > headerBottom && event.getY() < titleTop + getImmersiveShift();
                    tapX = event.getX();
                    tapY = event.getY();
                    if (tapTracking) {
                        return true;
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (tapTracking) {
                        final int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
                        if (Math.abs(event.getX() - tapX) > slop || Math.abs(event.getY() - tapY) > slop) {
                            tapTracking = false;
                        }
                        return true;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (tapTracking) {
                        tapTracking = false;
                        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                            setImmersive(!immersive, true);
                        }
                        return true;
                    }
                    break;
            }
            return super.onTouchEvent(event);
        }

        private boolean tapTracking;
        private float tapX;
        private float tapY;
    }
}
