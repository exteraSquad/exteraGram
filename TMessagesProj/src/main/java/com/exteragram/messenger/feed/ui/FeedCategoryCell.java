/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.feed.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import android.widget.ImageView;

import androidx.core.content.ContextCompat;

import com.exteragram.messenger.feed.FeedConfig;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.Objects;

@SuppressLint("ViewConstructor")
public class FeedCategoryCell extends TextCell {

    private static final float HIDDEN_ALPHA = 0.5f;
    private static final int VISIBILITY_DURATION = 200;

    private final Theme.ResourcesProvider resourcesProvider;
    private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable iconDrawable;
    private final Drawable placeholderDrawable;
    private final Drawable reorderDrawable;
    private final ImageView visibilityView;
    private final Drawable shownDrawable;
    private final Drawable hiddenDrawable;
    private ValueAnimator visibilityAnimator;
    private float hiddenProgress;
    private int boundCategoryId;

    public FeedCategoryCell(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);
        this.resourcesProvider = resourcesProvider;
        iconDrawable = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(imageView, dp(24));
        iconDrawable.setCurrentAccount(currentAccount);
        placeholderDrawable = tinted(context, R.drawable.msg_folders);
        reorderDrawable = tinted(context, R.drawable.list_reorder);

        shownDrawable = tinted(context, R.drawable.msg_view_file);
        hiddenDrawable = tinted(context, R.drawable.menu_hide_gift);
        visibilityView = new ImageView(context);
        visibilityView.setScaleType(ImageView.ScaleType.CENTER);
        visibilityView.setImageDrawable(new LayerDrawable(new Drawable[]{shownDrawable, hiddenDrawable}));
        setHiddenProgress(0f);
        visibilityView.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), Theme.RIPPLE_MASK_CIRCLE_20DP));
        addView(visibilityView);
    }

    private Drawable tinted(Context context, int resId) {
        Drawable drawable = ContextCompat.getDrawable(context, resId).mutate();
        drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon, resourcesProvider), PorterDuff.Mode.MULTIPLY));
        return drawable;
    }

    public void set(FeedConfig.Category category, boolean divider, View.OnClickListener onVisibilityClick) {
        boolean animated = boundCategoryId == category.getId();
        boundCategoryId = category.getId();
        if (category.getEmojiId() != 0) {
            iconDrawable.set(category.getEmojiId(), AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW, false);
            iconDrawable.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        } else {
            iconDrawable.set(placeholderDrawable, false);
            iconDrawable.setColor(null);
        }
        setTextAndIconAndValueDrawable(category.getTitle(), iconDrawable, reorderDrawable, divider);
        imageView.setPadding(0, dp(7), 0, 0);

        final boolean hidden = category.isHidden();
        final float targetProgress = hidden ? 1f : 0f;
        if (visibilityAnimator != null) {
            visibilityAnimator.cancel();
            visibilityAnimator = null;
        }
        if (animated && hiddenProgress != targetProgress) {
            visibilityAnimator = ValueAnimator.ofFloat(hiddenProgress, targetProgress);
            visibilityAnimator.addUpdateListener(a -> setHiddenProgress((float) a.getAnimatedValue()));
            visibilityAnimator.setDuration(VISIBILITY_DURATION);
            visibilityAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            visibilityAnimator.start();
        } else {
            setHiddenProgress(targetProgress);
        }
        visibilityView.setContentDescription(getString(hidden ? R.string.FeedCategoryShow : R.string.FeedCategoryHide));
        visibilityView.setOnClickListener(onVisibilityClick);
        final float alpha = hidden ? HIDDEN_ALPHA : 1f;
        final View[] views = {textView, imageView, visibilityView};
        for (View view : views) {
            view.animate().cancel();
            if (animated) {
                view.animate().alpha(alpha).setDuration(VISIBILITY_DURATION).start();
            } else {
                view.setAlpha(alpha);
            }
        }
    }

    private void setHiddenProgress(float progress) {
        hiddenProgress = progress;
        shownDrawable.setAlpha((int) (255 * (1f - progress)));
        hiddenDrawable.setAlpha((int) (255 * progress));
        visibilityView.invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        visibilityView.measure(MeasureSpec.makeMeasureSpec(dp(40), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(40), MeasureSpec.EXACTLY));
        final int textWidth = getMeasuredWidth() - dp(offsetFromImage) - dp(valueImageRight + 24 + 8 + 40 + 8);
        textView.measure(MeasureSpec.makeMeasureSpec(Math.max(0, textWidth), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(dp(20), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        final int width = right - left;
        final int size = visibilityView.getMeasuredWidth();
        final int viewTop = (bottom - top - size) / 2;
        final int offset = dp(valueImageRight + 24 + 8);
        final int viewLeft = LocaleController.isRTL ? offset : width - offset - size;
        visibilityView.layout(viewLeft, viewTop, viewLeft + size, viewTop + size);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        iconDrawable.attach();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        iconDrawable.detach();
    }

    public static class Factory extends UItem.UItemFactory<FeedCategoryCell> {
        static {
            setup(new Factory());
        }

        @Override
        public FeedCategoryCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new FeedCategoryCell(context, currentAccount, resourcesProvider);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            if (view instanceof FeedCategoryCell cell && item.object instanceof FeedConfig.Category category) {
                cell.set(category, divider, item.clickCallback);
            }
        }

        @Override
        public boolean equals(UItem a, UItem b) {
            return a.id == b.id;
        }

        @Override
        public boolean contentsEquals(UItem a, UItem b) {
            return Objects.equals(a.object, b.object);
        }

        public static UItem of(int id, FeedConfig.Category category, View.OnClickListener onVisibilityClick) {
            UItem item = UItem.ofFactory(Factory.class);
            item.id = id;
            item.object = category;
            item.clickCallback = onVisibilityClick;
            return item;
        }
    }
}
