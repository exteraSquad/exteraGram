/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.pillstack.ui.pills.crypto;

import android.annotation.SuppressLint;
import android.content.Context;

import com.exteragram.messenger.pillstack.core.PillStackConfig;
import com.exteragram.messenger.pillstack.core.PillType;
import com.exteragram.messenger.pillstack.ui.pills.crypto.utils.ColoredBackground;
import com.exteragram.messenger.pillstack.ui.pills.crypto.utils.PillStackCurrencies;
import com.exteragram.messenger.utils.network.ExchangeRates;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

@SuppressLint("ViewConstructor")
public class UsdPill extends RatePill {

    private static final RateCache CACHE = new RateCache();

    public UsdPill(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider, CACHE, ExchangeRates.USD, 2, R.drawable.pillstack_usd, new ColoredBackground(0xFF1D8B5D, 0xFF187B59));
    }

    @Override
    public int getPillId() {
        return PillType.USD.getId();
    }

    @Override
    protected String getTargetSelection() {
        if (ExchangeRates.USD.equalsIgnoreCase(PillStackConfig.getUsdTargetCurrency())) {
            return PillStackCurrencies.TARGET_AUTO;
        }
        return PillStackConfig.getUsdTargetCurrency();
    }

    @Override
    protected void setTargetSelection(String currency) {
        PillStackConfig.setUsdTargetCurrency(currency);
    }

    @Override
    protected String[] getTargetCurrencies() {
        return PillStackCurrencies.getTargetCurrencies(ExchangeRates.USD);
    }
}
