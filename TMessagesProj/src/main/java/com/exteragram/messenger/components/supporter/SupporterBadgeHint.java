/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.components.supporter;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.widget.FrameLayout;

import androidx.core.graphics.ColorUtils;

import com.exteragram.messenger.badges.BadgesController;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.PeerColorActivity;
import org.telegram.ui.Stories.recorder.HintView2;

public class SupporterBadgeHint {

    private HintView2 hint;
    private Boolean hintVisible;
    private int backgroundColor;
    private int textColor;
    private int appliedTextColor;
    private long userId;
    private Integer number;
    private boolean customBadge;
    private boolean held;
    private final Runnable hideHint = this::hide;

    public static boolean hasNumber(TLRPC.User user) {
        return BadgesController.INSTANCE.hasBadge(user) && BadgesController.getNumber(user) != null;
    }

    public void set(FrameLayout container, int currentAccount, TLRPC.User user, Runnable onClick) {
        final long newUserId = user != null ? user.id : 0;
        final Integer newNumber = user != null && hasNumber(user) ? BadgesController.getNumber(user) : null;
        final boolean newCustomBadge = BadgesController.INSTANCE.getCustomBadgeId(user) != 0;
        final int newBackgroundColor = resolveBackgroundColor(currentAccount, user);
        if (newUserId == userId && newCustomBadge == customBadge && newBackgroundColor == backgroundColor && (newNumber == null ? number == null : newNumber.equals(number))) {
            return;
        }
        userId = newUserId;
        number = newNumber;
        customBadge = newCustomBadge;
        backgroundColor = newBackgroundColor;
        if (hint != null) {
            final HintView2 oldHint = hint;
            oldHint.setOnHiddenListener(() -> AndroidUtilities.removeFromParent(oldHint));
            oldHint.hide();
            hint = null;
        }
        if (container == null || newNumber == null) {
            return;
        }
        textColor = resolveTextColor(backgroundColor);
        hintVisible = null;
        hint = new HintView2(container.getContext(), HintView2.DIRECTION_BOTTOM);
        hint.setPadding(dp(4), 0, dp(4), dp(2));
        hint.setFlicker(.66f, Theme.multAlpha(textColor, .5f));
        container.addView(hint, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 24));
        hint.setTextSize(9.33f);
        hint.setTextTypeface(AndroidUtilities.bold());
        hint.setTextColor(appliedTextColor = textColor);
        hint.setText(LocaleController.formatString(customBadge ? R.string.SupporterBadgeNameCustom : R.string.SupporterBadgeName, BadgesController.formatNumber(newNumber)));
        hint.setDuration(-1);
        hint.setInnerPadding(4.66f + 1, 2.66f, 4.66f + 1, 2.66f);
        hint.setArrowSize(4, 2.66f);
        hint.setRoundingWithCornerEffect(false);
        hint.setRounding(16);
        hint.setBgColor(backgroundColor);
        if (onClick != null) {
            hint.setOnClickListener(v -> onClick.run());
        }
        if (!held) {
            showHint(hint);
        }
    }

    private void showHint(HintView2 hint) {
        hint.show();
        AndroidUtilities.cancelRunOnUIThread(hideHint);
        AndroidUtilities.runOnUIThread(hideHint, 6 * 1000);
    }

    public void hold() {
        held = true;
        AndroidUtilities.cancelRunOnUIThread(hideHint);
        if (hint != null) {
            hint.hide(false);
        }
    }

    public void release() {
        if (!held) {
            return;
        }
        held = false;
        if (hint != null) {
            showHint(hint);
        }
    }

    public void update(SimpleTextView nameView, Drawable badgeDrawable, float expanded, boolean visible) {
        if (hint == null || nameView == null || badgeDrawable == null) {
            return;
        }
        final int drawableX;
        if (nameView.getRightDrawable() == badgeDrawable) {
            drawableX = nameView.getRightDrawableX();
        } else if (nameView.getRightDrawable2() == badgeDrawable) {
            drawableX = nameView.rightDrawable2X;
        } else {
            visible = false;
            drawableX = 0;
        }
        visible &= drawableX >= 0 && drawableX <= nameView.getWidth();
        hint.setJointPx(0, -hint.getPaddingLeft() + nameView.getX() + drawableX * nameView.getScaleX());
        hint.setTranslationY(-hint.getPaddingBottom() + nameView.getY() - dp(24) + lerp(dp(6), -dp(12), expanded));
        hint.setBgColor(ColorUtils.blendARGB(backgroundColor, 0x50000000, expanded));
        final int currentTextColor = ColorUtils.blendARGB(textColor, Color.WHITE, expanded);
        if (appliedTextColor != currentTextColor) {
            hint.setTextColor(appliedTextColor = currentTextColor);
            hint.invalidate();
        }
        if (hintVisible == null || hintVisible != visible) {
            hint.animate().alpha((hintVisible = visible) ? 1.0f : 0.0f).setInterpolator(CubicBezierInterpolator.EASE_OUT).setDuration(200).start();
        }
    }

    public void hide() {
        if (hint != null) {
            hint.hide(true);
        }
    }

    public static int resolveTextColor(int backgroundColor) {
        return AndroidUtilities.computePerceivedBrightness(backgroundColor) > .721f ? Color.BLACK : Color.WHITE;
    }

    public static int resolveBackgroundColor(int currentAccount, TLRPC.User user) {
        if (user != null && user.emoji_status instanceof TLRPC.TL_emojiStatusCollectible status) {
            return Theme.blendOver(status.center_color | 0xFF000000, Theme.multAlpha(status.pattern_color | 0xFF000000, .5f));
        }
        final MessagesController.PeerColors peerColors = MessagesController.getInstance(currentAccount).profilePeerColors;
        final MessagesController.PeerColor peerColor = peerColors == null ? null : peerColors.getColor(UserObject.getProfileColorId(user));
        if (peerColor != null) {
            final int color = peerColor.getBgColor1(Theme.isCurrentThemeDark());
            final int patternColor = peerColor.patternColor != 0 ? peerColor.patternColor : PeerColorActivity.adaptProfileEmojiColor(color);
            return Theme.blendOver(color, Theme.multAlpha(patternColor | 0xFF000000, .5f));
        }
        final int actionBarColor = Theme.getColor(Theme.key_actionBarDefault);
        return Theme.blendOver(actionBarColor, Theme.multAlpha(Theme.getColor(Theme.key_actionBarDefaultTitle), .15f));
    }
}
