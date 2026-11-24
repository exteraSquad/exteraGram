/*
 * This is the source code of exteraGram for Android.
 *
 * We do not and cannot prevent the use of our code,
 * but be respectful and credit the original author.
 *
 * Copyright @exteraGram, 2022-2026
 *
 * Licensed under the GNU GPL v. 3 or later, with the additional terms
 * in ADDITIONAL_TERMS.md at the root of the repository.
 */

package com.exteragram.messenger.preferences.appearance.components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.TabCounterMode;
import com.exteragram.messenger.preferences.components.CustomPreferenceCell;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.FilterTabsView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.utils.glass.source.GlassSourceColor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@SuppressLint("ViewConstructor")
public class FilterTabsPreviewCell extends FrameLayout implements CustomPreferenceCell, NotificationCenter.NotificationCenterDelegate {

    private final FilterTabsView filterTabsView;
    private final Map<Integer, Integer> idsWithCounters = new HashMap<>();
    private final int COUNTER_SEED = 20 + Utilities.random.nextInt(40);

    public FilterTabsPreviewCell(Context context) {
        super(context);
        setWillNotDraw(false);

        var iBlur3SourceColor = new GlassSourceColor();
        iBlur3SourceColor.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        BlurredBackgroundDrawableViewFactory iBlur3FactoryLiquidGlass = new BlurredBackgroundDrawableViewFactory(iBlur3SourceColor);

        filterTabsView = new FilterTabsView(context, null);
        filterTabsView.setStaticAllChats(true);
        filterTabsView.setPadding(0, dp(7), 0, dp(7));
        BlurredBackgroundDrawable filterTabsViewBackground = iBlur3FactoryLiquidGlass.create(filterTabsView, BlurredBackgroundProviderImpl.topPanel(null));
        filterTabsViewBackground.setRadius(dp(18));
        filterTabsViewBackground.setPadding(dp(6.666f));
        filterTabsView.setBlurredBackground(filterTabsViewBackground);
        filterTabsView.setColors(
                Theme.key_actionBarTabLine,
                Theme.key_actionBarTabActiveText,
                Theme.key_actionBarTabUnactiveText,
                Theme.key_actionBarTabSelector,
                Theme.key_actionBarDefault
        );

        filterTabsView.setDelegate(new FilterTabsView.FilterTabsViewDelegate() {
            @Override
            public void onPageSelected(FilterTabsView.Tab tab, boolean forward) {}

            @Override
            public void onPageScrolled(float progress) {}

            @Override
            public void onSamePageSelected() {}

            @Override
            public int getTabCounter(int tabId) {
                if (ExteraConfig.getTabCounterMode() == TabCounterMode.HIDDEN) {
                    return 0;
                }
                return idsWithCounters.computeIfAbsent(tabId, k -> 0);
            }

            @Override
            public boolean didSelectTab(FilterTabsView.TabView tabView, boolean selected) {
                return false;
            }

            @Override
            public boolean isTabMenuVisible() {
                return false;
            }

            @Override
            public void onDeletePressed(int id) {}

            @Override
            public void onPageReorder(int fromId, int toId) {}

            @Override
            public boolean canPerformActions() {
                return false;
            }
        });

        addView(filterTabsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 50, Gravity.CENTER, 12, 0, 12, 0));
        updateTabs(false);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return true;
    }

    private void updateTabs(boolean animated) {
        filterTabsView.resetTabId();
        filterTabsView.removeTabs();
        idsWithCounters.clear();

        int currentAccount = UserConfig.selectedAccount;
        ArrayList<MessagesController.DialogFilter> filters = MessagesController.getInstance(currentAccount).getDialogFilters();

        int visualIndex = 0;
        int idToSelect = -1;

        for (int a = 0; a < filters.size(); a++) {
            MessagesController.DialogFilter filter = filters.get(a);

            if (filter.isDefault() && ExteraConfig.getHideAllChats()) {
                continue;
            }

            if (a == 0 || a == 1 || a == 3) {
                idsWithCounters.put(filter.id, COUNTER_SEED / (a + 1));
            }

            if (idToSelect == -1) {
                idToSelect = filter.id;
            }

            if (filter.isDefault()) {
                filterTabsView.addTab(filter.id, 0, LocaleController.getString(R.string.FilterAllChats), "\uD83D\uDCAC", null, false, true, filter.locked);
            } else {
                filterTabsView.addTab(filter.id, filter.localId, filter.name, filter.emoticon == null ? "\uD83D\uDCC1" : filter.emoticon, filter.entities, filter.title_noanimate, false, filter.locked);
            }

            visualIndex++;
        }

        if (visualIndex < 6) {
            int[] fakeIds = {100, 101, 102, 103};
            String[] fakeTitles = {
                    LocaleController.getString(R.string.FilterContacts),
                    LocaleController.getString(R.string.FilterGroups),
                    LocaleController.getString(R.string.FilterChannels),
                    LocaleController.getString(R.string.FilterBots)
            };
            String[] fakeIcons = {"\uD83D\uDC64", "\uD83D\uDC65", "\uD83D\uDCE2", "\uD83E\uDD16"};

            for (int i = 0; i < fakeIds.length; i++) {
                if (visualIndex >= 6) break;

                int absoluteIndex = filters.size() + i;
                if (absoluteIndex == 0 || absoluteIndex == 1 || absoluteIndex == 3) {
                    idsWithCounters.put(fakeIds[i], COUNTER_SEED / (absoluteIndex + 1));
                }

                if (idToSelect == -1) {
                    idToSelect = fakeIds[i];
                }

                filterTabsView.addTab(fakeIds[i], fakeIds[i], fakeTitles[i], fakeIcons[i], null, false, false, false);
                visualIndex++;
            }
        }

        filterTabsView.finishAddingTabs(animated);

        if (idToSelect == -1 && !filters.isEmpty()) {
            idToSelect = filters.get(0).id;
        }

        if (idToSelect != -1) {
            filterTabsView.selectTabWithId(idToSelect, 1.0f);
        } else {
            filterTabsView.selectFirstTab();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getInstance(UserConfig.selectedAccount).addObserver(this, NotificationCenter.dialogFiltersUpdated);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getInstance(UserConfig.selectedAccount).removeObserver(this, NotificationCenter.dialogFiltersUpdated);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.dialogFiltersUpdated) {
            updateTabs(true);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(74), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawLine(0, getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, Theme.dividerPaint);
    }

    @Override
    public boolean isSameCell(@Nullable Object other) {
        return this == other;
    }
}