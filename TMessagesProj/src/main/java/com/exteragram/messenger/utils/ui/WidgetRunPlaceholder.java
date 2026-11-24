/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.CircularProgressDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.utils.twidget.WidgetMedia;
import org.telegram.utils.twidget.WidgetSandboxMessage;
import org.telegram.utils.twidget.WidgetSandboxSession;

public final class WidgetRunPlaceholder {

    public static final int STATE_NONE = -1, STATE_IDLE = 0, STATE_LOADING = 1, STATE_FAILED = 2;

    private static final int BUTTON_HEIGHT_DP = 36, BUTTON_PADDING_DP = 16, ICON_WIDTH_DP = 10, ICON_HEIGHT_DP = 12, ICON_GAP_DP = 8;
    private static final int LOADING_WIDTH_DP = 56, SPINNER_SIZE_DP = 18, SLIDE_DP = 24;
    private static final float BOUNCE_SCALE = 0.1f;
    private static final long LOADING_DELAY_MS = 400;

    private final Paint buttonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Path icon = new Path();
    private final Path buttonClip = new Path();
    private final RectF button = new RectF();
    private final boolean withButton;
    private final AnimatedFloat loadingProgress;
    private final AnimatedFloat buttonWidth;
    private ButtonBounce bounce;
    private CircularProgressDrawable spinner;
    private View view;
    private int state = STATE_IDLE;
    private int shownState = STATE_NONE;
    private long loadingSince;
    private boolean drawn;

