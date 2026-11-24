/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.drawer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.RoundedCorner;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.core.math.MathUtils;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import com.exteragram.messenger.utils.ui.AccountsUiHelper;
import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.badges.BadgesController;
import com.exteragram.messenger.components.supporter.SupporterBadgeEditSheet;
import com.exteragram.messenger.utils.AppUtils;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.DrawerLayoutContainer;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.MainTabsActivity;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.ProxyListActivity;
import org.telegram.ui.SelectAnimatedEmojiDialog;
import org.telegram.ui.ThemeActivity;

import static org.telegram.messenger.AndroidUtilities.dp;

public class DrawerContainer extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {
    private static final float PROGRESS_EPSILON = 0.001f;
    private static final int DRAWER_MAX_WIDTH_DP = 300;
    private static final int DRAWER_REMAINING_WIDTH_DP = 56;
    private static final int DRAWER_ROUNDING_MAX_DP = 24;
    private static final int HEADER_HEIGHT_DP = 160;
    private static final int POPUP_MAX_WIDTH_DP = 340;
    private static final int POPUP_WIDTH_PADDING_DP = 16;
    private static final int POPUP_VERTICAL_OFFSET_DP = 16;
    private static final float POPUP_WIDTH_RATIO = 0.95f;
    private static final long DRAWER_ANIMATION_DURATION_MS = 300L;
    private static final long FAST_ANIMATION_BASE_DURATION_MS = 250L;
    private static final long FAST_ANIMATION_MIN_DURATION_MS = 100L;
    private static final long PROFILE_NAVIGATION_DELAY_MS = 200L;
    private static final float NAV_TRANSLATION_FACTOR = 0.3f;
    private static final float OPEN_TOUCH_SLOP_CM = 0.2f;
    private static final float CLOSE_TOUCH_SLOP_CM = 0.4f;
    private static final int MAX_ALPHA_DEFAULT = 0x66;
    private static final int MAX_ALPHA_IMMERSIVE = 0xa0;
    private static final String DRAWER_TAG = "drawer_container";

    private static final int COLOR_KEY_DRAWER_BACKGROUND = Theme.key_windowBackgroundWhite;

    private final FrameLayout drawerPanel;
    private final FrameLayout bulletinContainer;
    private final DrawerHeaderView headerView;
    private final DrawerAccountPickerView accountPickerView;
    private final DrawerMenuView menuView;

    private float progress;
    private boolean isOpen;
    private boolean isAnimating;
    private int drawerWidth;

    private final Paint scrimPaint = new Paint();

    private float startX, startY;
    private float startProgress;
    private boolean tracking;
    private boolean startedEdgeSwipe;
    private boolean tapClosePending;
    private boolean animationInterruptedByTouch;
    private boolean predictiveBackInProgress;
    private float predictiveBackStartProgress;
    private VelocityTracker velocityTracker;

    private SpringAnimation springAnimation;
    private ValueAnimator standardAnimator;
    private final Rect rect = new Rect();
    private final Path clipPath = new Path();
    private final float[] radii = new float[8];
    private float cachedTopRightRadius = -1f;
    private float cachedBottomRightRadius = -1f;

    private static final FloatPropertyCompat<DrawerContainer> DRAWER_OFFSET = new FloatPropertyCompat<>("drawerOffset") {
        @Override
        public float getValue(DrawerContainer object) {
            return object.getDrawerOffset();
        }

        @Override
        public void setValue(DrawerContainer object, float value) {
            object.setDrawerOffset(value);
        }
    };

    private SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow selectAnimatedEmojiDialog;
    private View navigationTranslationTarget;
    private boolean notificationsRegistered;

    public DrawerContainer(Context context) {
        super(context);
        setVisibility(GONE);
        setTag(DRAWER_TAG);

        drawerWidth = calculateDrawerWidth();

        drawerPanel = new FrameLayout(context);
        drawerPanel.setBackgroundColor(Theme.getColor(COLOR_KEY_DRAWER_BACKGROUND));
        drawerPanel.setTranslationX(-drawerWidth);
        addView(drawerPanel, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT));

        var panelLp = (FrameLayout.LayoutParams) drawerPanel.getLayoutParams();
        panelLp.width = drawerWidth;
        drawerPanel.setLayoutParams(panelLp);

        var content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        drawerPanel.addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        bulletinContainer = new FrameLayout(context);
        drawerPanel.addView(bulletinContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        headerView = new DrawerHeaderView(context);
        content.addView(headerView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(HEADER_HEIGHT_DP)));

        accountPickerView = new DrawerAccountPickerView(context);
        content.addView(accountPickerView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        menuView = new DrawerMenuView(context);
        var menuLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0);
        menuLp.weight = 1;
        content.addView(menuView, menuLp);

