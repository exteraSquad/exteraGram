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
import com.exteragram.messenger.utils.network.ExchangeRates;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

@SuppressLint("ViewConstructor")
public class BtcPill extends RatePill {

    private static final RateCache CACHE = new RateCache();

    public BtcPill(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider, CACHE, ExchangeRates.BTC, 2, R.drawable.pillstack_btc, new ColoredBackground(0xFFEFA612, 0xFFE77512));
    }

    @Override
    public int getPillId() {
        return PillType.BTC.getId();
    }

    @Override
    protected String getTargetSelection() {
        return PillStackConfig.getBtcTargetCurrency();
    }

    @Override
    protected void setTargetSelection(String currency) {
        PillStackConfig.setBtcTargetCurrency(currency);
    }
}
