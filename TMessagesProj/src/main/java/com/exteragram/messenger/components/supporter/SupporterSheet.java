/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.components.supporter;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import com.exteragram.messenger.badges.BadgesController;
import com.exteragram.messenger.utils.ui.UIUtil;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.MessageDrawable;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;

public abstract class SupporterSheet extends BottomSheetWithRecyclerListView {

    private static final int BOTTOM_FADE = 16;

    protected final BaseFragment fragment;
    protected final ButtonWithCounterView button;
    protected final LinearLayout bottomContainer;
    private final SupporterArrowHeader header;
    private LinearLayout secondaryRow;
    private SupporterEnterFrom enterFrom;
    private ValueAnimator enterAnimator;
    private float enterProgress = 1f;
    private final int[] location = new int[2];
    private ArrayList<View> views;
    private UniversalAdapter adapter;
    private CharSequence title;
    private boolean touched;
    private TextView titleView;
    private TextView subtitleView;

    public SupporterSheet(BaseFragment fragment, Theme.ResourcesProvider resourcesProvider) {
        this(fragment, false, resourcesProvider);
    }

    protected SupporterSheet(BaseFragment fragment, boolean needFocus, Theme.ResourcesProvider resourcesProvider) {
        super(fragment.getParentActivity(), fragment, needFocus, false, false, true, ActionBarType.FADING, resourcesProvider);
        this.fragment = fragment;
        currentAccount = fragment.getCurrentAccount();
        final var context = fragment.getParentActivity();

        occupyNavigationBar = !needFocus;
        smoothKeyboardAnimationEnabled = needFocus;
        smoothKeyboardByBottom = needFocus;
        setApplyBottomPadding(false);
        topPadding = 0.2f;
        fixNavigationBar(getThemedColor(Theme.key_dialogBackground));

        recyclerListView.setClipToPadding(false);
        recyclerListView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        recyclerListView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);
        recyclerListView.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    touched = true;
                }
                return false;
            }
        });
        recyclerListView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (!touched && recyclerListView.canScrollVertically(-1)) {
                layoutManager.scrollToPositionWithOffset(0, 0);
            }
        });

        views = new ArrayList<>();
        header = new SupporterArrowHeader(context, resourcesProvider);

        button = new ButtonWithCounterView(context, true, resourcesProvider);
        button.setRound();

        bottomContainer = new LinearLayout(context);
        bottomContainer.setOrientation(LinearLayout.VERTICAL);
        bottomContainer.setBackground(createBottomBackdrop(getThemedColor(Theme.key_dialogBackground)));
        bottomContainer.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, needFocus ? 0 : AndroidUtilities.navigationBarHeight);
        bottomContainer.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 12, BOTTOM_FADE, 12, 12));
        bottomContainer.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            final int height = bottom - top;
            if (recyclerListView.getPaddingBottom() != height) {
                recyclerListView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, height);
            }
        });
        containerView.addView(bottomContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));
    }

    private static Drawable createBottomBackdrop(int color) {
        final LayerDrawable drawable = new LayerDrawable(new Drawable[]{UIUtil.createBottomFade(color), new ColorDrawable(color)});
        drawable.setLayerGravity(0, Gravity.TOP | Gravity.FILL_HORIZONTAL);
        drawable.setLayerHeight(0, dp(BOTTOM_FADE));
        drawable.setLayerInsetTop(1, dp(BOTTOM_FADE));
        return drawable;
    }

    protected void setContent(CharSequence title, CharSequence subtitle) {
        this.title = title;
        updateTitle();
        views.clear();
        views.add(header);

        final var context = getContext();
        titleView = new TextView(context);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setText(Emoji.replaceEmoji(title, titleView.getPaint().getFontMetricsInt(), false));
        titleView.setGravity(Gravity.CENTER_HORIZONTAL);
        titleView.setPadding(dp(22), 0, dp(22), 0);
        views.add(titleView);

        if (subtitle == null) {
            subtitleView = null;
            return;
        }
        final LinkSpanDrawable.LinksTextView linksView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
        linksView.setDisablePaddingsOffsetY(true);
        subtitleView = linksView;
        subtitleView.setLinkTextColor(getThemedColor(Theme.key_chat_messageLinkIn));
        subtitleView.setText(subtitle);
        subtitleView.setTypeface(AndroidUtilities.regular());
        subtitleView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitleView.setGravity(Gravity.CENTER_HORIZONTAL);
        subtitleView.setPadding(dp(22), dp(6), dp(22), dp(14));
        views.add(subtitleView);
    }

    protected void addView(View view) {
        views.add(view);
    }

    protected void addHeaderAccessory(View view) {
        views.add(views.indexOf(header) + 1, view);
    }

    protected void addNumberPill(Integer number, boolean customBadge) {
        if (number == null) {
            return;
        }
        final SupporterArrowView arrowView = header.arrowView;
        final GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{arrowView.color1, arrowView.color2});
        background.setCornerRadius(dp(12));

        TextView pill = new TextView(getContext());
        pill.setBackground(background);
        pill.setTypeface(AndroidUtilities.bold());
        pill.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        pill.setTextColor(SupporterBadgeHint.resolveTextColor(ColorUtils.blendARGB(arrowView.color1, arrowView.color2, .5f)));
        pill.setText(LocaleController.formatString(customBadge ? R.string.SupporterBadgeNameCustom : R.string.SupporterBadgeName, BadgesController.formatNumber(number)));
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(10), 0, dp(10), 0);

        FrameLayout container = new FrameLayout(getContext());
        container.addView(pill, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 24, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 12));
        addHeaderAccessory(container);
    }

    protected TextView addCaptionBubble(CharSequence text) {
        final LinkSpanDrawable.LinksTextView bubble = new LinkSpanDrawable.LinksTextView(getContext(), resourcesProvider);
        bubble.setDisablePaddingsOffsetY(true);
        bubble.setLinkTextColor(getThemedColor(Theme.key_chat_messageLinkIn));
        bubble.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        bubble.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        bubble.setTypeface(AndroidUtilities.regular());
        bubble.setText(text);

        final MessageDrawable background = new MessageDrawable(MessageDrawable.TYPE_TEXT, false, false, resourcesProvider);
        background.shadow = false;
        background.overrideColor = getThemedColor(Theme.key_windowBackgroundGray);
        bubble.setBackground(background);
        bubble.setPadding(dp(19), dp(9), dp(15), dp(8));

        FrameLayout container = new FrameLayout(getContext());
        container.addView(bubble, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 32, 10, 32, 4));
        views.add(views.indexOf(titleView) + 1, container);
        return bubble;
    }

    protected SupporterArrowView getArrowView() {
        return header.arrowView;
    }

    protected void addHeader(CharSequence text) {
        TextView headerView = new TextView(getContext());
        headerView.setText(text);
        headerView.setTypeface(AndroidUtilities.bold());
        headerView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        headerView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        headerView.setGravity(Gravity.CENTER_HORIZONTAL);
        headerView.setPadding(dp(22), views.get(views.size() - 1) == subtitleView ? dp(4) : dp(20), dp(22), dp(6));
        views.add(headerView);
    }

    protected ButtonWithCounterView addSecondaryButton(CharSequence text, View.OnClickListener onClick) {
        ButtonWithCounterView secondary = new ButtonWithCounterView(getContext(), true, resourcesProvider).setNeutral();
        secondary.setRound();
        secondary.setText(text, false);
        secondary.setOnClickListener(onClick);
        if (secondaryRow == null) {
            ((LinearLayout.LayoutParams) button.getLayoutParams()).bottomMargin = dp(8);
            secondaryRow = new LinearLayout(getContext());
            secondaryRow.setOrientation(LinearLayout.HORIZONTAL);
            bottomContainer.addView(secondaryRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 12, 0, 12, 12));
        }
        secondaryRow.addView(secondary, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f, secondaryRow.getChildCount() > 0 ? 8 : 0, 0, 0, 0));
        return secondary;
    }

    public void setBadge(long documentId) {
        header.arrowView.setBadge(currentAccount, documentId);
    }

    protected void commit() {
        if (adapter != null) {
            adapter.update(false);
        }
        layoutManager.scrollToPositionWithOffset(0, 0);
    }

    @Override
    protected CharSequence getTitle() {
        return title != null ? title : "";
    }

    @Override
    protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
        return adapter = new UniversalAdapter(listView, getContext(), currentAccount, 0, true, (items, a) -> {
            if (views == null) {
                return;
            }
            for (View view : views) {
                UItem item = UItem.asCustom(view);
                item.transparent = true;
                items.add(item);
            }
            items.add(UItem.asSpace(dp(8)));
        }, resourcesProvider);
    }

    public void setEnterFrom(SupporterEnterFrom enterFrom) {
        this.enterFrom = enterFrom;
    }

    @Override
    protected boolean onCustomOpenAnimation() {
        if (enterFrom == null) {
            return false;
        }
        final SupporterEnterFrom from = enterFrom;
        enterProgress = 0f;
        header.arrowView.setIconHidden(true);
        from.drawable.setAlpha(0);
        from.invalidate();
        if (!header.arrowView.hasBadge()) {
            header.arrowView.iconView.startEnterAnimation(-360, 100);
        }
        enterAnimator = ValueAnimator.ofFloat(0f, 1f);
        enterAnimator.addUpdateListener(animation -> {
            enterProgress = (float) animation.getAnimatedValue();
            container.invalidate();
        });
        enterAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                enterProgress = 1f;
                header.arrowView.setIconHidden(false);
                container.postOnAnimation(() -> {
                    enterFrom = null;
                    container.invalidate();
                });
                ValueAnimator alphaBack = ValueAnimator.ofInt(0, 255);
                alphaBack.addUpdateListener(a -> {
                    from.drawable.setAlpha((Integer) a.getAnimatedValue());
                    from.invalidate();
                });
                alphaBack.start();
            }
        });
        enterAnimator.setDuration(600);
        enterAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        enterAnimator.start();
        return super.onCustomOpenAnimation();
    }

    @Override
    protected void mainContainerDispatchDraw(Canvas canvas) {
        super.mainContainerDispatchDraw(canvas);
        final SupporterEnterFrom from = enterFrom;
        if (from == null) {
            return;
        }
        final View icon = header.arrowView.getIconView();
        container.getLocationOnScreen(location);
        final float cxFrom = from.screenX - location[0];
        final float cyFrom = from.screenY - location[1];
        final int containerX = location[0];
        final int containerY = location[1];
        icon.getLocationOnScreen(location);
        final float cxTo = location[0] - containerX + icon.getWidth() / 2f;
        final float cyTo = location[1] - containerY + icon.getHeight() / 2f - containerView.getTranslationY();

        final Drawable drawable = from.drawable instanceof AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable swap && swap.getDrawable() != null ? swap.getDrawable() : from.drawable;
        final ColorFilter colorFilter = drawable != from.drawable ? from.drawable.getColorFilter() : null;
        final float fromSize = from.scale * drawable.getIntrinsicWidth();
        final float toSize = icon.getHeight() * (header.arrowView.hasBadge() ? 1f : .8f);
        if (fromSize <= 0 || toSize <= 0) {
            return;
        }
        final float x = AndroidUtilities.lerp(cxFrom, cxTo, CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(enterProgress));
        final float y = AndroidUtilities.lerp(cyFrom, cyTo, enterProgress);

        canvas.save();
        float s = AndroidUtilities.lerp(from.scale, toSize / fromSize * from.scale, enterProgress);
        canvas.scale(s, s, x, y);
        drawable.setBounds(
                (int) x - drawable.getIntrinsicWidth() / 2,
                (int) y - drawable.getIntrinsicHeight() / 2,
                (int) x + drawable.getIntrinsicWidth() / 2,
                (int) y + drawable.getIntrinsicHeight() / 2);
        drawable.setAlpha((int) (255 * (1f - Utilities.clamp(enterProgress, 1, 0))));
        if (colorFilter != null) {
            drawable.setColorFilter(colorFilter);
        }
        drawable.draw(canvas);
        if (colorFilter != null) {
            drawable.setColorFilter(null);
        }
        drawable.setAlpha(drawable == from.drawable ? 0 : 255);
        canvas.restore();

        canvas.save();
        s = AndroidUtilities.lerp(fromSize / toSize, 1f, enterProgress);
        canvas.scale(s, s, x, y);
        canvas.translate(x - icon.getWidth() / 2f, y - icon.getHeight() / 2f);
        icon.draw(canvas);
        canvas.restore();
    }

    @Override
    public void dismiss() {
        if (enterAnimator != null) {
            enterAnimator.cancel();
        }
        super.dismiss();
    }

    public static class SupporterArrowHeader extends FrameLayout {

        final SupporterArrowView arrowView;

        public SupporterArrowHeader(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            setClipChildren(false);
            addView(arrowView = new SupporterArrowView(context, resourcesProvider), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 190, Gravity.TOP, 0, 10, 0, 0));
        }
    }
}
