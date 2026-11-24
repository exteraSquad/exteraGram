/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.profilecolor;

import static com.exteragram.messenger.profilecolor.ProfileColorPalette.ROLES_COUNT;
import static com.exteragram.messenger.profilecolor.ProfileColorPalette.ROLE_ACCENT;
import static com.exteragram.messenger.profilecolor.ProfileColorPalette.ROLE_ACCENT_PRESSED;
import static com.exteragram.messenger.profilecolor.ProfileColorPalette.ROLE_BACKGROUND;
import static com.exteragram.messenger.profilecolor.ProfileColorPalette.ROLE_CARD;
import static com.exteragram.messenger.profilecolor.ProfileColorPalette.ROLE_DIVIDER;
import static com.exteragram.messenger.profilecolor.ProfileColorPalette.ROLE_ICON;
import static com.exteragram.messenger.profilecolor.ProfileColorPalette.ROLE_KEEP_HUE;
import static com.exteragram.messenger.profilecolor.ProfileColorPalette.ROLE_KEEP_HUE_FILL;
import static com.exteragram.messenger.profilecolor.ProfileColorPalette.ROLE_LINK_SELECTION;
import static com.exteragram.messenger.profilecolor.ProfileColorPalette.ROLE_ON_ACCENT;
import static com.exteragram.messenger.profilecolor.ProfileColorPalette.ROLE_PLACEHOLDER;
import static com.exteragram.messenger.profilecolor.ProfileColorPalette.ROLE_RAISED;
import static com.exteragram.messenger.profilecolor.ProfileColorPalette.ROLE_SELECTOR;
import static com.exteragram.messenger.profilecolor.ProfileColorPalette.ROLE_TEXT;
import static com.exteragram.messenger.profilecolor.ProfileColorPalette.ROLE_TEXT_HINT;
import static com.exteragram.messenger.profilecolor.ProfileColorPalette.ROLE_TEXT_SECONDARY;
import static com.exteragram.messenger.profilecolor.ProfileColorPalette.ROLE_TRACK;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.util.SparseIntArray;

import androidx.core.graphics.ColorUtils;

import com.exteragram.messenger.debug.DebugConfig;

import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;

public final class ProfileColorTheme implements Theme.ResourcesProvider {

    private static final long ANIMATION_DURATION = 350;

    private static int[] keyRoles;

    private static void map(int role, int... keys) {
        for (int key : keys) {
            keyRoles[key] = role + 1;
        }
    }

