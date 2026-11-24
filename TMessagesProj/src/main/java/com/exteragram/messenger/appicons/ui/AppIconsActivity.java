/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.appicons.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.exteragram.messenger.appicons.AppIcon;
import com.exteragram.messenger.appicons.AppIconController;
import com.exteragram.messenger.appicons.AppIconPreviewLoader;
import com.exteragram.messenger.appicons.ui.components.AppIconBulletinLayout;
import com.exteragram.messenger.appicons.ui.components.AppIconCell;
import com.exteragram.messenger.appicons.ui.components.AppIconHeroView;
import com.exteragram.messenger.utils.ui.ChatHeaderUiHelper;

import com.exteragram.messenger.utils.ui.UIUtil;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.EdgeToEdgeSupportMode;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.ui.Components.chat.ViewPositionWatcher;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.utils.glass.source.GlassSourceColor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AppIconsActivity extends BaseFragment {

    private static final int PANEL_RADIUS_DP = 20;
    private static final int TOP_FADE_HEIGHT_DP = 24;
    private static final int TOP_FADE_SCROLL_DP = 16;
    private static final int COLUMN_WIDTH_DP = 100;
    private static final int CELL_MAX_WIDTH_DP = 112;
    private static final int LIST_PADDING_DP = 8;

    private ContentView contentView;
    private UniversalRecyclerView listView;
    private AppIconHeroView heroView;
    private FrameLayout buttonContainer;
    private ButtonWithCounterView button;

    private AppIcon appliedIcon;
    private AppIcon previewIcon;

    private int heroHeight;
    private int bottomInset;
    private int backgroundColor;
    private ValueAnimator colorAnimator;
    private GlassSourceColor glassSource;

    private int scrolled;
    private float collapse;
    private float topFade;

    private boolean twoPane;
    private int paneWidth;
    private int leftInset, rightInset;
    private Insets cutout = Insets.NONE;
    private boolean buttonFaded = true;

    @Override
    public View createView(Context context) {
        appliedIcon = AppIconController.getSelectedIcon();
        previewIcon = appliedIcon;
        backgroundColor = AppIconPreviewLoader.getAccentTint(previewIcon);

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setCastShadows(false);
        actionBar.setAddToContainer(false);
        actionBar.setItemsColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), false);
        actionBar.setItemsBackgroundColor(getThemedColor(Theme.key_listSelector), false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        contentView = new ContentView(context);

        glassSource = new GlassSourceColor();
        glassSource.setColor(getTopColor());
        final BlurredBackgroundDrawableViewFactory glassFactory = new BlurredBackgroundDrawableViewFactory(glassSource);
        glassFactory.setSourceRootView(new ViewPositionWatcher(contentView), contentView);
        actionBar.setGlassPadding(dp(12));
        actionBar.setupGlass(glassFactory, BlurredBackgroundProviderImpl.topPanelChatActivity(getResourceProvider()));
        ChatHeaderUiHelper.applyChatHeaderGlassStyle(actionBar, true);

        heroView = new AppIconHeroView(context, this);
        heroView.setOnPreviewReady(this::applyAccent);
        heroView.set(previewIcon);
        contentView.addView(heroView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        listView.setSpanCount(getSpanCount(getGridWidth(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y)));
        listView.adapter.setApplyBackground(false);
        listView.setDrawSelection(false);
        listView.setClipToPadding(false);
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                updateCollapse(dy);
            }
        });
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        button = new ButtonWithCounterView(context, true, getResourceProvider());
        button.setRound();
        button.setOnClickListener(v -> onButtonClick());
        updateButton(false);

        buttonContainer = new FrameLayout(context);
        buttonContainer.setBackground(UIUtil.createBottomFade(getThemedColor(Theme.key_windowBackgroundWhite)));
        buttonContainer.addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM, 16, 12, 16, 12));
        buttonContainer.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> updateListPadding());
        contentView.addView(buttonContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

        contentView.addView(actionBar);

        return fragmentView = contentView;
    }

    @Override
    public void onFragmentDestroy() {
        if (colorAnimator != null) {
            colorAnimator.cancel();
            colorAnimator = null;
        }
        AppIconPreviewLoader.trimAbove(dp(AppIconCell.PREVIEW_SIZE_DP));
        super.onFragmentDestroy();
    }

    private int getPreviewSizeDp(int height) {
        if (heroView == null) {
            return AppIconHeroView.PREVIEW_SIZE_DP;
        }
        final int budget = twoPane
                ? height - AndroidUtilities.statusBarHeight - ActionBar.getCurrentActionBarHeight()
                        - dp(12 + 48 + 12) - bottomInset
                : (int) (height * 0.45f) - AndroidUtilities.statusBarHeight - dp(28 + 40);
        final int forPreview = budget - heroView.getTextBlockHeight();
        return Math.max(AppIconHeroView.PREVIEW_MIN_SIZE_DP,
                Math.min(AppIconHeroView.PREVIEW_SIZE_DP, (int) (forPreview / AndroidUtilities.density)));
    }

    private static boolean isTwoPane(int width, int height) {
        return width > height && width >= dp(560);
    }

    private static int paneWidthFor(int width) {
        return Math.max(dp(280), Math.min(dp(400), (int) (width * 0.42f)));
    }

    private int getSpanCount(int gridWidth) {
        final int content = gridWidth - dp(LIST_PADDING_DP) * 2;
        int columns = Math.max(3, Math.min(8, Math.round(content / (float) dp(COLUMN_WIDTH_DP))));
        if (columns < 8 && content / (float) columns > dp(CELL_MAX_WIDTH_DP)) {
            columns++;
        }
        return columns;
    }

    private int getGridWidth(int width, int height) {
        return isTwoPane(width, height)
                ? width - paneWidthFor(width) - rightInset
                : width - leftInset - rightInset;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        final List<AppIcon> icons = AppIconController.getAvailableIcons();
        if (previewIcon == null || !icons.contains(previewIcon)) {
            appliedIcon = AppIconController.getSelectedIcon();
            previewIcon = appliedIcon;
        }

        for (AppIcon icon : icons) {
            items.add(AppIconCell.Factory.asAppIcon(icon, icon == previewIcon));
        }
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (!(item.object instanceof AppIcon icon) || icon == previewIcon) {
            return;
        }
        previewIcon = icon;
        heroView.set(icon);
        applyAccent();
        updateButton(true);
        listView.adapter.update(true);
    }

    private void applyAccent() {
        if (previewIcon == null) {
            return;
        }
        final int tint = AppIconPreviewLoader.getAccentTint(previewIcon);
        if (tint != 0) {
            animateBackgroundColor(tint);
        }
    }

    private void onButtonClick() {
        if (previewIcon == null) {
            return;
        }
        if (previewIcon == appliedIcon) {
            finishFragment();
            return;
        }
        AppIconController.setIcon(previewIcon);
        appliedIcon = previewIcon;
        updateButton(true);
        Bulletin.make(this, new AppIconBulletinLayout(getContext(), appliedIcon, getResourceProvider()), Bulletin.DURATION_SHORT).show();
    }

    private void updateButton(boolean animated) {
        if (button == null) {
            return;
        }
        button.setText(getString(previewIcon == appliedIcon ? R.string.AppIconsKeep : R.string.Select), animated);
    }

    private void animateBackgroundColor(int color) {
        if (backgroundColor == color) {
            return;
        }
        if (colorAnimator != null) {
            colorAnimator.cancel();
            colorAnimator = null;
        }
        if (backgroundColor == 0) {
            backgroundColor = color;
            invalidateHeader();
            updateStatusBar();
            return;
        }
        final int from = backgroundColor;
        colorAnimator = ValueAnimator.ofFloat(0f, 1f).setDuration(280);
        colorAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        colorAnimator.addUpdateListener(animation -> {
            backgroundColor = ColorUtils.blendARGB(from, color, (float) animation.getAnimatedValue());
            invalidateHeader();
        });
        colorAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                backgroundColor = color;
                updateStatusBar();
            }
        });
        colorAnimator.start();
    }

    private void invalidateHeader() {
        contentView.invalidate();
        if (glassSource != null) {
            glassSource.setColor(getTopColor());
            actionBar.invalidate();
        }
    }

    private void updateStatusBar() {
        if (getParentActivity() != null) {
            AndroidUtilities.setLightStatusBar(getParentActivity().getWindow(), isLightStatusBar());
        }
    }

    @Override
    public boolean isLightStatusBar() {
        return !AndroidUtilities.isDarkColor(getTopColor());
    }

    private int getBaseColor() {
        final int base = getThemedColor(Theme.key_windowBackgroundGray);
        return backgroundColor == 0 ? base : ColorUtils.blendARGB(base, backgroundColor, 0.25f);
    }

    private int getTopColor() {
        return backgroundColor == 0 ? getBaseColor() : ColorUtils.blendARGB(getBaseColor(), backgroundColor, 0.7f);
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    public EdgeToEdgeSupportMode getEdgeToEdgeSupportMode() {
        return EdgeToEdgeSupportMode.FULL;
    }

    @Override
    public WindowInsetsCompat onInsetsInternal(@NonNull View view, @NonNull WindowInsetsCompat windowInsets) {
        cutout = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout());
        return super.onInsetsInternal(view, windowInsets);
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        bottomInset = bottom;
        leftInset = Math.max(left, cutout.left);
        rightInset = Math.max(right, cutout.right);
        if (contentView != null) {
            contentView.requestLayout();
        }
        updateListPadding();
    }

    private void updateListPadding() {
        if (listView == null) {
            return;
        }
        final int left = dp(LIST_PADDING_DP) + (twoPane ? 0 : leftInset);
        final int right = dp(LIST_PADDING_DP) + rightInset;
        final int top, bottom;
        if (twoPane) {
            top = AndroidUtilities.statusBarHeight + dp(10);
            bottom = bottomInset + dp(10);
        } else {
            top = heroHeight + dp(10);
            bottom = buttonContainer == null || buttonContainer.getHeight() <= 0
                    ? bottomInset + dp(12 + 48 + 12)
                    : buttonContainer.getHeight();
        }
        if (listView.getPaddingTop() != top || listView.getPaddingBottom() != bottom
                || listView.getPaddingLeft() != left || listView.getPaddingRight() != right) {
            listView.setPadding(left, top, right, bottom);
        }
        updateCollapse(0);
    }

    private int getCollapseRange() {
        if (twoPane) {
            return 0;
        }
        return Math.max(0, heroHeight - ActionBar.getCurrentActionBarHeight() - AndroidUtilities.statusBarHeight);
    }

    private void updateCollapse(int dy) {
        if (listView == null || heroView == null) {
            return;
        }
        int offset = scrolled + dy;
        for (int i = 0; i < listView.getChildCount(); ++i) {
            final View child = listView.getChildAt(i);
            if (listView.getChildAdapterPosition(child) == 0) {
                offset = listView.getPaddingTop() - child.getTop();
                break;
            }
        }
        scrolled = Math.max(0, offset);

        final int range = getCollapseRange();
        final float progress = range <= 0 ? 0f : Math.min(1f, scrolled / (float) range);
        final float fade = Math.min(1f, scrolled / (float) dp(TOP_FADE_SCROLL_DP));
        if (Math.abs(progress - collapse) < 0.0005f && Math.abs(fade - topFade) < 0.0005f) {
            return;
        }
        collapse = progress;
        topFade = fade;
        heroView.setCollapse(collapse);
        contentView.invalidate();
    }

    private class ContentView extends FrameLayout implements Theme.Colorable {

        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint panelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path panelPath = new Path();
        private final float[] panelRadii = new float[8];

        private int glowColor;
        private int glowWidth, glowHeight;
        private float glowCx, glowCy;

        private Drawable topFadeDrawable;
        private int topFadeColor;

        private boolean heroLinkPressed;

        public ContentView(Context context) {
            super(context);
            setWillNotDraw(false);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            final int width = MeasureSpec.getSize(widthMeasureSpec);
            final int height = MeasureSpec.getSize(heightMeasureSpec);

            final boolean wasTwoPane = twoPane;
            twoPane = isTwoPane(width, height);
            paneWidth = twoPane ? paneWidthFor(width) : width;

            if (twoPane) {
                heroView.setPadding(0, 0, 0, 0);
            } else {
                heroView.setPadding(0, AndroidUtilities.statusBarHeight + dp(28), 0, dp(40));
            }
            heroView.setPreviewSizeDp(getPreviewSizeDp(height));
            buttonContainer.setPadding(twoPane ? 0 : leftInset, 0, twoPane ? 0 : rightInset, bottomInset);
            if (buttonFaded != !twoPane) {
                buttonFaded = !twoPane;
                buttonContainer.setBackground(buttonFaded
                        ? UIUtil.createBottomFade(getThemedColor(Theme.key_windowBackgroundWhite)) : null);
            }

            final int spanCount = getSpanCount(getGridWidth(width, height));
            if (listView.getSpanCount() != spanCount) {
                listView.setSpanCount(spanCount);
            }

            if (!twoPane) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            } else {
                final int paneSpec = MeasureSpec.makeMeasureSpec(paneWidth - leftInset, MeasureSpec.EXACTLY);
                final int heightAtMost = MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST);
                actionBar.measure(paneSpec, heightAtMost);
                buttonContainer.measure(paneSpec, heightAtMost);
                heroView.measure(paneSpec, heightAtMost);
                listView.measure(
                        MeasureSpec.makeMeasureSpec(width - paneWidth, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
                setMeasuredDimension(width, height);
            }

            final int measured = heroView.getMeasuredHeight();
            if (measured > 0 && (measured != heroHeight || wasTwoPane != twoPane)) {
                heroHeight = measured;
                AndroidUtilities.runOnUIThread(AppIconsActivity.this::updateListPadding);
            }
        }

        @Override
        public void updateColors() {
            backgroundColor = AppIconPreviewLoader.getAccentTint(previewIcon);
            actionBar.setItemsColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), false);
            actionBar.setItemsBackgroundColor(getThemedColor(Theme.key_listSelector), false);
            if (buttonFaded) {
                buttonContainer.setBackground(UIUtil.createBottomFade(getThemedColor(Theme.key_windowBackgroundWhite)));
            }
            invalidateHeader();
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            if (!twoPane) {
                super.onLayout(changed, left, top, right, bottom);
                updateCollapse(0);
                return;
            }

            final int width = right - left;
            final int height = bottom - top;
            actionBar.layout(leftInset, 0, leftInset + actionBar.getMeasuredWidth(), actionBar.getMeasuredHeight());
            listView.layout(paneWidth, 0, width, height);

            final int buttonHeight = buttonContainer.getMeasuredHeight();
            buttonContainer.layout(leftInset, height - buttonHeight, leftInset + buttonContainer.getMeasuredWidth(), height);

            final int barBottom = AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight();
            final int heroTop = barBottom + Math.max(0, (height - buttonHeight - barBottom - heroHeight) / 2);
            heroView.layout(leftInset, heroTop, leftInset + heroView.getMeasuredWidth(), heroTop + heroHeight);

            updateCollapse(0);
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            if (heroView != null) {
                final int action = ev.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    heroLinkPressed = heroView.dispatchLinkTouch(ev);
                    if (heroLinkPressed) {
                        return true;
                    }
                } else if (heroLinkPressed) {
                    if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                        heroLinkPressed = false;
                    }
                    heroView.dispatchLinkTouch(ev);
                    return true;
                }
            }
            return super.dispatchTouchEvent(ev);
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            canvas.drawColor(getBaseColor());
            if (backgroundColor == 0 || collapse >= 1f) {
                return;
            }
            final float cx = twoPane ? (leftInset + paneWidth) / 2f : getWidth() / 2f;
            final float cy = heroView.getTop() + heroView.getPaddingTop() + heroView.getPreviewSize() / 2f;
            if (glowColor != backgroundColor || glowWidth != getWidth() || glowHeight != getHeight()
                    || glowCx != cx || glowCy != cy) {
                final float radius = Math.max(twoPane ? paneWidth : getWidth(), heroHeight) * 0.9f;
                if (radius <= 0) {
                    return;
                }
                glowColor = backgroundColor;
                glowWidth = getWidth();
                glowHeight = getHeight();
                glowCx = cx;
                glowCy = cy;
                glowPaint.setShader(new RadialGradient(cx, cy, radius,
                        new int[]{ColorUtils.setAlphaComponent(backgroundColor, 0xC8), ColorUtils.setAlphaComponent(backgroundColor, 0)},
                        new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            }
            glowPaint.setAlpha((int) (255 * (1f - collapse)));
            canvas.drawRect(0, 0, getWidth(), getHeight(), glowPaint);
        }

        @Override
        protected boolean drawChild(@NonNull Canvas canvas, View child, long drawingTime) {
            if (child != listView) {
                return super.drawChild(canvas, child, drawingTime);
            }

            final float radius = dp(PANEL_RADIUS_DP);
            Arrays.fill(panelRadii, 0f);
            if (twoPane) {
                panelRadii[0] = panelRadii[1] = panelRadii[6] = panelRadii[7] = radius;
                AndroidUtilities.rectTmp.set(paneWidth, 0, getWidth(), getHeight());
            } else {
                panelRadii[0] = panelRadii[1] = panelRadii[2] = panelRadii[3] = radius;
                AndroidUtilities.rectTmp.set(0, listView.getY() + heroHeight - getCollapseRange() * collapse, getWidth(), getHeight());
            }
            panelPath.rewind();
            panelPath.addRoundRect(AndroidUtilities.rectTmp, panelRadii, Path.Direction.CW);

            final float panelTop = AndroidUtilities.rectTmp.top;
            panelPaint.setColor(getThemedColor(Theme.key_windowBackgroundWhite));
            canvas.drawPath(panelPath, panelPaint);

            canvas.save();
            canvas.clipPath(panelPath);
            final boolean result = super.drawChild(canvas, child, drawingTime);
            if (topFade > 0f) {
                final int color = getThemedColor(Theme.key_windowBackgroundWhite);
                if (topFadeDrawable == null || topFadeColor != color) {
                    topFadeColor = color;
                    topFadeDrawable = UIUtil.createTopFade(color);
                }
                topFadeDrawable.setBounds(0, (int) panelTop, getWidth(), (int) panelTop + dp(TOP_FADE_HEIGHT_DP));
                topFadeDrawable.setAlpha((int) (255 * topFade));
                topFadeDrawable.draw(canvas);
            }
            canvas.restore();
            return result;
        }
    }
}
