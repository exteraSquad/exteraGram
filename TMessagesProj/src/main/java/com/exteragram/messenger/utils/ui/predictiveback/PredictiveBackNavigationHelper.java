/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.ui.predictiveback;

import android.view.View;
import android.view.ViewTreeObserver;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import androidx.annotation.RequiresApi;

import com.exteragram.messenger.plugins.PluginsController;

import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.Components.GroupCallPip;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.MainTabsActivity;
import org.telegram.ui.ViewPagerActivity;

import java.util.function.BooleanSupplier;

@RequiresApi(33)
public final class PredictiveBackNavigationHelper implements ViewTreeObserver.OnPreDrawListener,
        ViewTreeObserver.OnWindowFocusChangeListener {

    private static final Class<?>[] BACK_HANDLER_CLASSES = {
            LaunchActivity.class, ActionBarLayout.class, BaseFragment.class,
            ViewPagerActivity.class, MainTabsActivity.class, DialogsActivity.class
    };

    private final LaunchActivity activity;
    private final View decorView;
    private final OnBackInvokedCallback callback;
    private final BooleanSupplier canUseSystemBack;
    private boolean registered;
    private boolean gestureInProgress;
    private boolean detached;

    public PredictiveBackNavigationHelper(LaunchActivity activity, OnBackInvokedCallback callback, BooleanSupplier isSwitchingAccount) {
        this.activity = activity;
        this.decorView = activity.getWindow().getDecorView();
        this.callback = callback;
        this.canUseSystemBack = () -> !isSwitchingAccount.getAsBoolean() && canUseSystemBack(activity);
        decorView.getViewTreeObserver().addOnPreDrawListener(this);
        decorView.getViewTreeObserver().addOnWindowFocusChangeListener(this);
        update();
    }

    private static boolean canUseSystemBack(LaunchActivity activity) {
        INavigationLayout layout = activity.getActionBarLayout();
        if (!(layout instanceof ActionBarLayout) || !((ActionBarLayout) layout).canUseSystemBack()
                || hasFragments(activity.getLayersActionBarLayout()) || hasFragments(activity.getRightActionBarLayout())
                || PluginsController.getInstance().hasXposedHook("onBackPressed", BACK_HANDLER_CLASSES)
                || GroupCallPip.onBackPressed(false) || !activity.onBackPressed(false)) {
            return false;
        }
        BaseFragment fragment = layout.getLastFragment();
        if (fragment instanceof MainTabsActivity) {
            return ((MainTabsActivity) fragment).canUseSystemBack();
        }
        return fragment instanceof DialogsActivity && ((DialogsActivity) fragment).canUseSystemBack();
    }

    private static boolean hasFragments(INavigationLayout layout) {
        return layout != null && !layout.getFragmentStack().isEmpty();
    }

    public static void invalidate() {
        LaunchActivity activity = LaunchActivity.instance;
        if (activity != null) {
            activity.getWindow().getDecorView().invalidate();
        }
    }

    public void setGestureInProgress(boolean inProgress) {
        gestureInProgress = inProgress;
        if (!inProgress) decorView.invalidate();
    }

    private void update() {
        if (detached || gestureInProgress || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        boolean shouldRegister = !canUseSystemBack.getAsBoolean();
        if (registered == shouldRegister) {
            return;
        }
        if (shouldRegister) {
            activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
        } else {
            activity.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(callback);
        }
        registered = shouldRegister;
    }

    @Override
    public boolean onPreDraw() {
        update();
        return true;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus) update();
    }

    public void detach() {
        detached = true;
        ViewTreeObserver observer = decorView.getViewTreeObserver();
        if (observer.isAlive()) {
            observer.removeOnPreDrawListener(this);
            observer.removeOnWindowFocusChangeListener(this);
        }
        if (registered) {
            activity.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(callback);
            registered = false;
        }
    }
}