        setupCallbacks();
        headerView.setChevronExpanded(accountPickerView.isExpanded());
    }

    private void setupCallbacks() {
        headerView.setOnChevronClick(() -> {
            accountPickerView.toggleExpand();
            headerView.setChevronExpanded(accountPickerView.isExpanded());
        });

        headerView.setOnThemeToggle(() -> {
            if (DialogsActivity.switchingTheme) {
                return;
            }
            var pos = headerView.getThemeTogglePosition();

            var preferences = ApplicationLoader.applicationContext.getSharedPreferences("themeconfig", Activity.MODE_PRIVATE);
            var dayThemeName = preferences.getString("lastDayTheme", "Blue");
            if (Theme.getTheme(dayThemeName) == null || Theme.getTheme(dayThemeName).isDark()) {
                dayThemeName = "Blue";
            }
            var nightThemeName = preferences.getString("lastDarkTheme", "Dark Blue");
            if (Theme.getTheme(nightThemeName) == null || !Theme.getTheme(nightThemeName).isDark()) {
                nightThemeName = "Dark Blue";
            }
            var themeInfo = Theme.getActiveTheme();
            if (dayThemeName.equals(nightThemeName)) {
                if (themeInfo.isDark() || dayThemeName.equals("Dark Blue") || dayThemeName.equals("Night")) {
                    dayThemeName = "Blue";
                } else {
                    nightThemeName = "Dark Blue";
                }
            }

            boolean toDark;
            if (toDark = dayThemeName.equals(themeInfo.getKey())) {
                themeInfo = Theme.getTheme(nightThemeName);
            } else {
                themeInfo = Theme.getTheme(dayThemeName);
            }

            if (themeInfo != null) {
                DialogsActivity.switchingTheme = true;
                headerView.animateThemeToggle(toDark);
                NotificationCenter.getGlobalInstance().postNotificationName(
                        NotificationCenter.needSetDayNightTheme, themeInfo, false, pos, -1, toDark, headerView.getThemeToggleView(), null, null, false, null
                );
                var fragment = getLastFragment();
                if (fragment != null) {
                    Theme.turnOffAutoNight(BulletinFactory.of(fragment), () -> {
                        fragment.presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_NIGHT));
                    });
                }
            }
        });

        headerView.setOnThemeToggleLongClick(() -> {
            closeDrawer(true);
            AndroidUtilities.runOnUIThread(() -> {
                var fragment = getLastFragment();
                if (fragment != null) {
                    fragment.presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));
                }
            }, PROFILE_NAVIGATION_DELAY_MS);
        });

        headerView.setOnNavigateToProfile(() -> {
            closeDrawer(true);
            AndroidUtilities.runOnUIThread(() -> {
                var fragment = getLastFragment();
                var args = new Bundle();
                args.putLong("user_id", UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId());
                args.putBoolean("my_profile", true);
                if (fragment != null) {
                    fragment.presentFragment(new ProfileActivity(args));
                }
            }, PROFILE_NAVIGATION_DELAY_MS);
        });

        headerView.setOnStatusClick(this::showStatusSelect);
        headerView.setOnBadgeClick(this::showBadgeSelect);
        headerView.setOnProxyClick(() -> {
            closeDrawer(true);
            AndroidUtilities.runOnUIThread(() -> {
                var fragment = getLastFragment();
                if (fragment != null) {
                    fragment.presentFragment(new ProxyListActivity());
                }
            }, PROFILE_NAVIGATION_DELAY_MS);
        });

        accountPickerView.setOnAccountSelected(() -> closeDrawer(true));
        accountPickerView.setOnAccountLongClick((account, view) -> showAccountPreview(account));

        menuView.setOnItemClick(() -> closeDrawer(true));
    }

    private void showStatusSelect() {
        if (selectAnimatedEmojiDialog != null) {
            return;
        }
        var fragment = getLastFragment();
        if (fragment == null) {
            return;
        }

        var account = UserConfig.selectedAccount;
        var user = UserConfig.getInstance(account).getCurrentUser();
        if (user == null || !MessagesController.getInstance(account).isPremiumUser(user)) {
            return;
        }

        var nameView = headerView.getNameView();
        var loc = new int[2];
        nameView.getLocationOnScreen(loc);

        var emojiScreenX = loc[0] + nameView.getRightDrawableX();
        var popupWidth = getPopupWidth();
        var xoff = MathUtils.clamp(emojiScreenX - popupWidth / 2, 0, AndroidUtilities.displaySize.x - popupWidth);
        var ecenter = emojiScreenX - xoff;
        var yoff = loc[1] + nameView.getHeight();

        final SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow[] popup = new SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow[1];

        var popupLayout = new SelectAnimatedEmojiDialog(fragment, getContext(), true, Math.max(0, ecenter), SelectAnimatedEmojiDialog.TYPE_EMOJI_STATUS, true, null, POPUP_WIDTH_PADDING_DP) {
            @Override
            protected void onEmojiSelected(View emojiView, Long documentId, TLRPC.Document document, TL_stars.TL_starGiftUnique gift, Integer until) {
                final TLRPC.EmojiStatus emojiStatus;
                if (gift != null) {
                    final TLRPC.TL_inputEmojiStatusCollectible status = new TLRPC.TL_inputEmojiStatusCollectible();
                    status.collectible_id = gift.id;
                    if (until != null) {
                        status.flags |= 1;
                        status.until = until;
                    }
                    emojiStatus = status;
                } else if (documentId == null) {
                    emojiStatus = new TLRPC.TL_emojiStatusEmpty();
                } else {
                    final TLRPC.TL_emojiStatus status = new TLRPC.TL_emojiStatus();
                    status.document_id = documentId;
                    if (until != null) {
                        status.flags |= 1;
                        status.until = until;
                    }
                    emojiStatus = status;
                }

                MessagesController.getInstance(account).updateEmojiStatus(0, emojiStatus, gift);
                headerView.updateUserInfo();

                if (popup[0] != null) {
                    selectAnimatedEmojiDialog = null;
                    popup[0].dismiss();
                }
            }
        };

        popupLayout.setExpireDateHint(DialogObject.getEmojiStatusUntil(user.emoji_status));
        long currentStatusId = DialogObject.getEmojiStatusDocumentId(user.emoji_status);
        popupLayout.setSelected(currentStatusId != 0 ? currentStatusId : null);
        popupLayout.setSaveState(3);

        popup[0] = selectAnimatedEmojiDialog = new SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow(popupLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
            @Override
            public void dismiss() {
                super.dismiss();
                selectAnimatedEmojiDialog = null;
            }
        };
        var anchorLoc = new int[2];
        this.getLocationOnScreen(anchorLoc);
        popup[0].showAsDropDown(this, xoff, yoff - anchorLoc[1] - dp(POPUP_VERTICAL_OFFSET_DP), Gravity.TOP | Gravity.LEFT);
        popup[0].dimBehind();
    }

    private void showBadgeSelect() {
        var fragment = getLastFragment();
        if (fragment == null) {
            return;
        }
        closeDrawer(true);

        if (!BadgesController.INSTANCE.canChangeBadge()) {
            BadgesController.INSTANCE.openProfileBadge(fragment, UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser(), fragment.getResourceProvider());
            return;
        }

        SupporterBadgeEditSheet.show(fragment, () -> {
            headerView.updateUserInfo();
            accountPickerView.loadAccounts();
        });
    }

    private void showAccountPreview(int account) {
        if (!(getParent() instanceof DrawerLayoutContainer drawerLayoutContainer)) {
            return;
        }
        var nav = drawerLayoutContainer.getParentActionBarLayout();
        if (nav == null) {
            return;
        }

        var args = new Bundle();
        args.putBoolean("drawer_account_preview", true);
        var mainTabsActivity = new MainTabsActivity(args) {
            @Override
            public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
                super.onTransitionAnimationEnd(isOpen, backward);
                if (!isOpen && backward) {
                    restoreDrawerAbovePreview();
                }
            }

            @Override
            public void onPreviewOpenAnimationEnd() {
                super.onPreviewOpenAnimationEnd();
                restoreDrawerAbovePreview();
                closeDrawer(false);
                if (account != UserConfig.selectedAccount) {
                    AccountsUiHelper.switchTo(account);
                }
            }
        };
        mainTabsActivity.setCurrentAccount(account);
        mainTabsActivity.prepareDialogsActivity(args);

        boolean presented = nav.presentFragment(new INavigationLayout.NavigationParams(mainTabsActivity)
                .setPreview(true)
                .setCheckPresentFromDelegate(false));
        if (presented) {
            drawerLayoutContainer.setDrawCurrentPreviewFragmentAbove(true);
        }
    }

    private void restoreDrawerAbovePreview() {
        if (getParent() instanceof DrawerLayoutContainer drawerLayoutContainer) {
            drawerLayoutContainer.setDrawCurrentPreviewFragmentAbove(false);
        }
    }

    private void updateDrawerWidth() {
        drawerWidth = calculateDrawerWidth();
        var panelLp = (FrameLayout.LayoutParams) drawerPanel.getLayoutParams();
        panelLp.width = drawerWidth;
        drawerPanel.setLayoutParams(panelLp);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateDrawerWidth();
        setProgress(progress);
    }

    public boolean isDrawerOpen() {
        return progress > PROGRESS_EPSILON || isAnimating || predictiveBackInProgress;
    }

    public boolean startPredictiveBack() {
        if (predictiveBackInProgress || tracking || startedEdgeSwipe || getVisibility() != VISIBLE) {
            return false;
        }
        if (isAnimating) {
            cancelAnimations();
        }
        if (progress <= PROGRESS_EPSILON) {
            return false;
        }
        predictiveBackInProgress = true;
        predictiveBackStartProgress = progress;
        tapClosePending = false;
        super.setVisibility(VISIBLE);
        return true;
    }

    public void updatePredictiveBackProgress(float progress) {
        if (!predictiveBackInProgress) {
            return;
        }
        float clamped = Math.max(0f, Math.min(1f, progress));
        setProgress(predictiveBackStartProgress * (1f - clamped * 0.5f));
    }

    public void cancelPredictiveBack() {
        if (!predictiveBackInProgress) {
            return;
        }
        predictiveBackInProgress = false;
        isOpen = predictiveBackStartProgress > PROGRESS_EPSILON;
        animateProgress(isOpen ? 1f : 0f, true, 0f);
    }

    public void commitPredictiveBack() {
        if (!predictiveBackInProgress) {
            closeDrawer(true);
            return;
        }
        predictiveBackInProgress = false;
        isOpen = false;
        if (progress <= PROGRESS_EPSILON) {
            onCloseComplete();
        } else {
            animateProgress(0f, true, 0f);
        }
    }

    public void toggleDrawer() {
        if (isDrawerOpen()) closeDrawer(true);
        else openDrawer(true);
    }

    public void openDrawer(boolean animated) {
        if (!ExteraConfig.getNavigationDrawer()) {
            onCloseComplete();
            return;
        }
        if (progress >= 1f - PROGRESS_EPSILON && !isAnimating) {
            isOpen = true;
            setProgress(1f);
            return;
        }
        isOpen = true;
        updateDrawerWidth();
        super.setVisibility(VISIBLE);
        applyDrawerPanelPadding();

        refreshContents();

        if (animated) {
            animateProgress(1f);
        } else {
            setProgress(1f);
        }
    }

    public void closeDrawer(boolean animated) {
        if (progress <= PROGRESS_EPSILON && !isAnimating) {
            isOpen = false;
            onCloseComplete();
            return;
        }
        isOpen = false;
        if (animated) {
            animateProgress(0f);
        } else {
            setProgress(0f);
            onCloseComplete();
        }
    }

    private void refreshContents() {
        headerView.updateUserInfo();
        accountPickerView.loadAccounts();

        BaseFragment fragment = getLastFragment();
        if (fragment != null) {
            menuView.rebuildMenu(UserConfig.selectedAccount, fragment);
        } else {
            menuView.clearMenu();
        }
    }

    private void refreshAccountViews(int account, boolean reloadAccounts) {
        if (account == UserConfig.selectedAccount) {
            headerView.updateUserInfo();
        }
        if (reloadAccounts) {
            accountPickerView.loadAccounts();
        }
    }

    private void refreshAccountViews(int account, int mask) {
        boolean headerChanged = (mask & MessagesController.UPDATE_MASK_AVATAR) != 0
                || (mask & MessagesController.UPDATE_MASK_NAME) != 0
                || (mask & MessagesController.UPDATE_MASK_PHONE) != 0
                || (mask & MessagesController.UPDATE_MASK_EMOJI_STATUS) != 0;
        boolean accountPickerChanged = (mask & MessagesController.UPDATE_MASK_AVATAR) != 0
                || (mask & MessagesController.UPDATE_MASK_NAME) != 0
                || (mask & MessagesController.UPDATE_MASK_EMOJI_STATUS) != 0;

        if (headerChanged && account == UserConfig.selectedAccount) {
            headerView.updateUserInfo();
        }
        if (accountPickerChanged) {
            accountPickerView.loadAccounts();
        }
    }

    private void setProgress(float p) {
        progress = Math.max(0f, Math.min(1f, p));
        syncDrawerState();
        invalidate();
    }

    private void syncDrawerState() {
        boolean fullyClosedAndIdle = progress <= PROGRESS_EPSILON && !isAnimating && !tracking && !startedEdgeSwipe && !predictiveBackInProgress;
        if (fullyClosedAndIdle) {
            applyClosedState();
            return;
        }
        drawerPanel.setTranslationX(-drawerWidth * (1f - progress));
        translateNavigationLayout(progress <= PROGRESS_EPSILON ? 0f : getNavigationLayoutTranslation(progress));
        if (getVisibility() != VISIBLE) {
            super.setVisibility(VISIBLE);
        }
    }

    private void applyClosedState() {
        progress = 0f;
        drawerPanel.setTranslationX(-drawerWidth);
        translateNavigationLayout(0f);
        if (getVisibility() != GONE) {
            super.setVisibility(GONE);
        }
        tapClosePending = false;
    }

    private float getDrawerOffset() {
        return drawerWidth * progress;
    }

    private void setDrawerOffset(float value) {
        float clamped = Math.max(0f, Math.min(drawerWidth, value));
        setProgress(drawerWidth == 0 ? 0f : clamped / drawerWidth);
    }

    private float getNavigationLayoutTranslation(float progress) {
        if (ExteraConfig.getImmersiveDrawerAnimation()) {
            return drawerWidth * progress;
        }
        return drawerWidth * progress * NAV_TRANSLATION_FACTOR;
    }

    private void translateNavigationLayout(float tx) {
        if (!(getParent() instanceof DrawerLayoutContainer drawerLayoutContainer)) {
            resetNavigationTranslationTarget();
            return;
        }
        INavigationLayout nav = drawerLayoutContainer.getParentActionBarLayout();
        if (nav == null) {
            resetNavigationTranslationTarget();
            return;
        }
        View target = resolveNavigationTranslationTarget(drawerLayoutContainer, nav);
        if (navigationTranslationTarget != null && navigationTranslationTarget != target) {
            navigationTranslationTarget.setTranslationX(0f);
        }
        navigationTranslationTarget = target;
        if (navigationTranslationTarget != null) {
            navigationTranslationTarget.setTranslationX(tx);
        }
    }

    private View resolveNavigationTranslationTarget(DrawerLayoutContainer drawerLayoutContainer, INavigationLayout nav) {
        View navView = nav.getView();
        if (!(navView.getParent() instanceof View parentView)) {
            return navView;
        }
        if (parentView.getParent() == drawerLayoutContainer) {
            return parentView;
        }
        return navView;
    }

    private void resetNavigationTranslationTarget() {
        if (navigationTranslationTarget != null) {
            navigationTranslationTarget.setTranslationX(0f);
            navigationTranslationTarget = null;
        }
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        if (progress <= 0f) {
            super.dispatchDraw(canvas);
            return;
        }

        float p = Math.max(0f, Math.min(1f, progress));

        int alpha;
        int r, g, b;

        if (ExteraConfig.getImmersiveDrawerAnimation() && !AndroidUtilities.isTablet()) {
            alpha = (int) (MAX_ALPHA_IMMERSIVE * p);
            int bgColor = Theme.getColor(COLOR_KEY_DRAWER_BACKGROUND);
            r = Color.red(bgColor);
            g = Color.green(bgColor);
            b = Color.blue(bgColor);
        } else {
            alpha = (int) (MAX_ALPHA_DEFAULT * p);
            r = g = b = 0;
        }

        scrimPaint.setColor(Color.argb(alpha, r, g, b));

        canvas.drawRect(0, 0, getWidth(), getHeight(), scrimPaint);
        super.dispatchDraw(canvas);
    }

    @Override
    protected boolean drawChild(@NonNull Canvas canvas, View child, long drawingTime) {
        if (child == drawerPanel && !ExteraConfig.getImmersiveDrawerAnimation()) {
            float tr = cachedTopRightRadius >= 0 ? cachedTopRightRadius : dp(DRAWER_ROUNDING_MAX_DP);
            float br = cachedBottomRightRadius >= 0 ? cachedBottomRightRadius : dp(DRAWER_ROUNDING_MAX_DP);

            radii[0] = radii[1] = 0;
            radii[2] = radii[3] = tr;
            radii[4] = radii[5] = br;
            radii[6] = radii[7] = 0;

            AndroidUtilities.rectTmp.set(child.getX(), child.getY(),
                    child.getX() + child.getWidth(), child.getY() + child.getHeight());

            clipPath.rewind();
            clipPath.addRoundRect(AndroidUtilities.rectTmp, radii, Path.Direction.CW);

            final int restoreCount = canvas.save();
            canvas.clipPath(clipPath);
            final boolean result = super.drawChild(canvas, child, drawingTime);
            canvas.restoreToCount(restoreCount);

            return result;
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    private void animateProgress(float target) {
        animateProgress(target, false, 0f);
    }

    private void animateProgress(float target, boolean fast, float velocity) {
        cancelAnimations();
        isAnimating = true;
        final float targetOffset = drawerWidth * target;

        if (ExteraConfig.getSpringSwipeback()) {
            springAnimation = new SpringAnimation(this, DRAWER_OFFSET);
            springAnimation.setSpring(new SpringForce(targetOffset)
                    .setStiffness(fast ? 1500f : 950f)
                    .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY));
            if (velocity != 0f) {
                springAnimation.setStartVelocity(velocity);
            }
            springAnimation.addEndListener((animation, canceled, value, velocity1) -> {
                if (springAnimation == animation) {
                    springAnimation = null;
                }
                if (canceled) {
                    return;
                }
                isAnimating = false;
                setDrawerOffset(targetOffset);
                if (target == 0f) {
                    onCloseComplete();
                }
            });
            springAnimation.animateToFinalPosition(targetOffset);
        } else {
            standardAnimator = ValueAnimator.ofFloat(getDrawerOffset(), targetOffset);
            standardAnimator.setDuration(getAnimationDuration(targetOffset, fast));
            standardAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            standardAnimator.addUpdateListener(a -> setDrawerOffset((float) a.getAnimatedValue()));
            standardAnimator.addListener(new AnimatorListenerAdapter() {
                private boolean canceled;

                @Override
                public void onAnimationCancel(Animator animation) {
                    canceled = true;
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (standardAnimator == animation) {
                        standardAnimator = null;
                    }
                    if (canceled) {
                        return;
                    }
                    isAnimating = false;
                    setDrawerOffset(targetOffset);
                    if (target == 0f) {
                        onCloseComplete();
                    }
                }
            });
            standardAnimator.start();
        }
    }

    private long getAnimationDuration(float targetOffset, boolean fast) {
        if (!fast) {
            return DRAWER_ANIMATION_DURATION_MS;
        }
        var currentOffset = getDrawerOffset();
        var distance = targetOffset > currentOffset ? drawerWidth - currentOffset : currentOffset;
        return Math.max((long) (FAST_ANIMATION_BASE_DURATION_MS / (float) Math.max(drawerWidth, 1) * distance), FAST_ANIMATION_MIN_DURATION_MS);
    }

    private void cancelAnimations() {
        if (springAnimation != null) {
            SpringAnimation animation = springAnimation;
            springAnimation = null;
            animation.cancel();
        }
        if (standardAnimator != null) {
            ValueAnimator animator = standardAnimator;
            standardAnimator = null;
            animator.cancel();
        }
        isAnimating = false;
        setProgress(progress);
        if (!isOpen && progress <= PROGRESS_EPSILON && !tracking && !startedEdgeSwipe) {
            onCloseComplete();
        }
    }

    private void onCloseComplete() {
        isOpen = false;
        tracking = false;
        startedEdgeSwipe = false;
        animationInterruptedByTouch = false;
        predictiveBackInProgress = false;
        predictiveBackStartProgress = 0f;
        setProgress(0f);
        tapClosePending = false;
        dismissSelectionPopup();
        menuView.clearMenu();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (isClosingAnimationInProgress()) {
            return !shouldPassClosingTouchThrough(ev);
        }

        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            if (isAnimating) {
                cancelAnimations();
                animationInterruptedByTouch = true;
            }
            startX = ev.getX();
            startY = ev.getY();
            startProgress = progress;
            tracking = false;

            var panelRight = drawerPanel.getTranslationX() + drawerWidth;
            tapClosePending = ev.getX() > panelRight;
            return ev.getX() > panelRight;
        }

        if (ev.getAction() == MotionEvent.ACTION_MOVE) {
            var dx = ev.getX() - startX;
            var dy = Math.abs(ev.getY() - startY);
            if (shouldStartVisibleDrawerTracking(dx, dy)) {
                beginVisibleDrawerTracking(ev, dx);
                return true;
            }
        }

        return false;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (isClosingAnimationInProgress()) {
            return !shouldPassClosingTouchThrough(ev);
        }

        if (velocityTracker == null) velocityTracker = VelocityTracker.obtain();
        velocityTracker.addMovement(ev);

        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (isAnimating) {
                    cancelAnimations();
                    animationInterruptedByTouch = true;
                }
                startX = ev.getX();
                startY = ev.getY();
                startProgress = progress;
                tracking = false;

                var panelRight = drawerPanel.getTranslationX() + drawerWidth;
                tapClosePending = ev.getX() > panelRight;
                return true;

            case MotionEvent.ACTION_MOVE:
                if (!tracking) {
                    var dx = ev.getX() - startX;
                    var dy = Math.abs(ev.getY() - startY);
                    if (shouldStartVisibleDrawerTracking(dx, dy)) {
                        beginVisibleDrawerTracking(ev, dx);
                    }
                }
                if (tracking) {
                    var dx = ev.getX() - startX;
                    var newProgress = Math.max(0, Math.min(1, startProgress + dx / drawerWidth));
                    setProgress(newProgress);
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (tracking) {
                    finishTracking();
                    return true;
                }
                if (ev.getAction() == MotionEvent.ACTION_UP && tapClosePending) {
                    tapClosePending = false;
                    closeDrawer(true);
                    return true;
                }
                tapClosePending = false;
                break;
        }
        return true;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        boolean handled = super.dispatchTouchEvent(ev);
        var action = ev.getActionMasked();
        if (animationInterruptedByTouch && (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)) {
            animationInterruptedByTouch = false;
            settleInterruptedAnimation();
        }
        return handled;
    }

    private void settleInterruptedAnimation() {
        if (isAnimating || tracking || startedEdgeSwipe || predictiveBackInProgress) {
            return;
        }
        var target = isOpen ? 1f : 0f;
        if (Math.abs(progress - target) <= PROGRESS_EPSILON) {
            setProgress(target);
            if (!isOpen) {
                onCloseComplete();
            }
            return;
        }
        animateProgress(target, true, 0f);
    }

    private boolean isClosingAnimationInProgress() {
        // пока хз насколько норм
        // если поставить тру, то при нажатии на кнопку, которая открывает фрагмент,
        // можно схватить дравер и он по итогу будет поверх фрагмента нового
        return isAnimating && !isOpen;
    }

    private boolean shouldPassClosingTouchThrough(MotionEvent ev) {
        return ev != null
                && ev.getAction() == MotionEvent.ACTION_DOWN
                && ev.getX() > drawerPanel.getTranslationX() + drawerWidth;
    }

    private boolean shouldStartVisibleDrawerTracking(float dx, float dy) {
        if (dx < 0) {
            return Math.abs(dx) >= dy && Math.abs(dx) >= getDrawerCloseTouchSlop();
        }
        return startProgress < 1f - PROGRESS_EPSILON
                && dx > 0
                && dx / 3.0f > dy
                && dx >= getDrawerOpenTouchSlop();
    }

    public boolean handleEdgeSwipeIntercept(MotionEvent ev) {
        if (!ExteraConfig.getNavigationDrawer()) {
            return false;
        }
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            startX = ev.getX();
            startY = ev.getY();
            startProgress = progress;
            startedEdgeSwipe = false;
            tracking = false;

            if (canStartClosedDrawerSwipe(ev)) {
                startedEdgeSwipe = true;
                if (velocityTracker == null) velocityTracker = VelocityTracker.obtain();
                velocityTracker.clear();
                velocityTracker.addMovement(ev);
            }
            return false;
        }

        if (startedEdgeSwipe) {
            if (velocityTracker != null) velocityTracker.addMovement(ev);
            if (ev.getAction() == MotionEvent.ACTION_MOVE) {
                if (getParent() instanceof DrawerLayoutContainer drawerLayoutContainer) {
                    INavigationLayout nav = drawerLayoutContainer.getParentActionBarLayout();
                    if (nav != null && (nav.isInPreviewMode() || nav.isPreviewOpenAnimationInProgress())) {
                        startedEdgeSwipe = false;
                        recycleVelocityTracker();
                        return false;
                    }
                }
                var dx = ev.getX() - startX;
                var dy = ev.getY() - startY;
                if (shouldBlockClosedDrawerSwipe(dx, dy)) {
                    startedEdgeSwipe = false;
                    return false;
                }
                if (shouldStartClosedDrawerTracking(dx, Math.abs(dy))) {
                    beginClosedDrawerTracking(ev, dx);
                    return true;
                }
            }
            if (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL) {
                startedEdgeSwipe = false;
            }
        }
        return false;
    }

    public boolean handleEdgeSwipeTouch(MotionEvent ev) {
        if (!ExteraConfig.getNavigationDrawer()) {
            return false;
        }
        if (!startedEdgeSwipe && !tracking) return false;
        if (velocityTracker == null) velocityTracker = VelocityTracker.obtain();
        velocityTracker.addMovement(ev);

        switch (ev.getAction()) {
            case MotionEvent.ACTION_MOVE:
                if (tracking) {
                    var dx = ev.getX() - startX;
                    var newProgress = Math.max(0, Math.min(1, startProgress + dx / drawerWidth));
                    setProgress(newProgress);
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (tracking) {
                    finishTracking();
                }
                startedEdgeSwipe = false;
                return true;
        }
        return true;
    }

    private boolean shouldBlockClosedDrawerSwipe(float dx, float dy) {
        var absDy = Math.abs(dy);
        var verticalScrollSlop = AndroidUtilities.touchSlop > 0 ? AndroidUtilities.touchSlop : getDrawerOpenTouchSlop();
        return absDy >= verticalScrollSlop && absDy > Math.abs(dx);
    }

    private boolean shouldStartClosedDrawerTracking(float dx, float dy) {
        return dx > 0 && dx / 3.0f > dy && Math.abs(dx) >= getDrawerOpenTouchSlop();
    }

    private void beginVisibleDrawerTracking(MotionEvent ev, float dx) {
        tracking = true;
        tapClosePending = false;
        if (isAnimating) {
            cancelAnimations();
        }
        offsetTrackingStart(ev, dx);
        resetTrackingVelocity(ev);
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
    }

    private void beginClosedDrawerTracking(MotionEvent ev, float dx) {
        tracking = true;
        tapClosePending = false;
        if (isAnimating) {
            cancelAnimations();
        }
        super.setVisibility(VISIBLE);
        applyDrawerPanelPadding();
        refreshContents();
        offsetTrackingStart(ev, dx);
        resetTrackingVelocity(ev);
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
    }

    private void offsetTrackingStart(MotionEvent ev, float dx) {
        startX += Math.signum(dx) * getTrackingTouchSlop(dx);
        startY = ev.getY();
        startProgress = progress;
    }

    private void resetTrackingVelocity(MotionEvent ev) {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        } else {
            velocityTracker.clear();
        }
        velocityTracker.addMovement(ev);
    }

    private float getTrackingTouchSlop(float dx) {
        return dx < 0 ? getDrawerCloseTouchSlop() : getDrawerOpenTouchSlop();
    }

    private float getDrawerOpenTouchSlop() {
        return AndroidUtilities.getPixelsInCM(OPEN_TOUCH_SLOP_CM, true);
    }

    private float getDrawerCloseTouchSlop() {
        return AndroidUtilities.getPixelsInCM(CLOSE_TOUCH_SLOP_CM, true);
    }

    private void finishTracking() {
        var velocityX = 0f;
        var velocityY = 0f;
        if (velocityTracker != null) {
            velocityTracker.computeCurrentVelocity(1000);
            velocityX = velocityTracker.getXVelocity();
            velocityY = velocityTracker.getYVelocity();
        }

        var swipeVelocity = AppUtils.getSwipeVelocity();
        var backAnimation = progress < 1f / (isOpen ? 1.25f : 5f) && (velocityX < swipeVelocity || Math.abs(velocityX) < Math.abs(velocityY))
                || velocityX < 0 && Math.abs(velocityX) >= swipeVelocity;
        if (!backAnimation) {
            var fast = !isOpen && Math.abs(velocityX) >= swipeVelocity;
            isOpen = true;
            animateProgress(1f, fast, velocityX);
        } else {
            var fast = isOpen && Math.abs(velocityX) >= swipeVelocity;
            isOpen = false;
            animateProgress(0f, fast, velocityX);
        }

        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
        tracking = false;
        startedEdgeSwipe = false;
        tapClosePending = false;
    }

    private boolean canOpen(MotionEvent ev) {
        var last = getLastFragment();
        if (last instanceof DialogsActivity dialogsActivity) {
            return dialogsActivity.canOpenDrawerBySwipe(ev);
        }
        return false;
    }

    private boolean canStartClosedDrawerSwipe(MotionEvent ev) {
        if (!canOpen(ev) || !(getParent() instanceof DrawerLayoutContainer drawerLayoutContainer)) {
            return false;
        }
        var nav = drawerLayoutContainer.getParentActionBarLayout();
        if (nav == null || nav.getFragmentStack().size() != 1 || !nav.allowSwipe()) {
            return false;
        }
        var navLast = nav.getLastFragment();
        if (navLast != null && navLast.getLastSheet() != null && navLast.getLastSheet().attachedToParent()) {
            return false;
        }
        var navView = nav.getView();
        if (navView == null) {
            return false;
        }
        navView.getHitRect(rect);
        if (!rect.contains((int) ev.getX(), (int) ev.getY())) {
            return false;
        }
        return findScrollingChild(navView, ev.getX() - rect.left, ev.getY() - rect.top) == null;
    }

    private View findScrollingChild(ViewGroup parent, float x, float y) {
        var count = parent.getChildCount();
        for (int i = 0; i < count; i++) {
            var child = parent.getChildAt(i);
            if (child.getVisibility() != VISIBLE) {
                continue;
            }
            child.getHitRect(rect);
            if (!rect.contains((int) x, (int) y)) {
                continue;
            }
            if (child.canScrollHorizontally(-1)) {
                return child;
            } else if (child instanceof ViewGroup childGroup) {
                var scrolling = findScrollingChild(childGroup, x - rect.left, y - rect.top);
                if (scrolling != null) {
                    return scrolling;
                }
            }
        }
        return null;
    }

    private BaseFragment getLastFragment() {
        var parent = (ViewGroup) getParent();
        if (parent instanceof DrawerLayoutContainer drawerLayoutContainer) {
            var nav = drawerLayoutContainer.getParentActionBarLayout();
            if (nav != null) {
                var last = nav.getLastFragment();
                if (last instanceof MainTabsActivity mainTabsActivity) {
                    return mainTabsActivity.getCurrentVisibleFragment();
                }
                return last;
            }
        }
        return null;
    }

    private void dismissSelectionPopup() {
        if (selectAnimatedEmojiDialog != null) {
            selectAnimatedEmojiDialog.dismiss();
            selectAnimatedEmojiDialog = null;
        }
    }

    private void recycleVelocityTracker() {
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }

    public void dispose() {
        cancelAnimations();
        onCloseComplete();
        recycleVelocityTracker();
        accountPickerView.dispose();
        unregisterNotifications();
        resetNavigationTranslationTarget();
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        if (!ExteraConfig.getImmersiveDrawerAnimation()) {
            float baseRadius = dp(DRAWER_ROUNDING_MAX_DP);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                RoundedCorner topRight = insets.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT);
                RoundedCorner bottomRight = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT);

                cachedTopRightRadius = topRight != null ? Math.max(baseRadius, topRight.getRadius() / 2f) : baseRadius;
                cachedBottomRightRadius = bottomRight != null ? Math.max(baseRadius, bottomRight.getRadius() / 2f) : baseRadius;
            } else {
                cachedTopRightRadius = baseRadius;
                cachedBottomRightRadius = baseRadius;
            }
        }
        return super.onApplyWindowInsets(insets);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        registerNotifications();
        Bulletin.addDelegate(bulletinContainer, new Bulletin.Delegate() {
            @Override
            public int getBottomOffset(int tag) {
                return AndroidUtilities.navigationBarHeight;
            }
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        Bulletin.removeDelegate(bulletinContainer);
        cancelAnimations();
        onCloseComplete();
        dismissSelectionPopup();
        recycleVelocityTracker();
        accountPickerView.dispose();
        resetNavigationTranslationTarget();
        unregisterNotifications();
    }

    private void registerNotifications() {
        if (notificationsRegistered) {
            return;
        }
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            var center = NotificationCenter.getInstance(a);
            center.addObserver(this, NotificationCenter.mainUserInfoChanged);
            center.addObserver(this, NotificationCenter.userEmojiStatusUpdated);
            center.addObserver(this, NotificationCenter.currentUserPremiumStatusChanged);
            center.addObserver(this, NotificationCenter.updateInterfaces);
            center.addObserver(this, NotificationCenter.appDidLogout);
            center.addObserver(this, NotificationCenter.attachMenuBotsDidLoad);
            center.addObserver(this, NotificationCenter.didUpdateConnectionState);
        }

        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.themeAccentListUpdated);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.notificationsCountUpdated);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.reloadInterface);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.pluginMenuItemsUpdated);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.proxySettingsChanged);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.proxyPingUpdated);
        notificationsRegistered = true;
    }

    private void unregisterNotifications() {
        if (!notificationsRegistered) {
            return;
        }
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            var center = NotificationCenter.getInstance(a);
            center.removeObserver(this, NotificationCenter.mainUserInfoChanged);
            center.removeObserver(this, NotificationCenter.userEmojiStatusUpdated);
            center.removeObserver(this, NotificationCenter.currentUserPremiumStatusChanged);
            center.removeObserver(this, NotificationCenter.updateInterfaces);
            center.removeObserver(this, NotificationCenter.appDidLogout);
            center.removeObserver(this, NotificationCenter.attachMenuBotsDidLoad);
            center.removeObserver(this, NotificationCenter.didUpdateConnectionState);
        }

        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.themeAccentListUpdated);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.notificationsCountUpdated);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.reloadInterface);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.pluginMenuItemsUpdated);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.proxySettingsChanged);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.proxyPingUpdated);
        notificationsRegistered = false;
    }

    public void onAccountChanged() {
        refreshContents();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.mainUserInfoChanged
                || id == NotificationCenter.userEmojiStatusUpdated
                || id == NotificationCenter.currentUserPremiumStatusChanged) {
            refreshAccountViews(account, true);
        } else if (id == NotificationCenter.updateInterfaces) {
            if (args.length > 0 && args[0] instanceof Integer mask) {
                refreshAccountViews(account, mask);
            }
            menuView.updateUnreadCounters(UserConfig.selectedAccount);
        } else if (id == NotificationCenter.didSetNewTheme) {
            updateColors();
        } else if (id == NotificationCenter.themeAccentListUpdated) {
            AndroidUtilities.runOnUIThread(this::updateColors);
        } else if (id == NotificationCenter.notificationsCountUpdated) {
            accountPickerView.updateUnreadCounters();
            menuView.updateUnreadCounters(UserConfig.selectedAccount);
        } else if (id == NotificationCenter.reloadInterface) {
            headerView.updateUserInfo();
            accountPickerView.updateUnreadCounters();
            menuView.updateUnreadCounters(UserConfig.selectedAccount);
            updateColors();
        } else if (id == NotificationCenter.attachMenuBotsDidLoad) {
            if (account == UserConfig.selectedAccount && isOpen) {
                refreshContents();
            }
        } else if (id == NotificationCenter.pluginMenuItemsUpdated) {
            if (isOpen) {
                refreshContents();
            }
        } else if (id == NotificationCenter.proxySettingsChanged || id == NotificationCenter.proxyPingUpdated || id == NotificationCenter.didUpdateConnectionState) {
            headerView.updateProxyStatus();
        } else if (id == NotificationCenter.appDidLogout) {
            refreshAccountViews(account, true);
            if (isOpen) {
                closeDrawer(false);
            }
        }
    }

    private void updateColors() {
        drawerPanel.setBackgroundColor(Theme.getColor(COLOR_KEY_DRAWER_BACKGROUND));
        headerView.updateColors();
        accountPickerView.updateColors();
        menuView.updateColors();
        invalidate();
    }

    private int calculateDrawerWidth() {
        return Math.min(dp(DRAWER_MAX_WIDTH_DP), AndroidUtilities.displaySize.x - dp(DRAWER_REMAINING_WIDTH_DP));
    }

    private void applyDrawerPanelPadding() {
        drawerPanel.setPadding(0, AndroidUtilities.statusBarHeight, 0, 0);
    }

    private int getPopupWidth() {
        return (int) Math.min(dp(POPUP_MAX_WIDTH_DP - POPUP_WIDTH_PADDING_DP), AndroidUtilities.displaySize.x * POPUP_WIDTH_RATIO);
    }
}
