/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.pillstack.ui.pills.crypto;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.exteragram.messenger.pillstack.core.PillStackConfig;
import com.exteragram.messenger.pillstack.ui.pills.BasePill;
import com.exteragram.messenger.pillstack.ui.PillStackPreferencesActivity;
import com.exteragram.messenger.pillstack.ui.pills.crypto.utils.ColoredBackground;
import com.exteragram.messenger.pillstack.ui.pills.crypto.utils.PillStackCurrencies;
import com.exteragram.messenger.utils.network.ExchangeRates;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.LaunchActivity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@SuppressLint("ViewConstructor")
public abstract class RatePill extends BasePill implements NotificationCenter.NotificationCenterDelegate {

    public static final class RateCache {
        private final AtomicReference<String> cachedPrice = new AtomicReference<>();
        private final AtomicReference<String> cachedCurrency = new AtomicReference<>();
    }

    private final ImageView iconView;
    private final AnimatedTextView textView;
    private final LinearLayout layout;
    private final RateCache cache;
    private final String baseCurrency;
    private final int scale;
    private final int iconResId;
    private final ColoredBackground background;
    private boolean requestInFlight;

    protected RatePill(Context context, Theme.ResourcesProvider resourcesProvider, RateCache cache, String baseCurrency, int scale, int iconResId, ColoredBackground background) {
        super(context, resourcesProvider);
        this.cache = cache;
        this.baseCurrency = baseCurrency;
        this.scale = scale;
        this.iconResId = iconResId;
        this.background = background;

        layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER);
        layout.setMinimumWidth(dp(48));
        layout.setPadding(dp(8), 0, dp(8), 0);
        addView(layout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 28, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT)));

        iconView = new ImageView(context);
        iconView.setScaleType(iconResId == R.drawable.mini_gram_14 ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_INSIDE);
        layout.addView(iconView, LayoutHelper.createLinear(16, 16, Gravity.CENTER_VERTICAL, 0, 0, 4, 0));

        textView = new AnimatedTextView(context, true, true, true);
        textView.setTextSize(dp(13));
        textView.setIncludeFontPadding(false);
        textView.setTypeface(AndroidUtilities.bold());
        textView.adaptWidth = true;
        layout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        setLoadingTargetView(layout);
        updateColors();
        ScaleStateListAnimator.apply(layout);

        String price = cache.cachedPrice.get();
        if (price != null) {
            setData(price, false);
        }
    }

    @Override
    public void onPillClicked() {
        if (iconView.getVisibility() == VISIBLE && textView.getText() != null && TextUtils.equals(textView.getText(), LocaleController.getString(R.string.Retry))) {
            onUpdateData(true);
            return;
        }
        onPillLongClicked();
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
            if (!PillStackConfig.shouldUpdatePill(args, getPillId()) || !getTargetSelection().equals(PillStackCurrencies.TARGET_AUTO)) {
                return;
            }
            PillStackConfig.checkAndClearPendingUpdate(getPillId());
            onUpdateData(true);
        }
    }

    @Override
    public boolean onPillLongClicked() {
        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (fragment != null) {
            ItemOptions options = ItemOptions.makeOptions(fragment, this, true);
            ItemOptions targetOptions = options.makeSwipeback(true)
                    .add(R.drawable.ic_ab_back, LocaleController.getString(R.string.Back), options::closeSwipeback)
                    .addGap();
            String current = getTargetSelection();
            String[] currencies = getTargetCurrencies();
            List<String> available = Arrays.asList(currencies);
            LinkedHashSet<String> hinted = new LinkedHashSet<>();
            for (String currency : new String[]{PillStackCurrencies.TARGET_AUTO, ExchangeRates.USD, ExchangeRates.EUR}) {
                if (available.contains(currency)) {
                    hinted.add(currency);
                }
            }
            Utilities.Callback<String> addCurrency = currency -> {
                ActionBarMenuSubItem button = new ActionBarMenuSubItem(options.getContext(), 2, false, false, resourcesProvider);
                button.setPadding(dp(18), 0, dp(18), 0);
                if (PillStackCurrencies.TARGET_AUTO.equalsIgnoreCase(currency)) {
                    button.setText(LocaleController.getString(R.string.QualityAuto));
                    button.setSubtext(PillStackCurrencies.getCurrencyName(PillStackCurrencies.resolveTargetCurrency(UserConfig.selectedAccount, currency)));
                } else {
                    button.setText(currency);
                    button.setSubtext(PillStackCurrencies.getCurrencyName(currency));
                }
                boolean checked = currency.equalsIgnoreCase(current);
                button.subtextView.setPadding(0, 0, dp(checked ? 42 : 0), 0);
                button.setChecked(checked);
                button.setOnClickListener(b -> {
                    options.dismiss();
                    if (checked) {
                        return;
                    }
                    setTargetSelection(currency);
                    onUpdateData(false);
                });
                targetOptions.addView(button, LayoutHelper.createLinear(200, LayoutHelper.WRAP_CONTENT));
            };
            for (String currency : hinted) {
                addCurrency.run(currency);
            }
            targetOptions.addGapIf(!hinted.isEmpty() && hinted.size() < currencies.length);
            for (String currency : currencies) {
                if (!hinted.contains(currency)) {
                    addCurrency.run(currency);
                }
            }
            ActionBarMenuSubItem targetItem = new ActionBarMenuSubItem(options.getContext(), false, false, resourcesProvider);
            targetItem.setTextAndIcon(LocaleController.getString(R.string.CryptoPillTargetCurrency), R.drawable.msg_language);
            targetItem.setSubtext(PillStackCurrencies.getTargetCurrencySubtext(getTargetSelection()));
            targetItem.setItemHeight(56);
            targetItem.setOnClickListener(v -> options.openSwipeback(targetOptions));
            options.add(targetItem).addGap()
                    .add(R.drawable.msg_retry, LocaleController.getString(R.string.Refresh), () -> onUpdateData(true))
                    .add(R.drawable.msg_settings, LocaleController.getString(R.string.Settings), () -> fragment.presentFragment(new PillStackPreferencesActivity()));
            addUpdatedAt(options)
                    .setSwipebackGravity(!LocaleController.isRTL, false)
                    .forceBelowScrim(true)
                    .setDrawScrim(false)
                    .setGravity(LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT)
                    .setDimAlpha(0)
                    .setDismissOnMoveOutside(true)
                    .show();
            return true;
        }
        return false;
    }

    @Override
    public void onUpdateData(boolean force) {
        String selection = getTargetSelection();
        String targetCurrency = PillStackCurrencies.resolveTargetCurrency(UserConfig.selectedAccount, selection);
        String price = cache.cachedPrice.get();
        if (!TextUtils.equals(targetCurrency, cache.cachedCurrency.get())) {
            price = null;
        }
        if (!force && price != null && !isRefreshDue()) {
            setData(price, false);
            return;
        }

        if (requestInFlight) {
            return;
        }
        requestInFlight = true;

        if (force) {
            animateSizeChange();
        }

        startLoading();
        if (price == null && cache.cachedPrice.get() == null) {
            iconView.setVisibility(View.GONE);
            textView.setVisibility(View.GONE);
        } else {
            iconView.setImageResource(iconResId);
            iconView.setVisibility(VISIBLE);
            textView.setVisibility(VISIBLE);
        }

        if (force) {
            ExchangeRates.clearCache();
        }

        ExchangeRates.fetch(state -> {
            requestInFlight = false;
            if (state == null) {
                String cached = cache.cachedPrice.get();
                if (cached != null) {
                    setData(cached, true);
                } else {
                    setErrorState(true);
                }
                return;
            }

            BigDecimal rate = state.getRate(baseCurrency, targetCurrency);
            if (rate == null) {
                String cached = cache.cachedPrice.get();
                if (cached != null) {
                    setData(cached, true);
                } else {
                    setErrorState(true);
                }
                return;
            }

            String formattedPrice = formatPrice(rate, targetCurrency);
            cache.cachedPrice.set(formattedPrice);
            cache.cachedCurrency.set(targetCurrency);
            setData(formattedPrice, true);
            markDataUpdated();
        });
    }

    protected String formatPrice(BigDecimal priceValue, String targetCurrency) {
        String formatted = PillStackCurrencies.formatFiatPrice(priceValue, targetCurrency);
        if (formatted != null) {
            return formatted;
        }
        BigDecimal rounded = priceValue.setScale(scale, RoundingMode.HALF_UP);
        return rounded.toPlainString() + " " + targetCurrency;
    }

    protected String[] getTargetCurrencies() {
        return PillStackCurrencies.TARGET_CURRENCIES;
    }

    protected abstract String getTargetSelection();

    protected abstract void setTargetSelection(String currency);

    private void setErrorState(boolean animate) {
        stopLoading();
        if (animate) animateSizeChange();
        iconView.setImageResource(R.drawable.msg_retry);
        iconView.setVisibility(View.VISIBLE);
        textView.setText(LocaleController.getString(R.string.Retry), animate);
        textView.setVisibility(View.VISIBLE);
    }

    private void setData(String price, boolean animate) {
        stopLoading();
        if (animate) animateSizeChange();
        iconView.setImageResource(iconResId);
        iconView.setVisibility(View.VISIBLE);
        textView.setText(price, animate);
        textView.setVisibility(View.VISIBLE);
    }

    @Override
    public long getRefreshInterval() {
        return 5 * 60 * 1000L; // 5 minutes
    }

    @Override
    public void setPressed(boolean pressed) {
        if (loading) pressed = false;
        super.setPressed(pressed);
        layout.setPressed(pressed);
    }

    @Override
    public void updateColors() {
        layout.setBackground(background);
        textView.setTextColor(0xFFFFFFFF);
        iconView.setColorFilter(0xFFFFFFFF);
        updateLoadingColors();
    }

    @Override
    protected void updateLoadingColors() {
        if (loadingDrawable != null) {
            loadingDrawable.setColors(
                    Theme.multAlpha(0xFFFFFFFF, 0.1f), Theme.multAlpha(0xFFFFFFFF, 0.3f),
                    Theme.multAlpha(0xFFFFFFFF, 0.2f), Theme.multAlpha(0xFFFFFFFF, 0.45f));
        }
    }
}
