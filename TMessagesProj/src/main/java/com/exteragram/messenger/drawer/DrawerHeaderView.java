/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.drawer;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.transition.ChangeBounds;
import android.transition.TransitionManager;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.api.dto.BadgeDTO;
import com.exteragram.messenger.badges.BadgesController;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.PremiumGradient;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.DialogsActivity;

public class DrawerHeaderView extends FrameLayout {
    private static final int AVATAR_SIZE_DP = 72;
    private static final int HEADER_PADDING_DP = 16;
    private static final int THEME_TOGGLE_SIZE_DP = 36;
    private static final int THEME_TOGGLE_ICON_SIZE_DP = 24;
    private static final float THEME_TOGGLE_RADIUS_DP = THEME_TOGGLE_SIZE_DP / 2f;
    private static final float THEME_TOGGLE_BACKGROUND_ALPHA = 0.075f;
    private static final int INFO_AREA_HEIGHT_DP = 50;
    private static final int INFO_AREA_TOP_MARGIN_DP = HEADER_PADDING_DP + AVATAR_SIZE_DP + 12;
    private static final int INFO_CONTENT_END_MARGIN_DP = HEADER_PADDING_DP + THEME_TOGGLE_SIZE_DP + 12;
    private static final int NAME_HEIGHT_DP = 24;
    private static final int NAME_TEXT_SIZE_DP = 15;
    private static final int SUBTITLE_TEXT_SIZE_DP = 12;
    private static final int SUBTITLE_TOP_MARGIN_DP = NAME_HEIGHT_DP + 2;
    private static final int CHEVRON_SIZE_DP = 24;
    private static final int CHEVRON_END_MARGIN_DP = HEADER_PADDING_DP + 6;
    private static final int STATUS_DRAWABLE_SIZE_DP = 22;
    private static final long CHEVRON_ANIMATION_DURATION_MS = 250L;

    private static final int COLOR_KEY_TEXT = Theme.key_windowBackgroundWhiteBlackText;
    private static final int COLOR_KEY_SUBTITLE = Theme.key_windowBackgroundWhiteGrayText2;
    private static final int COLOR_KEY_ICON = Theme.key_windowBackgroundWhiteGrayIcon;
    private static final int COLOR_KEY_STATUS = Theme.key_profile_verifiedBackground;

    private final BackupImageView avatarView;
    private final SimpleTextView nameView;
    private final SimpleTextView subtitleView;
    private final ImageView chevronView;
    private final RLottieImageView themeToggleView;
    private final FrameLayout themeToggleBg;
    private final FrameLayout proxyButton;
    private final ImageView proxyIcon;
    private final AnimatedTextView proxyTextView;
    private final AvatarDrawable avatarDrawable;
    private final RLottieDrawable sunDrawable;

    private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable premiumStatusDrawable;
    private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable exteraBadgeDrawable;

    private boolean chevronExpanded;
    private int lastProxyState = -1;
    private int lastProxyColor = -1;
    private Runnable onChevronClick;
    private Runnable onThemeToggle;
    private Runnable onThemeToggleLongClick;
    private Runnable onNavigateToProfile;
    private Runnable onStatusClick;
    private Runnable onBadgeClick;
    private Runnable onProxyClick;

