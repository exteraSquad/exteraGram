/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.pillstack.ui.pills.system;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.exteragram.messenger.pillstack.core.PillStackConfig;
import com.exteragram.messenger.pillstack.core.PillType;
import com.exteragram.messenger.pillstack.ui.PillStackPreferencesActivity;
import com.exteragram.messenger.pillstack.ui.pills.BasePill;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.CacheControlActivity;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.LaunchActivity;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.telegram.messenger.NotificationCenter;

@SuppressLint("ViewConstructor")
public class CachePill extends BasePill implements NotificationCenter.NotificationCenterDelegate {

    private final ImageView iconView;
    private final AnimatedTextView textView;
    private final LinearLayout layout;
    private final StorageProgressDrawable progressDrawable;
    
    private static final AtomicLong lastKnownCacheSize = new AtomicLong(-1);
    private static float lastKnownProgress = -1f;
    private final AtomicBoolean calculating = new AtomicBoolean(false);

    public CachePill(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);

        layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER);
        layout.setMinimumWidth(dp(48));
        layout.setPadding(dp(6), 0, dp(8), 0);
        addView(layout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 28, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT)));

        iconView = new ImageView(context);
        iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        layout.addView(iconView, LayoutHelper.createLinear(16, 16, Gravity.CENTER_VERTICAL, 0, 0, 6, 0));
        progressDrawable = new StorageProgressDrawable(iconView);
        iconView.setImageDrawable(progressDrawable);

        textView = new AnimatedTextView(context, true, true, true);
        textView.setTextSize(dp(13));
        textView.setTypeface(AndroidUtilities.bold());
        textView.setIncludeFontPadding(false);
        textView.adaptWidth = true;
        layout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        setLoadingTargetView(layout);
        updateColors();
        ScaleStateListAnimator.apply(layout);

        if (lastKnownCacheSize.get() != -1 && !isRefreshDue()) {
            setData(lastKnownCacheSize.get(), lastKnownProgress, false);
        } else {
            iconView.setVisibility(GONE);
            textView.setVisibility(GONE);
        }
    }

    @Override
    public long getRefreshInterval() {
        return 3 * 60 * 1000L; // 3 minutes
    }

    @Override
    public int getPillId() {
        return PillType.CACHE.getId();
    }

    @Override
    public void onUpdateData(boolean force) {
        boolean firstRun = lastKnownCacheSize.get() == -1;
        if (force || firstRun || isRefreshDue()) {
            if (!calculating.compareAndSet(false, true)) {
                return;
            }
            if (force || firstRun) {
                CacheControlActivity.resetCalculatedTotalSIze();
            }
            startLoading();

            ImageLoader.getInstance().checkMediaPaths(() -> {
                CacheControlActivity.calculateTotalSize(cacheSize -> {
                    lastKnownCacheSize.set(cacheSize);

                    CacheControlActivity.getDeviceTotalSize((totalSize, freeSize) -> {
                        float progress = 0f;
                        if (totalSize > 0) {
                            progress = (float) (totalSize - freeSize) / totalSize;
                        }
                        lastKnownProgress = progress;

                        final float finalProgress = progress;
                        calculating.set(false);
                        setData(cacheSize, finalProgress, true);
                    });
                });
            });
        }
    }

    private void setData(long cacheSize, float progress, boolean animate) {
        stopLoading();
        String sizeText = AndroidUtilities.formatFileSize(cacheSize);
        if (animate && (textView.getText() == null || !TextUtils.equals(textView.getText(), sizeText) || textView.getVisibility() == GONE)) {
            animateSizeChange();
        }
        textView.setText(sizeText, animate);
        progressDrawable.setProgress(progress, animate);

        iconView.setVisibility(VISIBLE);
        textView.setVisibility(VISIBLE);
        markDataUpdated();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.pillStackSettingsChanged);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.pillStackSettingsChanged);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.pillStackSettingsChanged) {
            if (!PillStackConfig.shouldUpdatePill(args, getPillId())) {
                return;
            }
            PillStackConfig.checkAndClearPendingUpdate(getPillId());
            onUpdateData(true);
        }
    }

    @Override
    public void onPillClicked() {
        openCacheSettings();
    }

    @Override
    public boolean onPillLongClicked() {
        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (fragment != null) {
            addUpdatedAt(ItemOptions.makeOptions(fragment, this)
                    .add(R.drawable.msg2_data, LocaleController.getString(R.string.StorageUsage), this::openCacheSettings)
                    .addGap()
                    .add(R.drawable.msg_retry, LocaleController.getString(R.string.Refresh), () -> onUpdateData(true))
                    .add(R.drawable.msg_settings, LocaleController.getString(R.string.Settings), () -> fragment.presentFragment(new PillStackPreferencesActivity())))
                    .setDrawScrim(false)
                    .setDimAlpha(0)
                    .setDismissOnMoveOutside(true)
                    .show();
            return true;
        }
        return false;
    }

    private void openCacheSettings() {
        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (fragment != null) {
            fragment.presentFragment(new CacheControlActivity());
        }
    }

    @Override
    public void updateColors() {
        int textColor = getThemedColor(Theme.key_windowBackgroundWhiteBlackText, 0.75f);
        int backgroundColor = Theme.isCurrentThemeDark() ? getThemedColor(Theme.key_windowBackgroundWhite) : Theme.multAlpha(textColor, 0.09f);
        layout.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(14), backgroundColor, Theme.multAlpha(textColor, 0.1f)));
        textView.setTextColor(textColor);
        progressDrawable.setColor(textColor);
        updateLoadingColors();
    }

    private static class StorageProgressDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rectF = new RectF();
        private final AnimatedFloat animatedProgress;
        private float progress = 0f;
        private int color;

        public StorageProgressDrawable(View parent) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            animatedProgress = new AnimatedFloat(parent, 650, CubicBezierInterpolator.EASE_OUT_QUINT);
        }

        public void setProgress(float progress, boolean animated) {
            this.progress = Math.max(0.05f, Math.min(progress, 1f));
            if (!animated) {
                animatedProgress.force(this.progress);
            }
            invalidateSelf();
        }

        public void setColor(int color) {
            this.color = color;
            invalidateSelf();
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            int width = getBounds().width();
            int height = getBounds().height();
            float size = Math.min(width, height) - dp(2);
            float x = (width - size) / 2f;
            float y = (height - size) / 2f;
            rectF.set(x, y, x + size, y + size);

            float p = animatedProgress.set(progress);

            paint.setStrokeWidth(dp(2));
            
            paint.setColor(color);
            paint.setAlpha(50);
            canvas.drawCircle(width / 2f, height / 2f, size / 2f, paint);

            paint.setAlpha(255);
            canvas.drawArc(rectF, -90, 360 * p, false, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @SuppressWarnings("deprecation")
        @Override
        public int getOpacity() {
            return PixelFormat.TRANSPARENT;
        }
    }
}
