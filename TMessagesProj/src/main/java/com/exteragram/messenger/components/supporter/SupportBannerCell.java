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
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.badges.BadgesController;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.GLIcon.GLIconTextureView;
import org.telegram.ui.Components.Premium.StarParticlesView;
import org.telegram.ui.Components.voip.CellFlickerDrawable;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

public class SupportBannerCell extends FrameLayout {

    private static final int ARROW_SIZE = 132;
    private static final int ARROW_INSET = 12;
    private static final int BUTTON_RADIUS = 18;

    private final int currentAccount;
    private final StarParticlesView particlesView;
    private final GLIconTextureView iconView;
    private final LinearLayout contentLayout;
    private final TextView titleView;
    private final TextView subtitleView;
    private final ButtonWithCounterView button;
    private final CellFlickerDrawable flicker = new CellFlickerDrawable();

    private boolean supporter;
    private Integer number;
    private boolean bound;

    public SupportBannerCell(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider, Runnable onClick, Runnable onButtonClick) {
        super(context);
        this.currentAccount = currentAccount;

        final boolean rtl = LocaleController.isRTL;
        final int accentColor = ContextCompat.getColor(context, R.color.ic_background);
        final int radiusDp = ExteraConfig.getSectionRadiusDp();

        final GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{
                accentColor,
                SupporterArrowView.shiftColor(accentColor, 0f, 1f, .88f)
        });
        background.setCornerRadius(dp(radiusDp));

        FrameLayout card = new FrameLayout(context);
        card.setBackground(background);
        card.setClipToOutline(true);
        card.setMinimumHeight(dp(ARROW_SIZE));
        addView(card, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 12, 12, 12, 12));

        final int particlesCount = switch (SharedConfig.getDevicePerformanceClass()) {
            case SharedConfig.PERFORMANCE_CLASS_HIGH -> 60;
            case SharedConfig.PERFORMANCE_CLASS_AVERAGE -> 35;
            default -> 20;
        };
        particlesView = new StarParticlesView(context, particlesCount) {
            @Override
            protected void configure() {
                super.configure();
                drawable.useGradient = false;
                drawable.useBlur = false;
                drawable.forceMaxAlpha = true;
                drawable.checkBounds = true;
                drawable.color = Color.WHITE;
                drawable.init();
            }
        };
        final int particlesWidth = 420;
        final int particlesShift = -(particlesWidth - ARROW_SIZE) / 2;
        card.addView(particlesView, LayoutHelper.createFrame(particlesWidth, LayoutHelper.MATCH_PARENT, rtl ? Gravity.LEFT : Gravity.RIGHT, rtl ? particlesShift : 0, 0, rtl ? 0 : particlesShift, 0));

        View selectorView = new View(context);
        selectorView.setBackground(Theme.createRadSelectorDrawable(Theme.multAlpha(Color.WHITE, .14f), radiusDp, radiusDp));
        selectorView.setOnClickListener(v -> onClick.run());
        card.addView(selectorView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        card.addView(contentLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, rtl ? ARROW_SIZE + 12 : 20, 18, rtl ? 20 : ARROW_SIZE + 12, 18));

        titleView = new TextView(context);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        titleView.setTextColor(Color.WHITE);
        titleView.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);
        contentLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        subtitleView = new TextView(context);
        subtitleView.setTypeface(AndroidUtilities.regular());
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitleView.setTextColor(Theme.multAlpha(Color.WHITE, .86f));
        subtitleView.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);
        contentLayout.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));

        flicker.drawFrame = false;
        flicker.repeatProgress = 3f;
        flicker.setColors(accentColor, 48, 0);
        final int buttonRippleColor = Theme.multAlpha(accentColor, .16f);
        button = new ButtonWithCounterView(context, true, resourcesProvider) {
            @Override
            public void updateColors() {
                super.updateColors();
                setColor(Color.WHITE, buttonRippleColor);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                flicker.setParentWidth(getMeasuredWidth());
                AndroidUtilities.rectTmp.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
                flicker.draw(canvas, AndroidUtilities.rectTmp, dp(BUTTON_RADIUS), this);
            }
        };
        button.setRoundRadius(BUTTON_RADIUS);
        button.setTextColor(accentColor);
        button.setOnClickListener(v -> onButtonClick.run());
        contentLayout.addView(button, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 36, rtl ? Gravity.RIGHT : Gravity.LEFT, 0, 14, 0, 0));

        iconView = SupporterArrowView.createIcon(context, resourcesProvider, Color.WHITE, ColorUtils.blendARGB(Color.WHITE, accentColor, .12f), ColorUtils.blendARGB(accentColor, Color.WHITE, .5f));
        iconView.setStarParticlesView(particlesView);
        FrameLayout iconContainer = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                final int size = (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY ? MeasureSpec.getSize(heightMeasureSpec) : dp(ARROW_SIZE)) - dp(ARROW_INSET) * 2;
                super.onMeasure(MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY));
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                return true;
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                return false;
            }
        };
        iconContainer.addView(iconView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        card.addView(iconContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL | (rtl ? Gravity.LEFT : Gravity.RIGHT), ARROW_INSET, 0, ARROW_INSET, 0));

        update(false);
    }

    public void update(boolean animated) {
        final long userId = UserConfig.getInstance(currentAccount).getClientUserId();
        final boolean supporter = BadgesController.INSTANCE.isSupporter(userId);
        final Integer number = supporter ? BadgesController.INSTANCE.getNumber(userId) : null;
        if (bound && this.supporter == supporter && (number == null ? this.number == null : number.equals(this.number))) {
            return;
        }
        final boolean granted = bound && supporter && !this.supporter;
        this.supporter = supporter;
        this.number = number;
        bound = true;

        if (number != null) {
            SupporterArrowView.setNumber(iconView, BadgesController.formatNumber(number));
        }
        if (animated && granted && isAttachedToWindow()) {
            iconView.startEnterAnimation(-360, 100);
            particlesView.flingParticles(200);
            contentLayout.animate().cancel();
            contentLayout.animate().alpha(0f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).withEndAction(() -> {
                applyTexts();
                contentLayout.animate().alpha(1f).setDuration(220).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            }).start();
        } else {
            applyTexts();
        }
    }

    private void applyTexts() {
        if (supporter) {
            titleView.setText(getString(R.string.SupporterWelcomeTitle));
            subtitleView.setText(number != null ? LocaleController.formatString(R.string.SupportBannerBadge, BadgesController.formatNumber(number)) : getString(R.string.SupportBannerPerksInfo));
            button.setText(getString(R.string.LearnMore), false);
        } else {
            titleView.setText(getString(R.string.SupportBannerTitle));
            subtitleView.setText(getString(R.string.SupportBannerInfo));
            button.setText(getString(R.string.SupportBannerButton), false);
        }
        final CharSequence text = button.text.getText();
        button.getLayoutParams().width = (int) Math.ceil(button.getTextPaint().measureText(text, 0, text.length())) + dp(40);
        button.requestLayout();
    }
}