    private static int[] getKeyRoles() {
        if (keyRoles != null) {
            return keyRoles;
        }
        keyRoles = new int[Theme.colorsCount];
        map(ROLE_BACKGROUND,
                Theme.key_actionBarDefaultSubmenuBackground,
                Theme.key_dialogBackgroundGray,
                Theme.key_windowBackgroundGray,
                Theme.key_graySection);
        map(ROLE_CARD,
                Theme.key_dialogBackground,
                Theme.key_dialogTopBackground,
                Theme.key_dialogFloatingIcon,
                Theme.key_dialogRoundCheckBoxCheck,
                Theme.key_dialogCheckboxSquareCheck,
                Theme.key_windowBackgroundWhite,
                Theme.key_actionBarActionModeDefault,
                Theme.key_actionBarActionModeDefaultTop,
                Theme.key_switchTrackBlueThumb,
                Theme.key_switchTrackBlueThumbChecked);
        map(ROLE_RAISED,
                Theme.key_dialogSearchBackground,
                Theme.key_dialogGiftsBackground,
                Theme.key_sharedMedia_linkPlaceholder,
                Theme.key_sharedMedia_photoPlaceholder,
                Theme.key_files_folderIconBackground,
                Theme.key_groupcreate_spanBackground,
                Theme.key_contacts_inviteBackground);
        map(ROLE_TEXT,
                Theme.key_actionBarDefaultSubmenuItem,
                Theme.key_actionBarDefaultSubmenuItemIcon,
                Theme.key_dialogTextBlack,
                Theme.key_dialogSearchText,
                Theme.key_dialogGiftsTabText,
                Theme.key_windowBackgroundWhiteBlackText,
                Theme.key_player_actionBarTitle,
                Theme.key_player_actionBarItems,
                Theme.key_actionBarActionModeDefaultIcon,
                Theme.key_profile_tabSelectedText,
                Theme.key_sharedMedia_linkPlaceholderText,
                Theme.key_files_iconText,
                Theme.key_files_folderIcon,
                Theme.key_groupcreate_spanText,
                Theme.key_glass_defaultText,
                Theme.key_glass_tabSelectedText,
                Theme.key_chats_name,
                Theme.key_chats_secretName,
                Theme.key_chat_messageTextIn);
        map(ROLE_TEXT_SECONDARY,
                Theme.key_chats_date_bold,
                Theme.key_chat_inAdminText,
                Theme.key_chat_inAdminSelectedText,
                Theme.key_dialogTextGray,
                Theme.key_dialogTextGray2,
                Theme.key_dialogTextGray3,
                Theme.key_dialogTextGray4,
                Theme.key_dialogEmptyText,
                Theme.key_windowBackgroundWhiteGrayText,
                Theme.key_windowBackgroundWhiteGrayText2,
                Theme.key_windowBackgroundWhiteGrayText3,
                Theme.key_windowBackgroundWhiteGrayText4,
                Theme.key_windowBackgroundWhiteGrayText5,
                Theme.key_windowBackgroundWhiteGrayText6,
                Theme.key_windowBackgroundWhiteGrayText7,
                Theme.key_windowBackgroundWhiteGrayText8,
                Theme.key_player_actionBarSubtitle,
                Theme.key_player_time,
                Theme.key_graySectionText,
                Theme.key_profile_tabText,
                Theme.key_glass_tabUnselected,
                Theme.key_chats_message,
                Theme.key_chats_date,
                Theme.key_chats_actionMessage,
                Theme.key_chats_nameMessage,
                Theme.key_chats_attachMessage,
                Theme.key_chats_message_threeLines,
                Theme.key_chats_nameMessage_threeLines,
                Theme.key_chat_inTimeText);
        map(ROLE_TEXT_HINT,
                Theme.key_dialogTextHint,
                Theme.key_dialogSearchHint,
                Theme.key_dialogEmptyImage,
                Theme.key_dialogCheckboxSquareUnchecked,
                Theme.key_dialogCheckboxSquareDisabled,
                Theme.key_dialogRadioBackground,
                Theme.key_windowBackgroundWhiteHintText,
                Theme.key_emptyListPlaceholder,
                Theme.key_fastScrollInactive,
                Theme.key_groupcreate_hintText,
                Theme.key_chats_pinnedIcon,
                Theme.key_checkboxDisabled,
                Theme.key_checkboxSquareDisabled,
                Theme.key_checkboxSquareUnchecked,
                Theme.key_radioBackground);
        map(ROLE_ICON,
                Theme.key_chats_sentCheck,
                Theme.key_chats_sentReadCheck,
                Theme.key_chats_sentClock,
                Theme.key_dialogIcon,
                Theme.key_dialogSearchIcon,
                Theme.key_sheet_other,
                Theme.key_windowBackgroundWhiteGrayIcon,
                Theme.key_glass_defaultIcon,
                Theme.key_profile_creatorIcon,
                Theme.key_chats_muteIcon,
                Theme.key_groupcreate_spanDelete);
        map(ROLE_ACCENT,
                Theme.key_checkbox,
                Theme.key_telegram_color_text,
                Theme.key_dialogTextBlue,
                Theme.key_dialogTextBlue2,
                Theme.key_dialogTextBlue4,
                Theme.key_dialogTextLink,
                Theme.key_dialogInputFieldActivated,
                Theme.key_dialogCheckboxSquareBackground,
                Theme.key_dialogRoundCheckBox,
                Theme.key_dialogRadioBackgroundChecked,
                Theme.key_dialogLineProgress,
                Theme.key_dialogButton,
                Theme.key_dialog_inlineProgress,
                Theme.key_dialogFloatingButton,
                Theme.key_windowBackgroundWhiteBlueText,
                Theme.key_windowBackgroundWhiteBlueText2,
                Theme.key_windowBackgroundWhiteBlueText3,
                Theme.key_windowBackgroundWhiteBlueText4,
                Theme.key_windowBackgroundWhiteBlueText5,
                Theme.key_windowBackgroundWhiteBlueText6,
                Theme.key_windowBackgroundWhiteBlueText7,
                Theme.key_windowBackgroundWhiteBlueButton,
                Theme.key_windowBackgroundWhiteBlueIcon,
                Theme.key_windowBackgroundWhiteBlueHeader,
                Theme.key_windowBackgroundWhiteLinkText,
                Theme.key_windowBackgroundWhiteValueText,
                Theme.key_windowBackgroundWhiteInputFieldActivated,
                Theme.key_chat_messageLinkIn,
                Theme.key_profile_tabSelectedLine,
                Theme.key_profile_status,
                Theme.key_switchTrackChecked,
                Theme.key_switchTrackBlueChecked,
                Theme.key_switch2TrackChecked,
                Theme.key_checkboxSquareBackground,
                Theme.key_radioBackgroundChecked,
                Theme.key_featuredStickers_addButton,
                Theme.key_featuredStickers_addButton2,
                Theme.key_featuredStickers_addedIcon,
                Theme.key_featuredStickers_unread,
                Theme.key_progressCircle,
                Theme.key_sharedMedia_startStopLoadIcon,
                Theme.key_fastScrollActive,
                Theme.key_groupcreate_cursor,
                Theme.key_glass_tabSelected,
                Theme.key_chats_actionBackground,
                Theme.key_profile_actionBackground,
                Theme.key_chats_verifiedBackground,
                Theme.key_chats_draft,
                Theme.key_player_progress,
                Theme.key_player_button,
                Theme.key_player_buttonActive,
                Theme.key_chat_inLoader,
                Theme.key_chat_inLoaderSelected,
                Theme.key_chat_inAudioSeekbarFill,
                Theme.key_chat_inFileProgress,
                Theme.key_contacts_inviteText);
        map(ROLE_ACCENT_PRESSED,
                Theme.key_dialogFloatingButtonPressed,
                Theme.key_featuredStickers_addButtonPressed,
                Theme.key_chats_actionPressedBackground,
                Theme.key_profile_actionPressedBackground);
        map(ROLE_ON_ACCENT,
                Theme.key_featuredStickers_buttonText,
                Theme.key_featuredStickers_buttonProgress,
                Theme.key_checkboxCheck,
                Theme.key_checkboxSquareCheck,
                Theme.key_fastScrollText,
                Theme.key_chats_actionIcon,
                Theme.key_profile_actionIcon,
                Theme.key_chats_verifiedCheck,
                Theme.key_chat_inMediaIcon,
                Theme.key_chat_inMediaIconSelected);
        map(ROLE_DIVIDER,
                Theme.key_dialogInputField,
                Theme.key_dialogGrayLine,
                Theme.key_dialogShadowLine,
                Theme.key_actionBarDefaultSubmenuSeparator,
                Theme.key_divider,
                Theme.key_windowBackgroundWhiteInputField);
        map(ROLE_SELECTOR,
                Theme.key_dialogButtonSelector,
                Theme.key_sheet_scrollUp,
                Theme.key_listSelector,
                Theme.key_settings_listSelector,
                Theme.key_profile_tabSelector,
                Theme.key_actionBarActionModeDefaultSelector,
                Theme.key_actionBarWhiteSelector,
                Theme.key_switchTrackBlueSelector,
                Theme.key_switchTrackBlueSelectorChecked);
        map(ROLE_TRACK,
                Theme.key_dialogLineProgressBackground,
                Theme.key_dialog_inlineProgressBackground,
                Theme.key_switchTrack,
                Theme.key_switchTrackBlue,
                Theme.key_switch2Track,
                Theme.key_player_progressBackground,
                Theme.key_player_progressCachedBackground,
                Theme.key_chat_inAudioSeekbar,
                Theme.key_chat_inFileBackground);
        map(ROLE_PLACEHOLDER,
                Theme.key_windowBackgroundGrayShadow,
                Theme.key_groupcreate_sectionShadow);
        map(ROLE_LINK_SELECTION,
                Theme.key_dialogLinkSelection,
                Theme.key_windowBackgroundWhiteLinkSelection,
                Theme.key_chat_linkSelectBackground,
                Theme.key_chat_inTextSelectionHighlight);
        map(ROLE_KEEP_HUE,
                Theme.key_avatar_nameInMessageRed,
                Theme.key_avatar_nameInMessageOrange,
                Theme.key_avatar_nameInMessageViolet,
                Theme.key_avatar_nameInMessageGreen,
                Theme.key_avatar_nameInMessageCyan,
                Theme.key_avatar_nameInMessageBlue,
                Theme.key_avatar_nameInMessagePink,
                Theme.key_chat_tagCreator,
                Theme.key_chat_tagAdmin,
                Theme.key_text_RedRegular,
                Theme.key_text_RedBold,
                Theme.key_windowBackgroundWhiteGreenText,
                Theme.key_windowBackgroundWhiteGreenText2,
                Theme.key_featuredStickers_removeButtonText);
        map(ROLE_KEEP_HUE_FILL,
                Theme.key_color_red,
                Theme.key_color_green,
                Theme.key_color_lightgreen,
                Theme.key_color_orange,
                Theme.key_color_yellow,
                Theme.key_color_purple,
                Theme.key_color_cyan,
                Theme.key_color_blue,
                Theme.key_color_lightblue,
                Theme.key_fill_RedNormal,
                Theme.key_fill_RedDark);
        return keyRoles;
    }

