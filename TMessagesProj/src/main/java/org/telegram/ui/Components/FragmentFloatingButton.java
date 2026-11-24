package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.utils.ViewOutlineProviderImpl;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.utils.ui.FabUiHelper;
import com.exteragram.messenger.utils.ui.UIUtil;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.utils.glass.source.GlassSourceColor;

import java.util.ArrayList;

import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

@SuppressLint("ViewConstructor")
public class FragmentFloatingButton extends FrameLayout implements FactorAnimator.Target, Theme.Colorable {
    private final int ANIMATOR_ID_BUTTON_VISIBLE = 0;
    private final int ANIMATOR_ID_PROGRESS_VISIBLE = 1;

    private final BoolAnimator animatorButtonVisible = new BoolAnimator(ANIMATOR_ID_BUTTON_VISIBLE, this,
        CubicBezierInterpolator.EASE_OUT_QUINT, 380, true);

    private final BoolAnimator animatorProgressVisible = new BoolAnimator(ANIMATOR_ID_PROGRESS_VISIBLE, this,
        CubicBezierInterpolator.EASE_OUT_QUINT, 380);


    public final RLottieImageView imageView;
    public final RadialProgressView progressView;
    private final Theme.ResourcesProvider resourcesProvider;
    private ArrayList<View> additionalContentViews;
    private final boolean isSubButton;

    public FragmentFloatingButton(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
        this(context, resourcesProvider, false);
    }

    public FragmentFloatingButton(@NonNull Context context, Theme.ResourcesProvider resourcesProvider, boolean isSubButton) {
        super(context);

        this.resourcesProvider = resourcesProvider;
        this.isSubButton = isSubButton;

        imageView = new RLottieImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        addView(imageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        progressView = new RadialProgressView(context);
        progressView.setSize(dp(18));
        progressView.setStrokeWidth(2);
        addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        setAnimatedVisibility(progressView, 0);

        ScaleStateListAnimator.apply(this);
        if (!isSubButton) {
            setOutlineProvider(
                    ExteraConfig.getSquareFab()
                            ? ViewOutlineProviderImpl.boundsWithPaddingRoundRect(0, dp(FabUiHelper.getFabCornerRadiusDp()))
                            : ViewOutlineProviderImpl.BOUNDS_OVAL
            );
            setTranslationZ(dpf2(FabUiHelper.getFabElevationDp()));
        }

        if (isSubButton) {
            iBlur3SourceColor = new GlassSourceColor();
            iBlur3Background = new BlurredBackgroundDrawable(iBlur3SourceColor);
            iBlur3Background.setColorProvider(BlurredBackgroundProviderImpl.subFloatingButton(resourcesProvider));
            iBlur3Background.setRadius(dp(FabUiHelper.getSubFabBackgroundRadiusDp()));
            iBlur3Background.setPadding(dp(FabUiHelper.getSubFabPaddingDp()));
        }

        updateColors();
    }

    public void setProgressVisible(boolean visible, boolean animated) {
        animatorProgressVisible.setValue(visible, animated);
    }

    public void setButtonVisible(boolean visible, boolean animated) {
        animatorButtonVisible.setValue(visible, animated);
    }

    public boolean getButtonVisible() {
        return animatorButtonVisible.getValue();
    }

    public boolean getProgressVisible() {
        return animatorProgressVisible.getValue();
    }

    @Override
    public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
        if (id == ANIMATOR_ID_BUTTON_VISIBLE) {
            setAnimatedVisibility(this, factor);
            setClickable(factor >= 0.99f);
            setAdditionalTranslationY(dp(isSubButton ? 64 : 40) * (1f - factor));
        } else if (id == ANIMATOR_ID_PROGRESS_VISIBLE) {
            setAnimatedVisibility(progressView, factor);
            setAnimatedVisibility(imageView, 1f - factor);
            if (additionalContentViews != null) {
                for (View v : additionalContentViews) {
                    setAnimatedVisibility(v, 1f - factor);
                }
            }
        }
    }

    public void setAnimation(@RawRes int animation, int size) {
        imageView.setAnimation(animation, size, size);
    }

    public void setImageResource(@DrawableRes int drawable) {
        imageView.setImageResource(drawable);
    }

    private GlassSourceColor iBlur3SourceColor;
    private BlurredBackgroundDrawable iBlur3Background;

    private Drawable themedBackground;
    private int themedIconColor;
    private boolean themedIconColorSet;
    private int themedBackgroundColor, themedBackgroundPressedColor;