    public DrawerHeaderView(Context context) {
        super(context);

        avatarDrawable = new AvatarDrawable();

        avatarView = new BackupImageView(context);
        avatarView.setRoundRadius(ExteraConfig.getAvatarCorners(AVATAR_SIZE_DP));
        addView(avatarView, LayoutHelper.createFrame(AVATAR_SIZE_DP, AVATAR_SIZE_DP, Gravity.LEFT | Gravity.TOP, HEADER_PADDING_DP, HEADER_PADDING_DP, 0, 0));
        avatarView.setOnClickListener(v -> {
            if (onNavigateToProfile != null) {
                onNavigateToProfile.run();
            }
        });

        themeToggleBg = new FrameLayout(context);
        ScaleStateListAnimator.apply(themeToggleBg);
        themeToggleBg.setBackground(Theme.createRoundRectDrawable(dp(THEME_TOGGLE_RADIUS_DP), getThemeToggleBackgroundColor()));
        addView(themeToggleBg, LayoutHelper.createFrame(THEME_TOGGLE_SIZE_DP, THEME_TOGGLE_SIZE_DP, Gravity.RIGHT | Gravity.TOP, 0, HEADER_PADDING_DP, HEADER_PADDING_DP, 0));

        sunDrawable = new RLottieDrawable(R.raw.sun, dp(THEME_TOGGLE_ICON_SIZE_DP), dp(THEME_TOGGLE_ICON_SIZE_DP), true, null);
        sunDrawable.setPlayInDirectionOfCustomEndFrame(true);

        themeToggleView = new RLottieImageView(context);
        themeToggleView.setAnimation(sunDrawable);
        themeToggleView.setScaleType(ImageView.ScaleType.CENTER);
        themeToggleBg.addView(themeToggleView, LayoutHelper.createFrame(THEME_TOGGLE_ICON_SIZE_DP, THEME_TOGGLE_ICON_SIZE_DP, Gravity.CENTER));
        setThemeToggleStaticState(Theme.isCurrentThemeDark());
        themeToggleBg.setOnClickListener(v -> {
            resetThemeTogglePressAnimation();
            if (onThemeToggle != null) {
                onThemeToggle.run();
            }
        });
        themeToggleBg.setOnLongClickListener(v -> {
            if (onThemeToggleLongClick != null) {
                onThemeToggleLongClick.run();
                return true;
            }
            return false;
        });
        updateThemeToggleColors();

        proxyButton = new FrameLayout(context);
        ScaleStateListAnimator.apply(proxyButton);
        proxyButton.setBackground(Theme.createRoundRectDrawable(dp(THEME_TOGGLE_RADIUS_DP), getThemeToggleBackgroundColor()));
        addView(proxyButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, THEME_TOGGLE_SIZE_DP, Gravity.RIGHT | Gravity.TOP, 0, HEADER_PADDING_DP, HEADER_PADDING_DP + THEME_TOGGLE_SIZE_DP + 8, 0));
        proxyButton.setOnClickListener(v -> {
            if (onProxyClick != null) {
                onProxyClick.run();
            }
        });

