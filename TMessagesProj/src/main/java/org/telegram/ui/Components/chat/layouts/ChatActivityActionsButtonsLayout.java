package org.telegram.ui.Components.chat.layouts;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;
import org.telegram.ui.Components.chat.buttons.ChatActivityBlurredRoundButton;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

@SuppressLint("ViewConstructor")
public class ChatActivityActionsButtonsLayout extends LinearLayout {
    private final Theme.ResourcesProvider resourcesProvider;

    private final ButtonHolder replyButton = new ButtonHolder();
    private final ButtonHolder forwardButton = new ButtonHolder();
    private final ButtonHolder selectButton = new ButtonHolder();

    public ChatActivityActionsButtonsLayout(@NonNull Context context,
                                            Theme.ResourcesProvider resourcesProvider,
                                            BlurredBackgroundColorProvider colorProvider,
                                            BlurredBackgroundDrawableViewFactory blurredBackgroundDrawableViewFactory) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        replyButton.button = ChatActivityBlurredRoundButton.create(context, blurredBackgroundDrawableViewFactory,
                colorProvider, resourcesProvider);
        replyButton.button.setOnClickListener(v -> {
        });
        ScaleStateListAnimator.apply(replyButton.button, .065f, 2f);

        forwardButton.button = ChatActivityBlurredRoundButton.create(context, blurredBackgroundDrawableViewFactory,
                colorProvider, resourcesProvider);
        forwardButton.button.setOnClickListener(v -> {
        });
        ScaleStateListAnimator.apply(forwardButton.button, .065f, 2f);

        selectButton.button = ChatActivityBlurredRoundButton.create(context, blurredBackgroundDrawableViewFactory,
                colorProvider, resourcesProvider);
        selectButton.button.setOnClickListener(v -> {
        });
        ScaleStateListAnimator.apply(selectButton.button, .065f, 2f);
        selectButton.button.setVisibility(INVISIBLE);
        selectButton.button.setAlpha(0f);

        addTextView(replyButton, LocaleController.getString(R.string.Reply), R.drawable.input_reply, false);
        addTextView(forwardButton, LocaleController.getString(R.string.Forward), R.drawable.input_forward, true);
        addTextView(selectButton, LocaleController.getString(R.string.Select), R.drawable.select_between, false);

        setOrientation(HORIZONTAL);
        setClipChildren(false);

        FrameLayout leftButtonContainer = new FrameLayout(context);
        leftButtonContainer.setClipChildren(false);

        leftButtonContainer.addView(replyButton.button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        leftButtonContainer.addView(selectButton.button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        addView(leftButtonContainer, LayoutHelper.createLinear(0, 56, 1f, 1, 0, -1, 0));

        addView(forwardButton.button, LayoutHelper.createLinear(0, 56, 1f, -1, 0, 1, 0));
    }

    public void setReplyButtonOnClickListener(View.OnClickListener listener) {
        replyButton.button.setOnClickListener(listener);
    }

    public void setForwardButtonOnClickListener(View.OnClickListener listener) {
        forwardButton.button.setOnClickListener(listener);
    }

    public void setForwardButtonOnLongClickListener(View.OnLongClickListener listener) {
        forwardButton.button.setOnLongClickListener(listener);
    }

    public void setSelectButtonOnClickListener(View.OnClickListener listener) {
        selectButton.button.setOnClickListener(listener);
    }

    public View getForwardButton() {
        return forwardButton.button;
    }

    private void addTextView(ButtonHolder button, String text, @DrawableRes int iconRes, boolean iconLeft) {
        TextView textView = new TextView(getContext());
        textView.setText(text);
        textView.setGravity(Gravity.CENTER_VERTICAL);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        textView.setPadding(AndroidUtilities.dp(21), 0, AndroidUtilities.dp(21), 0);
        textView.setCompoundDrawablePadding(AndroidUtilities.dp(6));
        ThemeBind.textColor(textView, Theme.key_glass_defaultText, resourcesProvider);
        textView.setTypeface(AndroidUtilities.bold());
        Drawable image = getContext().getResources().getDrawable(iconRes).mutate();
        ThemeBind.drawableColorFilter(this, image, Theme.key_glass_defaultIcon, resourcesProvider, PorterDuff.Mode.MULTIPLY);
        textView.setCompoundDrawablesWithIntrinsicBounds(iconLeft ? image : null, null, iconLeft ? null : image, null);

        button.textView = textView;
        button.button.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
    }


    public void showReplyButton(boolean visible, boolean animated) {
        replyButton.visibilityAnimator.setValue(visible, animated);
    }

    public void showSelectButton(boolean visible, boolean animated) {
        selectButton.visibilityAnimator.setValue(visible, animated);
    }

    public void setReplyButtonEnabled(boolean enabled, boolean animated) {
        replyButton.enabledAnimator.setValue(enabled, animated);
        replyButton.button.setEnabled(enabled);
    }

    public void showForwardButton(boolean visible, boolean animated) {
        forwardButton.visibilityAnimator.setValue(visible, animated);
    }

    public void setForwardButtonEnabled(boolean enabled, boolean animated) {
        forwardButton.enabledAnimator.setValue(enabled, animated);
        forwardButton.button.setEnabled(enabled);
    }


    public void updateColors() {
        replyButton.button.updateColors();
        forwardButton.button.updateColors();
        selectButton.button.updateColors();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        checkButtonsPositionsAndVisibility();
    }

    private float totalVisibilityFactor;

    public void setTotalVisibilityFactor(float factor) {
        if (totalVisibilityFactor != factor) {
            totalVisibilityFactor = factor;
            checkButtonsPositionsAndVisibility();
        }
    }

    private void checkButtonsPositionsAndVisibility() {
        checkHolderPositionsAndVisibility(forwardButton);
        checkHolderPositionsAndVisibility(replyButton);
        checkHolderPositionsAndVisibility(selectButton);
    }

    private void checkHolderPositionsAndVisibility(ButtonHolder holder) {
        final float visibility = totalVisibilityFactor * holder.visibilityAnimator.getFloatValue();
        final float offsetY = -dp(54) * (1f - visibility);
        float offsetX = (getMeasuredWidth() / 2f) * (1f - AnimatorUtils.DECELERATE_INTERPOLATOR.getInterpolation(visibility));
        if (holder == replyButton || holder == selectButton) {
            offsetX *= -1;
        }

        holder.button.setTranslationX(offsetX);
        holder.button.setTranslationY(offsetY);
        holder.button.setAlpha(visibility);

        if (visibility <= 0.05f) {
            holder.button.setVisibility(INVISIBLE);
        } else {
            holder.button.setVisibility(VISIBLE);
        }
    }

    private class ButtonHolder implements FactorAnimator.Target {
        public ChatActivityBlurredRoundButton button;
        public TextView textView;

        public BoolAnimator visibilityAnimator = new BoolAnimator(0, this, CubicBezierInterpolator.EASE_OUT_QUINT, 320, true);
        public BoolAnimator enabledAnimator = new BoolAnimator(1, this, CubicBezierInterpolator.EASE_OUT_QUINT, 320, true);

        @Override
        public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
            textView.setAlpha(lerp(0.5f, 1, enabledAnimator.getFloatValue()));
            checkHolderPositionsAndVisibility(this);
        }
    }
}