    private final Runnable onColorsChanged;
    private final SparseIntArray keptHueColors = new SparseIntArray();
    private final Paint dividerPaint = new Paint();
    private final int[] colors = new int[ROLES_COUNT];
    private final int[] fromColors = new int[ROLES_COUNT];

    private Theme.ResourcesProvider base;
    private MessagesController.PeerColor lastPeerColor;
    private ProfileColorPalette palette;
    private ProfileColorPalette hueSource;
    private float fromAmount;
    private float amount;
    private float progress = 1f;
    private ValueAnimator animator;
    private ColorFilter emojiColorFilter;
    private int emojiColorFilterColor;

    public ProfileColorTheme(Runnable onColorsChanged) {
        this.onColorsChanged = onColorsChanged;
    }

    public void setBase(Theme.ResourcesProvider base) {
        this.base = base;
    }

    public Theme.ResourcesProvider getBase() {
        return base;
    }

    public boolean isTinted() {
        return amount > 0f;
    }

    public boolean isAnimating() {
        return animator != null;
    }

    public static Theme.ResourcesProvider unwrap(Theme.ResourcesProvider resourcesProvider) {
        return resourcesProvider instanceof ProfileColorTheme ? ((ProfileColorTheme) resourcesProvider).base : resourcesProvider;
    }

