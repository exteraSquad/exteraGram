/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.pillstack.ui.pills.weather;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.DrawableRes;

import com.exteragram.messenger.pillstack.core.PillType;
import com.exteragram.messenger.pillstack.ui.pills.BasePill;
import com.exteragram.messenger.pillstack.core.PillStackConfig;

import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.LaunchActivity;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Stories.recorder.Weather;

@SuppressLint("ViewConstructor")
public class WeatherPill extends BasePill implements NotificationCenter.NotificationCenterDelegate {

    private final ImageView iconView;
    private final AnimatedTextView textView;
    private final LinearLayout layout;
    private boolean showingWeather;

    public WeatherPill(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);

        layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER);
        layout.setMinimumWidth(dp(48));
        layout.setPadding(dp(8), 0, dp(8), 0);
        addView(layout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 28, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT)));

        iconView = new ImageView(context);
        iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        layout.addView(iconView, LayoutHelper.createLinear(16, 16, Gravity.CENTER_VERTICAL, 0, 0, 4, 0));

        textView = new AnimatedTextView(context, true, true, true);
        textView.setTextSize(dp(13));
        textView.setTypeface(AndroidUtilities.bold());
        textView.setIncludeFontPadding(false);
        textView.adaptWidth = true;
        NotificationCenter.listenEmojiLoading(textView);
        layout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        setLoadingTargetView(layout);
        updateColors();
        ScaleStateListAnimator.apply(layout);

        Weather.State cached = Weather.getCached();
        if (cached != null) {
            setData(cached, false);
        }
    }

    @Override
    public long getRefreshInterval() {
        return 20 * 60 * 1000L; // 20 minutes
    }

    @Override
    public int getPillId() {
        return PillType.WEATHER.getId();
    }

    @Override
    public void onPillClicked() {
        if (PillStackConfig.getUseCurrentLocation() && !showingWeather
                && (!Weather.isLocationPermissionGranted() || !Weather.isLocationEnabled())) {
            requestLocationAndUpdate();
            return;
        }
        onPillLongClicked();
    }

    @Override
    public boolean onPillLongClicked() {
        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (fragment != null) {
            addUpdatedAt(ItemOptions.makeOptions(fragment, this)
                    .add(R.drawable.msg_retry, LocaleController.getString(R.string.Refresh), () -> onUpdateData(true))
                    .add(R.drawable.msg_settings, LocaleController.getString(R.string.Settings), () -> fragment.presentFragment(new WeatherSettingsActivity())))
                    .setDrawScrim(false)
                    .setDimAlpha(0)
                    .setDismissOnMoveOutside(true)
                    .show();
            return true;
        }
        return false;
    }

    @Override
    public void onUpdateData(boolean force) {
        if (PillStackConfig.getUseCurrentLocation()) {
            if (!Weather.isLocationPermissionGranted()) {
                setLocationState(R.string.WeatherLocationPermissionGrant, showingWeather);
                return;
            }
            if (!Weather.isLocationEnabled()) {
                setLocationState(R.string.WeatherLocationServicesEnable, showingWeather);
                return;
            }
        }

        if (force) {
            Weather.clearCache();
        }

        startLoading();
        Weather.fetchExtera(state -> {
            if (state != null) {
                markDataUpdated();
                postDelayed(() -> setData(state, true), 300);
            } else {
                postDelayed(() -> setErrorState(true), 300);
            }
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.pillStackSettingsChanged);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.pillStackSettingsChanged);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.pillStackSettingsChanged) {
            if (!PillStackConfig.shouldUpdatePill(args, getPillId())) {
                return;
            }
            PillStackConfig.checkAndClearPendingUpdate(getPillId());
            onUpdateData(true);
        }
    }

    private void setLocationState(int textResId, boolean animate) {
        stopLoading();
        if (animate) animateSizeChange();
        iconView.setImageResource(R.drawable.filled_location);
        iconView.setVisibility(VISIBLE);
        textView.setText(LocaleController.getString(textResId), animate);
        showingWeather = false;
    }

    private void requestLocationAndUpdate() {
        Weather.getUserLocation(true, location -> {
            if (location != null) {
                onUpdateData(true);
            }
        });
    }

    private void setErrorState(boolean animate) {
        stopLoading();
        if (animate) animateSizeChange();
        iconView.setImageResource(R.drawable.msg_retry);
        iconView.setVisibility(VISIBLE);
        textView.setText(LocaleController.getString(R.string.Retry), animate);
        showingWeather = false;
    }

    public void setData(Weather.State state, boolean animate) {
        stopLoading();
        if (state == null) {
            return;
        }

        if (animate) {
            animateSizeChange();
        }

        int iconRes = getWeatherIconRes(state.getEmoji());
        if (iconRes != 0) {
            iconView.setImageResource(iconRes);
            iconView.setVisibility(VISIBLE);
            textView.setText(state.getTemperature(), animate);
        } else {
            iconView.setVisibility(GONE);
            textView.setText(
                    Emoji.replaceEmoji(
                            String.format("%s %s", state.getEmoji(), state.getTemperature()),
                            textView.getPaint().getFontMetricsInt(),
                            true
                    ),
                    animate
            );
        }
        showingWeather = true;
    }

    @DrawableRes
    private int getWeatherIconRes(String emoji) {
        if (emoji == null) return 0;
        return switch (emoji) {
            case "☀" -> R.drawable.weather_sunny;
            case "🌤", "⛅" -> R.drawable.weather_partly_cloudy;
            case "☁" -> R.drawable.weather_cloudy;
            case "🌦", "🌧" -> R.drawable.weather_rainy;
            case "⛈", "⚡" -> R.drawable.weather_thunderstorm;
            case "❄", "🌨" -> R.drawable.weather_snowy;
            case "😶‍🌫" -> R.drawable.weather_foggy;
            case "🌚", "🌛", "🌓", "🌔", "🌝", "🌖", "🌗", "🌜" -> R.drawable.weather_night;
            default -> 0;
        };
    }

    @Override
    public void setPressed(boolean pressed) {
        if (loading) pressed = false;
        super.setPressed(pressed);
        layout.setPressed(pressed);
    }

    @Override
    public void drawableHotspotChanged(float x, float y) {
        if (loading) return;
        super.drawableHotspotChanged(x, y);
        layout.drawableHotspotChanged(x - layout.getLeft(), y - layout.getTop());
    }

    @Override
    public void updateColors() {
        int textColor = getThemedColor(Theme.key_windowBackgroundWhiteBlackText, 0.75f);
        int backgroundColor = Theme.isCurrentThemeDark() ? getThemedColor(Theme.key_windowBackgroundWhite) : Theme.multAlpha(textColor, 0.09f);
        layout.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(14), backgroundColor, Theme.multAlpha(textColor, 0.1f)));
        textView.setTextColor(textColor);
        iconView.setColorFilter(new PorterDuffColorFilter(textColor, PorterDuff.Mode.MULTIPLY));
        updateLoadingColors();
    }
}
