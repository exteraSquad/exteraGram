/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.player.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Shader;
import android.os.SystemClock;
import android.text.Layout;
import android.text.TextPaint;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.core.widget.NestedScrollView;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import com.exteragram.messenger.player.lyrics.Lyrics;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;

@SuppressLint("ViewConstructor")
public class LyricsView extends FrameLayout {

    public static final int STATE_EMPTY = 0;
    public static final int STATE_OFFER = 1;
    public static final int STATE_LOADING = 2;
    public static final int STATE_READY = 3;
    public static final int STATE_NOT_FOUND = 4;
    public static final int STATE_INSTRUMENTAL = 5;
    public static final int STATE_ERROR = 6;

    public static final int PROGRESS_NONE = 0;
    public static final int PROGRESS_HORIZONTAL = 1;
    public static final int PROGRESS_VERTICAL = 2;

    public static final int SUBTITLE_NONE = 0;
    public static final int SUBTITLE_TRANSLATION = 1;
    public static final int SUBTITLE_ROMANIZATION = 2;

    public interface Delegate {
        void onSeekTo(long ms);

        void onConsent();

        void onRetry();
    }

    private static final float SYNCED_TEXT_DP = 22f;
    private static final float SYNCED_LINE_HEIGHT_DP = 29f;
    private static final float SYNCED_PADDING_DP = 7f;
    private static final float BACKGROUND_TEXT_DP = 17f;
    private static final float BACKGROUND_LINE_HEIGHT_DP = 23f;
    private static final float SUBTITLE_TEXT_DP = 15f;
    private static final float SUBTITLE_LINE_HEIGHT_DP = 20f;
    private static final float COMPANION_GAP_DP = 2f;
    private static final int SUBTITLE_ALPHA = 0xB3;
    private static final long UPGRADE_FADE_MS = 220;
    private static final float PLAIN_TEXT_DP = 18f;
    private static final float PLAIN_LINE_HEIGHT_DP = 26f;
    private static final float PLAIN_PADDING_DP = 2f;
    private static final float PLAIN_GAP_DP = 18f;
    private static final float EDGE_PADDING_DP = 96f;
    private static final float SIDE_PADDING_DP = PlayerDimens.SIDE_DP;
    private static final float CREDIT_TEXT_DP = 13f;
    private static final float CREDIT_GAP_DP = 28f;
    private static final float SYNCED_TOP_PADDING_DP = 32f;
    private static final float SYNCED_BOTTOM_PADDING_DP = 48f;
    private static final float INACTIVE_ALPHA = 0.3f;
    private static final float PLAIN_ALPHA = 0.92f;
    private static final float ANCHOR = 0.5f;
    private static final long USER_SCROLL_PAUSE_MS = 3000;
    private static final long LINE_FADE_MS = 350;
    private static final long POSITION_JITTER_MS = 1000;
    private static final long CATCH_UP_MIN_MS = 40;
    private static final long CATCH_UP_MS = 600;
    private static final long CATCH_UP_LIMIT_MS = 1000;
    private static final long WORD_LINE_LEAD_MS = 300;
    private static final float UNFILLED_ALPHA = 0.45f;
    private static final float FILL_FEATHER_DP = 28f;
    private static final float FILL_FEATHER_VERTICAL_DP = 10f;
    private static final float FILL_FEATHER_WORD_DP = 12f;
    private static final float INACTIVE_SCALE = 0.95f;
    private static final long MIN_LINE_FILL_MS = 2000;
    private static final long LINE_FILL_MS_PER_CHAR = 200;

    private final NestedScrollView scrollView;
    private final LinesLayout linesLayout;
    private final LinearLayout emptyView;
    private final RLottieImageView emptyStickerView;
    private final ImageView emptyImageView;
    private final TextView emptyTitleView;
    private final TextView emptySubtitleView;
    private final ButtonWithCounterView emptyButton;
    private final RadialProgressView progressView;
    private final SpringAnimation scrollSpring;
    private final ArrayList<View> lineViews = new ArrayList<>();

    private Delegate delegate;
    private Lyrics lyrics;
    private int state = STATE_EMPTY;
    private int currentIndex = -1;
    private long userScrollUntil;

    private int textColor;
    private int secondaryColor;
    private TextView creditView;
    private int viewportHeight;
    private boolean pendingSnap;
    private long clockPositionMs;
    private long clockAtMs;
    private boolean clockPlaying;
    private float clockSpeed = 1f;
    private long lastDisplayPositionMs = -1;
    private long catchUpMs;
    private long catchUpAtMs;
    private int progressMode = PROGRESS_HORIZONTAL;

    private static final FloatPropertyCompat<NestedScrollView> SCROLL_Y = new FloatPropertyCompat<>("scrollY") {
        @Override
        public float getValue(NestedScrollView view) {
            return view.getScrollY();
        }

        @Override
        public void setValue(NestedScrollView view, float value) {
            view.scrollTo(0, Math.round(value));
        }
    };

