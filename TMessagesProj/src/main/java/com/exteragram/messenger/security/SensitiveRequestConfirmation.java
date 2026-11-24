/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.security;

import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.app.Dialog;
import android.util.SparseIntArray;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.tgnet.tl.TL_payments;
import org.telegram.tgnet.tl.TL_phone;
import org.telegram.tgnet.tl.TL_wallet;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.LaunchActivity;

import java.util.HashSet;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SensitiveRequestConfirmation {

    private static final SparseIntArray CATEGORIES = new SparseIntArray();
    private static final HashSet<Integer> MERGEABLE = new HashSet<>();

    private static SensitiveRequestSheet currentSheet;

    static {
        CATEGORIES.put(TL_stars.transferStarGift.constructor, R.string.ProfileGifts);
        CATEGORIES.put(TL_stars.updateStarGiftPrice.constructor, R.string.ProfileGifts);
        CATEGORIES.put(TL_stars.convertStarGift.constructor, R.string.ProfileGifts);
        CATEGORIES.put(TL_stars.upgradeStarGift.constructor, R.string.ProfileGifts);
        CATEGORIES.put(TL_stars.getStarGiftWithdrawalUrl.constructor, R.string.ProfileGifts);
        CATEGORIES.put(TL_stars.withdrawStarGift.constructor, R.string.ProfileGifts);
        CATEGORIES.put(TL_stars.deleteStarGift.constructor, R.string.ProfileGifts);
        CATEGORIES.put(TL_stars.craftStarGift.constructor, R.string.ProfileGifts);
        CATEGORIES.put(TL_stars.TL_payments_sendStarsForm.constructor, R.string.SensitiveRequestCategoryPayments);
        CATEGORIES.put(TLRPC.TL_payments_sendPaymentForm.constructor, R.string.SensitiveRequestCategoryPayments);
        CATEGORIES.put(TLRPC.TL_messages_sendPaidReaction.constructor, R.string.SensitiveRequestCategoryPayments);
        CATEGORIES.put(TL_stars.TL_fulfillStarsSubscription.constructor, R.string.SensitiveRequestCategoryPayments);
        CATEGORIES.put(TL_stars.TL_changeStarsSubscription.constructor, R.string.SensitiveRequestCategoryPayments);
        CATEGORIES.put(TLRPC.TL_auth_acceptLoginToken.constructor, R.string.SecurityTitle);
        CATEGORIES.put(TLRPC.TL_auth_exportAuthorization.constructor, R.string.SecurityTitle);
        CATEGORIES.put(TLRPC.TL_auth_resetAuthorizations.constructor, R.string.SecurityTitle);
        CATEGORIES.put(TL_account.resetAuthorization.constructor, R.string.SecurityTitle);
        CATEGORIES.put(TL_account.resetWebAuthorization.constructor, R.string.SecurityTitle);
        CATEGORIES.put(TL_account.resetWebAuthorizations.constructor, R.string.SecurityTitle);
        CATEGORIES.put(TL_account.deleteAccount.constructor, R.string.SecurityTitle);
        CATEGORIES.put(TL_account.updatePasswordSettings.constructor, R.string.SecurityTitle);
        CATEGORIES.put(TL_account.resetPassword.constructor, R.string.SecurityTitle);
        CATEGORIES.put(TL_account.changePhone.constructor, R.string.SecurityTitle);
        CATEGORIES.put(TL_account.updateConnectedBot.constructor, R.string.SecurityTitle);
        CATEGORIES.put(TL_account.acceptAuthorization.constructor, R.string.SecurityTitle);
        CATEGORIES.put(TLRPC.TL_channels_editCreator.constructor, R.string.SensitiveRequestCategoryChatManagement);
        CATEGORIES.put(TLRPC.TL_channels_editAdmin.constructor, R.string.SensitiveRequestCategoryChatManagement);
        CATEGORIES.put(TLRPC.TL_channels_deleteChannel.constructor, R.string.SensitiveRequestCategoryChatManagement);
        CATEGORIES.put(TLRPC.TL_channels_updateUsername.constructor, R.string.SensitiveRequestCategoryChatManagement);
        CATEGORIES.put(TLRPC.TL_channels_toggleUsername.constructor, R.string.SensitiveRequestCategoryChatManagement);
        CATEGORIES.put(TL_account.updateUsername.constructor, R.string.SecurityTitle);
        CATEGORIES.put(TL_account.toggleUsername.constructor, R.string.SecurityTitle);
        CATEGORIES.put(TL_account.setAuthorizationTTL.constructor, R.string.SecurityTitle);
        CATEGORIES.put(TLRPC.TL_payments_getStarsRevenueWithdrawalUrl.constructor, R.string.SensitiveRequestCategoryPayments);
        CATEGORIES.put(TL_wallet.sendTransfer.constructor, R.string.WalletTitle);
        CATEGORIES.put(TL_wallet.exportSecretPhrase.constructor, R.string.WalletTitle);
        CATEGORIES.put(TL_wallet.replaceWallet.constructor, R.string.WalletTitle);
        CATEGORIES.put(TL_wallet.enableBackup.constructor, R.string.WalletTitle);
        CATEGORIES.put(TL_wallet.disableBackup.constructor, R.string.WalletTitle);
        CATEGORIES.put(TL_wallet.tonConnectSubmitConnectResult.constructor, R.string.WalletTitle);
        CATEGORIES.put(TL_wallet.tonConnectSubmitResponse.constructor, R.string.WalletTitle);
        CATEGORIES.put(TLRPC.TL_messages_sendMessage.constructor, R.string.SensitiveRequestCategoryPayments);
        CATEGORIES.put(TLRPC.TL_messages_sendMedia.constructor, R.string.SensitiveRequestCategoryPayments);
        CATEGORIES.put(TLRPC.TL_messages_forwardMessages.constructor, R.string.SensitiveRequestCategoryPayments);
        CATEGORIES.put(TLRPC.TL_messages_sendMultiMedia.constructor, R.string.SensitiveRequestCategoryPayments);
        CATEGORIES.put(TLRPC.TL_messages_sendInlineBotResult.constructor, R.string.SensitiveRequestCategoryPayments);
        CATEGORIES.put(TLRPC.TL_channels_searchPosts.constructor, R.string.SensitiveRequestCategoryPayments);
        CATEGORIES.put(TL_phone.sendGroupCallMessage.constructor, R.string.SensitiveRequestCategoryPayments);
        CATEGORIES.put(TL_payments.TL_sendStarGiftOffer.constructor, R.string.ProfileGifts);

        MERGEABLE.add(TLRPC.TL_messages_sendPaidReaction.constructor);
        MERGEABLE.add(TLRPC.TL_messages_sendMessage.constructor);
        MERGEABLE.add(TLRPC.TL_messages_sendMedia.constructor);
        MERGEABLE.add(TLRPC.TL_messages_sendMultiMedia.constructor);
        MERGEABLE.add(TLRPC.TL_messages_forwardMessages.constructor);
        MERGEABLE.add(TLRPC.TL_messages_sendInlineBotResult.constructor);
    }

    private SensitiveRequestConfirmation() {
    }

    private static native void native_resolve(int currentAccount, int requestToken, long nonce, boolean approved);

    private static native int[] native_protectedConstructors();

    public static void verifyPolicy() {
        HashSet<Integer> nativeConstructors = new HashSet<>();
        StringBuilder drift = new StringBuilder();
        for (int constructor : native_protectedConstructors()) {
            nativeConstructors.add(constructor);
            if (CATEGORIES.indexOfKey(constructor) < 0) {
                drift.append(String.format(Locale.US, " native-only 0x%08x", constructor));
            }
        }
        for (int i = 0; i < CATEGORIES.size(); i++) {
            if (!nativeConstructors.contains(CATEGORIES.keyAt(i))) {
                drift.append(String.format(Locale.US, " java-only 0x%08x", CATEGORIES.keyAt(i)));
            }
        }
        if (drift.length() > 0) {
            throw new IllegalStateException("SensitiveRequestPolicy is out of sync with TL constructors:" + drift);
        }
    }

    public static void request(int currentAccount, int requestToken, int constructor, long nonce, long[] details, String[] texts, String method) {
        AtomicBoolean answered = new AtomicBoolean();
        AndroidUtilities.runOnUIThread(() -> {
            try {
                BaseFragment fragment = LaunchActivity.getLastFragment();
                Activity activity = fragment != null ? fragment.getParentActivity() : null;
                if (activity == null || activity.isFinishing()) {
                    resolve(currentAccount, requestToken, nonce, false, answered);
                    return;
                }
                Utilities.Callback<Boolean> onResult = approved -> {
                    if (currentSheet != null && currentSheet.isAnswered()) {
                        currentSheet = null;
                    }
                    resolve(currentAccount, requestToken, nonce, approved, answered);
                };
                if (currentSheet != null && MERGEABLE.contains(constructor) && currentSheet.merge(currentAccount, constructor, details, texts, onResult)) {
                    return;
                }
                currentSheet = new SensitiveRequestSheet(activity, fragment.getResourceProvider(), currentAccount, constructor, getCategoryText(constructor), method, details, texts, onResult);
                currentSheet.show();
            } catch (Exception e) {
                FileLog.e(e);
                resolve(currentAccount, requestToken, nonce, false, answered);
            }
        });
    }

    public static void onDialogShown(Dialog dialog) {
        if (currentSheet != null && currentSheet != dialog) {
            currentSheet.bringToFront();
        }
    }

    private static void resolve(int currentAccount, int requestToken, long nonce, boolean approved, AtomicBoolean answered) {
        if (!answered.compareAndSet(false, true)) {
            return;
        }
        Utilities.stageQueue.postRunnable(() -> native_resolve(currentAccount, requestToken, nonce, approved));
    }

    private static String getCategoryText(int constructor) {
        int category = CATEGORIES.get(constructor, 0);
        return category != 0 ? getString(category) : null;
    }
}
