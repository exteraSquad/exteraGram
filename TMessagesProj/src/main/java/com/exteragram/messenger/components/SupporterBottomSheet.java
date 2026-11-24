/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.components;

import static org.telegram.messenger.LocaleController.getString;

import android.text.SpannableStringBuilder;
import android.text.TextUtils;

import com.exteragram.messenger.api.dto.BadgeDTO;
import com.exteragram.messenger.badges.BadgesController;
import com.exteragram.messenger.badges.DonationLauncher;
import com.exteragram.messenger.components.supporter.SupporterFeatureCell;
import com.exteragram.messenger.components.supporter.SupporterEnterFrom;
import com.exteragram.messenger.components.supporter.SupporterPerks;
import com.exteragram.messenger.components.supporter.SupporterSheet;
import com.exteragram.messenger.preferences.OtherPreferencesActivity;
import com.exteragram.messenger.utils.network.ExchangeRates;
import com.exteragram.messenger.utils.network.RemoteUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;

public class SupporterBottomSheet extends SupporterSheet {

    public SupporterBottomSheet(BaseFragment baseFragment, TLObject previewPeer, Theme.ResourcesProvider resourcesProvider) {
        super(baseFragment, resourcesProvider);

        var context = baseFragment.getParentActivity();

        final BadgeDTO customBadge = BadgesController.INSTANCE.hasCustomBadge(previewPeer) ? BadgesController.INSTANCE.getBadge(previewPeer) : null;
        if (customBadge != null) {
            final String badgeText = customBadge.getText();
            setBadge(customBadge.getDocumentId());
            setContent(formatOtherTitle(previewPeer), null);
            addNumberPill(BadgesController.getNumber(previewPeer), true);
            if (!TextUtils.isEmpty(badgeText)) {
                addCaptionBubble(BadgesController.formatBadgeCaption(badgeText, DialogObject.getName(previewPeer), baseFragment, this::dismiss));
            }
            addHowToGet(baseFragment, true);
            button.setText(getString(R.string.MakeDonation), false);
            button.setOnClickListener(v -> openDonations(baseFragment, true));
            commit();
            return;
        }

        final boolean self = previewPeer == null || previewPeer instanceof TLRPC.User user && user.id == UserConfig.getInstance(currentAccount).getClientUserId();
        final TLObject peer = self ? UserConfig.getInstance(currentAccount).getCurrentUser() : previewPeer;
        final BadgeDTO badge = self ? null : BadgesController.INSTANCE.getBadge(peer);
        final String badgeText = badge != null ? badge.getText() : null;
        final boolean hasBadgeText = !TextUtils.isEmpty(badgeText);

        setContent(self ? getString(R.string.SupportDevelopment) : formatOtherTitle(peer), getString(R.string.SupporterOtherSubtitle));
        if (hasBadgeText) {
            addCaptionBubble(BadgesController.formatBadgeCaption(badgeText, DialogObject.getName(peer), baseFragment, this::dismiss));
        }
        final Integer number = self ? null : BadgesController.getNumber(peer);
        if (number != null) {
            getArrowView().setNumber(BadgesController.formatNumber(number));
            addNumberPill(number, false);
        }
        SupporterPerks.addTo(context, this::addView, resourcesProvider, null, null, null);

        if (!self && BadgesController.INSTANCE.isSupporter(UserConfig.getInstance(currentAccount).getClientUserId())) {
            button.setText(getString(R.string.GotIt), false);
            button.setOnClickListener(v -> dismiss());
        } else {
            addHowToGet(baseFragment, false);
            button.setText(getString(R.string.MakeDonation), false);
            button.setOnClickListener(v -> openDonations(baseFragment, false));
        }
        commit();
    }

    private CharSequence formatOtherTitle(TLObject peer) {
        return AndroidUtilities.replaceSingleLink(LocaleController.formatString(R.string.SupporterOtherTitle, DialogObject.getName(peer)), getThemedColor(Theme.key_featuredStickers_addButton));
    }

    private void openDonations(BaseFragment baseFragment, boolean customBadge) {
        dismiss();
        if (DonationLauncher.isEnabled()) {
            DonationLauncher.open(baseFragment, customBadge);
        } else if (!(baseFragment instanceof OtherPreferencesActivity)) {
            baseFragment.presentFragment(new OtherPreferencesActivity());
        }
    }

    private void addHowToGet(BaseFragment baseFragment, boolean customBadge) {
        var context = getContext();

        addHeader(getString(R.string.SupporterHowToGet));

        float usdValue = customBadge ? RemoteUtils.getFloatConfigValue(RemoteUtils.DONATES_CUSTOM_BADGE_AMOUNT_USD, 50f) : RemoteUtils.getFloatConfigValue(RemoteUtils.DONATES_AMOUNT_USD, 5f);
        String donationAmountUsd = "$" + ExchangeRates.State.formatter.format(usdValue);

        addView(new SupporterFeatureCell(context, R.drawable.msg_payment_card, getString(R.string.MakeDonation), LocaleController.formatString(R.string.DonateAppInfo, donationAmountUsd), resourcesProvider));
        addView(new SupporterFeatureCell(context, R.drawable.menu_feature_reactions, getString(customBadge ? R.string.SupporterCustomBadgeChoose : R.string.ReceiveBadge), getString(customBadge ? R.string.SupporterCustomBadgeChooseInstantInfo : R.string.ReceiveBadgeInstantInfo), resourcesProvider));
        SpannableStringBuilder issuesDescription = AndroidUtilities.replaceSingleTag(getString(R.string.DonationIssuesInfo), Theme.key_chat_messageLinkIn, 0, () -> {
            dismiss();
            MessagesController.getInstance(currentAccount).openByUserName("exteraOwner", baseFragment, 1);
        });
        addView(new SupporterFeatureCell(context, R.drawable.msg_psa, getString(R.string.DonationIssues), issuesDescription, resourcesProvider));
    }

    public static BottomSheet showAlert(BaseFragment fragment) {
        return showAlert(fragment, fragment.getResourceProvider());
    }

    public static BottomSheet showAlert(BaseFragment fragment, Theme.ResourcesProvider resourcesProvider) {
        return showAlert(fragment, null, resourcesProvider);
    }

    public static BottomSheet showAlert(BaseFragment fragment, TLObject previewPeer, Theme.ResourcesProvider resourcesProvider) {
        return showAlert(fragment, previewPeer, resourcesProvider, null);
    }

    public static BottomSheet showAlert(BaseFragment fragment, TLObject previewPeer, Theme.ResourcesProvider resourcesProvider, SupporterEnterFrom enterFrom) {
        if (fragment.getParentActivity() == null) {
            return null;
        }
        if (previewPeer == null && BadgesController.INSTANCE.isSupporter(UserConfig.getInstance(fragment.getCurrentAccount()).getClientUserId())) {
            final long customBadgeId = BadgesController.INSTANCE.getCustomBadgeId(UserConfig.getInstance(fragment.getCurrentAccount()).getCurrentUser());
            return SupporterWelcomeSheet.show(fragment, SupporterWelcomeSheet.MODE_INFO, resourcesProvider, enterFrom, customBadgeId);
        }
        SupporterBottomSheet alert = new SupporterBottomSheet(fragment, previewPeer, resourcesProvider);
        alert.setEnterFrom(enterFrom);
        return fragment.showDialog(alert) != null ? alert : null;
    }
}
