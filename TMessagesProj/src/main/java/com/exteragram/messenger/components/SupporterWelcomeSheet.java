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

import android.os.Build;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;

import com.exteragram.messenger.badges.BadgesController;
import com.exteragram.messenger.components.supporter.SupporterBadgeLanding;
import com.exteragram.messenger.components.supporter.SupporterEnterFrom;
import com.exteragram.messenger.components.supporter.SupporterNumberView;
import com.exteragram.messenger.components.supporter.SupporterPerks;
import com.exteragram.messenger.components.supporter.SupporterSheet;
import com.exteragram.messenger.components.supporter.SupporterTransfer;
import com.exteragram.messenger.nowplaying.ui.SetupNowPlayingActivity;
import com.exteragram.messenger.utils.network.RemoteUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.FireworksOverlay;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.ProfileActivity;

public class SupporterWelcomeSheet extends SupporterSheet {

    public static final int MODE_GRANTED = 0;
    public static final int MODE_INFO = 1;

    private FireworksOverlay fireworksOverlay;
    private SupporterNumberView numberView;

    public SupporterWelcomeSheet(BaseFragment fragment, int mode, Theme.ResourcesProvider resourcesProvider) {
        super(fragment, resourcesProvider);

        var context = fragment.getParentActivity();

        final long userId = UserConfig.getInstance(currentAccount).getClientUserId();
        final Integer number = BadgesController.INSTANCE.getNumber(userId);
        final boolean isDeveloper = BadgesController.INSTANCE.isDeveloper(UserConfig.getInstance(currentAccount).getCurrentUser());

        final boolean reveal = mode == MODE_GRANTED && number != null;
        if (number != null) {
            getArrowView().setNumber(BadgesController.formatNumber(number));
        }
        setContent(
                getString(mode == MODE_GRANTED && !isDeveloper ? R.string.SupporterWelcomeTitle : R.string.DeveloperPerksTitle),
                number != null && !reveal
                        ? AndroidUtilities.replaceTags(LocaleController.formatString(R.string.SupporterWelcomeInfo, BadgesController.formatNumber(number)))
                        : getString(R.string.SupporterWelcomeInfoNoNumber)
        );
        if (reveal) {
            numberView = new SupporterNumberView(context);
            numberView.setNumber(BadgesController.formatNumber(number));
            numberView.setColors(getArrowView().color1, getArrowView().color2);
            addHeaderAccessory(numberView);
        }

        final String channelLink = RemoteUtils.getStringConfigValue(RemoteUtils.SUPPORTERS_CHANNEL_LINK, "");
        SupporterPerks.addTo(context, this::addView, resourcesProvider, () -> {
            if (fragment instanceof ProfileActivity profile && profile.getDialogId() == userId) {
                dismiss();
                return;
            }
            if (mode == MODE_GRANTED && !getArrowView().hasBadge() && SupporterBadgeLanding.prepare(userId, getArrowView().getIconView(), .8f)) {
                getArrowView().setIconHidden(true);
            }
            dismiss();
            fragment.presentFragment(ProfileActivity.of(userId));
        }, () -> {
            dismiss();
            fragment.presentFragment(new SetupNowPlayingActivity());
        }, TextUtils.isEmpty(channelLink) ? null : () -> {
            dismiss();
            Browser.openUrl(context, channelLink);
        });

        if (!TextUtils.isEmpty(channelLink)) {
            button.setText(getString(R.string.SupporterJoinChannel), false);
            button.setOnClickListener(v -> {
                dismiss();
                Browser.openUrl(context, channelLink);
            });
        } else {
            button.setText(getString(R.string.GotIt), false);
            button.setOnClickListener(v -> dismiss());
        }

        if (mode == MODE_INFO && SupporterTransfer.canTransfer(currentAccount)) {
            addSecondaryButton(getString(R.string.SupporterTransfer), v -> SupporterTransfer.start(fragment, container, v, resourcesProvider, this::dismiss));
        }

        if (mode == MODE_GRANTED) {
            fireworksOverlay = new FireworksOverlay(context);
            container.addView(fireworksOverlay, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }
        commit();
    }

    @Override
    public void show() {
        super.show();
        if (numberView != null) {
            if (!getArrowView().hasBadge()) {
                getArrowView().iconView.startEnterAnimation(-360, 250);
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (isDismissed()) {
                    return;
                }
                numberView.start(() -> {
                    try {
                        container.performHapticFeedback(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.LONG_PRESS);
                    } catch (Exception ignored) {
                    }
                    fireworksOverlay.start(true);
                });
            }, 250);
        } else if (fireworksOverlay != null) {
            AndroidUtilities.runOnUIThread(() -> {
                if (isDismissed()) {
                    return;
                }
                try {
                    container.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                } catch (Exception ignored) {
                }
                fireworksOverlay.start(true);
            }, 200);
        }
    }

    public static SupporterWelcomeSheet show(BaseFragment fragment, int mode) {
        return show(fragment, mode, fragment.getResourceProvider(), null, 0);
    }

    public static SupporterWelcomeSheet show(BaseFragment fragment, int mode, Theme.ResourcesProvider resourcesProvider, SupporterEnterFrom enterFrom, long badgeDocumentId) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return null;
        }
        SupporterWelcomeSheet sheet = new SupporterWelcomeSheet(fragment, mode, resourcesProvider);
        sheet.setEnterFrom(enterFrom);
        if (badgeDocumentId != 0) {
            sheet.setBadge(badgeDocumentId);
        }
        return fragment.showDialog(sheet) != null ? sheet : null;
    }
}