    @SuppressLint("ClickableViewAccessibility")
    public LyricsView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);

        scrollView = new NestedScrollView(context) {
            @Override
            public boolean onInterceptTouchEvent(@NonNull MotionEvent ev) {
                if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    onUserScroll();
                }
                return super.onInterceptTouchEvent(ev);
            }

            @Override
            public boolean onTouchEvent(@NonNull MotionEvent ev) {
                if (ev.getActionMasked() == MotionEvent.ACTION_DOWN || ev.getActionMasked() == MotionEvent.ACTION_MOVE) {
                    onUserScroll();
                }
                return super.onTouchEvent(ev);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                final int height = MeasureSpec.getSize(heightMeasureSpec);
                if (height != viewportHeight) {
                    viewportHeight = height;
                    pendingSnap = true;
                    linesLayout.forceLayout();
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                super.onLayout(changed, l, t, r, b);
                if (pendingSnap && getHeight() > 0) {
                    pendingSnap = false;
                    if (SystemClock.elapsedRealtime() >= userScrollUntil) {
                        snapToCurrent();
                    }
                }
            }
        };
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setOverScrollMode(OVER_SCROLL_NEVER);
        scrollView.setVerticalFadingEdgeEnabled(true);
        scrollView.setFadingEdgeLength(dp(48));
        scrollView.setClipToPadding(false);
        addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        linesLayout = new LinesLayout(context);
        linesLayout.setPadding(dp(SIDE_PADDING_DP), 0, dp(SIDE_PADDING_DP), 0);
        scrollView.addView(linesLayout, new NestedScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        emptyView = new LinearLayout(context);
        emptyView.setOrientation(LinearLayout.VERTICAL);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(dp(32), 0, dp(32), 0);
        addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        final FrameLayout imageContainer = new FrameLayout(context);
        emptyStickerView = new RLottieImageView(context);
        emptyStickerView.setAnimation(R.raw.utyan_empty, 120, 120);
        emptyStickerView.setOnClickListener(v -> {
            emptyStickerView.setProgress(0f);
            emptyStickerView.playAnimation();
        });
        imageContainer.addView(emptyStickerView, LayoutHelper.createFrame(120, 120, Gravity.CENTER));
        emptyImageView = new ImageView(context);
        emptyImageView.setScaleType(ImageView.ScaleType.CENTER);
        emptyImageView.setImageResource(R.drawable.music_empty);
        imageContainer.addView(emptyImageView, LayoutHelper.createFrame(120, 120, Gravity.CENTER));
        emptyView.addView(imageContainer, LayoutHelper.createLinear(120, 120, Gravity.CENTER_HORIZONTAL));

        emptyTitleView = new TextView(context);
        emptyTitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        emptyTitleView.setTypeface(AndroidUtilities.bold());
        emptyTitleView.setGravity(Gravity.CENTER);
        emptyView.addView(emptyTitleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 12, 0, 0));

        emptySubtitleView = new TextView(context);
        emptySubtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        emptySubtitleView.setTypeface(AndroidUtilities.regular());
        emptySubtitleView.setGravity(Gravity.CENTER);
        emptySubtitleView.setMaxWidth(dp(272));
        emptySubtitleView.setLineSpacing(dp(2), 1f);
        emptyView.addView(emptySubtitleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 8, 0, 0));

        emptyButton = new ButtonWithCounterView(context, resourcesProvider).setRound();
        emptyButton.setOnClickListener(v -> {
            if (delegate == null) {
                return;
            }
            if (state == STATE_OFFER) {
                delegate.onConsent();
            } else if (state == STATE_ERROR) {
                delegate.onRetry();
            }
        });
        emptyView.addView(emptyButton, LayoutHelper.createLinear(256, 48, Gravity.CENTER_HORIZONTAL, 0, 20, 0, 0));

        progressView = new RadialProgressView(context, resourcesProvider);
        progressView.setStyle(RadialProgressView.STYLE_MATERIAL3_LOADING);
        progressView.setSize(dp(48));
        addView(progressView, LayoutHelper.createFrame(64, 64, Gravity.CENTER));

        scrollSpring = new SpringAnimation(scrollView, SCROLL_Y);
        scrollSpring.setSpring(new SpringForce(0f).setDampingRatio(0.9f).setStiffness(180f));
        scrollSpring.setMinimumVisibleChange(1f);

        applyState(false);
    }

    public void setProgressMode(int mode) {
        if (progressMode != mode) {
            progressMode = mode;
            if (currentIndex >= 0 && currentIndex < lineViews.size()) {
                lineViews.get(currentIndex).invalidate();
            }
        }
    }

    public void setDelegate(Delegate delegate) {
        this.delegate = delegate;
    }

    public void setColors(int textColor, int secondaryColor, int accentColor, int onAccentColor) {
        this.textColor = textColor;
        this.secondaryColor = secondaryColor;
        if (creditView != null) {
            creditView.setTextColor(secondaryColor);
        }
        emptyTitleView.setTextColor(textColor);
        emptySubtitleView.setTextColor(secondaryColor);
        emptyImageView.setColorFilter(new PorterDuffColorFilter(ColorUtils.setAlphaComponent(secondaryColor, 0xB3), PorterDuff.Mode.SRC_IN));
        emptyButton.setColor(accentColor, ColorUtils.setAlphaComponent(onAccentColor, 0x26));
        emptyButton.setTextColor(onAccentColor);
        progressView.setProgressColor(accentColor);
        for (View view : lineViews) {
            if (view instanceof LineView) {
                ((LineView) view).applyColor(textColor);
            } else if (view instanceof TextView) {
                ((TextView) view).setTextColor(textColor);
            } else if (view instanceof DotsView) {
                ((DotsView) view).setColor(textColor);
            }
        }
    }

    public int getState() {
        return state;
    }

    public void setState(int state, CharSequence title, CharSequence subtitle, CharSequence button) {
        this.state = state;
        if (state != STATE_READY) {
            lyrics = null;
            clearLines();
        }
        emptyTitleView.setText(title);
        emptySubtitleView.setText(subtitle);
        emptySubtitleView.setVisibility(TextUtils.isEmpty(subtitle) ? GONE : VISIBLE);
        if (!TextUtils.isEmpty(button)) {
            emptyButton.setText(button, false);
            emptyButton.setVisibility(VISIBLE);
        } else {
            emptyButton.setVisibility(GONE);
        }
        applyState(true);
    }

    public Lyrics getLyrics() {
        return lyrics;
    }

    public void setLyrics(Lyrics lyrics, CharSequence credit, int subtitle) {
        final boolean upgrade = state == STATE_READY && this.lyrics != null && this.lyrics.synced && lyrics.synced && scrollView.getHeight() > 0;
        final long keepUserScroll = upgrade ? userScrollUntil : 0;
        this.state = STATE_READY;
        this.lyrics = lyrics;
        currentIndex = -1;
        userScrollUntil = 0;
        scrollSpring.cancel();
        clearLines();
        final Context context = getContext();
        if (lyrics.synced) {
            linesLayout.topSpacer = new View(context);
            linesLayout.addView(linesLayout.topSpacer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0));
            for (int i = 0; i < lyrics.lines.size(); i++) {
                final Lyrics.Line line = lyrics.lines.get(i);
                final View view;
                if (TextUtils.isEmpty(line.text)) {
                    final DotsView dots = new DotsView(context);
                    dots.setColor(textColor);
                    dots.rtl = isNeighborLineRtl(lyrics, i);
                    view = dots;
                    linesLayout.addView(dots, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 41));
                } else {
                    final long next = i + 1 < lyrics.lines.size() ? lyrics.lines.get(i + 1).time : Long.MAX_VALUE;
                    final LineView textView = createSyncedLine(context, line, next, null);
                    final String subtitleText = subtitle == SUBTITLE_TRANSLATION ? line.translation : subtitle == SUBTITLE_ROMANIZATION ? line.romanization : null;
                    final boolean companions = line.background != null || subtitleText != null;
                    if (companions) {
                        textView.setPadding(0, dp(SYNCED_PADDING_DP), 0, dp(COMPANION_GAP_DP));
                    }
                    view = textView;
                    linesLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    if (line.background != null) {
                        final LineView background = createSyncedLine(context, line.background, next, textView);
                        background.setTextSize(TypedValue.COMPLEX_UNIT_DIP, BACKGROUND_TEXT_DP);
                        setLineHeight(background, BACKGROUND_LINE_HEIGHT_DP);
                        background.setPadding(0, 0, 0, dp(subtitleText != null ? COMPANION_GAP_DP : SYNCED_PADDING_DP));
                        linesLayout.addView(background, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    }
                    if (subtitleText != null) {
                        final LineView sub = new LineView(context);
                        sub.owner = textView;
                        sub.subtitle = true;
                        sub.setTypeface(AndroidUtilities.regular());
                        sub.setTextSize(TypedValue.COMPLEX_UNIT_DIP, SUBTITLE_TEXT_DP);
                        setLineHeight(sub, SUBTITLE_LINE_HEIGHT_DP);
                        sub.setPadding(0, 0, 0, dp(SYNCED_PADDING_DP));
                        sub.setAlignEnd(line.voice == Lyrics.VOICE_SECOND);
                        sub.setText(subtitleText);
                        textView.companions.add(sub);
                        linesLayout.addView(sub, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    }
                    textView.applyColor(textColor);
                }
                view.setAlpha(INACTIVE_ALPHA);
                if (view instanceof LineView) {
                    for (LineView member : ((LineView) view).group()) {
                        member.setAlpha(INACTIVE_ALPHA);
                        member.setScaleX(INACTIVE_SCALE);
                        member.setScaleY(INACTIVE_SCALE);
                    }
                }
                final long time = line.time;
                final OnClickListener seek = v -> {
                    userScrollUntil = 0;
                    onSeek();
                    if (delegate != null) {
                        delegate.onSeekTo(time);
                    }
                };
                view.setOnClickListener(seek);
                if (view instanceof LineView) {
                    for (LineView companion : ((LineView) view).companions) {
                        companion.setOnClickListener(seek);
                    }
                }
                lineViews.add(view);
            }
            addCredit(context, credit, CREDIT_GAP_DP);
            linesLayout.bottomSpacer = new View(context);
            linesLayout.addView(linesLayout.bottomSpacer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0));
        } else {
            linesLayout.addView(new View(context), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 12));
            for (int i = 0; i < lyrics.lines.size(); i++) {
                final Lyrics.Line line = lyrics.lines.get(i);
                if (TextUtils.isEmpty(line.text)) {
                    linesLayout.addView(new View(context), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, (int) PLAIN_GAP_DP));
                    continue;
                }
                final TextView textView = createLine(context, false);
                textView.setText(line.text);
                textView.setAlpha(PLAIN_ALPHA);
                linesLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                lineViews.add(textView);
            }
            addCredit(context, credit, CREDIT_GAP_DP);
            linesLayout.addView(new View(context), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, (int) EDGE_PADDING_DP));
        }
        if (upgrade) {
            userScrollUntil = keepUserScroll;
            linesLayout.animate().cancel();
            linesLayout.setAlpha(0f);
            linesLayout.animate().alpha(1f).setDuration(UPGRADE_FADE_MS).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        } else {
            linesLayout.animate().cancel();
            linesLayout.setAlpha(1f);
            scrollView.scrollTo(0, 0);
        }
        pendingSnap = true;
        applyState(true);
    }

    private LineView createSyncedLine(Context context, Lyrics.Line line, long next, LineView owner) {
        final LineView view = new LineView(context);
        styleLine(view, true);
        view.owner = owner;
        view.setAlignEnd(line.voice == Lyrics.VOICE_SECOND || owner != null && owner.alignEnd);
        view.setText(line.text);
        if (line.words != null) {
            view.setWords(line.words);
        } else {
            final long cap = Math.max(MIN_LINE_FILL_MS, line.text.length() * LINE_FILL_MS_PER_CHAR);
            view.setTiming(line.time, line.time + Math.min(Math.max(1, next - line.time), cap));
        }
        if (owner != null) {
            owner.companions.add(view);
        }
        return view;
    }

    private void addCredit(Context context, CharSequence credit, float topMarginDp) {
        if (TextUtils.isEmpty(credit)) {
            return;
        }
        creditView = new TextView(context);
        creditView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, CREDIT_TEXT_DP);
        creditView.setTypeface(AndroidUtilities.regular());
        creditView.setTextColor(secondaryColor);
        creditView.setText(credit);
        linesLayout.addView(creditView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, topMarginDp, 0, 0));
    }

    private TextView createLine(Context context, boolean synced) {
        final TextView textView = new TextView(context);
        styleLine(textView, synced);
        return textView;
    }

    private void styleLine(TextView textView, boolean synced) {
        textView.setTextColor(textColor);
        if (synced) {
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, SYNCED_TEXT_DP);
            textView.setTypeface(AndroidUtilities.bold());
            setLineHeight(textView, SYNCED_LINE_HEIGHT_DP);
            textView.setPadding(0, dp(SYNCED_PADDING_DP), 0, dp(SYNCED_PADDING_DP));
        } else {
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, PLAIN_TEXT_DP);
            textView.setTypeface(AndroidUtilities.regular());
            setLineHeight(textView, PLAIN_LINE_HEIGHT_DP);
            textView.setPadding(0, dp(PLAIN_PADDING_DP), 0, dp(PLAIN_PADDING_DP));
            textView.setTextIsSelectable(false);
        }
    }

    private static void setLineHeight(TextView textView, float lineHeightDp) {
        final Paint.FontMetricsInt metrics = textView.getPaint().getFontMetricsInt();
        final int fontHeight = metrics.descent - metrics.ascent;
        textView.setIncludeFontPadding(false);
        textView.setLineSpacing(Math.max(0, dp(lineHeightDp) - fontHeight), 1f);
    }

    private void clearLines() {
        for (View view : lineViews) {
            view.animate().cancel();
            if (view instanceof LineView) {
                for (LineView companion : ((LineView) view).companions) {
                    companion.animate().cancel();
                }
            }
        }
        lineViews.clear();
        linesLayout.removeAllViews();
        linesLayout.topSpacer = null;
        linesLayout.bottomSpacer = null;
        creditView = null;
        currentIndex = -1;
    }

    private void applyState(boolean animated) {
        final boolean ready = state == STATE_READY;
        final boolean loading = state == STATE_LOADING;
        final boolean empty = !ready && !loading && state != STATE_EMPTY;
        scrollView.setVisibility(ready ? VISIBLE : GONE);
        progressView.setVisibility(loading ? VISIBLE : GONE);
        final boolean sticker = state == STATE_NOT_FOUND || state == STATE_ERROR;
        emptyStickerView.setVisibility(sticker ? VISIBLE : GONE);
        emptyImageView.setVisibility(sticker ? GONE : VISIBLE);
        if (empty && sticker) {
            emptyStickerView.setProgress(0f);
            emptyStickerView.playAnimation();
        }
        if (empty && emptyView.getVisibility() != VISIBLE && animated) {
            emptyView.setAlpha(0f);
            emptyView.setTranslationY(dp(12));
            emptyView.setVisibility(VISIBLE);
            emptyView.animate().alpha(1f).translationY(0).setDuration(320).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
        } else {
            emptyView.animate().cancel();
            emptyView.setAlpha(1f);
            emptyView.setTranslationY(0);
            emptyView.setVisibility(empty ? VISIBLE : GONE);
        }
    }

    private void onUserScroll() {
        userScrollUntil = SystemClock.elapsedRealtime() + USER_SCROLL_PAUSE_MS;
        scrollSpring.cancel();
    }

    public void onSeek() {
        lastDisplayPositionMs = -1;
        catchUpMs = 0;
    }

    public void setPosition(long ms, boolean playing) {
        if (state != STATE_READY || lyrics == null || !lyrics.synced || lineViews.isEmpty()) {
            return;
        }
        final boolean wasPlaying = clockPlaying;
        final long shown = wasPlaying && playing && lastDisplayPositionMs >= 0 ? getClockPositionMs() : -1;
        clockPositionMs = ms;
        clockAtMs = SystemClock.elapsedRealtime();
        clockPlaying = playing;
        clockSpeed = MediaController.getInstance().getPlaybackSpeed(true);
        final long drift = shown >= 0 ? ms - shown : 0;
        final boolean words = lyrics.timedWords;
        final int index = lyrics.indexAt((drift < 0 && -drift < POSITION_JITTER_MS ? shown : ms) + (words ? WORD_LINE_LEAD_MS : 0));
        if (words) {
            catchUpMs = 0;
        } else if (index == currentIndex && !wasPlaying && lastDisplayPositionMs >= 0 && ms > lastDisplayPositionMs && ms - lastDisplayPositionMs <= CATCH_UP_LIMIT_MS) {
            catchUpMs = ms - lastDisplayPositionMs;
            catchUpAtMs = clockAtMs;
        } else if (index == currentIndex && drift > CATCH_UP_MIN_MS && drift <= CATCH_UP_LIMIT_MS) {
            catchUpMs = drift;
            catchUpAtMs = clockAtMs;
        }
        if (index != currentIndex) {
            final int previous = currentIndex;
            currentIndex = index;
            lastDisplayPositionMs = -1;
            catchUpMs = 0;
            if (previous >= 0 && previous < lineViews.size()) {
                highlight(lineViews.get(previous), false);
            }
            if (index >= 0 && index < lineViews.size()) {
                highlight(lineViews.get(index), true);
            }
        }
        for (int i = 0; i < lineViews.size(); i++) {
            final View view = lineViews.get(i);
            if (view instanceof DotsView) {
                ((DotsView) view).setActive(i == currentIndex && playing);
            }
        }
        if (currentIndex >= 0 && currentIndex < lineViews.size()) {
            lineViews.get(currentIndex).invalidate();
        }
        autoScroll();
    }

    private long getClockPositionMs() {
        return clockPlaying ? clockPositionMs + (long) ((SystemClock.elapsedRealtime() - clockAtMs) * clockSpeed) : clockPositionMs;
    }

    private long getDisplayPositionMs() {
        final long now = SystemClock.elapsedRealtime();
        long position = getClockPositionMs();
        if (catchUpMs > 0) {
            final float t = (now - catchUpAtMs) / (float) CATCH_UP_MS;
            if (t >= 1f) {
                catchUpMs = 0;
            } else {
                position -= (long) (catchUpMs * (1f - CubicBezierInterpolator.DEFAULT.getInterpolation(t)));
            }
        }
        if (lastDisplayPositionMs >= 0 && position < lastDisplayPositionMs && lastDisplayPositionMs - position < POSITION_JITTER_MS) {
            position = lastDisplayPositionMs;
        }
        lastDisplayPositionMs = position;
        return position;
    }

    private static void highlight(View view, boolean current) {
        if (!(view instanceof LineView)) {
            view.animate().cancel();
            view.animate().alpha(current ? 1f : INACTIVE_ALPHA).setDuration(LINE_FADE_MS).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            return;
        }
        final float scale = current ? 1f : INACTIVE_SCALE;
        for (LineView line : ((LineView) view).group()) {
            line.animate().cancel();
            final ViewPropertyAnimator animator = line.animate().alpha(current ? 1f : INACTIVE_ALPHA).scaleX(scale).scaleY(scale).setDuration(LINE_FADE_MS).setInterpolator(CubicBezierInterpolator.DEFAULT);
            line.setFadingProgress(current ? -1f : line.drawnProgress);
            line.drawnProgress = -1f;
            if (!current) {
                animator.withEndAction(() -> line.setFadingProgress(-1f));
            }
            animator.start();
        }
    }

    private float getCurrentScrollTarget() {
        final View view = lineViews.get(Math.max(0, Math.min(lineViews.size() - 1, currentIndex)));
        int bottom = view.getBottom();
        if (view instanceof LineView && !((LineView) view).companions.isEmpty()) {
            final ArrayList<LineView> companions = ((LineView) view).companions;
            bottom = companions.get(companions.size() - 1).getBottom();
        }
        final int maxScroll = Math.max(0, linesLayout.getHeight() - scrollView.getHeight());
        return Math.max(0, Math.min(maxScroll, (view.getTop() + bottom) / 2f - scrollView.getHeight() * ANCHOR));
    }

    private void snapToCurrent() {
        if (lyrics == null || !lyrics.synced || lineViews.isEmpty() || scrollView.getHeight() <= 0) {
            return;
        }
        scrollSpring.cancel();
        scrollView.scrollTo(0, Math.round(getCurrentScrollTarget()));
    }

    private void autoScroll() {
        if (SystemClock.elapsedRealtime() < userScrollUntil || lineViews.isEmpty() || scrollView.getHeight() <= 0) {
            return;
        }
        final float target = getCurrentScrollTarget();
        if (Math.abs(scrollSpring.getSpring().getFinalPosition() - target) > 0.5f || !scrollSpring.isRunning() && Math.abs(scrollView.getScrollY() - target) > 0.5f) {
            scrollSpring.getSpring().setFinalPosition(target);
            if (!scrollSpring.isRunning()) {
                scrollSpring.setStartValue(scrollView.getScrollY());
                scrollSpring.start();
            }
        }
    }

    public void jumpToCurrent() {
        userScrollUntil = 0;
        scrollSpring.cancel();
        if (scrollView.getHeight() > 0 && !scrollView.isLayoutRequested()) {
            snapToCurrent();
        } else {
            pendingSnap = true;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        scrollSpring.cancel();
    }

    private class LineView extends TextView {

        private long start;
        private long end;
        private Lyrics.Word[] words;
        LineView owner;
        boolean subtitle;
        boolean alignEnd;
        final ArrayList<LineView> companions = new ArrayList<>(2);
        private float drawnProgress = -1f;
        private float fadingProgress = -1f;

        LineView(Context context) {
            super(context);
        }

        void setTiming(long start, long end) {
            this.start = start;
            this.end = end;
        }

        void setAlignEnd(boolean alignEnd) {
            this.alignEnd = alignEnd;
            setGravity(alignEnd ? Gravity.END : Gravity.START);
        }

        ArrayList<LineView> group() {
            final ArrayList<LineView> out = new ArrayList<>(companions.size() + 1);
            out.add(this);
            out.addAll(companions);
            return out;
        }

        void applyColor(int color) {
            setTextColor(subtitle ? ColorUtils.setAlphaComponent(color, Color.alpha(color) * SUBTITLE_ALPHA / 0xFF) : color);
            for (LineView companion : companions) {
                companion.applyColor(color);
            }
        }

        void setWords(Lyrics.Word[] words) {
            this.words = words;
            setTiming(words[0].start, words[words.length - 1].end);
        }

        void setFadingProgress(float progress) {
            if (fadingProgress != progress) {
                fadingProgress = progress;
                invalidate();
            }
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            final Layout layout = getLayout();
            final boolean rtl = layout != null && layout.getLineCount() > 0 && layout.getParagraphDirection(0) == Layout.DIR_RIGHT_TO_LEFT;
            setPivotX(rtl != alignEnd ? w : 0);
            setPivotY(h / 2f);
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            final Layout layout = getLayout();
            final boolean current = lineViews.indexOf(owner != null ? owner : this) == currentIndex;
            if (layout == null || subtitle || progressMode == PROGRESS_NONE || !current && fadingProgress < 0) {
                super.onDraw(canvas);
                return;
            }
            final long position = current || words != null ? getDisplayPositionMs() : 0;
            final float progress = current ? Utilities.clamp01((position - start) / (float) Math.max(1, end - start)) : fadingProgress;
            if (current) {
                drawnProgress = progress;
            }
            final int color = getCurrentTextColor();
            final float alpha = getAlpha();
            final TextPaint paint = getPaint();
            canvas.save();
            canvas.translate(getCompoundPaddingLeft(), getExtendedPaddingTop());
            final float activeness = Utilities.clamp01((alpha - INACTIVE_ALPHA) / (1f - INACTIVE_ALPHA));
            final float unfilledAlpha = AndroidUtilities.lerp(INACTIVE_ALPHA, UNFILLED_ALPHA, activeness) / Math.max(alpha, 0.01f);
            paint.setColor(ColorUtils.setAlphaComponent(color, (int) (Color.alpha(color) * Math.min(1f, unfilledAlpha))));
            layout.draw(canvas);
            final float fillAlpha = current ? 1f : activeness / Math.max(alpha, 0.01f);
            final int fillColor = ColorUtils.setAlphaComponent(color, (int) (Color.alpha(color) * Math.min(1f, fillAlpha)));
            paint.setColor(fillColor);
            if (progressMode == PROGRESS_VERTICAL) {
                drawVerticalFill(canvas, layout, paint, fillColor, progress);
            } else if (words != null) {
                drawWordFill(canvas, layout, paint, fillColor, current ? position : Math.max(position, start + (long) (progress * (end - start))));
            } else {
                drawHorizontalFill(canvas, layout, paint, fillColor, progress);
            }
            paint.setShader(null);
            paint.setColor(color);
            canvas.restore();
            if (!current || (clockPlaying || catchUpMs > 0) && progress < 1f || alpha < 1f) {
                postInvalidateOnAnimation();
            }
        }

        private void drawVerticalFill(Canvas canvas, Layout layout, TextPaint paint, int color, float progress) {
            if (progress <= 0f) {
                return;
            }
            final float feather = dpf2(FILL_FEATHER_VERTICAL_DP);
            final float edge = progress * (layout.getHeight() + feather);
            paint.setShader(new LinearGradient(0, edge - feather, 0, edge, color, color & 0x00FFFFFF, Shader.TileMode.CLAMP));
            canvas.save();
            canvas.clipRect(0, 0, layout.getWidth(), edge);
            layout.draw(canvas);
            canvas.restore();
        }

        private void drawWordFill(Canvas canvas, Layout layout, TextPaint paint, int color, long position) {
            int filledTo = 0;
            Lyrics.Word active = null;
            float fraction = 0f;
            for (Lyrics.Word word : words) {
                if (position >= word.end) {
                    filledTo = word.to;
                } else {
                    if (position > word.start) {
                        active = word;
                        fraction = (position - word.start) / (float) (word.end - word.start);
                    }
                    break;
                }
            }
            if (filledTo > 0) {
                drawFilledRange(canvas, layout, filledTo);
            }
            if (active == null) {
                return;
            }
            final int line = layout.getLineForOffset(active.from);
            final boolean rtl = layout.getParagraphDirection(line) == Layout.DIR_RIGHT_TO_LEFT;
            final float x0 = layout.getPrimaryHorizontal(active.from);
            final int to = Math.min(active.to, layout.getLineEnd(line));
            final float width = Math.abs(Layout.getDesiredWidth(layout.getText(), active.from, to, paint));
            final float feather = Math.min(dpf2(FILL_FEATHER_WORD_DP), Math.max(1f, width));
            final float shift = fraction * (width + feather);
            final float edge = rtl ? x0 - shift : x0 + shift;
            paint.setShader(new LinearGradient(rtl ? edge + feather : edge - feather, 0, edge, 0, color, color & 0x00FFFFFF, Shader.TileMode.CLAMP));
            canvas.save();
            if (rtl) {
                canvas.clipRect(Math.max(edge, x0 - width), layout.getLineTop(line), x0, layout.getLineBottom(line));
            } else {
                canvas.clipRect(x0, layout.getLineTop(line), Math.min(edge, x0 + width), layout.getLineBottom(line));
            }
            layout.draw(canvas);
            canvas.restore();
            paint.setShader(null);
        }

        private void drawFilledRange(Canvas canvas, Layout layout, int offset) {
            for (int i = 0; i < layout.getLineCount(); i++) {
                final int lineStart = layout.getLineStart(i);
                if (offset <= lineStart) {
                    break;
                }
                final float lineLeft = layout.getLineLeft(i);
                final float lineRight = layout.getLineRight(i);
                float left = lineLeft;
                float right = lineRight;
                if (offset < layout.getLineEnd(i)) {
                    final float x = layout.getPrimaryHorizontal(offset);
                    if (layout.getParagraphDirection(i) == Layout.DIR_RIGHT_TO_LEFT) {
                        left = x;
                    } else {
                        right = x;
                    }
                }
                canvas.save();
                canvas.clipRect(left, layout.getLineTop(i), right, layout.getLineBottom(i));
                layout.draw(canvas);
                canvas.restore();
            }
        }

        private void drawHorizontalFill(Canvas canvas, Layout layout, TextPaint paint, int color, float progress) {
            final float feather = dpf2(FILL_FEATHER_DP);
            float total = 0;
            for (int i = 0; i < layout.getLineCount(); i++) {
                total += layout.getLineWidth(i) + feather;
            }
            float remaining = progress * total;
            for (int i = 0; i < layout.getLineCount() && remaining > 0; i++) {
                final float width = layout.getLineWidth(i) + feather;
                final float filled = Math.min(width, remaining);
                remaining -= width;
                final boolean rtl = layout.getParagraphDirection(i) == Layout.DIR_RIGHT_TO_LEFT;
                final float lineLeft = layout.getLineLeft(i);
                final float lineRight = layout.getLineRight(i);
                final float edge = rtl ? lineRight - filled : lineLeft + filled;
                final boolean partial = filled < width;
                paint.setShader(partial ? new LinearGradient(rtl ? edge + feather : edge - feather, 0, edge, 0, color, color & 0x00FFFFFF, Shader.TileMode.CLAMP) : null);
                canvas.save();
                canvas.clipRect(rtl ? edge : lineLeft, layout.getLineTop(i), rtl ? lineRight : edge, layout.getLineBottom(i));
                layout.draw(canvas);
                canvas.restore();
            }
        }
    }

    private class LinesLayout extends LinearLayout {

        View topSpacer;
        View bottomSpacer;

        LinesLayout(Context context) {
            super(context);
            setOrientation(VERTICAL);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            if (topSpacer == null || bottomSpacer == null || lineViews.isEmpty() || viewportHeight <= 0) {
                return;
            }
            final View first = lineViews.get(0);
            final View last = lineViews.get(lineViews.size() - 1);
            int afterLast = 0;
            for (int i = indexOfChild(last) + 1; i < getChildCount(); i++) {
                final View child = getChildAt(i);
                if (child != bottomSpacer && child.getVisibility() != GONE) {
                    final LayoutParams lp = (LayoutParams) child.getLayoutParams();
                    afterLast += child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin;
                }
            }
            final int top = Math.max(0, Math.min(dp(SYNCED_TOP_PADDING_DP), Math.round(viewportHeight * ANCHOR - first.getMeasuredHeight() / 2f)));
            final int bottom = Math.max(0, Math.min(dp(SYNCED_BOTTOM_PADDING_DP), Math.round(viewportHeight * (1f - ANCHOR) - last.getMeasuredHeight() / 2f) - afterLast));
            final LayoutParams topParams = (LayoutParams) topSpacer.getLayoutParams();
            final LayoutParams bottomParams = (LayoutParams) bottomSpacer.getLayoutParams();
            if (topParams.height != top || bottomParams.height != bottom) {
                topParams.height = top;
                bottomParams.height = bottom;
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        }
    }

    private static boolean isNeighborLineRtl(Lyrics lyrics, int index) {
        for (int i = index - 1; i >= 0; i--) {
            final String text = lyrics.lines.get(i).text;
            if (!TextUtils.isEmpty(text)) {
                return TextDirectionHeuristics.FIRSTSTRONG_LTR.isRtl(text, 0, text.length());
            }
        }
        for (int i = index + 1; i < lyrics.lines.size(); i++) {
            final String text = lyrics.lines.get(i).text;
            if (!TextUtils.isEmpty(text)) {
                return TextDirectionHeuristics.FIRSTSTRONG_LTR.isRtl(text, 0, text.length());
            }
        }
        return false;
    }

    private static class DotsView extends View {

        private static final float DOT_DP = 9f;
        private static final float GAP_DP = 7f;
        private static final long PERIOD_MS = 1400;
        private static final float MIN_SCALE = 0.7f;
        private static final float MAX_SCALE = 1.15f;

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boolean rtl;
        private boolean active;
        private long startTime;

        DotsView(Context context) {
            super(context);
        }

        void setColor(int color) {
            paint.setColor(color);
            invalidate();
        }

        void setActive(boolean active) {
            if (this.active != active) {
                this.active = active;
                startTime = SystemClock.elapsedRealtime();
                invalidate();
            }
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            final float r = dpf2(DOT_DP) / 2f;
            final float start = r * MAX_SCALE;
            final float cy = getHeight() / 2f;
            final long now = SystemClock.elapsedRealtime();
            for (int i = 0; i < 3; i++) {
                float scale = 1f;
                int alpha = 0xFF;
                if (active) {
                    final float t = ((now - startTime - i * 180L) % PERIOD_MS + PERIOD_MS) % PERIOD_MS / (float) PERIOD_MS;
                    final float wave = (float) (0.5 - 0.5 * Math.cos(t * 2 * Math.PI));
                    scale = MIN_SCALE + (MAX_SCALE - MIN_SCALE) * wave;
                    alpha = (int) (0xFF * (0.45f + 0.55f * wave));
                }
                final int baseAlpha = paint.getAlpha();
                paint.setAlpha(alpha);
                final float offset = start + i * (dpf2(DOT_DP) + dpf2(GAP_DP));
                canvas.drawCircle(rtl ? getWidth() - offset : offset, cy, r * scale, paint);
                paint.setAlpha(baseAlpha);
            }
            if (active) {
                postInvalidateOnAnimation();
            }
        }
    }
}
