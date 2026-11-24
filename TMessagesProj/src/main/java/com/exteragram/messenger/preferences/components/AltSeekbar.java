/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.preferences.components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.utils.ui.material.MaterialSliderUiHelper;
import com.google.android.material.slider.Slider;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SeekBarView;

import java.util.Locale;

@SuppressLint("ViewConstructor")
public class AltSeekbar extends FrameLayout {

    private final AnimatedTextView headerValue;
    protected final TextView leftTextView;
    protected final TextView rightTextView;
    private final int min, max;
    public SeekBarView seekBarView;
    public Slider slider;
    protected float currentValue;
    private int roundedValue;
    private int vibro = -1;

    private final OnDrag onDrag;

    public AltSeekbar(Context context, OnDrag onDrag, int min, int max, String title, String left, String right) {
        super(context);

        this.onDrag = onDrag;
        this.max = max;
        this.min = min;

        LinearLayout headerLayout = new LinearLayout(context);
        headerLayout.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);

        TextView headerTextView = new TextView(context);
        headerTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        headerTextView.setTypeface(AndroidUtilities.bold());
        headerTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        headerTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        headerTextView.setText(title);
        headerLayout.addView(headerTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        headerValue = new AnimatedTextView(context, false, true, true) {
            final Drawable backgroundDrawable = Theme.createRoundRectDrawable(AndroidUtilities.dp(4), Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader), 0.15f));

            @Override
            protected void onDraw(Canvas canvas) {
                backgroundDrawable.setBounds(0, 0, (int) (getPaddingLeft() + getDrawable().getCurrentWidth() + getPaddingRight()), getMeasuredHeight());
                backgroundDrawable.draw(canvas);

                super.onDraw(canvas);
            }
        };
        headerValue.setAnimationProperties(.45f, 0, 240, CubicBezierInterpolator.EASE_OUT_QUINT);
        headerValue.setAllowCancel(true);
        headerValue.setTypeface(AndroidUtilities.bold());
        headerValue.setPadding(AndroidUtilities.dp(5.33f), AndroidUtilities.dp(2), AndroidUtilities.dp(5.33f), AndroidUtilities.dp(2));
        headerValue.setTextSize(AndroidUtilities.dp(12));
        headerValue.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        headerLayout.addView(headerValue, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 17, Gravity.CENTER_VERTICAL, 6, 1, 0, 0));

        addView(headerLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 21, 17, 21, 0));

        FrameLayout valuesView = new FrameLayout(context);

        leftTextView = new TextView(context);
        leftTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        leftTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        leftTextView.setGravity(Gravity.LEFT);
        leftTextView.setText(left);
        valuesView.addView(leftTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL));

        rightTextView = new TextView(context);
        rightTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        rightTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        rightTextView.setGravity(Gravity.RIGHT);
        rightTextView.setText(right);
        valuesView.addView(rightTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL));

        addView(valuesView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 21, 52, 21, 0));

        initSlider();
    }

    private void updateValues() {
        int middle = (max - min) / 2 + min;
        if (currentValue >= middle * 1.5f - min * 0.5f) {
            rightTextView.setTextColor(ColorUtils.blendARGB(
                    Theme.getColor(Theme.key_windowBackgroundWhiteGrayText),
                    Theme.getColor(Theme.key_windowBackgroundWhiteBlueText),
                    (currentValue - (middle * 1.5f - min * 0.5f)) / (max - (middle * 1.5f - min * 0.5f))
            ));
            leftTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        } else if (currentValue <= (middle + min) * 0.5f) {
            leftTextView.setTextColor(ColorUtils.blendARGB(
                    Theme.getColor(Theme.key_windowBackgroundWhiteGrayText),
                    Theme.getColor(Theme.key_windowBackgroundWhiteBlueText),
                    (currentValue - (middle + min) * 0.5f) / (min - (middle + min) * 0.5f)
            ));
            rightTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        } else {
            leftTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            rightTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        }
    }

    public void setProgress(float value) {
        currentValue = clamp(value);
        roundedValue = Math.round(currentValue);
        if (slider != null) {
            MaterialSliderUiHelper.setValue(slider, currentValue);
        } else if (seekBarView != null) {
            seekBarView.setProgress((currentValue - min) / (float) (max - min));
        }
        headerValue.cancelAnimation();
        headerValue.setText(getTextForHeader(), true);
        checkEndpointHaptic(currentValue);
        updateValues();
    }

    public void updateHeader(float value) {
        currentValue = clamp(value);
        roundedValue = Math.round(currentValue);
        CharSequence text = getTextForHeader();
        if (!TextUtils.equals(headerValue.getText(), text)) {
            headerValue.setText(text, true);
        }
        checkEndpointHaptic(currentValue);
        updateValues();
    }

    private float clamp(float value) {
        return MathUtils.clamp(value, min, max);
    }

    private void checkEndpointHaptic(float value) {
        int endpoint = -1;
        if (useExactEndpointHaptic()) {
            if (value <= min) {
                endpoint = min;
            } else if (value >= max) {
                endpoint = max;
            }
        } else if (roundedValue == min || roundedValue == max) {
            endpoint = roundedValue;
        }

        if (endpoint != -1) {
            if (endpoint != vibro) {
                vibro = endpoint;
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            }
        } else {
            vibro = -1;
        }
    }

    protected boolean useExactEndpointHaptic() {
        return false;
    }

    public CharSequence getTextForHeader() {
        CharSequence text;
        if (roundedValue == min) {
            text = leftTextView.getText();
        } else if (roundedValue == max) {
            text = rightTextView.getText();
        } else {
            text = String.valueOf(roundedValue);
        }
        return text.toString().toUpperCase(Locale.getDefault());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(112), MeasureSpec.EXACTLY)
        );
    }

    private void initSlider() {
        if (ExteraConfig.getNewSliderStyle()) {
            slider = MaterialSliderUiHelper.create(getContext());
            MaterialSliderUiHelper.applyContinuousStyle(slider);
            slider.addOnChangeListener((slider, value, fromUser) -> {
                if (fromUser) onDrag.run(value);
                if (Math.round(value) != roundedValue) {
                    setProgress(value);
                }
            });
            MaterialSliderUiHelper.applyColors(
                    slider,
                    Theme.getColor(Theme.key_player_progress),
                    Theme.getColor(Theme.key_player_progressBackground)
            );
            slider.setValueFrom(min);
            slider.setValueTo(max);
            MaterialSliderUiHelper.setValue(slider, currentValue);
            addView(slider, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 50 + 6, Gravity.TOP, 7, 68, 7, 6));
        } else {
            seekBarView = new SeekBarView(getContext(), true, null);
            seekBarView.setReportChanges(true);
            seekBarView.setDelegate((stop, progress) -> {
                float newValue = min + (max - min) * progress;
                onDrag.run(newValue);
                if (Math.round(newValue) != roundedValue) {
                    setProgress(newValue);
                }
            });
            addView(seekBarView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38 + 6, Gravity.TOP, 6, 68, 6, 0));
        }
        setProgress(currentValue);
    }

    public void updateStyle() {
        if (slider != null) {
            removeView(slider);
            slider = null;
        }
        if (seekBarView != null) {
            removeView(seekBarView);
            seekBarView = null;
        }
        initSlider();
    }

    public interface OnDrag {
        void run(float progress);
    }
}
