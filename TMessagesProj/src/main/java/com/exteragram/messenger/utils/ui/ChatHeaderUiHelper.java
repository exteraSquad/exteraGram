/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.debug.DebugConfig;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.ChatActivityTopPanelLayout;
import org.telegram.ui.Components.ChatAvatarContainer;
import org.telegram.ui.Components.chat.ViewPositionWatcher;
import org.telegram.ui.Components.chat.layouts.ChatActivityFadeView;

public final class ChatHeaderUiHelper {

    public static final int GLASS_CHAT_AVATAR_SIZE_DP = 46;

    private static final int DEFAULT_CHAT_AVATAR_SIZE_DP = 42;
    private static final int CHAT_AVATAR_CONTAINER_LEFT_MARGIN_DP = 57;
    private static final float GLASS_PILL_EXTRA_RIGHT_PADDING_DP = 6f;
    private static final int CHAT_AVATAR_CONTAINER_PREVIEW_LEFT_MARGIN_DP = 4;
    private static final int CHAT_TOP_FADE_HEIGHT_DP = 78;
    private static final int CHAT_TOP_FADE_EXTRA_ZONE_DP = 42;
    private static final float CHAT_TOP_FADE_GROWTH = 0.5f;
    private static final int TOP_PANEL_ACTION_BAR_OVERLAP_DP = 5;
    private static final int MATERIAL3_TOP_PANEL_ACTION_BAR_OVERLAP_DP = 1;
    private static final int CHAT_TIME_LEFT_FROM_AVATAR_END_DP = 26;
    private static final int CHAT_TIME_TOP_OFFSET_DP = -10;
    private static final int CHAT_COMMUNITY_LEFT_FROM_AVATAR_END_DP = 14;
    private static final float CHAT_COMMUNITY_TOP_FROM_AVATAR_END_DP = 15.5f;
    private static final int CHAT_STAR_LEFT_FROM_AVATAR_END_DP = 14;
    private static final int CHAT_STAR_TOP_FROM_AVATAR_END_DP = 18;
    private static final float LEGACY_PROFILE_NAME_FALLBACK_TOP_DP = 1.3f;
    private static final float MATERIAL3_PROFILE_NAME_FALLBACK_TOP_DP = 1.66f;
    private static final float MATERIAL3_PROFILE_ONLINE_FALLBACK_TOP_DP = 26.66f;

    private ChatHeaderUiHelper() {
    }

    public static boolean isMaterial3ChatHeaderStyle() {
        return ExteraConfig.getNewChatHeaderStyle();
    }

    public static int getChatAvatarSizeDp() {
        return isMaterial3ChatHeaderStyle() ? GLASS_CHAT_AVATAR_SIZE_DP : DEFAULT_CHAT_AVATAR_SIZE_DP;
    }

    public static int getAvatarInsetPx() {
        return isMaterial3ChatHeaderStyle() ? 0 : 1;
    }

    public static float getGlassPillExtraRightPaddingDp() {
        return isMaterial3ChatHeaderStyle() ? 0f : GLASS_PILL_EXTRA_RIGHT_PADDING_DP;
    }

    public static int getAvatarSizePx(int sizeDp) {
        return dp(sizeDp) - getAvatarInsetPx() * 2;
    }

    public static int getAvatarRadius(int sizeDp, boolean forum) {
        if (isMaterial3ChatHeaderStyle()) {
            return ExteraConfig.getAvatarCorners(sizeDp, false, forum);
        }
        return ExteraConfig.getAvatarCorners(getAvatarSizePx(sizeDp), true, forum);
    }

    public static int getChatFadeColorKey() {
        return DebugConfig.getChatFadeUseWhiteBackground() ? Theme.key_windowBackgroundWhite : Theme.key_windowBackgroundGray;
    }

    public static void setupGlassAvatarContainer(ChatAvatarContainer avatarContainer) {
        avatarContainer.setGlassMode();
        if (isMaterial3ChatHeaderStyle()) {
            avatarContainer.setAvatarSizeInDp(GLASS_CHAT_AVATAR_SIZE_DP);
        }
    }

    public static void applyChatHeaderGlassStyle(ActionBar actionBar) {
        applyChatHeaderGlassStyle(actionBar, isMaterial3ChatHeaderStyle());
    }