    public static MessagesController.PeerColor resolve(int account, TLRPC.User user, TLRPC.Chat chat) {
        if (user == null && chat == null) {
            return null;
        }
        final MessagesController.PeerColor collectible = MessagesController.PeerColor.fromCollectible(user != null ? user.emoji_status : chat.emoji_status);
        if (collectible != null) {
            return collectible;
        }
        final MessagesController.PeerColors peerColors = MessagesController.getInstance(account).profilePeerColors;
        if (peerColors == null) {
            return null;
        }
        return peerColors.getColor(user != null ? UserObject.getProfileColorId(user) : ChatObject.getProfileColorId(chat));
    }

    public void set(MessagesController.PeerColor peerColor, boolean animated) {
        if (!DebugConfig.getProfileColorDesign()) {
            peerColor = null;
        }
        final boolean samePeerColor = peerColor == lastPeerColor;
        lastPeerColor = peerColor;
        final boolean enabled = peerColor != null;
        final boolean dark = Theme.isCurrentThemeDark();
        final int edgeColor = enabled ? peerColor.getBgColor1(dark) : 0;
        final int centerColor = enabled ? peerColor.getBgColor2(dark) : 0;
        if (enabled ? palette != null && palette.sameSource(edgeColor, centerColor) : palette == null) {
            return;
        }
        animated &= !samePeerColor;
        stopAnimator();
        System.arraycopy(colors, 0, fromColors, 0, ROLES_COUNT);
        fromAmount = amount;
        palette = enabled ? new ProfileColorPalette(edgeColor, centerColor) : null;
        if (palette != null) {
            hueSource = palette;
        }
        keptHueColors.clear();
        if (!animated) {
            progress = 1f;
            update();
            onColorsChanged.run();
            return;
        }
        progress = 0f;
        update();
        final ValueAnimator valueAnimator = animator = ValueAnimator.ofFloat(0f, 1f);
        valueAnimator.setDuration(ANIMATION_DURATION);
        valueAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        valueAnimator.addUpdateListener(a -> {
            if (animator != valueAnimator) {
                return;
            }
            progress = (float) a.getAnimatedValue();
            update();
            onColorsChanged.run();
        });
        valueAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animator != valueAnimator) {
                    return;
                }
                animator = null;
                progress = 1f;
                update();
                onColorsChanged.run();
            }
        });
        valueAnimator.start();
    }

    public void finishAnimation() {
        if (animator != null) {
            stopAnimator();
            progress = 1f;
            update();
        }
    }

    private void stopAnimator() {
        if (animator != null) {
            final ValueAnimator running = animator;
            animator = null;
            running.cancel();
        }
    }

    private void update() {
        final float toAmount = palette != null ? 1f : 0f;
        amount = progress >= 1f ? toAmount : fromAmount + (toAmount - fromAmount) * progress;
        for (int i = 0; i < ROLES_COUNT; ++i) {
            if (palette == null) {
                colors[i] = fromColors[i];
            } else if (fromAmount <= 0f || progress >= 1f) {
                colors[i] = palette.colors[i];
            } else {
                colors[i] = ColorUtils.blendARGB(fromColors[i], palette.colors[i], progress);
            }
        }
        if (amount <= 0f) {
            hueSource = null;
        }
    }

    public boolean hasDarkContent() {
        return palette != null && !palette.lightContent;
    }

    public int getHeaderContentColor(float photoProgress) {
        return hasDarkContent() ? ColorUtils.blendARGB(palette.colors[ROLE_TEXT], Color.WHITE, Math.max(0f, Math.min(1f, photoProgress))) : Color.WHITE;
    }

    public int getHeaderAccentColor(boolean online) {
        if (palette == null) {
            return online ? Color.WHITE : 0xB3FFFFFF;
        }
        if (!palette.lightContent) {
            return palette.colors[online ? ROLE_TEXT : ROLE_TEXT_SECONDARY];
        }
        return online ? palette.headerAccent : Theme.multAlpha(palette.headerAccent, 0.74f);
    }

    @Override
    public int getColor(int key) {
        final int color = Theme.getColor(key, base);
        if (amount <= 0f || key < 0) {
            return color;
        }
        final int[] roles = getKeyRoles();
        if (key >= roles.length) {
            return color;
        }
        final int role = roles[key] - 1;
        if (role < 0) {
            return color;
        }
        if (role == ROLE_DIVIDER && Color.alpha(color) == 0) {
            return color;
        }
        final int tinted;
        if (role >= ROLE_KEEP_HUE) {
            final ProfileColorPalette source = hueSource;
            if (source == null) {
                return color;
            }
            final int cacheKey = role == ROLE_KEEP_HUE_FILL ? ~color : color;
            final int index = keptHueColors.indexOfKey(cacheKey);
            if (index >= 0) {
                tinted = keptHueColors.valueAt(index);
            } else {
                tinted = source.keepHue(color, role == ROLE_KEEP_HUE_FILL);
                if (keptHueColors.size() > 64) {
                    keptHueColors.clear();
                }
                keptHueColors.put(cacheKey, tinted);
            }
        } else {
            tinted = colors[role];
        }
        return amount >= 1f ? tinted : ColorUtils.blendARGB(color, tinted, amount);
    }

    @Override
    public int getColorOrDefault(int key) {
        return getColor(key);
    }

    @Override
    public int getCurrentColor(int key) {
        if (amount > 0f) {
            return getColor(key);
        }
        return base != null ? base.getCurrentColor(key) : Theme.getColor(key);
    }

    @Override
    public void setAnimatedColor(int key, int color) {
        if (base != null) {
            base.setAnimatedColor(key, color);
        }
    }

    @Override
    public Drawable getDrawable(String drawableKey) {
        return base != null ? base.getDrawable(drawableKey) : null;
    }

    @Override
    public Paint getPaint(String paintKey) {
        if (amount > 0f && Theme.key_paint_divider.equals(paintKey)) {
            final Paint source = Theme.getThemePaint(paintKey, base);
            if (source != null) {
                dividerPaint.set(source);
            }
            dividerPaint.setColor(getColor(Theme.key_divider));
            return dividerPaint;
        }
        return base != null ? base.getPaint(paintKey) : Theme.getThemePaint(paintKey);
    }

    @Override
    public boolean hasGradientService() {
        return base != null && base.hasGradientService();
    }

    @Override
    public boolean isDark() {
        return base != null ? base.isDark() : Theme.isCurrentThemeDark();
    }

    @Override
    public boolean isMonet() {
        return base != null ? base.isMonet() : Theme.isCurrentThemeMonet();
    }

    @Override
    public void applyServiceShaderMatrix(int w, int h, float translationX, float translationY) {
        if (base != null) {
            base.applyServiceShaderMatrix(w, h, translationX, translationY);
        } else {
            Theme.applyServiceShaderMatrix(w, h, translationX, translationY);
        }
    }

    @Override
    public ColorFilter getAnimatedEmojiColorFilter() {
        if (amount > 0f) {
            final int color = getColor(Theme.key_windowBackgroundWhiteBlackText);
            if (emojiColorFilter == null || emojiColorFilterColor != color) {
                emojiColorFilterColor = color;
                emojiColorFilter = new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN);
            }
            return emojiColorFilter;
        }
        return base != null ? base.getAnimatedEmojiColorFilter() : Theme.chat_animatedEmojiTextColorFilter;
    }

    @Override
    public boolean isCompactMode() {
        return base != null ? base.isCompactMode() : SharedConfig.compactMode;
    }
}
