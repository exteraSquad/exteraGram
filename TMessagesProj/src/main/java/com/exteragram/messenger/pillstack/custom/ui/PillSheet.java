/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.pillstack.custom.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;

public abstract class PillSheet extends BottomSheetWithRecyclerListView {

    protected final BaseFragment fragment;
    protected UniversalAdapter adapter;
    @Nullable
    protected ButtonWithCounterView button;
    private final ImageView closeView;
    @Nullable
    private FrameLayout buttonContainer;
    private int navigationPadding = -1;
    private boolean ready;

    public PillSheet(BaseFragment fragment, boolean needFocus, @Nullable CharSequence buttonText) {
        super(fragment.getParentActivity(), fragment, needFocus, false, false, false, ActionBarType.SLIDING, fragment.getResourceProvider());
        Context context = getContext();
        this.fragment = fragment;

        closeView = new ImageView(context);
        closeView.setScaleType(ImageView.ScaleType.CENTER);
        closeView.setImageResource(R.drawable.ic_close_white);
        closeView.setColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        closeView.setBackground(Theme.createSelectorDrawable(Theme.multAlpha(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), .10f)));
        actionBar.addView(closeView, LayoutHelper.createFrame(54, 54, Gravity.BOTTOM | Gravity.RIGHT, 0, 0, 8, 0));
        ScaleStateListAnimator.apply(closeView, .1f, 1.5f);
        closeView.setOnClickListener(v -> dismiss());

        occupyNavigationBarWithoutKeyboard = true;
        fixNavigationBarKey(Theme.key_windowBackgroundGray);
        drawNavigationBar = false;
        drawDoubleNavigationBar = false;
        setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        recyclerListView.setClipToPadding(false);
        recyclerListView.setSections();
        recyclerListView.setOnItemClickListener((view, position) -> {
            UItem item = adapter.getItem(position - 1);
            if (item != null) {
                onItemClick(item, view);
            }
        });

        ignoreTouchActionBar = false;
        headerMoveTop = dp(12);
        topPadding = 0.3f;
        smoothKeyboardAnimationEnabled = true;
        takeTranslationIntoAccount = true;

        DefaultItemAnimator itemAnimator = new DefaultItemAnimator() {
            @Override
            protected void onMoveAnimationUpdate(RecyclerView.ViewHolder holder) {
                containerView.invalidate();
            }
        };
        itemAnimator.setSupportsChangeAnimations(false);
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDurations(350);
        recyclerListView.setItemAnimator(itemAnimator);

        if (buttonText != null) {
            buttonContainer = new FrameLayout(context);
            buttonContainer.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{
                    Theme.multAlpha(getThemedColor(Theme.key_windowBackgroundGray), 0.0f),
                    getThemedColor(Theme.key_windowBackgroundGray),
                    getThemedColor(Theme.key_windowBackgroundGray)
            }));
            FrameLayout.LayoutParams buttonContainerLayoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM);
            buttonContainerLayoutParams.leftMargin += backgroundPaddingLeft;
            buttonContainerLayoutParams.rightMargin += backgroundPaddingLeft;
            containerView.addView(buttonContainer, buttonContainerLayoutParams);

            button = new ButtonWithCounterView(context, resourcesProvider).setRound();
            button.setText(buttonText, false);
            button.setOnClickListener(v -> onButtonClick());
            buttonContainer.addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.FILL));
        }
        updateNavigationPadding();
    }

    @Override
    protected void onPreMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onPreMeasure(widthMeasureSpec, heightMeasureSpec);
        updateNavigationPadding();
    }

    private void updateNavigationPadding() {
        int padding = keyboardVisible ? 0 : AndroidUtilities.navigationBarHeight;
        if (navigationPadding == padding) {
            return;
        }
        navigationPadding = padding;
        recyclerListView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, (buttonContainer != null ? dp(6 + 48 + 12) : dp(12)) + padding);
        if (buttonContainer != null) {
            buttonContainer.setPadding(dp(12), dp(6), dp(12), dp(12) + padding);
        }
    }

    @Override
    protected void onActionBarAlpha(float alpha) {
        float t = 1f - alpha;
        closeView.setAlpha(t);
        closeView.setScaleX(t);
        closeView.setScaleY(t);
        closeView.setVisibility(t > 0 ? View.VISIBLE : View.INVISIBLE);
        closeView.setClickable(t > 0.5f);
    }

    protected void ready() {
        ready = true;
        updateTitle();
        adapter.update(false);
    }

    protected void setSegmented(boolean segmented) {
        recyclerListView.setSegmentedSectionsEnabled(segmented);
    }

    protected void update() {
        if (ready && adapter != null) {
            adapter.update(true);
        }
    }

    protected abstract void fillSheetItems(ArrayList<UItem> items, UniversalAdapter adapter);

    protected void onItemClick(UItem item, View view) {
    }

    protected void onButtonClick() {
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (!ready) {
            return;
        }
        items.add(UItem.asShadow(null));
        fillSheetItems(items, adapter);
    }

    @Override
    protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
        adapter = new UniversalAdapter(listView, getContext(), currentAccount, 0, true, this::fillItems, resourcesProvider);
        adapter.setApplyBackground(false);
        return adapter;
    }
}