    public static void applyChatHeaderGlassStyle(ActionBar actionBar, boolean material3Style) {
        if (material3Style) {
            actionBar.setDrawGlassMiddlePill(false);
            actionBar.setGlassShadowAlpha(0f);
        }
    }

    public static void setupChatTopFade(ChatActivityFadeView fadeView, ActionBar actionBar, int topFadeColor, int baseFadeZone) {
        setupChatTopFade(fadeView, actionBar, topFadeColor, baseFadeZone, isMaterial3ChatHeaderStyle());
    }

    public static void setupChatTopFade(ChatActivityFadeView fadeView, ActionBar actionBar, int topFadeColor, int baseFadeZone, boolean material3Style) {
        fadeView.setTopFadeAnchor(actionBar);
        fadeView.setFadeTopAlpha(actionBar.getVisibility() == View.VISIBLE ? 255 : 0);
        final int fadeZone = getChatTopFadeZone(baseFadeZone, material3Style);
        if (material3Style) {
            fadeView.setFadeZoneTop(getScaledChatTopFadeZone(actionBar, fadeZone, material3Style));
            fadeView.setTopFadeColor(topFadeColor);
        } else {
            fadeView.setFadeZoneTop(fadeZone);
        }
        fadeView.setFadeHeightTop(getChatTopFadeHeight(material3Style));
    }

    public static boolean scrimReachesTopFade(ChatActivityFadeView fadeView, ViewGroup listView, View scrimView, MessageObject.GroupedMessages scrimGroup, float listTop) {
        final int fadeZoneTop = fadeView.getFadeZoneTop();
        if (listTop >= fadeZoneTop) {
            return false;
        }
        for (int i = 0, count = listView.getChildCount(); i < count; i++) {
            final View child = listView.getChildAt(i);
            final ChatMessageCell cell = child instanceof ChatMessageCell ? (ChatMessageCell) child : null;
            if (child != scrimView && (scrimGroup == null || cell == null || cell.getCurrentMessagesGroup() != scrimGroup) || child.getAlpha() == 0f) {
                continue;
            }
            if (cell != null && cell.getTransitionParams().animateBackgroundBoundsInner || listView.getY() + child.getY() < fadeZoneTop) {
                return true;
            }
        }
        if (scrimGroup != null && scrimGroup.transitionParams.cell != null) {
            float groupTop = scrimGroup.transitionParams.top + scrimGroup.transitionParams.offsetTop;
            if (!scrimGroup.transitionParams.backgroundChangeBounds) {
                groupTop += scrimGroup.transitionParams.cell.getTranslationY();
            }
            return listView.getY() + groupTop < fadeZoneTop;
        }
        return false;
    }

    private static int getScaledChatTopFadeZone(ActionBar actionBar, int fadeZone, boolean material3Style) {
        final int actionBarHeight = actionBar.getMeasuredHeight();
        final int minZone = getChatTopFadeZone(actionBarHeight, material3Style);
        if (actionBarHeight == 0 || minZone >= fadeZone) {
            return fadeZone;
        }
        return minZone + Math.round((fadeZone - minZone) * CHAT_TOP_FADE_GROWTH);
    }

    public static boolean isLightChatStatusBar(ActionBar actionBar, int topFadeColor) {
        return isLightChatStatusBar(actionBar, topFadeColor, isMaterial3ChatHeaderStyle());
    }

    public static boolean isLightChatStatusBar(ActionBar actionBar, int topFadeColor, boolean material3Style) {
        int statusBarColor = material3Style && !actionBar.isActionModeShowed()
                ? topFadeColor
                : actionBar.getBackgroundColor();
        return AndroidUtilities.computePerceivedBrightness(statusBarColor) > 0.721f;
    }

    public static int getAvatarContainerLeftMargin(boolean inPreviewMode) {
        if (inPreviewMode) {
            return CHAT_AVATAR_CONTAINER_PREVIEW_LEFT_MARGIN_DP;
        }
        return isMaterial3ChatHeaderStyle() ? CHAT_AVATAR_CONTAINER_LEFT_MARGIN_DP : 52;
    }

    public static float getTopPanelActionBarGapOffset(ChatActivityTopPanelLayout topPanelLayout) {
        if (topPanelLayout == null) {
            return 0;
        }
        return getTopPanelActionBarGapOffset(topPanelLayout.getMetadata().getTotalVisibility());
    }

