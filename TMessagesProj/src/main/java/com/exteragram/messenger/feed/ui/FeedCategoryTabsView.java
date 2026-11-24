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
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.exteragram.messenger.feed.FeedConfig;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.ChatSearchTabs;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.TopicsTabsView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.GradientClip;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class FeedCategoryTabsView extends FrameLayout {

    public interface Delegate {
        void onCategorySelected(int categoryId);

        void onCreateCategory();

        void onEditCategory(FeedConfig.Category category);

        void onHideCategory(FeedConfig.Category category);

        void onDeleteCategory(FeedConfig.Category category);
    }

    private static final int ADD_TAB_ID = -2;

    private final BaseFragment fragment;
    private final int currentAccount;
    private final Theme.ResourcesProvider resourcesProvider;
    private final Delegate delegate;
    private final UniversalRecyclerView listView;
    private HashMap<Integer, TLRPC.TL_forumTopic> topics = new HashMap<>();

    private final int[] location = new int[2];
    private int selectedCategoryId;
    private int drawnSelectedCategoryId;
    private int animateFromCategoryId;

    public FeedCategoryTabsView(Context context, BaseFragment fragment, int currentAccount, Theme.ResourcesProvider resourcesProvider, Delegate delegate) {
        super(context);
        this.fragment = fragment;
        this.currentAccount = currentAccount;
        this.resourcesProvider = resourcesProvider;
        this.delegate = delegate;
        selectedCategoryId = drawnSelectedCategoryId = FeedConfig.getInstance(currentAccount).getSelectedCategoryId();

        listView = new UniversalRecyclerView(context, currentAccount, 0, this::fillItems, this::onItemClick, this::onItemLongClick, resourcesProvider) {
            private final GradientClip clip = new GradientClip();
            private final AnimatedFloat animatedClipL = new AnimatedFloat(this, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
            private final AnimatedFloat animatedClipR = new AnimatedFloat(this, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
            private final AnimatedFloat animateTab = new AnimatedFloat(this, 420, CubicBezierInterpolator.EASE_OUT_QUINT);
            private final RectF selectionRect = new RectF();
            private final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void dispatchDraw(@NonNull Canvas canvas) {
                final float clipAlphaL = animatedClipL.set(canScrollHorizontally(-1));
                final float clipAlphaR = animatedClipR.set(canScrollHorizontally(1));
                final boolean needClip = clipAlphaL > 0 || clipAlphaR > 0;
                if (needClip) {
                    canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), 0xFF);
                }
                super.dispatchDraw(canvas);
                drawSelection(canvas);
                if (needClip) {
                    canvas.save();
                    if (clipAlphaL > 0) {
                        AndroidUtilities.rectTmp.set(0, 0, dp(12), getHeight());
                        clip.draw(canvas, AndroidUtilities.rectTmp, GradientClip.LEFT, clipAlphaL);
                    }
                    if (clipAlphaR > 0) {
                        AndroidUtilities.rectTmp.set(getWidth() - dp(12), 0, getWidth(), getHeight());
                        clip.draw(canvas, AndroidUtilities.rectTmp, GradientClip.RIGHT, clipAlphaR);
                    }
                    canvas.restore();
                    canvas.restore();
                }
            }

            private void drawSelection(Canvas canvas) {
                if (drawnSelectedCategoryId != selectedCategoryId) {
                    animateFromCategoryId = drawnSelectedCategoryId;
                    animateTab.force(0.0f);
                }
                drawnSelectedCategoryId = selectedCategoryId;
                View selectedTab = null;
                View fromTab = null;
                for (int i = 0; i < getChildCount(); i++) {
                    final View child = getChildAt(i);
                    final UItem item = adapter.getItem(getChildAdapterPosition(child));
                    if (item == null || item.id == ADD_TAB_ID) {
                        continue;
                    }
                    if (item.id == selectedCategoryId) {
                        selectedTab = child;
                    }
                    if (item.id == animateFromCategoryId) {
                        fromTab = child;
                    }
                }
                if (selectedTab == null) {
                    return;
                }
                setTabRect(selectionRect, selectedTab);
                if (fromTab != null) {
                    setTabRect(AndroidUtilities.rectTmp, fromTab);
                    lerp(AndroidUtilities.rectTmp, selectionRect, animateTab.set(1.0f), selectionRect);
                }
                selectionPaint.setColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider), 31));
                canvas.drawRoundRect(selectionRect, dp(14), dp(14), selectionPaint);
            }

            private void setTabRect(RectF rect, View tab) {
                rect.set(tab.getX() + dp(1), tab.getY() + dp(4), tab.getX() + tab.getWidth() - dp(1), tab.getY() + getHeight() - dp(4));
            }

            @Override
            public Integer getSelectorColor(int position) {
                return 0;
            }
        };
        listView.setWillNotDraw(false);
        listView.adapter.setApplyBackground(false);
        listView.makeHorizontal();
        listView.setPadding(dp(3), 0, dp(3), 0);
        listView.setClipToPadding(false);
        addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, TopicsTabsView.TOP_TABS_HEIGHT, Gravity.CENTER_VERTICAL));
    }

    public void attachTo(ChatSearchTabs host) {
        if (host.tabs != null) {
            host.tabs.setVisibility(GONE);
        }
        host.setPadding(0, dp(7), 0, dp(7));
        host.addView(this, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 7, 0, 7, 0));
    }

    public boolean isTouchInside(MotionEvent ev) {
        if (ev == null || !isShown()) {
            return false;
        }
        getLocationOnScreen(location);
        return ev.getRawX() >= location[0] && ev.getRawX() < location[0] + getWidth()
                && ev.getRawY() >= location[1] && ev.getRawY() < location[1] + getHeight();
    }

    public boolean hasCategories() {
        return !FeedConfig.getInstance(currentAccount).getVisibleCategories().isEmpty();
    }

    public void setSelectedCategory(int categoryId) {
        if (selectedCategoryId == categoryId) {
            return;
        }
        selectedCategoryId = categoryId;
        listView.adapter.update(true);
        listView.invalidate();
        scrollToSelected();
    }

    public void update() {
        selectedCategoryId = FeedConfig.getInstance(currentAccount).getSelectedCategoryId();
        listView.adapter.update(true);
        listView.invalidate();
        scrollToSelected();
    }

    private void scrollToSelected() {
        final List<FeedConfig.Category> categories = FeedConfig.getInstance(currentAccount).getVisibleCategories();
        int position = 0;
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).getId() == selectedCategoryId) {
                position = i + 1;
                break;
            }
        }
        listView.smoothScrollToPosition(position);
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        final HashMap<Integer, TLRPC.TL_forumTopic> previousTopics = topics;
        topics = new HashMap<>();
        items.add(TopicsTabsView.HorizontalTabView.Factory.asAll(false, false).setChecked(selectedCategoryId == 0));
        for (FeedConfig.Category category : FeedConfig.getInstance(currentAccount).getVisibleCategories()) {
            TLRPC.TL_forumTopic topic = previousTopics.get(category.getId());
            if (topic == null) {
                topic = new TLRPC.TL_forumTopic();
            }
            topic.id = category.getId();
            topic.title = category.getTitle();
            topic.icon_emoji_id = category.getEmojiId();
            topics.put(category.getId(), topic);
            items.add(TopicsTabsView.HorizontalTabView.Factory.asTab(0, topic, false).setChecked(category.getId() == selectedCategoryId));
        }
        items.add(TopicsTabsView.HorizontalTabView.Factory.asAdd());
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ADD_TAB_ID) {
            delegate.onCreateCategory();
        } else if (item.id != selectedCategoryId) {
            delegate.onCategorySelected(item.id);
        }
    }

    private boolean onItemLongClick(UItem item, View view, int position, float x, float y) {
        final FeedConfig.Category category = item.id > 0 ? FeedConfig.getInstance(currentAccount).getCategory(item.id) : null;
        if (category == null) {
            return false;
        }
        ItemOptions.makeOptions(fragment, view)
                .add(R.drawable.msg_edit, getString(R.string.FeedCategoryEdit), () -> delegate.onEditCategory(category))
                .add(R.drawable.msg_view_file, getString(R.string.FeedCategoryHide), () -> delegate.onHideCategory(category))
                .add(R.drawable.msg_delete, getString(R.string.FeedCategoryDelete), true, () -> delegate.onDeleteCategory(category))
                .setScrimViewBackground(new TabScrimDrawable(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground, resourcesProvider)))
                .setGravity(Gravity.CENTER_HORIZONTAL)
                .setDismissOnMoveOutside(true)
                .show();
        return true;
    }

    private static class TabScrimDrawable extends Drawable {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bounds = new RectF();

        TabScrimDrawable(int color) {
            paint.setColor(color);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            bounds.set(getBounds());
            bounds.inset(dp(1), (bounds.height() - dp(28)) / 2f);
            canvas.drawRoundRect(bounds, dp(14), dp(14), paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
        }

        @SuppressWarnings("deprecation")
        @Override
        public int getOpacity() {
            return PixelFormat.TRANSPARENT;
        }
    }
}