        LinearLayout proxyLayout = new LinearLayout(context);
        proxyLayout.setOrientation(LinearLayout.HORIZONTAL);
        proxyLayout.setGravity(Gravity.CENTER);
        proxyLayout.setPadding(dp(6), 0, dp(6), 0);
        proxyButton.addView(proxyLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));

        proxyIcon = new ImageView(context);
        proxyIcon.setScaleType(ImageView.ScaleType.CENTER);
        proxyLayout.addView(proxyIcon, LayoutHelper.createLinear(THEME_TOGGLE_ICON_SIZE_DP, THEME_TOGGLE_ICON_SIZE_DP));

        proxyTextView = new AnimatedTextView(context, true, true, true);
        proxyTextView.setTextSize(dp(13));
        proxyTextView.adaptWidth = true;
        proxyTextView.setTypeface(AndroidUtilities.bold());
        proxyTextView.setTextColor(Theme.getColor(COLOR_KEY_ICON));
        proxyTextView.setPadding(dp(2), 0, dp(4), 0);
        proxyTextView.setVisibility(GONE);
        proxyLayout.addView(proxyTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        var infoArea = new FrameLayout(context);
        addView(infoArea, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, INFO_AREA_HEIGHT_DP, Gravity.LEFT | Gravity.TOP, 0, INFO_AREA_TOP_MARGIN_DP, 0, 0));
        infoArea.setOnClickListener(v -> {
            if (onChevronClick != null) {
                onChevronClick.run();
            }
        });

        nameView = new SimpleTextView(context);
        nameView.setTextSize(NAME_TEXT_SIZE_DP);
        nameView.setTypeface(AndroidUtilities.bold());
        nameView.setTextColor(Theme.getColor(COLOR_KEY_TEXT));
        nameView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        nameView.setEllipsizeByGradient(true);
        nameView.setCanHideRightDrawable(false);
        nameView.setRightDrawableOutside(true);
        nameView.setClickable(false);
        infoArea.addView(nameView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, NAME_HEIGHT_DP, Gravity.LEFT | Gravity.TOP, HEADER_PADDING_DP, 0, INFO_CONTENT_END_MARGIN_DP, 0));

        premiumStatusDrawable = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(nameView, dp(STATUS_DRAWABLE_SIZE_DP));
        exteraBadgeDrawable = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(nameView, dp(STATUS_DRAWABLE_SIZE_DP));
        nameView.setRightDrawable(premiumStatusDrawable);
        nameView.setRightDrawable2(exteraBadgeDrawable);
        nameView.setRightDrawableOnClick(v -> {
            if (onStatusClick != null) {
                onStatusClick.run();
            }
        });
        nameView.setRightDrawable2OnClick(v -> {
            if (onBadgeClick != null) {
                onBadgeClick.run();
            }
        });

        subtitleView = new SimpleTextView(context);
        subtitleView.setTextSize(SUBTITLE_TEXT_SIZE_DP);
        subtitleView.setTextColor(Theme.getColor(COLOR_KEY_SUBTITLE));
        subtitleView.setMaxLines(1);
        subtitleView.setClickable(false);
        infoArea.addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, HEADER_PADDING_DP, SUBTITLE_TOP_MARGIN_DP, INFO_CONTENT_END_MARGIN_DP, 0));

        chevronView = new ImageView(context);
        chevronView.setImageResource(R.drawable.msg_expand);
        chevronView.setScaleType(ImageView.ScaleType.CENTER);
        chevronView.setColorFilter(createColorFilter(COLOR_KEY_SUBTITLE));
        infoArea.addView(chevronView, LayoutHelper.createFrame(CHEVRON_SIZE_DP, CHEVRON_SIZE_DP, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, CHEVRON_END_MARGIN_DP, 0));
    }

    public void setOnChevronClick(Runnable r) {
        this.onChevronClick = r;
    }

    public void setOnThemeToggle(Runnable r) {
        this.onThemeToggle = r;
    }

    public void setOnThemeToggleLongClick(Runnable r) {
        this.onThemeToggleLongClick = r;
    }

    public void setOnNavigateToProfile(Runnable r) {
        this.onNavigateToProfile = r;
    }

    public void setOnStatusClick(Runnable r) {
        this.onStatusClick = r;
    }

    public void setOnBadgeClick(Runnable r) {
        this.onBadgeClick = r;
    }

    public void setOnProxyClick(Runnable r) {
        this.onProxyClick = r;
    }

    public SimpleTextView getNameView() {
        return nameView;
    }

    public RLottieImageView getThemeToggleView() {
        return themeToggleView;
    }

    public void updateUserInfo() {
        updateUserInfo(null);
    }

    public void updateUserInfo(BadgeDTO badgeOverride) {
        var currentAccount = UserConfig.selectedAccount;
        var user = UserConfig.getInstance(currentAccount).getCurrentUser();
        if (user == null) {
            return;
        }

        avatarDrawable.setInfo(currentAccount, user);
        avatarView.setRoundRadius(ExteraConfig.getAvatarCorners(AVATAR_SIZE_DP));
        avatarView.getImageReceiver().setCurrentAccount(currentAccount);
        avatarView.setForUserOrChat(user, avatarDrawable);

        nameView.setText(ContactsController.formatName(user.first_name, user.last_name));
        premiumStatusDrawable.setCurrentAccount(currentAccount);
        exteraBadgeDrawable.setCurrentAccount(currentAccount);

        var publicUsername = DialogObject.getPublicUsername(user);
        if (publicUsername != null && !publicUsername.isEmpty()) {
            subtitleView.setText("@" + publicUsername);
        } else if (user.phone != null && !user.phone.isEmpty()) {
            if (ExteraConfig.getHidePhoneNumber()) {
                subtitleView.setText(LocaleController.getString(R.string.MobileHidden));
            } else {
                subtitleView.setText(PhoneFormat.getInstance().format("+" + user.phone));
            }
        } else {
            subtitleView.setText(LocaleController.getString(R.string.NumberUnknown));
        }

        var emojiStatusId = DialogObject.getEmojiStatusDocumentId(user.emoji_status);
        var isPremium = MessagesController.getInstance(currentAccount).isPremiumUser(user);
        var statusColor = Theme.getColor(COLOR_KEY_STATUS);
        if (emojiStatusId != 0) {
            premiumStatusDrawable.set(emojiStatusId, true);
        } else if (isPremium) {
            premiumStatusDrawable.set(PremiumGradient.getInstance().premiumStarDrawableMini, true);
        } else {
            premiumStatusDrawable.set((Drawable) null, true);
        }
        premiumStatusDrawable.setParticles(DialogObject.isEmojiStatusCollectible(user.emoji_status), true);
        premiumStatusDrawable.setColor(statusColor);

        var badge = badgeOverride != null ? badgeOverride : BadgesController.INSTANCE.getBadge(user);
        var badgeDrawable = updateBadgeDrawable(badge, true, true, statusColor);
        applyNameDrawables(emojiStatusId != 0 || isPremium ? premiumStatusDrawable : null, badgeDrawable);
        updateProxyStatus();
    }

    public void updateProxyStatus() {
        boolean enabled = SharedConfig.isProxyEnabled();
        int state = ConnectionsManager.getInstance(UserConfig.selectedAccount).getConnectionState();
        boolean connected = state == ConnectionsManager.ConnectionStateConnected || state == ConnectionsManager.ConnectionStateUpdating;

        int currentState;
        long ping = 0;
        if (enabled && SharedConfig.currentProxy != null && connected) {
            ping = Utilities.clamp(SharedConfig.currentProxy.ping, 9999L, 0L);
            currentState = ping > 0 ? 2 : 1;
        } else if (!SharedConfig.proxyList.isEmpty()) {
            currentState = 1;
        } else {
            currentState = 0;
        }

        if (currentState != lastProxyState) {
            TransitionManager.beginDelayedTransition(this, new ChangeBounds().setDuration(150));
            lastProxyState = currentState;
        }

        if (currentState > 0) {
            proxyButton.setVisibility(VISIBLE);
            if (currentState == 2) {
                proxyTextView.setVisibility(VISIBLE);
                proxyTextView.setText(LocaleController.formatString(R.string.NavigationDrawerProxyPingShort, ping), true);
            } else {
                proxyTextView.setVisibility(GONE);
            }

            int color = (enabled && connected) ? Theme.getColor(Theme.key_windowBackgroundWhiteGreenText) : Theme.getColor(COLOR_KEY_ICON);
            if (color != lastProxyColor) {
                lastProxyColor = color;
                proxyButton.setBackground(Theme.createRoundRectDrawable(dp(THEME_TOGGLE_RADIUS_DP), Theme.multAlpha(color, THEME_TOGGLE_BACKGROUND_ALPHA)));
                proxyIcon.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
                proxyTextView.setTextColor(color);
            }
            proxyIcon.setImageResource((enabled && connected) ? R.drawable.drawer_proxy_on : R.drawable.drawer_proxy_off);
        } else {
            proxyButton.setVisibility(GONE);
        }
    }

    private Drawable updateBadgeDrawable(BadgeDTO badge, boolean animated, boolean showParticles, int statusColor) {
        if (badge == null) {
            clearBadgeDrawables(animated);
            return null;
        }

        exteraBadgeDrawable.set(badge.getDocumentId(), animated);
        exteraBadgeDrawable.setParticles(showParticles, animated);
        exteraBadgeDrawable.setColor(statusColor);
        return exteraBadgeDrawable;
    }

    private void clearBadgeDrawables(boolean animated) {
        exteraBadgeDrawable.set((Drawable) null, animated);
        exteraBadgeDrawable.setParticles(false, animated);
        exteraBadgeDrawable.setColor(null);
    }

    public void setChevronExpanded(boolean expanded) {
        if (chevronExpanded == expanded) {
            return;
        }
        chevronExpanded = expanded;
        chevronView.animate().cancel();
        chevronView.animate()
                .rotation(expanded ? 180 : 0)
                .setDuration(CHEVRON_ANIMATION_DURATION_MS)
                .setInterpolator(CubicBezierInterpolator.DEFAULT)
                .start();
    }

    public int[] getThemeTogglePosition() {
        int[] pos = new int[2];
        themeToggleBg.getLocationInWindow(pos);
        pos[0] += themeToggleBg.getMeasuredWidth() / 2;
        pos[1] += themeToggleBg.getMeasuredHeight() / 2;
        return pos;
    }

    public void animateThemeToggle(boolean toDark) {
        syncThemeToggle(toDark, true);
    }

    public void updateColors() {
        nameView.setTextColor(Theme.getColor(COLOR_KEY_TEXT));
        subtitleView.setTextColor(Theme.getColor(COLOR_KEY_SUBTITLE));
        chevronView.setColorFilter(createColorFilter(COLOR_KEY_SUBTITLE));
        themeToggleBg.setBackground(Theme.createRoundRectDrawable(dp(THEME_TOGGLE_RADIUS_DP), getThemeToggleBackgroundColor()));
        lastProxyState = -1;
        lastProxyColor = -1;
        updateUserInfo();
        if (!themeToggleView.isPlaying() && !DialogsActivity.switchingTheme) {
            syncThemeToggle(false);
        }
        if (!DialogsActivity.switchingTheme || Theme.isCurrentThemeDark()) {
            updateThemeToggleColors();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        premiumStatusDrawable.attach();
        exteraBadgeDrawable.attach();
        if (!themeToggleView.isPlaying() && !DialogsActivity.switchingTheme) {
            syncThemeToggle(false);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        premiumStatusDrawable.detach();
        exteraBadgeDrawable.detach();
    }

    private void updateThemeToggleColors() {
        applyThemeToggleColors(sunDrawable, Theme.getColor(COLOR_KEY_ICON));
        themeToggleView.setColorFilter(createColorFilter(COLOR_KEY_ICON));
        themeToggleView.invalidate();
    }

    private void applyThemeToggleColors(RLottieDrawable drawable, int color) {
        drawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
        drawable.beginApplyLayerColors();
        drawable.setLayerColor("Sunny", color);
        drawable.setLayerColor("Path 6", color);
        drawable.setLayerColor("Path", color);
        drawable.setLayerColor("Path 5", color);
        drawable.commitApplyLayerColors();
    }

    private void syncThemeToggle(boolean animated) {
        syncThemeToggle(Theme.isCurrentThemeDark(), animated);
    }

    private void syncThemeToggle(boolean isDark, boolean animated) {
        if (sunDrawable.getFramesCount() <= 0) {
            return;
        }
        int currentFrame = getThemeToggleCurrentFrame(isDark);
        int endFrame = getThemeToggleEndFrame(isDark);
        if (animated) {
            sunDrawable.setCustomEndFrame(endFrame);
            themeToggleView.playAnimation();
        } else {
            if (!isAttachedToWindow()) {
                setThemeToggleStaticState(isDark);
                return;
            }
            sunDrawable.stop();
            sunDrawable.setCurrentFrame(currentFrame, false, true);
            sunDrawable.setCustomEndFrame(currentFrame);
            themeToggleView.invalidate();
        }
    }

    private int getThemeToggleCurrentFrame(boolean isDark) {
        return isDark ? sunDrawable.getFramesCount() - 1 : 0;
    }

    private int getThemeToggleEndFrame(boolean isDark) {
        return isDark ? sunDrawable.getFramesCount() : 0;
    }

    private void setThemeToggleStaticState(boolean isDark) {
        sunDrawable.stop();
        sunDrawable.setCurrentFrame(getThemeToggleCurrentFrame(isDark));
        sunDrawable.setCustomEndFrame(getThemeToggleEndFrame(isDark));
        themeToggleView.invalidate();
    }

    private void applyNameDrawables(Drawable primaryDrawable, Drawable secondaryDrawable) {
        if (primaryDrawable != null && primaryDrawable == nameView.getRightDrawable2()) {
            nameView.setRightDrawable2(null);
        }
        nameView.setRightDrawable(primaryDrawable);
        nameView.setRightDrawable2(secondaryDrawable);
    }

    private static PorterDuffColorFilter createColorFilter(int colorKey) {
        return new PorterDuffColorFilter(Theme.getColor(colorKey), PorterDuff.Mode.SRC_IN);
    }

    private static int getThemeToggleBackgroundColor() {
        return Theme.multAlpha(Theme.getColor(COLOR_KEY_ICON), THEME_TOGGLE_BACKGROUND_ALPHA);
    }

    private void resetThemeTogglePressAnimation() {
        themeToggleBg.setPressed(false);
        themeToggleBg.setScaleX(1f);
        themeToggleBg.setScaleY(1f);
    }
}