    public static float getTopPanelActionBarGapOffset(float visibility) {
        if (!isMaterial3ChatHeaderStyle()) {
            return 0;
        }
        return dp(TOP_PANEL_ACTION_BAR_OVERLAP_DP - MATERIAL3_TOP_PANEL_ACTION_BAR_OVERLAP_DP) * visibility;
    }

    public static float getFinalTopPanelHeight(float topPanelHeight, ChatActivityTopPanelLayout topPanelLayout) {
        return topPanelHeight + getTopPanelActionBarGapOffset(topPanelLayout);
    }

    public static float getFinalTopPanelHeight(float topPanelHeight, float visibility) {
        return topPanelHeight + getTopPanelActionBarGapOffset(visibility);
    }

    public static float getTopPanelTranslationY(float baseTranslationY, float topicTabsSideSize, float hashtagTabsShownT) {
        int actionBarOffset = isMaterial3ChatHeaderStyle() ? -MATERIAL3_TOP_PANEL_ACTION_BAR_OVERLAP_DP : -TOP_PANEL_ACTION_BAR_OVERLAP_DP;
        return baseTranslationY + dp(actionBarOffset) - topicTabsSideSize * hashtagTabsShownT;
    }

    public static int getChatTopFadeHeight(boolean material3Style) {
        return dp(material3Style ? CHAT_TOP_FADE_HEIGHT_DP : 48);
    }

    public static int getChatTopFadeZone(int baseFadeZone, boolean material3Style) {
        return material3Style ? baseFadeZone + dp(CHAT_TOP_FADE_EXTRA_ZONE_DP) : baseFadeZone;
    }

    public static final class ProfileTransitionState {
        private float avatarTranslation;
        private float avatarStartY = Float.NaN;
        private float avatarSizeDp = DEFAULT_CHAT_AVATAR_SIZE_DP;
        private float nameTranslationY = Float.NaN;
        private float onlineTranslationY = Float.NaN;

        public void reset() {
            avatarTranslation = 0;
            avatarStartY = Float.NaN;
            avatarSizeDp = DEFAULT_CHAT_AVATAR_SIZE_DP;
            nameTranslationY = Float.NaN;
            onlineTranslationY = Float.NaN;
        }

        public void capture(ChatAvatarContainer sourceAvatarContainer, View sourceRoot, View profileNameView, View profileOnlineView) {
            if (sourceAvatarContainer == null || !(sourceRoot instanceof ViewGroup sourceParent)) {
                return;
            }
            BackupImageView sourceAvatarImage = sourceAvatarContainer.getAvatarImageView();
            if (sourceAvatarImage == null) {
                return;
            }
            avatarTranslation = ViewPositionWatcher.computeXCoordinateInParent(sourceAvatarImage, sourceParent);
            avatarStartY = ViewPositionWatcher.computeYCoordinateInParent(sourceAvatarImage, sourceParent);
            int sourceAvatarWidth = sourceAvatarImage.getMeasuredWidth() != 0 ? sourceAvatarImage.getMeasuredWidth() : sourceAvatarImage.getWidth();
            if (sourceAvatarWidth > 0) {
                avatarSizeDp = sourceAvatarWidth / AndroidUtilities.density;
            }

            if (sourceAvatarContainer.getTitleTextView() != null && profileNameView != null) {
                nameTranslationY = ViewPositionWatcher.computeYCoordinateInParent(sourceAvatarContainer.getTitleTextView(), sourceParent) - getTransitionLayoutTop(profileNameView);
            }
            View sourceSubtitle = sourceAvatarContainer.getSubtitleTextView();
            if (sourceSubtitle != null && profileOnlineView != null) {
                onlineTranslationY = ViewPositionWatcher.computeYCoordinateInParent(sourceSubtitle, sourceParent) - getTransitionLayoutTop(profileOnlineView) - profileOnlineView.getPaddingTop();
            }
        }

        public float getAvatarTranslation() {
            return avatarTranslation;
        }

        public float getAvatarStartY(ActionBar actionBar) {
            if (!Float.isNaN(avatarStartY)) {
                return avatarStartY;
            }
            return (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0)
                    + ActionBar.getCurrentActionBarHeight() / 2f
                    - dp(avatarSizeDp / 2f)
                    + actionBar.getTranslationY();
        }

