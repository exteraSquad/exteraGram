/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.pillstack.ui.pills;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.transition.ChangeBounds;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.text.format.DateUtils;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.exteragram.messenger.pillstack.core.PillStackConfig;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LoadingDrawable;

public abstract class BasePill extends FrameLayout {

    protected Theme.ResourcesProvider resourcesProvider;
    protected LoadingDrawable loadingDrawable;
    private final RectF rectF = new RectF();
    protected View loadingTargetView;
    protected boolean loading;

    private static final SparseArray<Long> globalLastUpdateTimes = new SparseArray<>();
    private static final SparseArray<Long> globalUpdatedAt = new SparseArray<>();

    private boolean stackVisible = true;

    private final Runnable autoRefreshRunnable = () -> {
        onUpdateData(false);
        scheduleNextUpdate();
    };

    public BasePill(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setLayoutParams(new FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT)
        ));
        setClipChildren(false);
        setClipToPadding(false);
    }

    public boolean isLoading() {
        return loading;
    }

    public void onPillSelected() {
    }

    public void onPillUnselected() {
    }

    public void onUpdateData(boolean force) {
    }

    public void onPillClicked() {
    }

    public boolean onPillLongClicked() {
        return false;
    }

    public void updateColors() {
    }

    public abstract long getRefreshInterval();

    public abstract int getPillId();

    private void scheduleNextUpdate() {
        removeCallbacks(autoRefreshRunnable);
        if (!stackVisible) {
            return;
        }
        long interval = getRefreshInterval();
        if (interval > 0) {
            postDelayed(autoRefreshRunnable, interval);
        }
    }

    protected boolean isRefreshDue() {
        long interval = getRefreshInterval();
        if (interval <= 0) {
            return true;
        }
        long lastUpdateTime = globalLastUpdateTimes.get(getPillId(), 0L);
        return lastUpdateTime == 0 || SystemClock.elapsedRealtime() - lastUpdateTime >= interval;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        long interval = getRefreshInterval();
        if (interval <= 0) {
            return;
        }
        if (PillStackConfig.checkAndClearPendingUpdate(getPillId())) {
            onUpdateData(true);
            scheduleNextUpdate();
            return;
        }
        if (!stackVisible) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        long lastUpdateTime = globalLastUpdateTimes.get(getPillId(), 0L);
        if (lastUpdateTime == 0 || now - lastUpdateTime >= interval) {
            autoRefreshRunnable.run();
        } else {
            postDelayed(autoRefreshRunnable, interval - (now - lastUpdateTime));
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(autoRefreshRunnable);
    }

    public void onStackVisibilityChanged(boolean visible) {
        if (stackVisible == visible) {
            return;
        }
        stackVisible = visible;
        if (visible) {
            long interval = getRefreshInterval();
            if (interval > 0 && isAttachedToWindow()) {
                if (isRefreshDue()) {
                    onUpdateData(false);
                }
                scheduleNextUpdate();
            }
        } else {
            removeCallbacks(autoRefreshRunnable);
        }
    }

    protected void markDataUpdated() {
        globalLastUpdateTimes.put(getPillId(), SystemClock.elapsedRealtime());
        globalUpdatedAt.put(getPillId(), System.currentTimeMillis());
        scheduleNextUpdate();
    }

    protected long getLastUpdatedAt() {
        return globalUpdatedAt.get(getPillId(), 0L);
    }

    protected ItemOptions addUpdatedAt(ItemOptions options) {
        long time = getLastUpdatedAt();
        if (time <= 0) {
            return options;
        }
        String formatted = LocaleController.getInstance().getFormatterDay().format(time);
        TextView textView = new TextView(options.getContext());
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        textView.setTypeface(AndroidUtilities.regular());
        textView.setTextColor(Theme.getColor(Theme.key_groupcreate_sectionText, resourcesProvider));
        textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        textView.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(9), AndroidUtilities.dp(18), AndroidUtilities.dp(9));
        textView.setTag(R.id.fit_width_tag, 1);
        if (DateUtils.isToday(time)) {
            textView.setText(LocaleController.formatString(R.string.CustomPillUpdatedAt, formatted));
        } else {
            String date = LocaleController.formatString(R.string.formatDateAtTime, LocaleController.getInstance().getFormatterDayMonth().format(time), formatted);
            textView.setText(LocaleController.formatString(R.string.CustomPillUpdatedDate, date));
        }
        options.addGap();
        options.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        return options;
    }

    protected void setLoadingTargetView(View view) {
        this.loadingTargetView = view;
    }

    public void startLoading() {
        loading = true;
        if (loadingDrawable == null) {
            loadingDrawable = new LoadingDrawable(resourcesProvider);
            loadingDrawable.setCallback(this);
            loadingDrawable.setGradientScale(2.0f);
            loadingDrawable.setRadiiDp(14);
            updateLoadingColors();
        }
        loadingDrawable.reset();
        loadingDrawable.resetDisappear();
        loadingDrawable.setAlpha(255);
        invalidate();
    }

    protected void animateSizeChange() {
        if (!isLaidOut() || getVisibility() != View.VISIBLE) {
            return;
        }
        if (getParent() != null && getParent().getParent() instanceof ViewGroup) {
            TransitionManager.beginDelayedTransition((ViewGroup) getParent().getParent(), new TransitionSet()
                    .addTransition(new ChangeBounds())
                    .setDuration(300)
                    .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT));
        }
    }

    public void stopLoading() {
        loading = false;
        if (loadingDrawable != null) {
            loadingDrawable.disappear();
        }
    }

    protected void updateLoadingColors() {
        if (loadingDrawable != null) {
            int textColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
            loadingDrawable.setColors(
                    Theme.multAlpha(textColor, 0.05f), Theme.multAlpha(textColor, 0.15f),
                    Theme.multAlpha(textColor, 0.10f), Theme.multAlpha(textColor, 0.25f));
        }
    }

    protected int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    protected int getThemedColor(int key, float alpha) {
        return Theme.multAlpha(getThemedColor(key), alpha);
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        super.dispatchDraw(canvas);
        if (loadingDrawable != null && (loadingDrawable.getAlpha() > 0 || !loadingDrawable.isDisappearing())) {
            View target = loadingTargetView != null ? loadingTargetView : this;
            rectF.set(target.getLeft(), target.getTop(), target.getRight(), target.getBottom());
            if (loadingDrawable.stroke) {
                float half = loadingDrawable.strokePaint.getStrokeWidth() / 2f;
                rectF.inset(half, half);
            }
            loadingDrawable.setBounds(rectF);
            loadingDrawable.draw(canvas);
            invalidate();
        }
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return who == loadingDrawable || super.verifyDrawable(who);
    }
}