    @Override
    public void updateColors() {
        if (imageView == null || progressView == null) {
            return;
        }
        final boolean recolorBackground = themedBackground != null && getBackground() == themedBackground;
        if (isSubButton) {
            final int iconColor = Theme.getColor(Theme.key_actionBarDefaultIcon, resourcesProvider);
            if (iconColor != themedIconColor || !themedIconColorSet) {
                themedIconColor = iconColor;
                themedIconColorSet = true;
                imageView.setColorFilter(iconColor, PorterDuff.Mode.SRC_IN);
            }
            progressView.setProgressColor(Theme.getColor(Theme.key_actionBarDefaultIcon, resourcesProvider));

            if (iBlur3SourceColor != null) {
                iBlur3SourceColor.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
            }
            if (iBlur3Background != null) {
                iBlur3Background.updateColors();
            }
            invalidate();

            int rad = dp(FabUiHelper.getSubFabBackgroundRadiusDp());
            int pressedColor = Theme.getColor(Theme.key_listSelector, resourcesProvider);
            if (recolorBackground) {
                if (pressedColor != themedBackgroundPressedColor) {
                    themedBackgroundPressedColor = pressedColor;
                    Theme.setSelectorDrawableColor(themedBackground, pressedColor, true);
                }
            } else {
                setBackground(themedBackground = Theme.createInsetRoundRectDrawable(themedBackgroundPressedColor = pressedColor, rad, dp(FabUiHelper.getSubFabPaddingDp())));
            }
        } else {
            final int iconColor = Theme.getColor(Theme.key_chats_actionIcon, resourcesProvider);
            if (iconColor != themedIconColor || !themedIconColorSet) {
                themedIconColor = iconColor;
                themedIconColorSet = true;
                imageView.setColorFilter(iconColor, PorterDuff.Mode.SRC_IN);
            }
            progressView.setProgressColor(Theme.getColor(Theme.key_chats_actionIcon, resourcesProvider));
            final int color = Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider);
            final int pressedColor = Theme.getColor(Theme.key_featuredStickers_addButtonPressed, resourcesProvider);
            if (!recolorBackground || color != themedBackgroundColor || pressedColor != themedBackgroundPressedColor) {
                themedBackgroundColor = color;
                themedBackgroundPressedColor = pressedColor;
                setBackground(themedBackground = UIUtil.createFabBackground(FabUiHelper.getFabSizeDp(), color, pressedColor));
            }
        }
    }

    public static FrameLayout.LayoutParams createSubButtonLayoutParams() {
        return LayoutHelper.createFrame(FabUiHelper.getFabSizeDp(), FabUiHelper.getFabSizeDp(),
                (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM,
                20, 0, 20, FabUiHelper.getSubFabBottomMarginDp());
    }

    public static FrameLayout.LayoutParams createDefaultLayoutParams() {
        return LayoutHelper.createFrame(FabUiHelper.getFabSizeDp(), FabUiHelper.getFabSizeDp(),
                (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM,
                20, 0, 20, FabUiHelper.getFabBottomMarginDp());
    }


    public static FrameLayout.LayoutParams createDefaultLayoutParamsBig() {
        return LayoutHelper.createFrame(FabUiHelper.LARGE_FAB_SIZE_DP, FabUiHelper.LARGE_FAB_SIZE_DP,
                (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM,
                20, 0, 20, FabUiHelper.getFabBottomMarginDp());
    }

    private float additionalTranslationY;
    private float internalTranslationY;
    private boolean setTranslationInternal;

    private void setAdditionalTranslationY(float translationY) {
        if (additionalTranslationY != translationY) {
            setTranslationInternal = true;
            super.setTranslationY(internalTranslationY + translationY);
            setTranslationInternal = false;
            additionalTranslationY = translationY;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (iBlur3Background != null) {
            iBlur3Background.setBounds(0, 0, w, h);
        }
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (iBlur3Background != null) {
            iBlur3Background.draw(canvas);
        }
        super.draw(canvas);
    }

    @Override
    public void setTranslationY(float translationY) {
        if (internalTranslationY != translationY) {
            setTranslationInternal = true;
            super.setTranslationY(translationY + additionalTranslationY);
            setTranslationInternal = false;
            internalTranslationY = translationY;
        }
    }

    @Override
    public float getTranslationY() {
        if (setTranslationInternal) {
            return super.getTranslationY();
        }

        return internalTranslationY;
    }

    public void addAdditionalView(View view) {
        if (additionalContentViews == null) {
            additionalContentViews = new ArrayList<>();
        }
        additionalContentViews.add(view);
        setAnimatedVisibility(view, 1f - animatorProgressVisible.getFloatValue());
    }

    public static void setAnimatedVisibility(@Nullable View v, float f) {
        if (v == null) {
            return;
        }

        v.setAlpha(f);
        v.setScaleX(lerp(0.4f, 1f, f));
        v.setScaleY(lerp(0.4f, 1f, f));
        v.setVisibility(f > 0 ? VISIBLE : GONE);
    }
}