        public float getAvatarStartScale() {
            return avatarSizeDp / 100f;
        }

        public float getAvatarSizeDp() {
            return avatarSizeDp;
        }

        public float getTextTranslationXFallback() {
            return avatarTranslation - dp(109) + dp(avatarSizeDp + 6);
        }

        public float getNameTranslationY(ActionBar actionBar) {
            if (!Float.isNaN(nameTranslationY)) {
                return nameTranslationY;
            }
            float fallbackTopDp = isMaterial3ChatHeaderStyle() ? MATERIAL3_PROFILE_NAME_FALLBACK_TOP_DP : LEGACY_PROFILE_NAME_FALLBACK_TOP_DP;
            return (float) Math.floor(getAvatarStartY(actionBar)) + AndroidUtilities.dp(fallbackTopDp);
        }

        public float getOnlineTranslationY(ActionBar actionBar) {
            if (!Float.isNaN(onlineTranslationY)) {
                return onlineTranslationY;
            }
            float fallbackTopDp = isMaterial3ChatHeaderStyle() ? MATERIAL3_PROFILE_ONLINE_FALLBACK_TOP_DP : 24f;
            return (float) Math.floor(getAvatarStartY(actionBar)) + AndroidUtilities.dp(fallbackTopDp);
        }

        public void updateBadgePositionsFromCollapsedAvatar(View avatarContainer, float avatarScale, ImageView timeItem, ImageView communityItem, ImageView starBgItem, ImageView starFgItem) {
            final float extra = dp(avatarSizeDp) * (avatarScale * 100f / avatarSizeDp) - dp(avatarSizeDp);
            updateBadgePositions(avatarContainer, extra, timeItem, communityItem, starBgItem, starFgItem);
        }

        public void updateBadgePositionsFromExpandedAvatar(View avatarContainer, float avatarScale, ImageView timeItem, ImageView communityItem, ImageView starBgItem, ImageView starFgItem) {
            final float extra = (avatarContainer.getMeasuredWidth() - dp(avatarSizeDp)) * (avatarScale * 100f / avatarSizeDp);
            updateBadgePositions(avatarContainer, extra, timeItem, communityItem, starBgItem, starFgItem);
        }

        private void updateBadgePositions(View avatarContainer, float extra, ImageView timeItem, ImageView communityItem, ImageView starBgItem, ImageView starFgItem) {
            if (timeItem != null) {
                timeItem.setTranslationX(avatarContainer.getX() + dp(avatarSizeDp - CHAT_TIME_LEFT_FROM_AVATAR_END_DP) + extra);
                timeItem.setTranslationY(avatarContainer.getY() + dp(CHAT_TIME_TOP_OFFSET_DP) + extra);
            }
            if (communityItem != null) {
                communityItem.setTranslationX(avatarContainer.getX() + dp(avatarSizeDp - CHAT_COMMUNITY_LEFT_FROM_AVATAR_END_DP) + extra);
                communityItem.setTranslationY(avatarContainer.getY() + dp(avatarSizeDp - CHAT_COMMUNITY_TOP_FROM_AVATAR_END_DP) + extra);
            }
            if (starBgItem != null) {
                starBgItem.setTranslationX(avatarContainer.getX() + dp(avatarSizeDp - CHAT_STAR_LEFT_FROM_AVATAR_END_DP) + extra);
                starBgItem.setTranslationY(avatarContainer.getY() + dp(avatarSizeDp - CHAT_STAR_TOP_FROM_AVATAR_END_DP) + extra);
            }
            if (starFgItem != null) {
                starFgItem.setTranslationX(avatarContainer.getX() + dp(avatarSizeDp - CHAT_STAR_LEFT_FROM_AVATAR_END_DP) + extra);
                starFgItem.setTranslationY(avatarContainer.getY() + dp(avatarSizeDp - CHAT_STAR_TOP_FROM_AVATAR_END_DP) + extra);
            }
        }

        private static int getTransitionLayoutTop(View view) {
            ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
            if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
                return ((ViewGroup.MarginLayoutParams) layoutParams).topMargin;
            }
            return view.getTop();
        }
    }
}