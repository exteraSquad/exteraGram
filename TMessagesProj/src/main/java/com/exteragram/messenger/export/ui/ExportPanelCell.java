/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.export.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.exteragram.messenger.export.ExportController;
import com.exteragram.messenger.export.ExportState;
import com.exteragram.messenger.player.ui.ExpressiveStateDrawable;
import com.exteragram.messenger.utils.ui.material.ColorTones;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

@SuppressLint("ViewConstructor")
public class ExportPanelCell extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    private static final int HEIGHT_DP = 48;
    private static final int PADDING_DP = 6;
    private static final int COVER_SIZE_DP = 36;
    private static final int BUTTON_SIZE_DP = 36;
    private static final int TEXT_LEFT_DP = PADDING_DP + COVER_SIZE_DP + 8;
    private static final int TEXT_RIGHT_DP = PADDING_DP + BUTTON_SIZE_DP + 8;
    private static final int TITLE_TOP_DP = 6;
    private static final int SUBTITLE_TOP_DP = 24;
    private static final float RING_RADIUS_DP = 15f;

    private final BaseFragment fragment;
    private final Theme.ResourcesProvider resourcesProvider;
    private final int account;
    private final Runnable onStateChanged;

    private final BackupImageView avatarView;
    private final AvatarDrawable avatarDrawable = new AvatarDrawable();
    private final PlaceholderView placeholderView;
    private final AnimatedTextView titleView;
    private final AnimatedTextView subtitleView;
    private int coverIcon;
    private final ActionButton actionButton;
    private NotificationCenter.ObserversGroup observers;
    private long boundDialogId = Long.MIN_VALUE;

    public ExportPanelCell(Context context, BaseFragment fragment, Runnable onStateChanged) {
        super(context);
        this.fragment = fragment;
        this.resourcesProvider = fragment.getResourceProvider();
        this.account = fragment.getCurrentAccount();
        this.onStateChanged = onStateChanged;

        placeholderView = new PlaceholderView(context);
        addView(placeholderView, LayoutHelper.createFrame(COVER_SIZE_DP, COVER_SIZE_DP, Gravity.TOP | Gravity.LEFT, PADDING_DP, PADDING_DP, 0, 0));

        avatarView = new BackupImageView(context);
        avatarView.setRoundRadius(dp(COVER_SIZE_DP / 2f));
        addView(avatarView, LayoutHelper.createFrame(COVER_SIZE_DP, COVER_SIZE_DP, Gravity.TOP | Gravity.LEFT, PADDING_DP, PADDING_DP, 0, 0));

        titleView = createLine(context, 14, true);
        ThemeBind.textColor(titleView, Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
        addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, Gravity.TOP | Gravity.LEFT, TEXT_LEFT_DP, TITLE_TOP_DP, TEXT_RIGHT_DP, 0));

        subtitleView = createLine(context, 13, false);
        NotificationCenter.listenEmojiLoading(subtitleView);
        ThemeBind.textColor(subtitleView, Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider);
        addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 18, Gravity.TOP | Gravity.LEFT, TEXT_LEFT_DP, SUBTITLE_TOP_DP, TEXT_RIGHT_DP, 0));

        actionButton = new ActionButton(context);
        actionButton.setOnClickListener(v -> onActionClick());
        addView(actionButton, LayoutHelper.createFrame(BUTTON_SIZE_DP, BUTTON_SIZE_DP, Gravity.TOP | Gravity.RIGHT, 0, (HEIGHT_DP - BUTTON_SIZE_DP) / 2f, PADDING_DP, 0));

        ThemeBind.selectorBackground(this, Theme.getSelectorDrawable(false), null, ThemeBind.of(Theme.key_listSelector));
        setOnClickListener(v -> ExportProgressSheet.show(fragment, account));
        bind();
    }

    private static AnimatedTextView createLine(Context context, int textSizeDp, boolean title) {
        AnimatedTextView textView = new AnimatedTextView(context, false, true, true);
        textView.setAnimationProperties(.4f, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
        textView.setEllipsizeByGradient(true);
        textView.setGravity(Gravity.LEFT);
        textView.setTextSize(dp(textSizeDp));
        textView.setTypeface(title ? AndroidUtilities.bold() : AndroidUtilities.regular());
        return textView;
    }

    private void setLine(AnimatedTextView view, CharSequence text) {
        view.setText(text, isAttachedToWindow() && !TextUtils.equals(view.getText(), text));
    }

    private void popIn(View view) {
        if (!isAttachedToWindow()) {
            return;
        }
        view.animate().cancel();
        view.setAlpha(0f);
        view.setScaleX(.6f);
        view.setScaleY(.6f);
        view.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(320).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(HEIGHT_DP), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        observers = NotificationCenter.getInstance(account).createObserversGroup(this).add(NotificationCenter.exportStateChanged);
        bind();
        onStateChanged.run();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (observers != null) {
            observers.removeAllObservers();
            observers = null;
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.exportStateChanged) {
            bind();
            onStateChanged.run();
        }
    }

    private void bind() {
        ExportState state = ExportController.getInstance(account).getState();
        if (state instanceof ExportState.Processing processing) {
            String entity = ExportUiHelper.entityTitle(processing);
            setLine(titleView, LocaleController.getString(R.string.ExportProgressTitle));
            setLine(subtitleView, entity.isEmpty() ? ExportUiHelper.stepTitle(processing) : Emoji.replaceEmoji(entity, subtitleView.getPaint().getFontMetricsInt(), false));
            bindCover(processing);
            actionButton.setProgress(processing.progress());
            actionButton.setContentDescription(LocaleController.getString(R.string.ExportStop));
        } else if (state instanceof ExportState.Finished finished) {
            setLine(titleView, LocaleController.getString(R.string.ExportFinished));
            setLine(subtitleView, ExportUiHelper.summary(finished));
            showPlaceholder(R.drawable.files_folder, false);
            actionButton.setClose();
            actionButton.setContentDescription(LocaleController.getString(R.string.Close));
        } else if (ExportUiHelper.hasVisibleState(state)) {
            setLine(titleView, LocaleController.getString(ExportUiHelper.isDelayError(state) ? R.string.ExportDelayTitle : R.string.ExportFailed));
            setLine(subtitleView, LocaleController.getString(R.string.ExportFailedPanelInfo));
            showPlaceholder(R.drawable.warning_sign, true);
            actionButton.setClose();
            actionButton.setContentDescription(LocaleController.getString(R.string.Close));
        }
    }

    private void bindCover(ExportState.Processing state) {
        int avatarType = switch (state.entityType) {
            case SavedMessages -> AvatarDrawable.AVATAR_TYPE_SAVED;
            case RepliesMessages -> AvatarDrawable.AVATAR_TYPE_REPLIES;
            default -> AvatarDrawable.AVATAR_TYPE_NORMAL;
        };
        long dialogId = avatarType != AvatarDrawable.AVATAR_TYPE_NORMAL ? Long.MIN_VALUE + 1 + avatarType : state.entityDialogId;
        if (dialogId == 0) {
            showPlaceholder(R.drawable.files_storage, false);
            return;
        }
        placeholderView.setVisibility(GONE);
        avatarView.setVisibility(VISIBLE);
        if (boundDialogId == dialogId) {
            return;
        }
        boundDialogId = dialogId;
        coverIcon = 0;
        popIn(avatarView);
        if (avatarType != AvatarDrawable.AVATAR_TYPE_NORMAL) {
            avatarDrawable.setAvatarType(avatarType);
            avatarView.setImageDrawable(avatarDrawable);
            return;
        }
        avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_NORMAL);
        MessagesController controller = MessagesController.getInstance(account);
        TLObject peer = dialogId > 0 ? controller.getUser(dialogId) : controller.getChat(-dialogId);
        if (peer != null) {
            avatarDrawable.setInfo(account, peer);
            avatarView.setForUserOrChat(peer, avatarDrawable);
        } else {
            avatarDrawable.setInfo(dialogId, state.entityName, null);
            avatarView.setImageDrawable(avatarDrawable);
        }
    }

    private void showPlaceholder(int iconRes, boolean error) {
        boundDialogId = Long.MIN_VALUE;
        avatarView.setVisibility(GONE);
        placeholderView.setVisibility(VISIBLE);
        if (coverIcon != iconRes) {
            coverIcon = iconRes;
            popIn(placeholderView);
        }
        placeholderView.set(iconRes, error);
    }

    private void onActionClick() {
        ExportController controller = ExportController.getInstance(account);
        if (controller.isRunning()) {
            ExportUiHelper.showStopDialog(fragment, getContext(), resourcesProvider, account);
        } else {
            controller.clearState();
        }
    }

    private int accentColor(boolean error) {
        return Theme.getColor(error ? Theme.key_text_RedBold : Theme.key_inappPlayerPlayPause, resourcesProvider);
    }

    private final class PlaceholderView extends View {

        private static final int ICON_SIZE_DP = 20;

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Drawable icon;
        private int iconRes;
        private int iconColor;
        private boolean error;

        PlaceholderView(Context context) {
            super(context);
        }

        void set(int iconRes, boolean error) {
            if (this.iconRes != iconRes) {
                this.iconRes = iconRes;
                Drawable drawable = ContextCompat.getDrawable(getContext(), iconRes);
                icon = drawable != null ? drawable.mutate() : null;
                iconColor = 0;
            }
            this.error = error;
            invalidate();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            final int accent = accentColor(error);
            final boolean dark = !ColorTones.isLightSurface(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
            paint.setColor(ColorUtils.setAlphaComponent(accent, (int) (0xFF * (dark ? 0.2f : 0.14f))));
            canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, Math.min(getWidth(), getHeight()) / 2f, paint);
            if (icon == null) {
                return;
            }
            if (iconColor != accent) {
                iconColor = accent;
                icon.setColorFilter(new PorterDuffColorFilter(accent, PorterDuff.Mode.SRC_IN));
            }
            final int size = dp(ICON_SIZE_DP);
            final int left = (getWidth() - size) / 2;
            final int top = (getHeight() - size) / 2;
            icon.setBounds(left, top, left + size, top + size);
            icon.draw(canvas);
        }
    }

    private final class ActionButton extends View {

        private static final float RING_STROKE_DP = 2.5f;
        private static final float RING_GAP_DP = 2f;
        private static final float RING_TRACK_ALPHA = 0.3f;
        private static final float WAVE_AMPLITUDE_DP = 1f;
        private static final float WAVE_LENGTH_DP = 11f;
        private static final float WAVE_SPEED_DP = 7f;
        private static final float WAVE_STEP_DP = 1f;
        private static final float WAVE_RAMP = 0.1f;
        private static final long WAVE_MIN_FRAME_GAP_MS = 16;
        private static final float STOP_SIZE_DP = 10f;
        private static final float STOP_RADIUS_DP = 2.5f;

        private final ExpressiveStateDrawable background;
        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path wavePath = new Path();
        private final RectF rect = new RectF();
        private final AnimatedFloat animatedProgress = new AnimatedFloat(this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
        private final Drawable closeIcon;
        private boolean closeMode = true;
        private float progress;
        private float wavePhase;
        private long lastWaveFrame;
        private int backgroundColor;
        private int closeColor;

        ActionButton(Context context) {
            super(context);
            background = new ExpressiveStateDrawable(RING_RADIUS_DP).setInset(BUTTON_SIZE_DP / 2f - RING_RADIUS_DP);
            setBackground(background);
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeCap(Paint.Cap.ROUND);
            ringPaint.setStrokeJoin(Paint.Join.ROUND);
            ringPaint.setStrokeWidth(dpf2(RING_STROKE_DP));
            Drawable drawable = ContextCompat.getDrawable(context, R.drawable.ic_close_white);
            closeIcon = drawable != null ? drawable.mutate() : null;
        }

        void setProgress(float value) {
            if (closeMode) {
                closeMode = false;
                animatedProgress.set(value, true);
                lastWaveFrame = 0;
            }
            progress = Utilities.clamp01(value);
            invalidate();
        }

        void setClose() {
            closeMode = true;
            invalidate();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            final int accent = accentColor(false);
            if (backgroundColor != accent) {
                backgroundColor = accent;
                background.setColors(accent, 0);
            }
            final float cx = getWidth() / 2f;
            final float cy = getHeight() / 2f;
            if (closeMode) {
                drawClose(canvas, cx, cy);
            } else {
                drawRing(canvas, cx, cy, accent);
                final float half = dpf2(STOP_SIZE_DP) / 2f;
                final float radius = dpf2(STOP_RADIUS_DP);
                fillPaint.setColor(accent);
                rect.set(cx - half, cy - half, cx + half, cy + half);
                canvas.drawRoundRect(rect, radius, radius, fillPaint);
            }
        }

        private void drawClose(Canvas canvas, float cx, float cy) {
            if (closeIcon == null) {
                return;
            }
            final int color = Theme.getColor(Theme.key_inappPlayerClose, resourcesProvider);
            if (closeColor != color) {
                closeColor = color;
                closeIcon.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
            }
            final int w = closeIcon.getIntrinsicWidth();
            final int h = closeIcon.getIntrinsicHeight();
            closeIcon.setBounds((int) (cx - w / 2f), (int) (cy - h / 2f), (int) (cx + w / 2f), (int) (cy + h / 2f));
            closeIcon.draw(canvas);
        }

        private void drawRing(Canvas canvas, float cx, float cy, int accent) {
            final float radius = dpf2(RING_RADIUS_DP);
            final float stroke = dpf2(RING_STROKE_DP);
            final float p = animatedProgress.set(progress);
            final float circumference = (float) (2 * Math.PI * radius);
            rect.set(cx - radius, cy - radius, cx + radius, cy + radius);
            if (p < 1f) {
                final float gap = p > 0f ? (dpf2(RING_GAP_DP) + stroke) / circumference * 360f : 0f;
                final float sweep = 360f * (1f - p) - gap * 2f;
                if (sweep > 0f) {
                    ringPaint.setColor(ColorUtils.setAlphaComponent(accent, (int) (0xFF * RING_TRACK_ALPHA)));
                    canvas.drawArc(rect, -90f + 360f * p + gap, sweep, false, ringPaint);
                }
            }
            if (p <= 0f) {
                lastWaveFrame = 0;
                return;
            }
            final long now = AnimationUtils.currentAnimationTimeMillis();
            if (lastWaveFrame != 0) {
                wavePhase = (wavePhase + (now - lastWaveFrame) / 1000f * WAVE_SPEED_DP / WAVE_LENGTH_DP * 2f * (float) Math.PI) % (2f * (float) Math.PI * 1000f);
            }
            lastWaveFrame = now;
            final float ramp = p < WAVE_RAMP ? p / WAVE_RAMP : p > 1f - WAVE_RAMP ? (1f - p) / WAVE_RAMP : 1f;
            final float amplitude = dpf2(WAVE_AMPLITUDE_DP) * Utilities.clamp01(ramp);
            ringPaint.setColor(accent);
            if (amplitude < 0.25f) {
                canvas.drawArc(rect, -90f, 360f * p, false, ringPaint);
            } else {
                final float length = circumference * p;
                final float wavelength = dpf2(WAVE_LENGTH_DP);
                final int count = Math.max(2, (int) Math.ceil(length / dpf2(WAVE_STEP_DP)));
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
            if (isShown()) {
                postInvalidateDelayed(Math.max(0, now + WAVE_MIN_FRAME_GAP_MS - SystemClock.uptimeMillis()));
            }
        }
    }
}
