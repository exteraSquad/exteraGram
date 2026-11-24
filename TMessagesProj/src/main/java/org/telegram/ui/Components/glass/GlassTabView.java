package org.telegram.ui.Components.glass;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.RawRes;
import androidx.annotation.StringRes;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.IconPackType;
import com.exteragram.messenger.icons.IconManager;
import com.exteragram.messenger.utils.ui.tabs.MainTabsUiHelper;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CrossfadeDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.PremiumGradient;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.MainTabsLayout;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

public class GlassTabView extends FrameLayout implements MainTabsLayout.Tab, FactorAnimator.Target, ItemOptions.ScrimView, Theme.Colorable {
    private final TextView textView;
    private final RLottieImageView imageView;
    private BackupImageView backupImageView;
    private Theme.ResourcesProvider resourcesProvider;
    private final Paint paintCounterBackground = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final AnimatedTextView.AnimatedTextDrawable counter;

    private static final int ANIMATOR_ID_IS_SELECTED = 0;
    private static final int ANIMATOR_ID_COUNTER_VISIBLE = 1;
    private static final int ANIMATOR_ID_COUNTER_ERROR = 2;

    private static final FloatPropertyCompat<GlassTabView> INDICATOR_SIZE_PROGRESS = new FloatPropertyCompat<>("indicatorSizeProgress") {
        @Override
        public float getValue(GlassTabView tab) {
            return tab.indicatorSizeProgress;
        }

        @Override
        public void setValue(GlassTabView tab, float value) {
            tab.indicatorSizeProgress = value;
            tab.invalidate();
        }
    };
    private static final FloatPropertyCompat<GlassTabView> COLOR_PROGRESS = new FloatPropertyCompat<>("colorProgress") {
        @Override
        public float getValue(GlassTabView tab) {
            return tab.colorProgress;
        }

        @Override
        public void setValue(GlassTabView tab, float value) {
            tab.colorProgress = value;
            tab.applyColors();
            tab.invalidate();
        }
    };

    private final BoolAnimator isSelectedAnimator = new BoolAnimator(ANIMATOR_ID_IS_SELECTED, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 320);
    private final BoolAnimator isHasCounterAnimator = new BoolAnimator(ANIMATOR_ID_COUNTER_VISIBLE, this, CubicBezierInterpolator.EASE_OUT_QUINT, 380);
    private final BoolAnimator isHasCounterErrorAnimator = new BoolAnimator(ANIMATOR_ID_COUNTER_ERROR, this, CubicBezierInterpolator.EASE_OUT_QUINT, 380);
    private int colorSelected;
    private int colorSelectedText;
    private int colorDefault;
    private boolean usePremiumCounter;
    private boolean useMainTabSelectedIndicator;
    private boolean mainNavigationTab;
    private boolean labelHidden;
    private boolean drawingScrim;
    private SpringAnimation indicatorSizeSpring;
    private SpringAnimation colorSpring;
    private float indicatorSizeProgress;
    private float colorProgress;

    private TabAnimation tabAnimation;
    private TLRPC.TL_attachMenuBot tabAnimationBot;

    private final TextPaint defaultTextPaint;

