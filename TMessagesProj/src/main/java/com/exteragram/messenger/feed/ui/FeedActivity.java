/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.feed.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.config.BottomNavigationBar;
import com.exteragram.messenger.feed.FeedController;
import com.exteragram.messenger.feed.FeedConfig;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.exteragram.messenger.utils.ui.tabs.MainTabsUiHelper;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.ChatActivityContainer;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.MainTabsActivity;
import org.telegram.ui.MainTabsActivityController;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ChatSearchTabs;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.utils.glass.source.GlassSourceRenderNode;

import java.util.List;

public class FeedActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, MainTabsActivity.TabFragmentDelegate {

    private final static int feed_settings = 75;
    private final static int feed_mark_read = 76;
    private final static long CATEGORY_FADE_OUT_MS = 120;
    private final static long CATEGORY_FADE_IN_MS = 280;
    private final static int NO_PENDING_CATEGORY = -1;

    private ChatActivityContainer chatContainer;
    private FeedCategoryTabsView categoryTabs;
    private int lastConfigGeneration;
    private int lastCategoriesVersion;
    private int lastSelectedCategoryId;
    private int headerAvatarShift;
    private int pendingCategoryId = NO_PENDING_CATEGORY;
    private ValueAnimator categoryTransitionAnimator;
    private final int[] headerLocation = new int[2];
    private final View.OnLayoutChangeListener headerAlignmentListener = (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
        if (left != oldLeft || right != oldRight) {
            v.post(this::updateHeaderAvatarAlignment);
        }
    };
    private boolean hasMainTabs;
    private boolean resumedOnce;
    private boolean uiActiveHeld;
    private boolean uiResumedHeld;
    private boolean embeddedChatCreated;
    private boolean viewportFullyVisible;
    private WindowInsetsCompat lastWindowInsets;
    private Runnable parentTabsGlassInvalidationCallback;
    private MainTabsActivityController mainTabsActivityController;

    public FeedActivity() {
        this(null);
    }

    public FeedActivity(Bundle args) {
        super(args);
    }

    public void setMainTabsActivityController(MainTabsActivityController controller) {
        mainTabsActivityController = controller;
    }

    @Override
    public void updateMainTabsVisibility() {
        if (mainTabsActivityController != null) {
            mainTabsActivityController.setTabsVisible(BottomNavigationBar.visible());
        }
    }

    public ChatActivity getChatActivity() {
        return chatContainer != null ? chatContainer.chatActivity : null;
    }

    public static void presentFeed(BaseFragment from) {
        if (AndroidUtilities.isTablet() && LaunchActivity.instance != null && LaunchActivity.instance.getRightActionBarLayout() != null) {
            final INavigationLayout layout = LaunchActivity.instance.getRightActionBarLayout();
            if (layout.getLastFragment() instanceof FeedActivity) {
                return;
            }
            if (!layout.getFragmentStack().isEmpty()) {
                for (int a = 0; a < layout.getFragmentStack().size() - 1; a++) {
                    layout.removeFragmentFromStack(layout.getFragmentStack().get(0));
                    a--;
                }
                layout.closeLastFragment(false);
            }
            layout.presentFragment(new INavigationLayout.NavigationParams(new FeedActivity()).setNoAnimation(true).forceRightLayout());
        } else if (from != null) {
            from.presentFragment(new FeedActivity());
        }
    }

    private final Runnable loadNewPosts = () -> {
        if (chatContainer == null || chatContainer.chatActivity == null) {
            return;
        }
        if (!uiResumedHeld) {
            return;
        }
        chatContainer.chatActivity.loadNewerFeed(true);
    };

