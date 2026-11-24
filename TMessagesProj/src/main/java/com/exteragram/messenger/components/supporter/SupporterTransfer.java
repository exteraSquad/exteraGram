/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.components.supporter;

import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.DialogInterface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.api.ApiController;
import com.exteragram.messenger.badges.BadgesController;
import com.exteragram.messenger.utils.chats.ChatUtils;
import com.exteragram.messenger.utils.ui.AccountsUiHelper;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;

import java.text.DateFormat;
import java.util.Date;

public final class SupporterTransfer {

    private static final String TOKEN_PREFIX = "token:";
    private static final String COOLDOWN_PREFIX = "cooldown:";

    private SupporterTransfer() {
    }

    public static boolean canTransfer(int account) {
        final TLRPC.User user = UserConfig.getInstance(account).getCurrentUser();
        return user != null && BadgesController.INSTANCE.isSupporter(user.id) && !BadgesController.INSTANCE.isDeveloper(user);
    }

    public static void start(BaseFragment fragment, ViewGroup container, View anchor, Theme.ResourcesProvider resourcesProvider, Runnable onPicked) {
        final Activity activity = fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        final int fromAccount = fragment.getCurrentAccount();
        AccountsUiHelper.menu(fragment, anchor)
                .container(container)
                .gravity(Gravity.CENTER_HORIZONTAL)
                .fromBottom(true)
                .withAddAccount(false)
                .filter(account -> account != fromAccount && UserConfig.getInstance(account).getCurrentUser() != null && !BadgesController.INSTANCE.isSupporter(UserConfig.getInstance(account).getClientUserId()))
                .extraItems(options -> options.add(R.drawable.msg_link2, getString(R.string.SupporterTransferOtherAccount), () -> {
                    onPicked.run();
                    Browser.openUrl(activity, "https://t.me/" + ExteraConfig.getApiBotInfo().second + "?start=transfer");
                }))
                .onSelected(account -> {
                    onPicked.run();
                    confirm(fragment, fromAccount, account, resourcesProvider);
                })
                .show();
    }

    private static void confirm(BaseFragment fragment, int fromAccount, int toAccount, Theme.ResourcesProvider resourcesProvider) {
        final TLRPC.User target = UserConfig.getInstance(toAccount).getCurrentUser();
        final Integer number = BadgesController.INSTANCE.getNumber(UserConfig.getInstance(fromAccount).getClientUserId());
        final String badge = number != null ? BadgesController.formatNumber(number) : "";
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity(), resourcesProvider);
        builder.setTitle(getString(R.string.SupporterTransferTitle));
        builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.SupporterTransferConfirm, badge, UserObject.getUserName(target))));
        builder.setPositiveButton(getString(R.string.Gift2TransferDo), (dialog, which) -> transfer(fragment, fromAccount, toAccount, resourcesProvider));
        builder.setNegativeButton(getString(R.string.Cancel), null);
        AlertDialog dialog = builder.create();
        fragment.showDialog(dialog);
        if (dialog.getButton(DialogInterface.BUTTON_POSITIVE) instanceof TextView button) {
            button.setTextColor(Theme.getColor(Theme.key_text_RedBold, resourcesProvider));
        }
    }

    private static void transfer(BaseFragment fragment, int fromAccount, int toAccount, Theme.ResourcesProvider resourcesProvider) {
        final AlertDialog progress = new AlertDialog(fragment.getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER, resourcesProvider);
        progress.setCanCancel(false);
        progress.showDelayed(300);
        ChatUtils.getInstance(fromAccount).sendBotRequest("transfer_init", false, initResponse -> AndroidUtilities.runOnUIThread(() -> {
            if (initResponse == null || !initResponse.startsWith(TOKEN_PREFIX)) {
                progress.dismiss();
                showResult(fragment, initResponse);
                return;
            }
            final String token = initResponse.substring(TOKEN_PREFIX.length());
            ChatUtils.getInstance(toAccount).sendBotRequest("transfer_claim " + token, false, claimResponse -> AndroidUtilities.runOnUIThread(() -> {
                progress.dismiss();
                if (!"ok".equals(claimResponse)) {
                    showResult(fragment, claimResponse);
                    return;
                }
                ApiController.applyTransfer(UserConfig.getInstance(fromAccount).getClientUserId(), UserConfig.getInstance(toAccount).getClientUserId());
                if (!fragment.isFinished) {
                    BulletinFactory.of(fragment).createSimpleBulletin(R.raw.contact_check, LocaleController.formatString(R.string.SupporterTransferDone, UserObject.getUserName(UserConfig.getInstance(toAccount).getCurrentUser()))).show();
                }
            }));
        }));
    }

    private static void showResult(BaseFragment fragment, String response) {
        if (fragment.isFinished) {
            return;
        }
        final CharSequence text;
        final long seconds = response != null && response.startsWith(COOLDOWN_PREFIX) ? Utilities.parseLong(response.substring(COOLDOWN_PREFIX.length())) : 0;
        if (seconds > 0) {
            final String date = DateFormat.getDateInstance(DateFormat.MEDIUM, LocaleController.getInstance().getCurrentLocale()).format(new Date(seconds * 1000));
            text = LocaleController.formatString(R.string.SupporterTransferCooldown, date);
        } else if ("target_not_eligible".equals(response)) {
            text = getString(R.string.SupporterTransferTargetHasBadge);
        } else {
            text = getString(R.string.SupporterTransferFailed);
        }
        BulletinFactory.of(fragment).createSimpleBulletin(R.raw.error, text).show();
    }
}