    public WidgetRunPlaceholder(boolean withButton) {
        this.withButton = withButton;
        textPaint.setTextSize(dp(14));
        textPaint.setTypeface(AndroidUtilities.bold());
        iconPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        iconPaint.setStrokeWidth(dp(1.5f));
        iconPaint.setStrokeJoin(Paint.Join.ROUND);
        iconPaint.setPathEffect(new CornerPathEffect(dp(1.5f)));
        loadingProgress = new AnimatedFloat(this::invalidate, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
        buttonWidth = new AnimatedFloat(this::invalidate, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
    }

    public static int stateOf(String url, WidgetSandboxMessage message) {
        if (WidgetMedia.waitsForTap(url)) return STATE_IDLE;
        WidgetSandboxSession.Frame frame = message == null ? null : message.frame();
        if (frame != null && frame.width > 0) return STATE_NONE;
        return message != null && message.failure() != null ? STATE_FAILED : STATE_LOADING;
    }

    public void draw(Canvas canvas, View view, int width, int height, int accentColor, int state) {
        this.view = view;
        this.state = state;
        state = updateShownState(state);
        if (state == STATE_NONE) {
            stopSpinner();
            return;
        }
        boolean force = !drawn;
        drawn = true;

        String text = label(width, state);
        boolean showIcon = state != STATE_FAILED;
        int iconWidth = showIcon ? dp(ICON_WIDTH_DP) : 0, gap = showIcon ? dp(ICON_GAP_DP) : 0, padding = dp(BUTTON_PADDING_DP);
        float contentWidth = iconWidth + gap + textPaint.measureText(text);
        float targetWidth = state == STATE_LOADING ? dp(LOADING_WIDTH_DP) : contentWidth + padding * 2;
        float currentWidth = buttonWidth.set(targetWidth, force);
        float loading = loadingProgress.set(state == STATE_LOADING ? 1f : 0f, force);

        float cx = width / 2f, cy = height / 2f;
        button.set(cx - currentWidth / 2f, cy - dp(BUTTON_HEIGHT_DP) / 2f, cx + currentWidth / 2f, cy + dp(BUTTON_HEIGHT_DP) / 2f);

        canvas.save();
        if (withButton) {
            float scale = bounce == null ? 1f : bounce.getScale(BOUNCE_SCALE);
            canvas.scale(scale, scale, cx, cy);
            buttonPaint.setColor(Theme.multAlpha(accentColor, 0.15f));
            canvas.drawRoundRect(button, button.height() / 2f, button.height() / 2f, buttonPaint);
            buttonClip.rewind();
            buttonClip.addRoundRect(button, button.height() / 2f, button.height() / 2f, Path.Direction.CW);
            canvas.clipPath(buttonClip);
        }

        if (loading < 1f) {
            canvas.save();
            if (loading > 0f) {
                canvas.translate(0, -loading * dp(SLIDE_DP));
                canvas.scale(1f, 1f - 0.4f * loading, cx, cy);
            }
            int alpha = (int) (0xFF * (1f - loading));
            float x = cx - contentWidth / 2f;
            if (showIcon) {
                float iconHeight = dp(ICON_HEIGHT_DP);
                icon.rewind();
                icon.moveTo(x, cy - iconHeight / 2f);
                icon.lineTo(x + iconWidth, cy);
                icon.lineTo(x, cy + iconHeight / 2f);
                icon.close();
                iconPaint.setColor(accentColor);
                iconPaint.setAlpha(alpha);
                canvas.drawPath(icon, iconPaint);
            }
            textPaint.setColor(accentColor);
            textPaint.setAlpha(alpha);
            canvas.drawText(text, x + iconWidth + gap, cy - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint);
            canvas.restore();
        }

        if (loading > 0f) {
            if (spinner == null) spinner = new CircularProgressDrawable(dp(SPINNER_SIZE_DP), dp(2), accentColor);
            spinner.setColor(accentColor);
            int y = (int) ((1f - loading) * dp(SLIDE_DP));
            spinner.setBounds((int) button.left, (int) button.top + y, (int) button.right, (int) button.bottom + y);
            spinner.setAlpha((int) (0xFF * loading));
            spinner.draw(canvas);
            invalidate();
        } else {
            stopSpinner();
        }
        canvas.restore();
    }

    public void stopSpinner() {
        if (spinner != null && spinner.isVisible()) spinner.setVisible(false, false);
    }

    private int updateShownState(int state) {
        if (state != STATE_LOADING) {
            loadingSince = 0;
            return shownState = state;
        }
        if (shownState == STATE_LOADING) return STATE_LOADING;
        long now = SystemClock.uptimeMillis();
        if (loadingSince == 0) loadingSince = now;
        long left = LOADING_DELAY_MS - (now - loadingSince);
        if (left > 0) {
            if (view != null) view.postInvalidateDelayed(left);
            return shownState;
        }
        return shownState = STATE_LOADING;
    }

    private String label(int width, int state) {
        int res = state == STATE_FAILED ? R.string.WidgetLoadFailed : withButton ? R.string.WidgetRun : R.string.WidgetNotRunning;
        float maxTextWidth = Math.max(0, width - dp(BUTTON_PADDING_DP) * 4 - dp(ICON_WIDTH_DP) - dp(ICON_GAP_DP));
        return TextUtils.ellipsize(LocaleController.getString(res), textPaint, maxTextWidth, TextUtils.TruncateAt.END).toString();
    }

    public boolean onTouchEvent(MotionEvent event, View view, Runnable onClick) {
        if (!withButton || state != STATE_IDLE) return false;
        if (bounce == null) bounce = new ButtonBounce(view);
        else bounce.setView(view);
        boolean inside = button.contains(event.getX(), event.getY());
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!inside) return false;
                bounce.setPressed(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (bounce.isPressed() && !inside) bounce.setPressed(false);
                return bounce.isPressed();
            case MotionEvent.ACTION_UP:
                if (!bounce.isPressed()) return false;
                bounce.setPressed(false);
                view.playSoundEffect(SoundEffectConstants.CLICK);
                onClick.run();
                return true;
            case MotionEvent.ACTION_CANCEL:
                bounce.setPressed(false);
                return false;
        }
        return bounce.isPressed();
    }

    private void invalidate() {
        if (view != null) view.invalidate();
    }
}