    @Override
    public boolean onFragmentCreate() {
        hasMainTabs = arguments != null && arguments.getBoolean("hasMainTabs", false);
        viewportFullyVisible = !hasMainTabs;
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.didReceiveNewMessages);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.feedNeedReload);
        FeedController.getInstance(currentAccount).activateSelectedTimeline();
        lastConfigGeneration = FeedConfig.getInstance(currentAccount).getGeneration();
        lastSelectedCategoryId = FeedController.getInstance(currentAccount).getActiveTimelineId();
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        AndroidUtilities.cancelRunOnUIThread(loadNewPosts);
        destroyEmbeddedChat();
        if (uiResumedHeld) {
            uiResumedHeld = false;
            FeedController.getInstance(currentAccount).setUiResumed(false);
        }
        if (uiActiveHeld) {
            uiActiveHeld = false;
            FeedController.getInstance(currentAccount).setUiActive(false);
        }
        Bulletin.removeDelegate(this);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.didReceiveNewMessages);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.feedNeedReload);
        super.onFragmentDestroy();
    }

    private void destroyEmbeddedChat() {
        cancelCategoryTransition();
        if (chatContainer != null && chatContainer.chatActivity != null) {
            if (!hasMainTabs && embeddedChatCreated) {
                chatContainer.chatActivity.saveFeedScrollPosition();
            }
            chatContainer.chatActivity.setFeedChannelsChangedCallback(null);
            chatContainer.chatActivity.setParentGlass(null, null);
            if (embeddedChatCreated) {
                chatContainer.chatActivity.onFragmentDestroy();
            }
        }
        embeddedChatCreated = false;
        chatContainer = null;
        categoryTabs = null;
        headerAvatarShift = 0;
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (chatContainer != null && chatContainer.chatActivity != null
                && chatContainer.chatActivity.getActionBar() != null
                && chatContainer.chatActivity.getActionBar().isActionModeShowed()) {
            if (invoked) {
                chatContainer.chatActivity.clearSelectionMode();
            }
            return false;
        }
        return super.onBackPressed(invoked);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didReceiveNewMessages) {
            boolean scheduled = (Boolean) args[2];
            if (scheduled || chatContainer == null) {
                return;
            }
            if (!FeedController.getInstance(currentAccount).isIncludedChannelPost((Long) args[0])) {
                return;
            }
            AndroidUtilities.cancelRunOnUIThread(loadNewPosts);
            AndroidUtilities.runOnUIThread(loadNewPosts, 1000);
        } else if (id == NotificationCenter.feedNeedReload) {
            if (chatContainer != null && chatContainer.chatActivity != null) {
                boolean needsReload = args.length > 0 && Boolean.TRUE.equals(args[0]);
                chatContainer.chatActivity.onFeedChannelsChanged(needsReload);
            }
            updateFeedSubtitle();
        }
    }

    @Override
    public View createView(Context context) {
        destroyEmbeddedChat();
        lastWindowInsets = null;

        actionBar.setAddToContainer(false);
        actionBar.setVisibility(View.GONE);

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));

        if (hasMainTabs) {
            ViewCompat.setOnApplyWindowInsetsListener(frameLayout, (v, insets) -> {
                lastWindowInsets = insets;
                int tabsInset = BottomNavigationBar.visible() ? dp(MainTabsUiHelper.getTabsViewHeightDp()) : 0;
                if (tabsInset == 0) {
                    return insets;
                }
                return addTabsBottomInset(insets, tabsInset);
            });
            frameLayout.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(@NonNull View v) {
                    if (lastWindowInsets != null) {
                        ViewCompat.dispatchApplyWindowInsets(v, lastWindowInsets);
                    } else {
                        v.requestApplyInsets();
                    }
                }

                @Override
                public void onViewDetachedFromWindow(@NonNull View v) {
                }
            });
        }

        FrameLayout contentView = new FrameLayout(context);
        frameLayout.addView(contentView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        Bundle args = new Bundle();
        args.putInt("chatMode", ChatActivity.MODE_SEARCH);
        args.putInt("searchType", ChatActivity.SEARCH_FEED);
        args.putBoolean("hasMainTabs", hasMainTabs);
        chatContainer = new ChatActivityContainer(context, getParentLayout(), args) {
            boolean activityCreated = false;
            @Override
            protected void initChatActivity() {
                if (!activityCreated) {
                    activityCreated = true;
                    embeddedChatCreated = true;
                    super.initChatActivity();
                    applyFloatingWindowLayout();
                    setupChatActionBar();
                    setupChatTitle();
                    setupCategoryTabs();
                    if (lastWindowInsets != null && fragmentView != null) {
                        ViewCompat.dispatchApplyWindowInsets(fragmentView, lastWindowInsets);
                    }
                    invalidateParentTabsGlass();
                }
            }
        };
        chatContainer.chatActivity.isInsideContainer = false;
        chatContainer.chatActivity.setFeedChannelsChangedCallback(this::updateFeedSubtitle);
        chatContainer.chatActivity.setParentGlass(parentGlassPosition.isEmpty() ? null : parentGlassPosition, parentGlassCaptured);
        updateFeedViewportActive(viewportFullyVisible);
        if (!uiResumedHeld) {
            chatContainer.onPause();
        }
        contentView.addView(chatContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        if (!uiActiveHeld) {
            uiActiveHeld = true;
            FeedController.getInstance(currentAccount).setUiActive(true);
        }

        Bulletin.addDelegate(this, new Bulletin.Delegate() {
            @Override
            public int getTopOffset(int tag) {
                if (chatContainer != null && chatContainer.chatActivity != null) {
                    return chatContainer.chatActivity.getBulletinTopOffset();
                }
                return AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight();
            }

            @Override
            public int getBottomOffset(int tag) {
                if (chatContainer != null && chatContainer.chatActivity != null) {
                    return chatContainer.chatActivity.getBulletinBottomOffset();
                }
                return 0;
            }
        });

        return fragmentView;
    }

    @SuppressWarnings("deprecation")
    private static WindowInsetsCompat addTabsBottomInset(WindowInsetsCompat insets, int tabsInset) {
        final int systemBars = WindowInsetsCompat.Type.systemBars();
        final int navigationBars = WindowInsetsCompat.Type.navigationBars();
        return new WindowInsetsCompat.Builder(insets)
                .setStableInsets(addBottomInset(insets.getInsetsIgnoringVisibility(systemBars), tabsInset))
                .setInsets(systemBars, addBottomInset(insets.getInsets(systemBars), tabsInset))
                .setInsets(navigationBars, addBottomInset(insets.getInsets(navigationBars), tabsInset))
                .setInsetsIgnoringVisibility(systemBars, addBottomInset(insets.getInsetsIgnoringVisibility(systemBars), tabsInset))
                .setInsetsIgnoringVisibility(navigationBars, addBottomInset(insets.getInsetsIgnoringVisibility(navigationBars), tabsInset))
                .build();
    }

    private static Insets addBottomInset(Insets insets, int bottom) {
        return Insets.of(insets.left, insets.top, insets.right, insets.bottom + bottom);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateMainTabsVisibility();
        if (chatContainer != null) {
            chatContainer.onResume();
            updateFeedViewportActive(viewportFullyVisible);
        }
        if (!uiResumedHeld) {
            uiResumedHeld = true;
            FeedController.getInstance(currentAccount).setUiResumed(true);
        }
        if (hasMainTabs && fragmentView != null && lastWindowInsets != null) {
            ViewCompat.dispatchApplyWindowInsets(fragmentView, lastWindowInsets);
        }
        reattachCurrentFeedVideoTexture();
        boolean categorySwitched = applyCategoryChanges();
        int generation = FeedConfig.getInstance(currentAccount).getGeneration();
        if (categorySwitched) {
            lastConfigGeneration = generation;
        } else if (generation != lastConfigGeneration) {
            lastConfigGeneration = generation;
            if (chatContainer != null && chatContainer.chatActivity != null) {
                chatContainer.chatActivity.applyFeedConfigChange();
            }
        } else if (resumedOnce && chatContainer != null && chatContainer.chatActivity != null) {
            chatContainer.chatActivity.reconcileFeedList();
            chatContainer.chatActivity.refreshFeedUnreadDivider();
            if (!FeedController.getInstance(currentAccount).getMessages().isEmpty()) {
                chatContainer.chatActivity.loadNewerFeed(true);
            }
        }
        resumedOnce = true;
        updateFeedSubtitle();
    }

    @Override
    public void onBecomeFullyVisible() {
        super.onBecomeFullyVisible();
        viewportFullyVisible = true;
        updateMainTabsVisibility();
        updateFeedViewportActive(true);
        reattachCurrentFeedVideoTexture();
    }

    @Override
    public void onBecomeFullyHidden() {
        viewportFullyVisible = false;
        updateFeedViewportActive(false);
        super.onBecomeFullyHidden();
    }

    @Override
    public void onTransitionAnimationStart(boolean isOpen, boolean backward) {
        if (hasMainTabs) {
            viewportFullyVisible = false;
            updateFeedViewportActive(false);
        }
        super.onTransitionAnimationStart(isOpen, backward);
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        super.onTransitionAnimationEnd(isOpen, backward);
        if (hasMainTabs) {
            viewportFullyVisible = isOpen;
            updateFeedViewportActive(isOpen);
        }
    }

    @Override
    public void onParentBecomeFullyVisible() {
        reattachCurrentFeedVideoTexture();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (chatContainer != null) {
            updateFeedViewportActive(false);
            chatContainer.onPause();
        }
        if (uiResumedHeld) {
            uiResumedHeld = false;
            FeedController.getInstance(currentAccount).setUiResumed(false);
        }
    }

    private void updateFeedViewportActive(boolean active) {
        if (chatContainer != null && chatContainer.chatActivity != null) {
            chatContainer.chatActivity.setFeedViewportActive(active);
        }
    }

    @Override
    public boolean canParentTabsSlide(MotionEvent ev, boolean forward) {
        if (categoryTabs != null && categoryTabs.isTouchInside(ev)) {
            return false;
        }
        if (chatContainer != null && chatContainer.chatActivity != null
                && chatContainer.chatActivity.getActionBar() != null
                && chatContainer.chatActivity.getActionBar().isActionModeShowed()) {
            return false;
        }
        return true;
    }

    @Override
    public boolean isLightStatusBar() {
        if (chatContainer != null && chatContainer.chatActivity != null) {
            return chatContainer.chatActivity.isLightStatusBar();
        }
        return !Theme.isCurrentThemeDark();
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    public boolean drawEdgeNavigationBar() {
        return false;
    }

    private void reattachCurrentFeedVideoTexture() {
        if (chatContainer != null && chatContainer.chatActivity != null) {
            chatContainer.chatActivity.reattachCurrentFeedVideoTexture();
        }
    }

    private void setupChatActionBar() {
        if (chatContainer == null || chatContainer.chatActivity == null) {
            return;
        }
        ActionBar chatActionBar = chatContainer.chatActivity.getActionBar();
        if (chatActionBar == null) {
            return;
        }
        ActionBarMenu menu = chatActionBar.createMenu();
        if (menu.getItem(feed_mark_read) == null) {
            ActionBarMenuItem markReadItem = menu.addItem(feed_mark_read, R.drawable.msg_markread, chatContainer.chatActivity.themeDelegate);
            markReadItem.setContentDescription(LocaleController.getString(R.string.MarkAllAsRead));
        }
        if (menu.getItem(feed_settings) == null) {
            ActionBarMenuItem feedSettingsItem = menu.addItem(feed_settings, R.drawable.msg_settings, chatContainer.chatActivity.themeDelegate);
            feedSettingsItem.setContentDescription(LocaleController.getString(R.string.FeedSettings));
        }
        if (hasMainTabs) {
            applyMainTabsHeaderLayout();
        }
        ActionBar.ActionBarMenuOnItemClick chatClickListener = chatActionBar.getActionBarMenuOnItemClick();
        chatActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1 && hasMainTabs && !chatActionBar.isActionModeShowed()) {
                    return;
                }
                if (id == feed_mark_read) {
                    showMarkAllReadDialog();
                    return;
                }
                if (id == feed_settings) {
                    presentFragment(new FeedChannelsActivity());
                    return;
                }
                if (chatClickListener != null) {
                    chatClickListener.onItemClick(id);
                }
            }

            @Override
            public boolean canOpenMenu() {
                return chatClickListener == null || chatClickListener.canOpenMenu();
            }
        });
    }

    private void applyFloatingWindowLayout() {
        if (getParentLayout() == null || !getParentLayout().isLayersLayout()) {
            return;
        }
        if (chatContainer == null || chatContainer.chatActivity == null) {
            return;
        }
        ChatActivity chatActivity = chatContainer.chatActivity;
        if (chatActivity.getActionBar() != null) {
            chatActivity.getActionBar().setOccupyStatusBar(false);
        }
        if (chatActivity.avatarContainer != null) {
            chatActivity.avatarContainer.setOccupyStatusBar(false);
        }
        if (chatActivity.contentView != null) {
            chatActivity.contentView.setOccupyStatusBar(false);
        }
    }

    private void applyMainTabsHeaderLayout() {
        if (chatContainer == null || chatContainer.chatActivity == null || chatContainer.chatActivity.avatarContainer == null) {
            return;
        }
        var layoutParams = chatContainer.chatActivity.avatarContainer.getLayoutParams();
        if (!(layoutParams instanceof ViewGroup.MarginLayoutParams marginLayoutParams)) {
            return;
        }
        int leftMargin = dp(ExteraConfig.getNewChatHeaderStyle() ? 12 : 0) + headerAvatarShift;
        if (marginLayoutParams.leftMargin != leftMargin) {
            marginLayoutParams.leftMargin = leftMargin;
            chatContainer.chatActivity.avatarContainer.setLayoutParams(marginLayoutParams);
        }
    }

    private void setupCategoryTabs() {
        if (chatContainer == null || chatContainer.chatActivity == null || chatContainer.chatActivity.hashtagSearchTabs == null) {
            return;
        }
        ChatSearchTabs host = chatContainer.chatActivity.hashtagSearchTabs;
        categoryTabs = new FeedCategoryTabsView(chatContainer.getContext(), this, currentAccount, chatContainer.chatActivity.getResourceProvider(), new FeedCategoryTabsView.Delegate() {
            @Override
            public void onCategorySelected(int categoryId) {
                switchCategoryAnimated(categoryId);
            }

            @Override
            public void onCreateCategory() {
                presentFragment(new FeedCategoryEditActivity(0, true));
            }

            @Override
            public void onEditCategory(FeedConfig.Category category) {
                presentFragment(new FeedCategoryEditActivity(category.getId(), false));
            }

            @Override
            public void onHideCategory(FeedConfig.Category category) {
                hideCategory(category);
            }

            @Override
            public void onDeleteCategory(FeedConfig.Category category) {
                FeedCategoryEditActivity.showDeleteDialog(FeedActivity.this, category, FeedActivity.this::onCategoryRemoved);
            }
        });
        categoryTabs.attachTo(host);
        lastCategoriesVersion = FeedConfig.getInstance(currentAccount).getCategoriesVersion();
        host.show(categoryTabs.hasCategories(), false);
        if (hasMainTabs) {
            categoryTabs.addOnLayoutChangeListener(headerAlignmentListener);
            if (chatContainer.chatActivity.avatarContainer != null) {
                chatContainer.chatActivity.avatarContainer.addOnLayoutChangeListener(headerAlignmentListener);
            }
        }
    }

    private void updateHeaderAvatarAlignment() {
        if (!hasMainTabs || chatContainer == null || chatContainer.chatActivity == null || chatContainer.chatActivity.avatarContainer == null) {
            return;
        }
        View avatar = chatContainer.chatActivity.avatarContainer.getAvatarImageView();
        View host = chatContainer.chatActivity.hashtagSearchTabs;
        int shift = 0;
        if (!LocaleController.isRTL && categoryTabs != null && categoryTabs.hasCategories() && host != null && categoryTabs.getWidth() > 0 && avatar.getWidth() > 0) {
            host.getLocationInWindow(headerLocation);
            int stripLeft = headerLocation[0] + categoryTabs.getLeft();
            avatar.getLocationInWindow(headerLocation);
            shift = headerAvatarShift + stripLeft - headerLocation[0];
        }
        if (shift != headerAvatarShift) {
            headerAvatarShift = shift;
            applyMainTabsHeaderLayout();
        }
    }

    private boolean applyCategoryChanges() {
        FeedConfig config = FeedConfig.getInstance(currentAccount);
        if (categoryTabs != null && lastCategoriesVersion != config.getCategoriesVersion()) {
            lastCategoriesVersion = config.getCategoriesVersion();
            categoryTabs.update();
            ChatSearchTabs host = chatContainer != null && chatContainer.chatActivity != null ? chatContainer.chatActivity.hashtagSearchTabs : null;
            if (host != null && host.isShown() != categoryTabs.hasCategories()) {
                host.show(categoryTabs.hasCategories());
            }
            updateHeaderAvatarAlignment();
        }
        if (config.getSelectedCategoryId() != lastSelectedCategoryId) {
            onCategorySwitched();
            return true;
        }
        return false;
    }

    private void onCategorySwitched() {
        FeedConfig config = FeedConfig.getInstance(currentAccount);
        int previousCategoryId = lastSelectedCategoryId;
        lastSelectedCategoryId = config.getSelectedCategoryId();
        lastConfigGeneration = config.getGeneration();
        if (categoryTabs != null) {
            lastCategoriesVersion = config.getCategoriesVersion();
            categoryTabs.update();
        }
        if (chatContainer != null && chatContainer.chatActivity != null) {
            ChatActivity chatActivity = chatContainer.chatActivity;
            if (chatActivity.getActionBar() != null && chatActivity.getActionBar().isActionModeShowed()) {
                chatActivity.clearSelectionMode();
            }
            chatActivity.switchFeedTimeline(previousCategoryId);
        } else {
            FeedController.getInstance(currentAccount).activateSelectedTimeline();
        }
        updateFeedSubtitle();
    }

    private void hideCategory(FeedConfig.Category category) {
        FeedConfig config = FeedConfig.getInstance(currentAccount);
        boolean wasSelected = config.getSelectedCategoryId() == category.getId();
        if (!config.setCategoryHidden(category.getId(), true)) {
            return;
        }
        FeedController.getInstance(currentAccount).notifyCategoriesChanged();
        onCategoryRemoved();
        BulletinFactory.of(this).createSimpleBulletin(R.raw.caption_hide, AndroidUtilities.replaceTags(LocaleController.formatString(R.string.FeedCategoryHiddenInfo, category.getTitle())), LocaleController.getString(R.string.Undo), () -> {
            if (!config.setCategoryHidden(category.getId(), false)) {
                return;
            }
            FeedController.getInstance(currentAccount).notifyCategoriesChanged();
            applyCategoryChanges();
            if (wasSelected) {
                switchCategoryAnimated(category.getId());
            }
        }).show();
    }

    private void onCategoryRemoved() {
        int selectedCategoryId = FeedConfig.getInstance(currentAccount).getSelectedCategoryId();
        if (selectedCategoryId != lastSelectedCategoryId) {
            switchCategoryAnimated(selectedCategoryId);
        } else {
            applyCategoryChanges();
        }
    }

    private void switchCategoryAnimated(int categoryId) {
        if (categoryTabs != null) {
            categoryTabs.setSelectedCategory(categoryId);
        }
        View listView = getFeedListView();
        if (listView == null || !viewportFullyVisible) {
            pendingCategoryId = NO_PENDING_CATEGORY;
            FeedController.getInstance(currentAccount).selectCategory(categoryId);
            applyCategoryChanges();
            return;
        }
        boolean fadingOut = pendingCategoryId != NO_PENDING_CATEGORY;
        pendingCategoryId = categoryId;
        if (fadingOut) {
            return;
        }
        if (categoryTransitionAnimator != null) {
            categoryTransitionAnimator.cancel();
        }
        final float fromAlpha = listView.getAlpha();
        final float fromTranslation = listView.getTranslationX();
        final float toTranslation = -getTransitionDirection(lastSelectedCategoryId, categoryId) * dp(24);
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            setFeedContentTransform(listView, AndroidUtilities.lerp(fromAlpha, 0f, t), AndroidUtilities.lerp(fromTranslation, toTranslation, t));
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (categoryTransitionAnimator == animation) {
                    categoryTransitionAnimator = null;
                    applyPendingCategory();
                }
            }
        });
        animator.setDuration((long) (CATEGORY_FADE_OUT_MS * fromAlpha));
        animator.setInterpolator(CubicBezierInterpolator.EASE_IN);
        categoryTransitionAnimator = animator;
        animator.start();
    }

    private void applyPendingCategory() {
        int categoryId = pendingCategoryId;
        int previousCategoryId = lastSelectedCategoryId;
        pendingCategoryId = NO_PENDING_CATEGORY;
        if (categoryId != NO_PENDING_CATEGORY) {
            FeedController.getInstance(currentAccount).selectCategory(categoryId);
        }
        if (!applyCategoryChanges() && categoryTabs != null) {
            categoryTabs.update();
        }
        View listView = getFeedListView();
        if (listView == null) {
            return;
        }
        final float fromTranslation = lastSelectedCategoryId == previousCategoryId
                ? listView.getTranslationX()
                : getTransitionDirection(previousCategoryId, lastSelectedCategoryId) * dp(24);
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            setFeedContentTransform(listView, t, fromTranslation * (1f - t));
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (categoryTransitionAnimator == animation) {
                    categoryTransitionAnimator = null;
                }
            }
        });
        animator.setDuration(CATEGORY_FADE_IN_MS);
        animator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        categoryTransitionAnimator = animator;
        animator.start();
    }

    private void cancelCategoryTransition() {
        pendingCategoryId = NO_PENDING_CATEGORY;
        if (categoryTransitionAnimator != null) {
            ValueAnimator animator = categoryTransitionAnimator;
            categoryTransitionAnimator = null;
            animator.cancel();
        }
        View listView = getFeedListView();
        if (listView != null) {
            setFeedContentTransform(listView, 1f, 0f);
        }
    }

    private void setFeedContentTransform(View listView, float alpha, float translationX) {
        listView.setAlpha(alpha);
        listView.setTranslationX(translationX);
        View emptyView = chatContainer != null && chatContainer.chatActivity != null ? chatContainer.chatActivity.getFeedEmptyView() : null;
        if (emptyView != null) {
            emptyView.setAlpha(alpha);
            emptyView.setTranslationX(translationX);
        }
    }

    private View getFeedListView() {
        return chatContainer != null && chatContainer.chatActivity != null ? chatContainer.chatActivity.getChatListView() : null;
    }

    private int getTransitionDirection(int fromCategoryId, int toCategoryId) {
        return getCategoryOrder(toCategoryId) >= getCategoryOrder(fromCategoryId) ? 1 : -1;
    }

    private int getCategoryOrder(int categoryId) {
        List<FeedConfig.Category> categories = FeedConfig.getInstance(currentAccount).getCategories();
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).getId() == categoryId) {
                return i + 1;
            }
        }
        return 0;
    }

    private void showMarkAllReadDialog() {
        if (getParentActivity() == null) {
            return;
        }
        var builder = new AlertDialog.Builder(getParentActivity(), getResourceProvider());
        builder.setTitle(LocaleController.getString(R.string.MarkAllAsRead));
        builder.setMessage(LocaleController.getString(R.string.FeedMarkAllReadConfirm));
        builder.setPositiveButton(LocaleController.getString(R.string.MarkAsRead), (dialog, which) -> {
            markAllRead();
            BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, LocaleController.getString(R.string.FeedMarkAllReadDone)).show();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    public void markAllRead() {
        if (chatContainer != null && chatContainer.chatActivity != null) {
            chatContainer.chatActivity.markFeedAsRead();
        } else {
            FeedController.getInstance(currentAccount).markAllRead();
        }
    }

    @Override
    public void onParentScrollToTop() {
        if (chatContainer != null && chatContainer.chatActivity != null) {
            chatContainer.chatActivity.onPageDownClicked(false);
        }
    }

    private void setupChatTitle() {
        if (chatContainer == null || chatContainer.chatActivity == null || chatContainer.chatActivity.avatarContainer == null) {
            return;
        }
        chatContainer.chatActivity.avatarContainer.setTitle(LocaleController.getString(R.string.Feed));
        chatContainer.chatActivity.avatarContainer.setFeedAvatar();
        updateFeedSubtitle();
    }

    private void updateFeedSubtitle() {
        FeedController controller = FeedController.getInstance(currentAccount);
        setFeedSubtitle(controller.getIncludedChannelCount());
        controller.loadChannels((channels, includedCount, failed, configGeneration) ->
                setFeedSubtitle(failed ? controller.getIncludedChannelCount() : includedCount));
    }

    private void setFeedSubtitle(int count) {
        if (chatContainer == null || chatContainer.chatActivity == null || chatContainer.chatActivity.avatarContainer == null) {
            return;
        }
        chatContainer.chatActivity.avatarContainer.setSubtitle(count < 0
                ? LocaleController.getString(R.string.Loading)
                : LocaleController.formatPluralString("Channels", count));
        View subtitle = chatContainer.chatActivity.avatarContainer.getSubtitleTextView();
        if (subtitle != null) {
            subtitle.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public GlassSourceRenderNode getGlassSource() {
        if (chatContainer != null && chatContainer.chatActivity != null) {
            return chatContainer.chatActivity.getGlassSource();
        }
        return null;
    }

    @Override
    public GlassSourceRenderNode getFrostedGlassSource() {
        return getGlassSource();
    }

    private final RectF parentGlassPosition = new RectF();
    private Runnable parentGlassCaptured;

    @Override
    public void setParentGlass(@Nullable RectF position, @Nullable Runnable onCaptured) {
        if (position == null) {
            parentGlassPosition.setEmpty();
        } else {
            parentGlassPosition.set(position);
        }
        parentGlassCaptured = onCaptured;
        if (chatContainer != null && chatContainer.chatActivity != null) {
            chatContainer.chatActivity.setParentGlass(position, onCaptured);
        }
    }

    @Override
    public void setParentTabsGlassInvalidationCallback(Runnable callback) {
        parentTabsGlassInvalidationCallback = callback;
    }

    private void invalidateParentTabsGlass() {
        if (parentTabsGlassInvalidationCallback != null) {
            parentTabsGlassInvalidationCallback.run();
        }
    }
}