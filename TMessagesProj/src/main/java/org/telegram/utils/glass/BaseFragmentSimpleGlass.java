package org.telegram.utils.glass;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.HeaderBlurStyle;
import com.exteragram.messenger.utils.ui.GlassProgressiveBlur;
import com.exteragram.messenger.utils.ui.GlassUiHelper;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.SimpleActionBar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SectionsScrollView;
import org.telegram.ui.Components.ViewPagerFixed;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.BlurredBackgroundWithFadeDrawable;
import org.telegram.ui.Components.blur3.capture.IBlur3Capture;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.utils.glass.source.GlassSourceColor;
import org.telegram.utils.glass.source.GlassSourceCrossfade;
import org.telegram.utils.glass.source.GlassSourceRenderNode;
import org.telegram.ui.Components.blur3.utils.Blur3Utils;
import org.telegram.ui.ComposeDrawable;
import org.telegram.utils.glass.fade.GlassFadeBlurredDrawable;
import org.telegram.utils.glass.fade.GlassFadeDirection;
import org.telegram.utils.glass.fade.GlassFadeRangeParams;
import org.telegram.utils.glass.positions.GlassPositionsArray;
import org.telegram.utils.glass.positions.GlassPositionsMerger;
import org.telegram.utils.glass.source.GlassSource;
import org.telegram.utils.glass.source.GlassSourceEmpty;

import me.vkryl.core.BitwiseUtils;

public class BaseFragmentSimpleGlass {
    private final GlassEngine glassEngine;

    private final @NonNull GlassSourceColor glassBackgroundSourceColor;

    public final @NonNull BlurredBackgroundDrawableViewFactory factory;
    public final @NonNull BlurredBackgroundDrawableViewFactory factoryColor;
    /** The menus: the glass of {@link #factory} going over to frosted glass as they open, see {@link #menuGlassSource}. */
    public final @NonNull BlurredBackgroundDrawableViewFactory menuFactory;
    /** The progress of the menus from the glass to the frosted glass; null without a capture. */
    public final @Nullable GlassSourceCrossfade menuGlassSource;
    /** Frosted glass, e.g. a sheet over the content; {@link #factory} without a capture. */
    public final @NonNull BlurredBackgroundDrawableViewFactory frostedFactory;

    private final @Nullable GlassCaptureSourceCanvas glassCaptureSourceCanvas;

    /** The source of {@link #factory} for a glass outside this screen's views; null without a capture. */
    private final @Nullable GlassSourceRenderNode glassSource;
    private final @Nullable GlassSourceRenderNode frostedGlassSource;
    private @Nullable Runnable onCaptured;

    private final @NonNull GlassSource glassSourceForFadeDrawable;
    private final @NonNull GlassSource glassSourceForHeaderFade;