    public GlassTabView(@NonNull Context context) {
        super(context);
        imageView = new RLottieImageView(context);
        addView(imageView, LayoutHelper.createFrame(44, 44, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, -6, 0, 0));

        imageView.setColorFilter(new PorterDuffColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN));

        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f);
        textView.setSingleLine();
        textView.setLines(1);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setTypeface(AndroidUtilities.bold());
        textView.setGravity(Gravity.CENTER);

        defaultTextPaint = new TextPaint(textView.getPaint());
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 28.33f, 0, 0));

        counter = new AnimatedTextView.AnimatedTextDrawable();
        counter.setTypeface(AndroidUtilities.bold());
        counter.setCallback(this);
        counter.setGravity(Gravity.CENTER);
        counter.setTextColor(Theme.getColor(Theme.key_glass_targetMainTabs));
        counter.setTextSize(dp(10));
        counter.setOverrideFullWidth(dp(200));
    }

    private boolean hasVisualWidth;
    private float visualWidth;
    public void setVisualWidth(float width) {
        hasVisualWidth = true;
        if (visualWidth != width) {
            visualWidth = width;
            checkVisualWidth();
            invalidate();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        checkVisualWidth();
    }

    private void checkVisualWidth() {
        if (hasVisualWidth) {
            final float offset = (visualWidth - getMeasuredWidth()) / 2f;
            imageView.setTranslationX(offset);
            textView.setTranslationX(offset);
        }
    }

    private static final RectF tmpRectF = new RectF();

    private boolean hasGestureSelectedOverride;
    private float gestureSelectedOverride;
    private boolean skipDrawSelector;

    public void setGestureSelectedOverride(float gestureSelectedOverride, boolean allow) {
        this.gestureSelectedOverride = gestureSelectedOverride;
        this.hasGestureSelectedOverride = allow && !useMainTabSelectedIndicator;
        invalidate();
    }

    public void setSkipDrawSelector(boolean skipDrawSelector) {
        if (this.skipDrawSelector != skipDrawSelector) {
            this.skipDrawSelector = skipDrawSelector;
            invalidate();
        }
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        final float viewWidth = hasVisualWidth ? visualWidth : getWidth();
        final float selectedFactor = hasGestureSelectedOverride ? gestureSelectedOverride : isSelectedAnimator.getFloatValue();
        final float indicatorFactor = useMainTabSelectedIndicator ? indicatorSizeProgress : selectedFactor;
        if (indicatorFactor > 0 && !skipDrawSelector) {
            final float scale;
            if (useMainTabSelectedIndicator) {
                paintCounterBackground.setColor(MainTabsUiHelper.getMainTabSelectedIndicatorColor(colorSelected, MathUtils.clamp(indicatorFactor, 0, 1)));
                MainTabsUiHelper.setMainTabSelectedIndicatorBounds(tmpRectF, viewWidth, getHeight(), indicatorFactor);
                scale = 1f;
            } else {
                final float alpha = AnimatorUtils.DECELERATE_INTERPOLATOR.getInterpolation(selectedFactor);
                paintCounterBackground.setColor(Theme.multAlpha(colorSelected, 0.09f * alpha));
                tmpRectF.set(0, 0, viewWidth, getHeight());
                scale = MainTabsUiHelper.getSelectedBackgroundScale(selectedFactor);
            }
            final float r = Math.min(tmpRectF.width(), tmpRectF.height()) / 2f;
            final float drawScale = scale * MathUtils.clamp(attachScale, 0, 1);
            canvas.save();
            canvas.scale(drawScale, drawScale, tmpRectF.centerX(), tmpRectF.centerY());
            canvas.drawRoundRect(tmpRectF, r, r, paintCounterBackground);
            canvas.restore();
        }

        final float hasCounter = (usePremiumCounter ? 1f : isHasCounterAnimator.getFloatValue()) * attachScale;
        final boolean saveLayer = hasCounter > 0;
        if (saveLayer) {
            canvas.saveLayer(0, 0, viewWidth, getHeight(), null);
        }

        super.dispatchDraw(canvas);

        if (hasCounter > 0) {
            canvas.save();

            final float gap = dpf2(1.33f);
            final float cx = viewWidth / 2f + dpf2(11);
            final float cy = MainTabsUiHelper.getMainTabCounterCenterY(mainNavigationTab);
            final float height = dpf2(16);
            final float width = Math.max(height, counter.getCurrentWidth() + dp(8));
            final float rOuter = dpf2(9.333f);
            final float rInner = dpf2(8f);
            tmpRectF.set(
                    cx - width / 2f - gap,
                    cy - height / 2f - gap,
                    cx + width / 2f + gap,
                    cy + height / 2f + gap
            );

            canvas.scale(hasCounter, hasCounter, cx, cy);
            canvas.drawRoundRect(tmpRectF, rOuter, rOuter, Theme.PAINT_CLEAR);
            tmpRectF.inset(gap, gap);

            if (usePremiumCounter) {
                if (premiumStarDrawable == null) {
                    premiumStarDrawable = getContext().getResources().getDrawable(R.drawable.star).mutate();
                }

                PremiumGradient.getInstance().updateMainGradientMatrix(0, 0, dp(96), dp(16), 0, 0);
                canvas.drawRoundRect(tmpRectF, rInner, rInner, PremiumGradient.getInstance().getMainGradientPaint());
                int x = (int)(cx - dpf2(7f));
                int y = (int)(cy - dpf2(7f));
                final int starColor = Theme.isCurrentThemeMonet() ? Theme.getColor(Theme.key_featuredStickers_buttonText) : 0;
                if (premiumStarColor != starColor) {
                    premiumStarDrawable.setColorFilter((premiumStarColor = starColor) != 0 ? new PorterDuffColorFilter(starColor, PorterDuff.Mode.SRC_IN) : null);
                }
                premiumStarDrawable.setBounds(x, y, x + dp(14), y + dp(14));
                premiumStarDrawable.draw(canvas);
            } else {
                paintCounterBackground.setColor(ColorUtils.blendARGB(Theme.getColor(Theme.key_telegram_color), Theme.getColor(Theme.key_fill_RedNormal), isHasCounterErrorAnimator.getFloatValue()));
                canvas.drawRoundRect(tmpRectF, rInner, rInner, paintCounterBackground);
                counter.setBounds(tmpRectF);
                counter.draw(canvas);
            }
            canvas.restore();
        }

        if (saveLayer) {
            canvas.restore();
        }
    }

    private Drawable premiumStarDrawable;
    private int premiumStarColor;

    @Override
    public void getBounds(RectF bounds) {
        if (useMainTabSelectedIndicator) {
            MainTabsUiHelper.setMainTabSelectedIndicatorBounds(bounds, getWidth(), getHeight(), 1f);
        } else {
            bounds.set(0, 0, getWidth(), getHeight());
        }
    }

    @Override
    public void drawScrim(Canvas canvas, float progress) {
        drawingScrim = useMainTabSelectedIndicator;
        draw(canvas);
        drawingScrim = false;
    }

    @Override
    protected boolean drawChild(@NonNull Canvas canvas, View child, long drawingTime) {
        if (drawingScrim && child == textView) {
            return false;
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    public void setCounter(String text, boolean isError, boolean animated) {
        counter.setText(text, animated);
        isHasCounterAnimator.setValue(!TextUtils.isEmpty(text), animated);
        isHasCounterErrorAnimator.setValue(isError, animated);
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return who == counter || super.verifyDrawable(who);
    }

    public void setPremiumBadge(boolean usePremiumBadge) {
        usePremiumCounter = usePremiumBadge;
    }

    public void setSelected(boolean selected, boolean animated) {
        if (useMainTabSelectedIndicator) {
            isSelectedAnimator.setValue(selected, false);
            final float target = selected ? 1f : 0f;
            if (animated) {
                indicatorSizeSpring.animateToFinalPosition(target);
                colorSpring.animateToFinalPosition(target);
            } else {
                indicatorSizeSpring.cancel();
                colorSpring.cancel();
                indicatorSizeProgress = target;
                colorProgress = target;
                applyColors();
                invalidate();
            }
        } else {
            isSelectedAnimator.setValue(selected, animated);
        }
        checkPlayAnimation(animated);

        textView.setTypeface(useMainTabSelectedIndicator || !selected
                ? AndroidUtilities.bold()
                : AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_EXTRA_BOLD));
    }

    public boolean isTabSelected() {
        return isSelectedAnimator.getValue();
    }

    @Override
    public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
        if (id == ANIMATOR_ID_IS_SELECTED) {
            applyColors();
        }
        invalidate();
    }

    private boolean needUpdateBackupViewColor;

    private void applyColors() {
        final float factor = useMainTabSelectedIndicator ? colorProgress : isSelectedAnimator.getFloatValue();
        final int color = ColorUtils.blendARGB(colorDefault, colorSelected, factor);
        final int colorText = ColorUtils.blendARGB(colorDefault, colorSelectedText, factor);

        final PorterDuffColorFilter filter = new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN);
        if (backupImageView != null && needUpdateBackupViewColor) {
            backupImageView.setColorFilter(filter);
            backupImageView.invalidate();
        }
        if (staticIconDrawable != null) {
            staticIconOutline.setColorFilter(filter);
            staticIconFilled.setColorFilter(filter);
            staticIconDrawable.setProgress(MathUtils.clamp(factor, 0, 1));
        }
        imageView.setColorFilter(filter);
        textView.setTextColor(colorText);
        counter.setTextColor(Theme.getColor(Theme.key_glass_targetMainTabs));
    }

    @Override
    public void updateColors() {
        // every theme frame: applyColors allocates a color filter, so only when a color changed
        if (colorDefault != Theme.getColor(Theme.key_glass_tabUnselected, resourcesProvider) ||
            colorSelected != Theme.getColor(Theme.key_glass_tabSelected, resourcesProvider) ||
            colorSelectedText != Theme.getColor(Theme.key_glass_tabSelectedText, resourcesProvider)) {
            updateColorsLottie();
        }
    }

    @Override
    public void addColorKeys(java.util.HashSet<Integer> colors) {
        colors.add(Theme.key_glass_tabUnselected);
        colors.add(Theme.key_glass_tabSelected);
        colors.add(Theme.key_glass_tabSelectedText);
    }

    public void updateColorsLottie() {
        colorDefault = Theme.getColor(Theme.key_glass_tabUnselected, resourcesProvider);
        colorSelected = Theme.getColor(Theme.key_glass_tabSelected, resourcesProvider);
        colorSelectedText = Theme.getColor(Theme.key_glass_tabSelectedText, resourcesProvider);
        applyColors();
        invalidate();
    }


    private boolean lastIsSelected;
    private int lastIconAnimationRaw;
    private long lastBotIconId;
    private CrossfadeDrawable staticIconDrawable;
    private Drawable staticIconOutline;
    private Drawable staticIconFilled;

    private void checkPlayAnimation(boolean animated) {
        final boolean isSelected = isSelectedAnimator.getValue();

        if (tabAnimationBot !=  null) {
            boolean animatedIcon = true;
            TLRPC.TL_attachMenuBotIcon icon = MediaDataController.getAnimatedAttachMenuBotIcon(tabAnimationBot, isSelected);
            if (icon == null) {
                icon = MediaDataController.getStaticAttachMenuBotIcon(tabAnimationBot);
                animatedIcon = false;
            }
            if (icon != null && icon.icon != null) {
                TLRPC.Document iconDoc = icon.icon;
                if (lastBotIconId != icon.icon.id) {
                    String filter = "24_24" + (animatedIcon && !animated || true ? "_lastframe" : "");
                    backupImageView.setImage(
                        ImageLocation.getForDocument(iconDoc), filter,
                        ImageLocation.getForDocument(iconDoc), filter,
                        animatedIcon ? null : DocumentObject.getSvgThumb(iconDoc, Theme.key_windowBackgroundGray, 1f),
                        tabAnimationBot
                    );
                    lastBotIconId = iconDoc.id;
                }
            } else {
                backupImageView.clearImage();
            }
            applyColors();
            return;
        }

        if (tabAnimation == null) {
            return;
        }

        if ((tabAnimation.iconToFilled == 0 || !IconManager.INSTANCE.isBasePackOnly(IconPackType.DEFAULT)) && (tabAnimation.iconDrawableOutline != 0 || tabAnimation.iconDrawableFilled != 0)) {
            final int outlineRes = tabAnimation.iconDrawableOutline != 0 ? tabAnimation.iconDrawableOutline : tabAnimation.iconDrawableFilled;
            final int filledRes = tabAnimation.iconDrawableFilled != 0 ? tabAnimation.iconDrawableFilled : tabAnimation.iconDrawableOutline;
            if (lastIconAnimationRaw != filledRes) {
                lastIconAnimationRaw = filledRes;
                staticIconOutline = getContext().getDrawable(outlineRes).mutate();
                staticIconFilled = getContext().getDrawable(filledRes).mutate();
                staticIconDrawable = new CrossfadeDrawable(staticIconOutline, staticIconFilled);
                imageView.clearAnimationDrawable();
                imageView.setImageDrawable(staticIconDrawable);
            }
            applyColors();
            return;
        }

        if (tabAnimation.iconStatic != -1) {
            imageView.setImageResource(tabAnimation.iconStatic);
            applyColors();
            return;
        }

        final int animationToSet = isSelected ?
            tabAnimation.iconToFilled : tabAnimation.iconToOutline;

        if (tabAnimation.endFrameMid != -1) {
            boolean update = lastIsSelected != isSelected;
            if (lastIconAnimationRaw != animationToSet) {
                lastIconAnimationRaw = animationToSet;
                imageView.setAnimation(animationToSet, 24, 24);
                update = true;
            }

            if (update) {
                final RLottieDrawable drawable = imageView.getAnimatedDrawable();
                if (drawable == null) {
                    return;
                }

                if (isSelected) {
                    drawable.setCustomEndFrame(tabAnimation.endFrameMid);
                    if (drawable.getCurrentFrame() >= tabAnimation.endFrameEnd - 2) {
                        drawable.setCurrentFrame(0, false);
                    }
                    if (drawable.getCurrentFrame() <= tabAnimation.endFrameMid) {
                        drawable.start();
                    } else {
                        drawable.setCurrentFrame(tabAnimation.endFrameMid);
                    }
                } else {
                    if (drawable.getCurrentFrame() >= tabAnimation.endFrameMid - 1) {
                        drawable.setCustomEndFrame(tabAnimation.endFrameEnd - 1);
                        drawable.start();
                    } else {
                        drawable.setCustomEndFrame(0);
                        drawable.setCurrentFrame(0);
                    }
                }
            }
            lastIsSelected = isSelected;
            return;
        }

        if (tabAnimation.iconToFilled != tabAnimation.iconToOutline) {
            if (lastIconAnimationRaw != animationToSet) {
                lastIconAnimationRaw = animationToSet;

                imageView.setAnimation(animationToSet, 24, 24);
                imageView.getAnimatedDrawable().setPlayInDirectionOfCustomEndFrame(false);
                if (animated) {
                    imageView.getAnimatedDrawable().setCurrentFrame(0);
                    imageView.playAnimation();
                } else {
                    imageView.getAnimatedDrawable().setProgress(0.99f);
                }
            }
            return;
        }

        if (imageView.getAnimatedDrawable() == null) {
            imageView.setAnimation(tabAnimation.iconToFilled, 24, 24);
        }

        final RLottieDrawable drawable = imageView.getAnimatedDrawable();
        if (drawable == null) {
            return;
        }

        if (lastIsSelected != isSelected) {
            lastIsSelected = isSelected;
            if (isSelected) {
                drawable.setPlayInDirectionOfCustomEndFrame(false);
                drawable.setCurrentFrame(0);
                drawable.setCustomEndFrame(drawable.getFramesCount());
            } else {
                drawable.setPlayInDirectionOfCustomEndFrame(true);
                drawable.setCurrentFrame(drawable.getFramesCount());
                drawable.setCustomEndFrame(0);
            }
            imageView.playAnimation();
        }
    }

    public static GlassTabView createMainTab(Context context, Theme.ResourcesProvider resourcesProvider, TabAnimation tabAnimation, @StringRes int stringRes) {
        GlassTabView tab = new GlassTabView(context);
        tab.resourcesProvider = resourcesProvider;
        tab.tabAnimation = tabAnimation;
        tab.textView.setText(LocaleController.getString(stringRes));
        tab.checkPlayAnimation(false);
        tab.imageView.setLayoutParams(LayoutHelper.createFrame(24, 24, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 4, 0, 0));
        tab.colorDefault = Theme.getColor(Theme.key_glass_tabUnselected, resourcesProvider);
        tab.colorSelected = Theme.getColor(Theme.key_glass_tabSelected, resourcesProvider);
        tab.colorSelectedText = Theme.getColor(Theme.key_glass_tabSelectedText, resourcesProvider);
        tab.applyColors();
        return tab;
    }

    public static GlassTabView createMainNavigationTab(Context context, Theme.ResourcesProvider resourcesProvider, TabAnimation tabAnimation, @StringRes int stringRes) {
        GlassTabView tab = createMainTab(context, resourcesProvider, tabAnimation, stringRes);
        tab.setMainTabStyle();
        return tab;
    }

    public static GlassTabView createAvatar(Context context, Theme.ResourcesProvider resourcesProvider, int currentAccount, @StringRes int stringRes) {
        GlassTabView tab = new GlassTabView(context);
        tab.textView.setText(LocaleController.getString(stringRes));
        tab.imageView.setVisibility(GONE);

        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(UserConfig.getInstance(currentAccount).getClientUserId());
        AvatarDrawable avatarDrawable = new AvatarDrawable(user);

        BackupImageView backupImageView = new BackupImageView(context);
        backupImageView.setForUserOrChat(user, avatarDrawable);
        backupImageView.setRoundRadius(ExteraConfig.getAvatarCorners(22));
        tab.backupImageView = backupImageView;

        tab.addView(backupImageView, LayoutHelper.createFrame(22, 22, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 5, 0, 0));
        tab.colorDefault = Theme.getColor(Theme.key_glass_tabUnselected, resourcesProvider);
        tab.colorSelected = Theme.getColor(Theme.key_glass_tabSelected, resourcesProvider);
        tab.colorSelectedText = Theme.getColor(Theme.key_glass_tabSelectedText, resourcesProvider);
        tab.applyColors();
        return tab;
    }

    public static GlassTabView createMainNavigationAvatar(Context context, Theme.ResourcesProvider resourcesProvider, int currentAccount, @StringRes int stringRes) {
        GlassTabView tab = createAvatar(context, resourcesProvider, currentAccount, stringRes);
        tab.setMainTabStyle();
        return tab;
    }

    private void setMainTabStyle() {
        mainNavigationTab = true;
        imageView.setLayoutParams(LayoutHelper.createFrame(
                24, 24, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, MainTabsUiHelper.getMainTabIconTopDp(), 0, 0
        ));
        if (backupImageView != null) {
            backupImageView.setLayoutParams(LayoutHelper.createFrame(
                    22, 22, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, MainTabsUiHelper.getMainTabAvatarTopDp(), 0, 0
            ));
        }
        if (ExteraConfig.getHideNavigationBarLabels()) {
            labelHidden = true;
            textView.setVisibility(GONE);
            setContentDescription(textView.getText());
        }
        if (!MainTabsUiHelper.isMaterial3NavigationBar()) {
            return;
        }
        useMainTabSelectedIndicator = true;
        indicatorSizeSpring = MainTabsUiHelper.createMaterial3IndicatorSizeSpring(this, INDICATOR_SIZE_PROGRESS);
        colorSpring = MainTabsUiHelper.createMaterial3ColorSpring(this, COLOR_PROGRESS);
        MainTabsUiHelper.applyMaterial3MainTabStyle(textView);
    }

    public void updateUserAvatar(int currentAccount) {
        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(UserConfig.getInstance(currentAccount).getClientUserId());
        AvatarDrawable avatarDrawable = new AvatarDrawable(user);
        backupImageView.setForUserOrChat(user, avatarDrawable);
    }

    public static GlassTabView createAttachTab(Context context, Theme.ResourcesProvider resourcesProvider) {
        GlassTabView tab = new GlassTabView(context);
        tab.resourcesProvider = resourcesProvider;
        tab.selfMeasure = true;
        tab.textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        tab.textView.setPadding(dp(8), 0, dp(8), 0);
        tab.checkPlayAnimation(false);
        tab.imageView.setLayoutParams(LayoutHelper.createFrame(24, 24, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 4, 0, 0));
        tab.colorDefault = Theme.getColor(Theme.key_glass_tabUnselected, resourcesProvider);
        tab.colorSelected = Theme.getColor(Theme.key_glass_tabSelected, resourcesProvider);
        tab.colorSelectedText = Theme.getColor(Theme.key_glass_tabSelectedText, resourcesProvider);
        tab.applyColors();
        return tab;
    }

    public static GlassTabView createAttachBotTab(Context context, Theme.ResourcesProvider resourcesProvider) {
        GlassTabView tab = new GlassTabView(context);
        tab.resourcesProvider = resourcesProvider;
        tab.selfMeasure = true;
        tab.textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        tab.textView.setPadding(dp(8), 0, dp(8), 0);
        tab.imageView.setVisibility(GONE);
        tab.checkPlayAnimation(false);
        tab.backupImageView = new BackupImageView(context);
        tab.addView(tab.backupImageView, LayoutHelper.createFrame(24, 24, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 4, 0, 0));
        tab.colorDefault = Theme.getColor(Theme.key_glass_tabUnselected, resourcesProvider);
        tab.colorSelected = Theme.getColor(Theme.key_glass_tabSelected, resourcesProvider);
        tab.colorSelectedText = Theme.getColor(Theme.key_glass_tabSelectedText, resourcesProvider);
        tab.applyColors();
        return tab;
    }

    public BackupImageView getBackupImageView() {
        return backupImageView;
    }

    private boolean selfMeasure;
    private int additionalWidth;

    public void setAdditionalWidth(int additionalWidth) {
        this.additionalWidth = additionalWidth;
        this.selfMeasure = true;
    }

    public float measureAttachTabWidth() {
        final float textWidth = measureTextWidth();
        final float padding = lerp(dpf2(16), dp(8), MathUtils.clamp((textWidth - dp(40)) / dp(16), 0, 1));
        return Math.min(dp(84), (int) (textWidth + padding * 2));
    }

    public float attachScale = 1;
    public void setAttachScale(float scale) {
        textView.setScaleX(scale);
        textView.setScaleY(scale);
        imageView.setScaleX(scale);
        imageView.setScaleY(scale);
        if (backupImageView != null) {
            backupImageView.setScaleX(scale);
            backupImageView.setScaleY(scale);
        }
        attachScale = scale;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (selfMeasure) {
            final int width = (int) (measureAttachTabWidth()) + additionalWidth;
            super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightMeasureSpec);
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    @Override
    public float measureTextWidth() {
        return defaultTextPaint.measureText(textView.getText().toString());
    }

    private TextPaint scaledTextPaint;

    @Override
    public float measureTextWidth(float textSizeDp) {
        if (labelHidden) {
            return MainTabsUiHelper.getMainTabIconOnlyWidth();
        }
        if (scaledTextPaint == null) {
            scaledTextPaint = new TextPaint(defaultTextPaint);
        }
        scaledTextPaint.setTextSize(dp(textSizeDp));
        return scaledTextPaint.measureText(textView.getText().toString());
    }

    @Override
    public void setTextSizeDp(float textSizeDp) {
        final float px = dp(textSizeDp);
        if (textView.getTextSize() != px) {
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, textSizeDp);
            defaultTextPaint.setTextSize(px);
        }
    }

    private enum TabAnimationType {
        LOTTIE,
        STATIC
    }

    public enum TabAnimation {
        CONTACTS(R.raw.tab_contacts, R.drawable.tabs_contact_active_24, R.drawable.tabs_contacts_24, -1, -1),
        CALLS(R.raw.tab_calls, R.drawable.tabs_calls_active_24, R.drawable.tabs_calls_24, -1, -1),
        CHATS(R.raw.tab_chats, R.drawable.tabs_chats_active_24, R.drawable.tabs_chats_24, -1, -1),
        SETTINGS(R.raw.tab_settings, R.drawable.filled_profile_settings, R.drawable.outline_profile_settings, -1, -1),
        FEED(0, R.drawable.ic_feed_filled, R.drawable.ic_feed, -1, -1),

        CHECKLIST(R.raw.tab_checklist, R.raw.tab_checklist_reverse),
        COLORS(R.raw.tab_colors, R.raw.tab_colors_reverse),
        FILES(R.raw.tab_files, R.raw.tab_files_reverse),
        GALLERY(R.raw.tab_gallery, R.raw.tab_gallery_reverse),
        GIFT(R.raw.tab_gift, R.raw.tab_gift_reverse),
        LOCATION(R.raw.tab_location, R.raw.tab_location_reverse),
        STICKER(R.raw.tab_sticker, R.raw.tab_sticker_reverse),
        EMOJI(R.raw.tab_emoji, R.raw.tab_emoji_reverse),
        MODELS(R.raw.tab_models, R.raw.tab_models_reverse),
        MUSIC(R.raw.tab_music, R.raw.tab_music_reverse),
        POLL(R.raw.tab_poll, R.raw.tab_poll_reverse),
        SYMBOLS(R.raw.tab_symbols, R.raw.tab_symbols_reverse),
        REPLIES(R.raw.tab_reply, R.raw.tab_reply_reverse),
        WALLET(R.raw.tab_wallet, R.raw.tab_wallet_reverse),
        LINK(TabAnimationType.STATIC, R.drawable.tabs_link_24),
        ARTICLE(R.raw.tab_article, R.raw.tab_article_reverse),
        GRAM(R.raw.gram, R.raw.gram_reverse),

        BOOSTS(R.raw.boosts, 25, 49),
        MONETIZATION(R.raw.monetize, 19, 45),
        STORIES_SAVED(R.raw.msg_stories_saved, 20, 40),
        STORIES_ARCHIVE(R.raw.msg_stories_archive);

        public final @RawRes int iconToFilled;
        public final @RawRes int iconToOutline;
        public final @DrawableRes int iconDrawableFilled;
        public final @DrawableRes int iconDrawableOutline;
        public final @DrawableRes int iconStatic;
        public final int endFrameMid, endFrameEnd;

        TabAnimation(int iconRes, int endFrameMid, int endFrameEnd) {
            this.iconToFilled = iconRes;
            this.iconToOutline = iconRes;
            this.iconDrawableFilled = 0;
            this.iconDrawableOutline = 0;
            this.endFrameMid = endFrameMid;
            this.endFrameEnd = endFrameEnd;
            this.iconStatic = -1;
        }

        TabAnimation(TabAnimationType type, int icon) {
            if (type == TabAnimationType.LOTTIE) {
                this.iconToFilled = icon;
                this.iconToOutline = icon;
                this.iconStatic = -1;
            } else {
                this.iconStatic = icon;
                this.iconToFilled = -1;
                this.iconToOutline = -1;
            }
            this.iconDrawableFilled = 0;
            this.iconDrawableOutline = 0;
            this.endFrameMid = -1;
            this.endFrameEnd = -1;
        }

        TabAnimation(int iconRes) {
            this.iconToFilled = iconRes;
            this.iconToOutline = iconRes;
            this.iconDrawableFilled = 0;
            this.iconDrawableOutline = 0;
            this.endFrameMid = -1;
            this.endFrameEnd = -1;
            this.iconStatic = -1;
        }

        TabAnimation(int iconRes, int staticIconFilled, int staticIconOutline, int endFrameMid, int endFrameEnd) {
            this.iconToFilled = iconRes;
            this.iconToOutline = iconRes;
            this.iconDrawableFilled = staticIconFilled;
            this.iconDrawableOutline = staticIconOutline;
            this.endFrameMid = endFrameMid;
            this.endFrameEnd = endFrameEnd;
            this.iconStatic = -1;
        }

        TabAnimation(int iconToFilled, int iconToOutline) {
            this.iconToFilled = iconToFilled;
            this.iconToOutline = iconToOutline;
            this.iconDrawableFilled = 0;
            this.iconDrawableOutline = 0;
            this.endFrameMid = -1;
            this.endFrameEnd = -1;
            this.iconStatic = -1;
        }
    }

    public void setTabAnimation(TabAnimation animation) {
        tabAnimation = animation;
        tabAnimationBot = null;
        lastIconAnimationRaw = 0;
        lastBotIconId = 0;
        staticIconDrawable = null;
        imageView.clearAnimationDrawable();
        checkPlayAnimation(false);
    }

    public void setText(CharSequence text) {
        textView.setText(text);
    }


    private AvatarDrawable avatarDrawable;

    public void setAttachBot(TLRPC.User user, TLRPC.TL_attachMenuBot bot, int currentAccount) {
        if (user == null || bot == null) {
            return;
        }
        tabAnimation = null;
        tabAnimationBot = bot;
        lastIconAnimationRaw = 0;
        lastBotIconId = 0;
        textView.setText(bot.short_name);

        backupImageView.setRoundRadius(0);
        backupImageView.setSize(dp(24), dp(24));
        backupImageView.setLayoutParams(LayoutHelper.createFrame(24, 24, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 4, 0, 0));
        needUpdateBackupViewColor = true;
        checkPlayAnimation(false);
        applyColors();
        invalidate();
    }

    public void setAttachBotUser(TLRPC.User user, int currentAccount) {
        if (user == null) {
            return;
        }
        tabAnimation = null;
        tabAnimationBot = null;
        lastIconAnimationRaw = 0;
        lastBotIconId = 0;

        textView.setText(ContactsController.formatName(user.first_name, user.last_name));
        if (avatarDrawable == null) {
            avatarDrawable = new AvatarDrawable();
        }
        avatarDrawable.setInfo(currentAccount, user);
        backupImageView.setForUserOrChat(user, avatarDrawable);
        backupImageView.setSize(-1, -1);
        backupImageView.setRoundRadius(ExteraConfig.getAvatarCorners(22));
        backupImageView.setLayoutParams(LayoutHelper.createFrame(22, 22, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 5, 0, 0));
        backupImageView.setColorFilter(null);
        needUpdateBackupViewColor = false;
        invalidate();
    }

    public void onPreBind() {

    }
}
