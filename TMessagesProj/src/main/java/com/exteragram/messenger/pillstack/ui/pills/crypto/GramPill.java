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
public class GramPill extends RatePill {

    private static final RateCache CACHE = new RateCache();

    public GramPill(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider, CACHE, ExchangeRates.GRAM, 3, R.drawable.mini_gram_14, new ColoredBackground());
    }

    @Override
    public int getPillId() {
        return PillType.GRAM.getId();
    }

    @Override
    protected String getTargetSelection() {
        return PillStackConfig.getGramTargetCurrency();
    }

    @Override
    protected void setTargetSelection(String currency) {
        PillStackConfig.setGramTargetCurrency(currency);
    }
}