    public BaseFragmentSimpleGlass(GlassEngine engine, GlassEngine.ThemedColorDelegate themedColor) {
        glassEngine = engine;
        glassEngine.setGlassInvalidationListener(this::invalidate);
        glassEngine.setPositionsMerger(new GlassPositionsMerger(Float.POSITIVE_INFINITY, dp(48)));

        glassBackgroundSourceColor = new GlassSourceColor();
        glassBackgroundSourceColor.setColor(themedColor.getThemedColor());
        glassEngine.addColorSource(glassBackgroundSourceColor, themedColor);

        factoryColor = new BlurredBackgroundDrawableViewFactory(glassBackgroundSourceColor);
        // factoryColor.setGlassEngine(glassEngine);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            glassCaptureSourceCanvas = new GlassCaptureSourceCanvas();

            final GlassSourceRenderNode glassBackgroundSourceRenderNode =
                    new GlassSourceRenderNode(glassBackgroundSourceColor);
            glassBackgroundSourceRenderNode.setUnderSource(glassBackgroundSourceColor);

            glassBackgroundSourceRenderNode.setGlassCaptureSourceCanvas(glassCaptureSourceCanvas, GlassCaptureSourceCanvas.SourceIndex.GLASS);
            glassSource = glassBackgroundSourceRenderNode;

            final GlassSourceRenderNode frostedSourceRenderNode = new GlassSourceRenderNode(glassBackgroundSourceColor);
            frostedGlassSource = frostedSourceRenderNode;
            frostedSourceRenderNode.setUnderSource(glassBackgroundSourceColor);
            frostedSourceRenderNode.setGlassCaptureSourceCanvas(glassCaptureSourceCanvas, GlassCaptureSourceCanvas.SourceIndex.FROSTED_GLASS);

            factory = new BlurredBackgroundDrawableViewFactory(glassBackgroundSourceRenderNode, frostedSourceRenderNode, glassBackgroundSourceColor);
            factory.setGlassEngine(glassEngine);
            factory.setLiquidGlassEffectAllowed(true);

            menuGlassSource = new GlassSourceCrossfade(glassBackgroundSourceRenderNode, frostedSourceRenderNode);
            frostedFactory = new BlurredBackgroundDrawableViewFactory(frostedSourceRenderNode, frostedSourceRenderNode, glassBackgroundSourceColor);
            frostedFactory.setGlassEngine(glassEngine);
            menuFactory = new BlurredBackgroundDrawableViewFactory(menuGlassSource, frostedSourceRenderNode, glassBackgroundSourceColor);
            menuFactory.setGlassEngine(glassEngine);
            menuFactory.setLiquidGlassEffectAllowed(true);

            glassSourceForFadeDrawable = glassCaptureSourceCanvas.getSource(GlassCaptureSourceCanvas.SourceIndex.BLURRED);
            glassSourceForHeaderFade = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    ? glassCaptureSourceCanvas.getSource(GlassCaptureSourceCanvas.SourceIndex.SHARP)
                    : glassSourceForFadeDrawable;
        } else {
            glassSourceForFadeDrawable = GlassSourceEmpty.INSTANCE;
            glassSourceForHeaderFade = GlassSourceEmpty.INSTANCE;
            glassCaptureSourceCanvas = null;
            glassSource = null;
            frostedGlassSource = null;
            factory = new BlurredBackgroundDrawableViewFactory(glassBackgroundSourceColor);
            factory.setGlassEngine(glassEngine);
            menuGlassSource = null;
            menuFactory = factory;
            frostedFactory = factory;
        }
    }


    /** The capture under the glass of this screen; null without one (before Android 12). Records nothing with the blur off, unless {@link #setCaptureIgnoresBlurSetting}. */
    @Nullable
    public GlassCaptureSourceCanvas getCaptureSourceCanvas() {
        return glassCaptureSourceCanvas;
    }

    private boolean captureIgnoresBlurSetting;

    /**
     * The capture is recorded with the blur off too (power saving), for a glass that has no other look over what is under
     * it (the profile's buttons over the photo). The glass of the settings still goes static: only that glass is captured.
     */
    public void setCaptureIgnoresBlurSetting(boolean ignores) {
        captureIgnoresBlurSetting = ignores;
    }

    @Nullable
    public GlassSourceRenderNode getGlassSource() {
        return GlassEngine.getCurrentGlassSettings().isBlurEnabled ? glassSource : null;
    }

    @Nullable
    public GlassSourceRenderNode getFrostedGlassSource() {
        return GlassEngine.getCurrentGlassSettings().isBlurEnabled ? frostedGlassSource : null;
    }

    /** After the capture: what draws {@link #getGlassSource} from outside records it again, it holds the offsets of the capture. */
    public void setOnCaptured(@Nullable Runnable onCaptured) {
        this.onCaptured = onCaptured;
    }

    private View contentView;
    private View clipView;

    private IBlur3Capture capture;



    @NonNull
    public Drawable createActionBarFadeDrawable(View view) {
        final HeaderBlurStyle style = ExteraConfig.getHeaderBlurStyle();
        final boolean progressive = style == HeaderBlurStyle.PROGRESSIVE && glassSourceForHeaderFade != glassSourceForFadeDrawable;
        final GlassFadeBlurredDrawable glassBlurredFadeDrawable = new GlassFadeBlurredDrawable(
            style == HeaderBlurStyle.FADE ? GlassSourceEmpty.INSTANCE : progressive ? glassSourceForHeaderFade : glassSourceForFadeDrawable);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && progressive) {
            final float[] sigmas = new float[GlassProgressiveBlur.HEADER_SIGMAS_DP.length];
            for (int i = 0; i < sigmas.length; i++) {
                sigmas[i] = AndroidUtilities.dpf2(GlassProgressiveBlur.HEADER_SIGMAS_DP[i]);
            }
            glassBlurredFadeDrawable.setProgressiveBlur(sigmas);
        }

        final GlassFadeRangeParams gradientRangeMask = new GlassFadeRangeParams(
                dp(32), GlassFadeRangeParams.Gravity.START, 0, 0);
        final GlassFadeRangeParams gradientRangeFade = new GlassFadeRangeParams(
                dp(56), GlassFadeRangeParams.Gravity.START, 0, 0);
        final GlassFadeRangeParams gradientRangeFallback = new GlassFadeRangeParams(
                dp(48), GlassFadeRangeParams.Gravity.START, 0, 0);

        glassBlurredFadeDrawable.setDirection(GlassFadeDirection.TOP);
        glassBlurredFadeDrawable.setOverlayColorSource(glassBackgroundSourceColor);
        glassBlurredFadeDrawable.setOverlayAlpha(160);
        glassBlurredFadeDrawable.setMaskRange(gradientRangeMask);
        glassBlurredFadeDrawable.setOverlayRange(gradientRangeFade);

        glassBlurredFadeDrawable.setOverlayOnlyAlpha(210);
        glassBlurredFadeDrawable.setOverlayOnlyRange(gradientRangeFallback);
        glassBlurredFadeDrawable.setGradientProfile(GlassUiHelper.HEADER_FADE_ALPHAS, GlassUiHelper.HEADER_FADE_POSITIONS);

        glassEngine.registerDrawable(view, glassBlurredFadeDrawable);
        return glassBlurredFadeDrawable;
    }

    public void updateActionBarFadeDrawable(View view) {
        if (view.getBackground() instanceof GlassFadeBlurredDrawable) {
            glassEngine.unregisterDrawable((GlassFadeBlurredDrawable) view.getBackground());
            view.setBackground(createActionBarFadeDrawable(view));
        }
    }

    @NonNull
    public Drawable createNavigationBarFadeDrawable(View view) {
        final GlassFadeBlurredDrawable glassBlurredFadeDrawable = new GlassFadeBlurredDrawable(
            glassSourceForFadeDrawable);

        final GlassFadeRangeParams gradientRangeMask = new GlassFadeRangeParams(
                dp(28), GlassFadeRangeParams.Gravity.START, 0, 0);
        final GlassFadeRangeParams gradientRangeFade = new GlassFadeRangeParams(
                dp(40), GlassFadeRangeParams.Gravity.START, 0, 0);
        final GlassFadeRangeParams gradientRangeFallback = new GlassFadeRangeParams(
                dp(30), GlassFadeRangeParams.Gravity.START, 0, 0);

        glassBlurredFadeDrawable.setDirection(GlassFadeDirection.BOTTOM);
        glassBlurredFadeDrawable.setOverlayColorSource(glassBackgroundSourceColor);
        glassBlurredFadeDrawable.setOverlayAlpha(160);
        glassBlurredFadeDrawable.setMaskRange(gradientRangeMask);
        glassBlurredFadeDrawable.setOverlayRange(gradientRangeFade);

        glassBlurredFadeDrawable.setOverlayOnlyAlpha(210);
        glassBlurredFadeDrawable.setOverlayOnlyRange(gradientRangeFallback);

        glassEngine.registerDrawable(view, glassBlurredFadeDrawable);
        return glassBlurredFadeDrawable;
    }



    public void setViews(FrameLayout contentView, RecyclerListView listView, @Nullable View actionBar, Theme.ResourcesProvider resourceProvider) {
        this.contentView = contentView;
        this.clipView = listView;
        listView.setCaptureSectionsDecoratorAllowed(true);
        capture = (canvas, position) -> Blur3Utils.captureRelativeParent(listView, canvas, position, listView, contentView);
        glassEngine.addScrolledView(listView);

        listView.setClipToPadding(false);

        setupHeader(contentView, actionBar, resourceProvider);

        if (BuildVars.DEBUG_PRIVATE_VERSION && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
             if (glassCaptureSourceCanvas != null) {
             //     contentView.addView(glassCaptureSourceCanvas.createDebugView(contentView.getContext()));
             }
        }
    }

    public void setViews(FrameLayout contentView, SectionsScrollView listView, @Nullable View actionBar, Theme.ResourcesProvider resourceProvider) {
        this.contentView = contentView;
        this.clipView = listView;
        capture = (canvas, position) -> Blur3Utils.captureRelativeParent(listView, canvas, position, listView, contentView);
        glassEngine.addScrolledView(listView);

        listView.setClipToPadding(false);

        setupHeader(contentView, actionBar, resourceProvider);
    }

    public void setViews(FrameLayout contentView, ViewPagerFixed viewPager, @Nullable View actionBar, Theme.ResourcesProvider resourceProvider) {
        this.contentView = contentView;
        this.clipView = viewPager;
        glassEngine.addScrolledView(viewPager);

        setupHeader(contentView, actionBar, resourceProvider);
    }

    /** For content captured only through {@link #setCapture}, e.g. the lists of several pages. */
    public void setViews(FrameLayout contentView, @Nullable View actionBar, Theme.ResourcesProvider resourceProvider) {
        this.contentView = contentView;
        this.clipView = null;

        setupHeader(contentView, actionBar, resourceProvider);
    }

    /** Moves the header (an {@link ActionBar} or a {@link SimpleActionBar}) above the content and puts it on glass. */
    private void setupHeader(FrameLayout contentView, @Nullable View header, Theme.ResourcesProvider resourceProvider) {
        if (header == null) {
            return;
        }
        AndroidUtilities.removeFromParent(header);
        contentView.addView(header, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));
        if (header instanceof SimpleActionBar) {
            final SimpleActionBar simpleActionBar = (SimpleActionBar) header;
            simpleActionBar.setExtraHeight(dp(6));
            simpleActionBar.setupGlass(factory, BlurredBackgroundProviderImpl.simpleActionBar(resourceProvider));
            simpleActionBar.setMenuGlass(menuFactory, menuGlassSource);
        } else if (header instanceof ActionBar) {
            final ActionBar actionBar = (ActionBar) header;
            actionBar.setAddToContainer(false);
            actionBar.setCenterTitleAndGlass(true);
            actionBar.setExtraHeight(dp(6));
            actionBar.setGlassPadding(dp(GlassUiHelper.SIDE_PADDING_DP));
            actionBar.doNotDrawCenterGlass = true;
            actionBar.setupGlass(factory, BlurredBackgroundProviderImpl.simpleActionBar(resourceProvider));
        }
    }

    public void setClipView(View clipView) {
        this.clipView = clipView;
    }

    public void setCapture(IBlur3Capture capture) {
        this.capture = capture;
    }

    private void invalidate(int flags) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || glassCaptureSourceCanvas == null || !captureIgnoresBlurSetting && !GlassEngine.getCurrentGlassSettings().isBlurEnabled) {
            return;
        }


        if (BitwiseUtils.hasFlag(flags, GlassEngine.FLAG_INVALIDATED_POSITIONS) || BitwiseUtils.hasFlag(flags, GlassEngine.FLAG_INVALIDATED_SCROLL_EDGES)) {
            GlassPositionsArray glassPositionsArray = glassEngine.getAllVisibleDrawablesWithDisplayListPositionsMerged();
            if (clipView != null) {
                glassPositionsArray.clip(0, clipView.getY(), contentView.getWidth(), clipView.getY() + clipView.getHeight());
            }

            if (glassCaptureSourceCanvas != null) {
                glassCaptureSourceCanvas.setPositions(glassPositionsArray);
            }
        }

        if (glassCaptureSourceCanvas != null) {
            glassCaptureSourceCanvas.setCapture(capture);
            glassCaptureSourceCanvas.setRootView(contentView);
            glassCaptureSourceCanvas.setAllowDownsample(!glassEngine.hasScrollEdges());
            glassCaptureSourceCanvas.setOffset(glassEngine.getScrollOffsetX(), glassEngine.getScrollOffsetY());
            glassCaptureSourceCanvas.invalidate();
        }

        if (onCaptured != null) {
            onCaptured.run();
        }
    }

    /** The vertical gap between the glass pills of a simple screen: the header's buttons, the tabs, a player. */
    public static final int PILLS_GAP_DP = 7;

    /**
     * Where the glass of the first pill under the glass header starts (the tabs, a player): 7dp under the header's
     * buttons. insetTop is the screen's top inset above the header row.
     */
    public static int getPillGlassTop(int insetTop) {
        final int rowHeight = ActionBar.getCurrentActionBarHeight();
        return insetTop + rowHeight - (rowHeight - dp(46)) / 2 + dp(PILLS_GAP_DP);
    }

    /** How far a list goes beyond an edge of the screen with this system inset, for the glass near the edge. */
    public static int getOverscan(int inset) {
        return Math.max(dp(8), dp(48) - inset);
    }

    public static void setListViewParams(ViewGroup listView, int insetTop, int insetBottom, int paddingTop, int paddingBottom) {
        final int mT = -getOverscan(insetTop);
        final int pT = insetTop + paddingTop - mT;
        final int mB = -getOverscan(insetBottom);
        final int pB = insetBottom + paddingBottom - mB;

        AndroidUtilities.setViewLayoutMargins(listView, 0, mT, 0, mB);
        listView.setPadding(0, pT, 0, pB);
    }
}
